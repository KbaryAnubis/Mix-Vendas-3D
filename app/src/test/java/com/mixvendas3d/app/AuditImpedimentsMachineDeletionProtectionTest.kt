package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.MachineValidationException
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class AuditImpedimentsMachineDeletionProtectionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
    }

    private suspend fun findMachine(app: MixVendasApplication, machineId: String) =
        app.machineRepository.observeMachines().first().find { it.id == machineId }

    private suspend fun createLinkedSale(
        app: MixVendasApplication,
        machineId: String,
        saleDate: LocalDate,
    ): String {
        val person = app.catalogRepository.observePeople().first().firstOrNull()
            ?: app.catalogRepository.createPerson("Carla")
        val product = app.catalogRepository.observeAllProducts().first().firstOrNull()
            ?: app.catalogRepository.createProduct(
                name = "Vaso 3D",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(person.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
        val machine = findMachine(app, machineId)!!
        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(product = product, quantity = 1)),
                finalAmountOverride = MoneyCents(5_000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.DEBIT,
                machine = machine,
                cardBrand = CardBrand.VISA,
                cardBrandKey = CardBrand.VISA.storageKey,
                note = "Venda vinculada à maquininha",
            ),
        )
        return app.saleRepository.saveSale(
            result = calc,
            saleDate = saleDate,
            zoneId = ZoneId.of("UTC"),
        ).id
    }

    @Test
    fun test01_blocksMachineDeletionInRepositoryAndApplicationWhenLinkedToOneOrMultipleSalesAndPreservesSnapshots() = runBlocking<Unit>(Dispatchers.IO) {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val machine = app.machineRepository.createMachine(
            MachineInputDraft(
                name = "Stone Protegida",
                debitRateBps = 190,
                creditEnabled = true,
                creditDefaultRateBps = 350,
                brandRatesEnabled = false,
                brandRatesBps = emptyMap(),
                installmentsEnabled = false,
                installmentAddonsBps = emptyMap(),
            ),
        )

        // 1 venda vinculada -> deve bloquear indicando 1 venda
        val sale1Id = createLinkedSale(app, machine.id, LocalDate.of(2026, 4, 10))
        assertEquals(1, app.database.saleDao().countSalesUsingMachine(machine.id))
        assertEquals(1, app.machineRepository.countLinkedSales(machine.id))
        try {
            app.machineRepository.deleteMachine(machine.id)
            fail("Exclusão de maquininha com 1 venda vinculada deveria ser bloqueada")
        } catch (ex: MachineValidationException) {
            assertTrue("Mensagem deve citar 1 venda registrada: ${ex.message}", ex.message.orEmpty().contains("1 venda"))
        }
        assertNotNull(findMachine(app, machine.id))

        // 2 vendas vinculadas -> deve bloquear indicando 2 vendas e preservar integralmente os snapshots
        val sale2Id = createLinkedSale(app, machine.id, LocalDate.of(2026, 4, 11))
        assertEquals(2, app.machineRepository.countLinkedSales(machine.id))
        try {
            app.machineRepository.deleteMachine(machine.id)
            fail("Exclusão de maquininha com 2 vendas vinculadas deveria ser bloqueada")
        } catch (ex: MachineValidationException) {
            assertTrue("Mensagem deve citar 2 vendas registradas: ${ex.message}", ex.message.orEmpty().contains("2 vendas"))
        }

        // Maquininha, vendas e snapshots financeiros permanecem intactos
        assertNotNull(findMachine(app, machine.id))
        val salesById = app.saleRepository.observeSales().first().associateBy { it.id }
        val sale1 = salesById[sale1Id]
        val sale2 = salesById[sale2Id]
        assertNotNull(sale1)
        assertNotNull(sale2)
        assertEquals("Stone Protegida", sale1!!.calculation.machineSnapshot?.machineName)
        assertEquals(190, sale1.calculation.machineSnapshot?.debitRateBps)
        assertEquals(5_000L, sale1.calculation.finalAmount.rawCents)
        assertEquals("Stone Protegida", sale2!!.calculation.machineSnapshot?.machineName)
    }

    @Test
    fun test02_allowsMachineDeletionWhenNoSalesReferenceIt() = runBlocking<Unit>(Dispatchers.IO) {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val machine = app.machineRepository.createMachine(
            MachineInputDraft(
                name = "Maquininha Sem Vendas",
                debitRateBps = 150,
                creditEnabled = true,
                creditDefaultRateBps = 310,
                brandRatesEnabled = false,
                brandRatesBps = emptyMap(),
                installmentsEnabled = false,
                installmentAddonsBps = emptyMap(),
            ),
        )

        assertEquals(0, app.machineRepository.countLinkedSales(machine.id))
        app.machineRepository.deleteMachine(machine.id)
        assertNull(findMachine(app, machine.id))
    }

    @Test
    fun test03_machinesScreenUiBlocksDeleteDialogWithSalesCountAndAllowsUnlinkedDelete() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val (linkedMachine, freeMachine) = runBlocking(Dispatchers.IO) {
            val linked = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "PagBank Com Vendas",
                    debitRateBps = 199,
                    creditEnabled = true,
                    creditDefaultRateBps = 349,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            val free = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "SumUp Livre",
                    debitRateBps = 180,
                    creditEnabled = true,
                    creditDefaultRateBps = 320,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            createLinkedSale(app, linked.id, LocalDate.of(2026, 4, 12))
            createLinkedSale(app, linked.id, LocalDate.of(2026, 4, 13))
            linked to free
        }

        // Navega para Maquininhas
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mais_item_machines").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_machines").assertIsDisplayed()

        // Aguarda carregamento do botão de exclusão da maquininha vinculada
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runCatching {
                composeRule.onNodeWithTag("machines_list_lazy")
                    .performScrollToNode(hasTestTag("delete_machine_${linkedMachine.id}"))
                true
            }.getOrDefault(false)
        }

        // Abre exclusão da maquininha vinculada -> diálogo bloqueia e informa "2 vendas registradas"
        composeRule.onNodeWithTag("delete_machine_${linkedMachine.id}").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("delete_machine_dialog", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("delete_machine_dialog", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Exclusão bloqueada", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("2 vendas registradas", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_machine_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("cancel_delete_machine_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("delete_machine_dialog", useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }

        // Maquininha vinculada continua no banco
        assertNotNull(runBlocking(Dispatchers.IO) { findMachine(app, linkedMachine.id) })

        // Abre exclusão da maquininha livre -> botão habilitado e exclusão conclui normalmente
        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("delete_machine_${freeMachine.id}"))
        composeRule.onNodeWithTag("delete_machine_${freeMachine.id}").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("delete_machine_dialog", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("delete_machine_dialog", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_machine_button").assertIsEnabled().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking(Dispatchers.IO) { findMachine(app, freeMachine.id) == null }
        }
        assertNull(runBlocking(Dispatchers.IO) { findMachine(app, freeMachine.id) })
    }
}
