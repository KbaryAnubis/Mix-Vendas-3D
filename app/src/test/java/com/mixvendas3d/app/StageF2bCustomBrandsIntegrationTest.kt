package com.mixvendas3d.app

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.data.repository.RoomSystemUpdateRepository
import com.mixvendas3d.app.domain.calculation.CustomCardBrandInput
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.MachineValidationException
import com.mixvendas3d.app.domain.calculation.MachineValidator
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleCalculationException
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.calculation.SalePersistenceException
import com.mixvendas3d.app.domain.calculation.SalePersistenceValidator
import com.mixvendas3d.app.domain.export.BackupCollectionType
import com.mixvendas3d.app.domain.export.CsvSaleExporter
import com.mixvendas3d.app.domain.export.JsonBackupErrorCode
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupMergeDecisionValidator
import com.mixvendas3d.app.domain.export.JsonBackupMergeExecutor
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanner
import com.mixvendas3d.app.domain.export.JsonBackupMergePreviewBuilder
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.update.SystemUpdateConflictCode
import com.mixvendas3d.app.ui.screens.BackupConflictFormatter
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StageF2bCustomBrandsIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val createdDbNames = mutableListOf<String>()

    @After
    fun tearDown() {
        val app = context as MixVendasApplication
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
        createdDbNames.forEach { dbName ->
            runCatching { context.deleteDatabase(dbName) }
        }
        createdDbNames.clear()
    }

    private fun newInMemoryDb(): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun newFileDb(tag: String): AppDatabase {
        val dbName = shortTestDatabaseName(tag)
        createdDbNames.add(dbName)
        context.deleteDatabase(dbName)
        return Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
            )
            .allowMainThreadQueries()
            .build()
    }

    private fun waitUiUntil(timeoutMillis: Long = 5_000L, condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun openMachinesScreen() {
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_mais").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("mais_item_machines").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_machines").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun selectProductsInVender(productIds: List<String>) {
        composeRule.onNodeWithTag("open_product_picker_button")
            .performScrollTo()
            .performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("product_picker_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        productIds.forEach { productId ->
            waitUiUntil {
                runCatching {
                    composeRule.onNodeWithTag("product_picker_list")
                        .performScrollToNode(hasTestTag("picker_row_$productId"))
                    true
                }.getOrDefault(false)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("picker_checkbox_$productId").performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("close_product_picker_button").performClick()
        if (productIds.isNotEmpty()) {
            val firstId = productIds.first()
            waitUiUntil {
                composeRule.onAllNodesWithTag("selected_inc_$firstId").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun selectMachineInVender(machineId: String) {
        composeRule.onNodeWithTag("machine_selector_dropdown")
            .performScrollTo()
            .performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("machine_picker_list")
                    .performScrollToNode(hasTestTag("machine_picker_row_$machineId"))
                true
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_picker_row_$machineId").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_dialog").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun assertMathematicalInvariants(sale: PersistedSale) {
        val calc = sale.calculation
        assertEquals(calc.finalAmount.rawCents, calc.originalSubtotal.rawCents - calc.discountTotal.rawCents)
        assertEquals(calc.finalAmount.rawCents, calc.items.sumOf { it.finalAmount.rawCents })
        assertEquals(calc.feeTotal.rawCents, calc.items.sumOf { it.feeAmount.rawCents })
        assertEquals(calc.netTotal.rawCents, calc.personTotals.sumOf { it.netAmount.rawCents })
        assertEquals(calc.netTotal.rawCents, calc.finalAmount.rawCents - calc.feeTotal.rawCents)
    }

    @Test
    fun `01 validator and codec enforce length, UTF-8 bytes and control character limits`() {
        val exactly60Ascii = "A".repeat(CustomBrandKeyCodec.MAX_CUSTOM_BRAND_NAME_LENGTH)
        val encoded60 = CustomBrandKeyCodec.encodeKey(exactly60Ascii)
        assertEquals(exactly60Ascii, CustomBrandKeyCodec.decodeNameOrNull(encoded60))
        assertFalse(encoded60.contains(':'))

        // Rejeita > 60 caracteres
        val over60 = "A".repeat(CustomBrandKeyCodec.MAX_CUSTOM_BRAND_NAME_LENGTH + 1)
        assertThrows(MachineValidationException::class.java) {
            MachineValidator.validateCustomBrands(listOf(CustomCardBrandInput(name = over60, rateBps = 250)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomBrandKeyCodec.encodeKey(over60)
        }

        // Rejeita caracteres de controle mesmo nas pontas antes do trim
        listOf("Crédz\nVIP", "Crédz\tVIP", "\u0000Crédz", "Crédz\r").forEach { dirty ->
            assertThrows(MachineValidationException::class.java) {
                MachineValidator.validateCustomBrands(listOf(CustomCardBrandInput(name = dirty, rateBps = 250)))
            }
            assertThrows(IllegalArgumentException::class.java) {
                CustomBrandKeyCodec.encodeKey(dirty)
            }
        }

        // Chaves com UTF-8 excessivo ou caracteres de controle em hex são rejeitadas na decodificação
        assertNull(CustomBrandKeyCodec.decodeNameOrNull("CUSTOM_610a62")) // "a\nb"
        val oversizedHex = CustomBrandKeyCodec.CUSTOM_BRAND_PREFIX + "61".repeat(CustomBrandKeyCodec.MAX_CUSTOM_BRAND_UTF8_BYTES + 1)
        assertNull(CustomBrandKeyCodec.decodeNameOrNull(oversizedHex))
        assertFalse(CustomBrandKeyCodec.isKnownBrandKey(oversizedHex))

        // Nomes com ':' são aceitos e codificados sem ':'
        val withColon = "Crédz: VIP São Paulo"
        val encodedWithColon = CustomBrandKeyCodec.encodeKey(withColon)
        assertFalse(encodedWithColon.contains(':'))
        assertEquals(withColon, CustomBrandKeyCodec.decodeNameOrNull(encodedWithColon))
        assertEquals(withColon, CustomBrandKeyCodec.resolveDisplayLabel(encodedWithColon))
    }

    @Test
    fun `02 machines UI adds, preserves across toggles, edits and deletes custom brands in Room`() {
        val app = context as MixVendasApplication
        openMachinesScreen()

        composeRule.onNodeWithTag("new_machine_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_editor_dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("machine_name_input"))
        composeRule.onNodeWithTag("machine_name_input").performTextInput("Stone Custom")
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("machine_debit_rate_input"))
        composeRule.onNodeWithTag("machine_debit_rate_input").performTextInput("1,75")
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("machine_credit_default_rate_input"))
        composeRule.onNodeWithTag("machine_credit_default_rate_input").performTextInput("3,50")

        // Ativa taxas por bandeira e adiciona duas bandeiras personalizadas (uma com ':' e acento)
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_brand_rates_switch"))
        composeRule.onNodeWithTag("editor_brand_rates_switch").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("custom_brand_name_input"))

        composeRule.onNodeWithTag("custom_brand_name_input").performScrollTo().performTextInput("Crédz: VIP")
        composeRule.onNodeWithTag("new_custom_brand_credit_rate_input").performScrollTo().performTextInput("4,25")
        composeRule.onNodeWithTag("add_custom_brand_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        val credzKey = CustomBrandKeyCodec.encodeKey("CRÉDZ: VIP")
        composeRule.onNodeWithTag("custom_brand_row_$credzKey").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("custom_brand_credit_rate_$credzKey").performScrollTo().assert(hasText("4,25"))

        // Tenta adicionar duplicata com acento/caixa diferente -> erro visível em custom_brand_error_banner
        composeRule.onNodeWithTag("custom_brand_name_input").performScrollTo().performTextInput("CREDZ:   vip")
        composeRule.onNodeWithTag("new_custom_brand_credit_rate_input").performScrollTo().performTextInput("4,99")
        composeRule.onNodeWithTag("add_custom_brand_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("custom_brand_error_banner").performScrollTo().assertIsDisplayed()

        // Adiciona segunda bandeira personalizada
        composeRule.onNodeWithTag("custom_brand_name_input").performScrollTo().performTextReplacement("Vale Loja")
        composeRule.onNodeWithTag("new_custom_brand_credit_rate_input").performScrollTo().performTextReplacement("5,10")
        composeRule.onNodeWithTag("add_custom_brand_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        val valeKey = CustomBrandKeyCodec.encodeKey("VALE LOJA")

        // Recolher e desativar/reativar bandeiras preserva rascunhos de personalizadas
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_brand_rates_switch"))
        composeRule.onNodeWithTag("editor_brand_rates_switch").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("custom_brand_row_$credzKey").assertCountEquals(0)

        composeRule.onNodeWithTag("editor_brand_rates_switch").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("custom_brand_row_$credzKey"))
        composeRule.onNodeWithTag("custom_brand_credit_rate_$credzKey").performScrollTo().assert(hasText("4,25"))

        // Salva no Room
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("save_machine_button"))
        composeRule.onNodeWithTag("save_machine_button").performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_editor_dialog").fetchSemanticsNodes().isEmpty()
        }

        val savedMachine = runBlocking(Dispatchers.IO) {
            app.machineRepository.observeMachines().first().single()
        }
        assertEquals(2, savedMachine.customBrandRates.size)
        assertEquals(425, savedMachine.findCustomBrand(credzKey)?.rateBps)
        assertEquals(510, savedMachine.findCustomBrand(valeKey)?.rateBps)
        assertEquals("CRÉDZ: VIP", savedMachine.findCustomBrand(credzKey)?.name)

        // Card da listagem exibe a contagem e os detalhes das personalizadas
        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("machine_card_${savedMachine.id}"))
        composeRule.onNodeWithTag("machine_card_${savedMachine.id}")
            .performScrollTo()
            .assert(hasAnyDescendant(hasText("2 personalizada(s)", substring = true)))
        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("toggle_machine_rates_${savedMachine.id}"))
        composeRule.onNodeWithTag("toggle_machine_rates_${savedMachine.id}").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("machine_card_${savedMachine.id}"))
        composeRule.onNodeWithTag("machine_card_${savedMachine.id}")
            .performScrollTo()
            .assert(hasAnyDescendant(hasText("CRÉDZ: VIP (personalizada) 4,25%", substring = true)))

        // Edita taxa de CRÉDZ: VIP e remove VALE LOJA pela UI
        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("edit_machine_${savedMachine.id}"))
        composeRule.onNodeWithTag("edit_machine_${savedMachine.id}").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_editor_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("custom_brand_credit_rate_$credzKey"))
        composeRule.onNodeWithTag("custom_brand_credit_rate_$credzKey").performScrollTo().performTextReplacement("4,80")
        composeRule.onNodeWithTag("remove_custom_brand_$valeKey").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("custom_brand_row_$valeKey").assertCountEquals(0)

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("save_machine_button"))
        composeRule.onNodeWithTag("save_machine_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_editor_dialog").fetchSemanticsNodes().isEmpty()
        }

        val editedMachine = runBlocking(Dispatchers.IO) {
            app.machineRepository.observeMachines().first().single()
        }
        assertEquals(1, editedMachine.customBrandRates.size)
        assertEquals(480, editedMachine.findCustomBrand(credzKey)?.rateBps)
        assertNull(editedMachine.findCustomBrand(valeKey))
    }

    @Test
    fun `03 financial engine and persistence validator handle custom brands strictly without silent fallback`() = runBlocking<Unit>(Dispatchers.IO) {
        val db = newInMemoryDb()
        try {
            val catalog = RoomCatalogRepository(db.personDao(), db.productDao())
            val machines = RoomMachineRepository(db.machineDao())
            val sales = RoomSaleRepository(db, db.saleDao())

            val p1 = catalog.createPerson("Ana")
            val prod = catalog.createProduct(
                name = "Vaso 3D",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = machines.createMachine(
                MachineInputDraft(
                    name = "PagBank Pro",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 250),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 18 to 400),
                    maxInstallments = 18,
                    customBrandRates = listOf(CustomCardBrandInput(name = "Crédz: VIP", rateBps = 415)),
                ),
            )
            val credzKey = CustomBrandKeyCodec.encodeKey("Crédz: VIP")

            // Venda com bandeira personalizada + 18x -> 415 + 400 = 815 bps (8,15%) -> R$ 8,15 de R$ 100,00
            val calc = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 1)),
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machine,
                    installments = 18,
                    cardBrandKey = credzKey,
                ),
            )
            assertEquals(815, calc.effectiveFeeRateBps)
            assertEquals(815L, calc.feeTotal.rawCents)
            assertEquals(9_185L, calc.netTotal.rawCents)
            val snap = requireNotNull(calc.machineSnapshot)
            assertNull(snap.appliedBrand)
            assertEquals(credzKey, snap.appliedBrandKey)
            assertEquals(credzKey, snap.resolvedAppliedBrandKey)
            assertEquals("Crédz: VIP", snap.appliedBrandDisplayLabel)

            // Persistência e reidratação no Room preservam chave personalizada (sem converter para OTHER)
            val saved = sales.saveSale(calc)
            assertMathematicalInvariants(saved)
            val reloaded = sales.getSaleById(saved.id)
            assertNotNull(reloaded)
            val reloadedSnap = requireNotNull(reloaded!!.calculation.machineSnapshot)
            assertNull(reloadedSnap.appliedBrand)
            assertEquals(credzKey, reloadedSnap.appliedBrandKey)
            assertEquals("Crédz: VIP", reloadedSnap.appliedBrandDisplayLabel)
            val editInput = reloaded.toEditableCalculationInput()
            assertEquals(credzKey, editInput.cardBrandKey)

            // Bandeira personalizada ausente na maquininha deve falhar explicitamente (sem fallback silencioso para creditDefaultRateBps)
            val missingKey = CustomBrandKeyCodec.encodeKey("Inexistente")
            assertThrows(SaleCalculationException::class.java) {
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        installments = 1,
                        cardBrandKey = missingKey,
                    ),
                )
            }

            // Override manual também preserva identidade da bandeira personalizada
            val manualCalc = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 1)),
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machine,
                    installments = 1,
                    manualRateOverrideBps = 220,
                    cardBrandKey = credzKey,
                ),
            )
            assertEquals(220, manualCalc.effectiveFeeRateBps)
            assertEquals(credzKey, manualCalc.machineSnapshot?.resolvedAppliedBrandKey)

            // Validador rejeita chave personalizada malformada
            val invalidSnapCalc = calc.copy(
                machineSnapshot = snap.copy(appliedBrandKey = "CUSTOM_ff"),
            )
            assertThrows(SalePersistenceException::class.java) {
                SalePersistenceValidator.validateForPersistence(invalidSnapCalc)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `04 vender, history search and historical sale edit work end-to-end with custom brands`() {
        val app = context as MixVendasApplication
        val seeded = runBlocking(Dispatchers.IO) {
            val person = app.catalogRepository.createPerson("Marina")
            val product = app.catalogRepository.createProduct(
                name = "Luminária Lua",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(person.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machineA = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Ton Custom A",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 250),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 3 to 150),
                    maxInstallments = 12,
                    customBrandRates = listOf(CustomCardBrandInput(name = "Crédz: VIP", rateBps = 420)),
                ),
            )
            val machineB = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Mercado Sem Custom B",
                    debitRateBps = 180,
                    creditEnabled = true,
                    creditDefaultRateBps = 290,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 210),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            Triple(product, machineA, machineB)
        }
        val (product, machineA, machineB) = seeded
        val credzKey = CustomBrandKeyCodec.encodeKey("Crédz: VIP")

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        composeRule.waitForIdle()
        selectProductsInVender(listOf(product.id))

        composeRule.onNodeWithTag("pay_method_credit").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Seleciona Maquininha A -> escolhe bandeira personalizada Crédz: VIP e 3x
        selectMachineInVender(machineA.id)
        composeRule.onNodeWithTag("brand_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("Crédz: VIP (4,20%)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Crédz: VIP (4,20%)", substring = false).performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("Crédz: VIP", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_rate_summary").performScrollTo()
            .assertTextContains("Crédz: VIP", substring = true)

        // Troca para Maquininha B (não tem Crédz: VIP) -> bandeira volta para Visa automaticamente e não aparece opção Crédz
        selectMachineInVender(machineB.id)
        composeRule.onNodeWithTag("brand_selector_dropdown").performScrollTo().assertTextContains("Visa", substring = true)
        composeRule.onNodeWithTag("vender_rate_summary").performScrollTo().assertTextContains("2,10%", substring = true)

        // Volta para Maquininha A, seleciona Crédz: VIP em 3x (4,20% + 1,50% = 5,70%) e salva
        selectMachineInVender(machineA.id)
        composeRule.onNodeWithTag("brand_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("Crédz: VIP (4,20%)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Crédz: VIP (4,20%)", substring = false).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("installments_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("3x (+1,50%)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("3x (+1,50%)", substring = false).performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("5,70%", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_rate_summary").performScrollTo()
            .assertTextContains("5,70%", substring = true)

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            runBlocking(Dispatchers.IO) {
                app.saleRepository.observeSales().first().size == 1
            }
        }
        val savedSale = runBlocking(Dispatchers.IO) {
            app.saleRepository.observeSales().first().single()
        }
        assertEquals(credzKey, savedSale.calculation.machineSnapshot?.resolvedAppliedBrandKey)
        assertEquals(570, savedSale.calculation.effectiveFeeRateBps)

        // Na aba VENDAS, exibe o rótulo legível "Crédz: VIP", nunca CUSTOM_ ou Outros, e busca pelo nome encontra a venda
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_vendas").fetchSemanticsNodes().isNotEmpty()
        }
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_${savedSale.id}"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("sale_history_card_${savedSale.id}")
            .assert(hasAnyDescendant(hasText("Crédz: VIP", substring = true)))
        composeRule.onAllNodesWithText(CustomBrandKeyCodec.CUSTOM_BRAND_PREFIX, substring = true).assertCountEquals(0)

        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sales_search_input"))
        composeRule.onNodeWithTag("sales_search_input").performScrollTo().performTextInput("Crédz")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sale_history_card_${savedSale.id}"))
        composeRule.onNodeWithTag("sale_history_card_${savedSale.id}").assertExists()
        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sales_search_input"))
        composeRule.onNodeWithTag("sales_search_input").performScrollTo().performTextReplacement("")
        composeRule.waitForIdle()

        // Remove a bandeira personalizada da maquininha A e altera taxa padrão; edição somente da observação preserva 5,70% e "Crédz: VIP"
        runBlocking(Dispatchers.IO) {
            app.machineRepository.updateMachine(
                machineId = machineA.id,
                draft = MachineInputDraft(
                    name = machineA.name,
                    debitRateBps = machineA.debitRateBps,
                    creditEnabled = true,
                    creditDefaultRateBps = 299,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 240),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 3 to 150),
                    maxInstallments = 12,
                    customBrandRates = emptyList(),
                ),
            )
        }

        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sale_history_card_${savedSale.id}"))
        composeRule.onNodeWithTag("sale_details_toggle_${savedSale.id}").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sale_edit_${savedSale.id}").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_editing_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("brand_selector_dropdown").performScrollTo().assertTextContains("Crédz: VIP", substring = true)
        composeRule.onNodeWithTag("vender_historical_machine_notice").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sale_note_input").performScrollTo().performTextInput("Nota editada")
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()

        waitUiUntil {
            runBlocking(Dispatchers.IO) {
                app.saleRepository.getSaleById(savedSale.id)?.calculation?.note == "Nota editada"
            }
        }
        val editedSale = runBlocking(Dispatchers.IO) {
            app.saleRepository.getSaleById(savedSale.id)!!
        }
        assertEquals(credzKey, editedSale.calculation.machineSnapshot?.resolvedAppliedBrandKey)
        assertEquals("Crédz: VIP", editedSale.calculation.machineSnapshot?.appliedBrandDisplayLabel)
        assertEquals(570, editedSale.calculation.effectiveFeeRateBps)
    }

    @Test
    fun `05 csv, json backup export-reader-merge and backup conflict labels handle custom brands safely`() = runBlocking<Unit>(Dispatchers.IO) {
        val sourceDb = newInMemoryDb()
        val restoreDb = newInMemoryDb()
        try {
            var seq = 0
            val idGen = {
                seq += 1
                "bkp-$seq"
            }
            val clock = { 1_765_000_000_000L }
            val catalog = RoomCatalogRepository(
                personDao = sourceDb.personDao(),
                productDao = sourceDb.productDao(),
                idGenerator = idGen,
                clockMillis = clock,
            )
            val machines = RoomMachineRepository(
                machineDao = sourceDb.machineDao(),
                idGenerator = idGen,
                clockMillis = clock,
            )
            val sales = RoomSaleRepository(
                database = sourceDb,
                saleDao = sourceDb.saleDao(),
                idGenerator = idGen,
                clockMillis = clock,
            )

            val ana = catalog.createPerson("Ana")
            val prod = catalog.createProduct(
                name = "Dragão Dourado",
                price = MoneyCents(20_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(20_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = machines.createMachine(
                MachineInputDraft(
                    name = "Stone Backup",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 350,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 299),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 100),
                    maxInstallments = 10,
                    customBrandRates = listOf(CustomCardBrandInput(name = "Crédz: VIP", rateBps = 440)),
                ),
            )
            val credzKey = CustomBrandKeyCodec.encodeKey("Crédz: VIP")
            val sale = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        installments = 2,
                        cardBrandKey = credzKey,
                    ),
                ),
            )

            // 1) CSV export exibe rótulo legível "Crédz: VIP" sem chave hexadecimal nem "Outros"
            val csv = CsvSaleExporter.export(
                sales = listOf(sale),
                zoneId = ZoneId.of("UTC"),
                generatedOnDate = LocalDate.of(2026, 11, 19),
            )
            assertTrue(csv.summaryCsvContent.contains("Crédz: VIP"))
            assertTrue(csv.detailedCsvContent.contains("Crédz: VIP"))
            assertFalse(csv.summaryCsvContent.contains(credzKey))
            assertFalse(csv.detailedCsvContent.contains("Outros"))

            // 2) JSON export -> reader -> planner -> validator -> preview -> executor para banco limpo
            val exported = JsonBackupExporter.export(sourceDb, ZoneId.of("UTC"), 1_765_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            assertTrue(readResult.isValid)
            val plan = JsonBackupMergePlanner.plan(readResult, restoreDb)
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, restoreDb)
            assertTrue(validation.isReadyForNextPhase)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, restoreDb)
            val execution = JsonBackupMergeExecutor.execute(
                readResult = readResult,
                plan = plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = validation,
                previewResult = preview,
                executionContract = preview.executionContract,
                userConfirmed = true,
                database = restoreDb,
            )
            assertTrue(execution.success)

            val restoredMachine = RoomMachineRepository(restoreDb.machineDao()).observeMachines().first().single()
            assertEquals(1, restoredMachine.customBrandRates.size)
            assertEquals(credzKey, restoredMachine.customBrandRates.single().key)
            assertEquals("Crédz: VIP", restoredMachine.customBrandRates.single().name)
            assertEquals(440, restoredMachine.customBrandRates.single().rateBps)

            val restoredSale = RoomSaleRepository(restoreDb, restoreDb.saleDao()).getSaleById(sale.id)!!
            assertEquals(credzKey, restoredSale.calculation.machineSnapshot?.resolvedAppliedBrandKey)
            assertEquals("Crédz: VIP", restoredSale.calculation.machineSnapshot?.appliedBrandDisplayLabel)
            assertNull(restoredSale.calculation.machineSnapshot?.appliedBrand)

            // 3) Conflito de máquina em backup exibe rótulo decodificado no BackupConflictFormatter
            RoomMachineRepository(restoreDb.machineDao()).updateMachine(
                machineId = restoredMachine.id,
                draft = MachineInputDraft(
                    name = "Stone Backup",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 350,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 299),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 100),
                    maxInstallments = 10,
                    customBrandRates = listOf(CustomCardBrandInput(name = "Crédz: VIP", rateBps = 490)),
                ),
            )
            val conflictPlan = JsonBackupMergePlanner.plan(readResult, restoreDb)
            val localSnap = JsonBackupExporter.captureConsistentSnapshot(restoreDb)
            val visualMap = BackupConflictFormatter.buildUnitVisualDetails(
                validatedBackup = readResult.validatedData!!,
                localSnapshot = localSnap,
                plan = conflictPlan,
            )
            val machineVisual = visualMap["MACHINE:${restoredMachine.id}"]
            assertNotNull(machineVisual)
            assertTrue(machineVisual!!.backupDataLines.any { it.contains("Crédz: VIP") })
            assertTrue(machineVisual.localDataLines.any { it.contains("Crédz: VIP") })

            // 4) JsonBackupReader rejeita chave CUSTOM malformada ou colisão no mesmo JSON
            val tamperedRateJson = JSONObject(exported.jsonContent)
            val brandRatesArr = tamperedRateJson.getJSONObject("collections").getJSONArray("machineBrandRates")
            for (i in 0 until brandRatesArr.length()) {
                val obj = brandRatesArr.getJSONObject(i)
                if (obj.getString("brandKey") == credzKey) {
                    obj.put("brandKey", "CUSTOM_ff")
                }
            }
            val invalidRead = JsonBackupReader.readAndValidate(tamperedRateJson.toString())
            assertFalse(invalidRead.isValid)
            assertTrue(
                invalidRead.errors.any {
                    it.code == JsonBackupErrorCode.RELATIONAL_OR_FINANCIAL_VALIDATION_ERROR ||
                        (it.field?.contains("brandKey") == true)
                },
            )
        } finally {
            sourceDb.close()
            restoreDb.close()
        }
    }

    @Test
    fun `06 system update evaluates, updates modified custom brand rate and blocks deleted custom brand without fallback`() = runBlocking<Unit>(Dispatchers.IO) {
        val db = newFileDb("sys_update_custom_brands")
        try {
            var clock = 1_766_000_000_000L
            val nextClock = {
                clock += 1_000L
                clock
            }
            val catalog = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = nextClock)
            val machines = RoomMachineRepository(db.machineDao(), clockMillis = nextClock)
            val sales = RoomSaleRepository(db, db.saleDao(), clockMillis = nextClock)
            val updateRepo = RoomSystemUpdateRepository(db, clockMillis = nextClock)

            val ana = catalog.createPerson("Ana")
            val prod = catalog.createProduct(
                name = "Suporte Headset",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = machines.createMachine(
                MachineInputDraft(
                    name = "Stone System",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 250),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 18 to 300),
                    maxInstallments = 18,
                    customBrandRates = listOf(CustomCardBrandInput(name = "Crédz: VIP", rateBps = 400)),
                ),
            )
            val credzKey = CustomBrandKeyCodec.encodeKey("Crédz: VIP")
            val sale = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        installments = 18,
                        cardBrandKey = credzKey,
                    ),
                ),
            )
            assertEquals(700, sale.calculation.effectiveFeeRateBps)

            // 1) Sem alterações -> AlreadyCorrect (também valida maxInstallments preservado em MachineWithRates.toDomain)
            val cleanAnalysis = updateRepo.analyze()
            assertEquals(1, cleanAnalysis.alreadyCorrectCount)
            assertEquals(0, cleanAnalysis.salesToUpdateCount)
            assertEquals(0, cleanAnalysis.requiresReviewCount)

            // 2) Altera a taxa da bandeira personalizada Crédz: VIP de 4,00% para 4,50% -> proposta 7,50%
            machines.updateMachine(
                machineId = machine.id,
                draft = MachineInputDraft(
                    name = "Stone System",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 250),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 18 to 300),
                    maxInstallments = 18,
                    customBrandRates = listOf(CustomCardBrandInput(name = "Crédz: VIP", rateBps = 450, key = credzKey)),
                ),
            )
            val rateChangeAnalysis = updateRepo.analyze()
            assertEquals(1, rateChangeAnalysis.salesToUpdateCount)
            assertEquals(0, rateChangeAnalysis.requiresReviewCount)
            val candidate = rateChangeAnalysis.updatableItems.single()
            assertEquals(700, candidate.historicalFeeRateBps)
            assertEquals(750, candidate.proposedFeeRateBps)
            assertEquals(credzKey, candidate.proposedCalculation.machineSnapshot?.resolvedAppliedBrandKey)

            val applyResult = updateRepo.applyUpdate(
                expectedFingerprint = rateChangeAnalysis.localStateFingerprint,
                allowPartialUpdate = false,
            )
            assertTrue(applyResult.success)
            val updatedSale = sales.getSaleById(sale.id)!!
            assertEquals(750, updatedSale.calculation.effectiveFeeRateBps)
            assertEquals(750L, updatedSale.calculation.feeTotal.rawCents)
            assertEquals(9_250L, updatedSale.calculation.netTotal.rawCents)

            // 3) Remove Crédz: VIP da maquininha -> deve bloquear com conflito MISSING_REQUIRED_BRAND (nunca fallback silencioso)
            machines.updateMachine(
                machineId = machine.id,
                draft = MachineInputDraft(
                    name = "Stone System",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 250),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 18 to 300),
                    maxInstallments = 18,
                    customBrandRates = emptyList(),
                ),
            )
            val missingBrandAnalysis = updateRepo.analyze()
            assertEquals(0, missingBrandAnalysis.salesToUpdateCount)
            assertEquals(1, missingBrandAnalysis.requiresReviewCount)
            val conflict = missingBrandAnalysis.conflictItems.single()
            assertTrue(conflict.codes.contains(SystemUpdateConflictCode.MISSING_REQUIRED_BRAND))
            assertTrue(conflict.reasons.any { it.contains("Crédz: VIP") })
        } finally {
            db.close()
        }
    }
}
