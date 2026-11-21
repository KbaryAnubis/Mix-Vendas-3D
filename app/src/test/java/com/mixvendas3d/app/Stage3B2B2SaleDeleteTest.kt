package com.mixvendas3d.app

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.data.repository.RoomPreferenceRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.data.repository.SaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.calculation.SalePersistenceException
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.ui.screens.MixVendasShellViewModel
import com.mixvendas3d.app.util.CurrencyFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage3B2B2SaleDeleteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanUpDatabase() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
    }

    private fun waitUiUntil(timeoutMillis: Long = 5_000L, condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun openDeleteDialogFromVendas(saleId: String, navigateToTab: Boolean = true) {
        if (navigateToTab) {
            composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        }
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_$saleId"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("sale_delete_$saleId")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("delete_sale_dialog").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `01 delete dialog shows sale info, cancel preserves everything, confirm removes only target and updates totals and people`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val setup = runBlocking(Dispatchers.IO) {
            app.preferenceRepository.savePreference("backup_flag", "keep_me")
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")
            val carla = app.catalogRepository.createPerson("Carla")

            val prodA = app.catalogRepository.createProduct(
                name = "Vaso Nebulosa",
                price = MoneyCents(6_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(4_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodB = app.catalogRepository.createProduct(
                name = "Luminária Lua",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(bruno.id, MoneyCents(4_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = bruno.id,
            )
            val prodC = app.catalogRepository.createProduct(
                name = "Suporte Aurora",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(carla.id, MoneyCents(5_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Ton Pro",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 400,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 400),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            // Venda 1 (será excluída): 2x Vaso (120) + 1x Luminária (40) -> subtotal 160, desconto 20 -> final 140,00 no Pix
            val saleToDelete = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodA, 2), SaleItemInput(prodB, 1)),
                        finalAmountOverride = MoneyCents(14_000L),
                        discountMode = SaleDiscountInput.Proportional,
                        paymentMethod = SalePaymentMethod.PIX,
                        note = "Pedido apagar",
                    ),
                ),
            )

            // Venda 2 (será preservada): 2x Suporte Aurora (100,00), desconto 10,00 -> final 90,00 no Crédito 4,00% (taxa 3,60, líquido 86,40)
            val preservedSale = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodC, 2)),
                        finalAmountOverride = MoneyCents(9_000L),
                        discountMode = SaleDiscountInput.Proportional,
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        cardBrand = CardBrand.VISA,
                        installments = 1,
                        note = "Pedido manter",
                    ),
                ),
            )

            listOf(saleToDelete.id, preservedSale.id, ana.id, bruno.id, carla.id)
        }
        val saleToDeleteId = setup[0]
        val preservedSaleId = setup[1]
        val anaId = setup[2]
        val brunoId = setup[3]
        val carlaId = setup[4]

        val saleToDelete = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleToDeleteId)!! }
        val preservedBefore = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(preservedSaleId)!! }

        // 1) Tocar em Excluir abre diálogo com título, mensagem e dados corretos
        openDeleteDialogFromVendas(saleToDeleteId)

        composeRule.onNodeWithTag("delete_sale_dialog_title")
            .assertTextContains("Excluir venda?")
        composeRule.onNodeWithTag("delete_sale_dialog_message")
            .assertTextContains("Esta ação excluirá permanentemente esta venda e seus registros financeiros. Não será possível desfazer.")
        val infoNode = composeRule.onNodeWithTag("delete_sale_dialog_info")
        infoNode.assertTextContains(CurrencyFormatter.formatEpochMillis(saleToDelete.soldAtEpochMs), substring = true)
        infoNode.assertTextContains("R$ 140,00", substring = true)
        infoNode.assertTextContains("2 produtos · 3 itens", substring = true)

        // 2) Cancelar fecha diálogo sem excluir nada nem alterar o banco
        composeRule.onNodeWithTag("delete_sale_cancel_button").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("delete_sale_dialog").assertCountEquals(0)
        assertNotNull(runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleToDeleteId) })
        assertEquals(2, runBlocking(Dispatchers.IO) { app.database.saleDao().countSales() })

        // 3) Reabrir diálogo e confirmar exclusão
        openDeleteDialogFromVendas(saleToDeleteId, navigateToTab = false)
        composeRule.onNodeWithTag("delete_sale_confirm_button").performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("delete_sale_dialog").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("vendas_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vendas_feedback_banner")
            .assertTextContains("Venda excluída com sucesso", substring = true)

        // 4) Somente a venda selecionada (e seus itens/participantes/totais) foi excluída no Room
        runBlocking(Dispatchers.IO) {
            assertNull(app.saleRepository.getSaleById(saleToDeleteId))
            val remaining = app.saleRepository.getSaleById(preservedSaleId)
            assertNotNull(remaining)
            assertEquals(preservedBefore.calculation, remaining!!.calculation)

            assertEquals(1, app.database.saleDao().countSales())
            assertEquals(1, app.database.saleDao().countSaleItems())
            assertEquals(1, app.database.saleDao().countSaleItemParticipants())
            assertEquals(1, app.database.saleDao().countSalePersonTotals())

            // Cadastros e preferências preservados intactos
            assertEquals(3, app.catalogRepository.observePeople().first().size)
            assertEquals(3, app.catalogRepository.observeAllProducts().first().size)
            assertEquals(1, app.machineRepository.observeMachines().first().size)
            assertEquals("keep_me", app.preferenceRepository.getPreference("backup_flag")?.value)
        }

        // 5) Lista e totais da aba VENDAS atualizam automaticamente pelo Flow do Room
        composeRule.onAllNodesWithTag("sale_history_card_$saleToDeleteId").assertCountEquals(0)
        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sales_top_summary_card"))

        composeRule.onNode(
            hasTestTag("sales_top_summary_card") and
                hasAnyDescendant(hasText("1 venda", substring = true)) and
                hasAnyDescendant(hasText("R$ 90,00", substring = true)) and
                hasAnyDescendant(hasText("R$ 86,40", substring = true)),
        ).assertExists()

        // Totais individuais das pessoas refletem apenas a venda restante (Ana e Bruno zerados/ausentes, Carla = R$ 86,40)
        composeRule.onAllNodesWithTag("sales_person_net_$anaId").assertCountEquals(0)
        composeRule.onAllNodesWithTag("sales_person_net_$brunoId").assertCountEquals(0)
        composeRule.onNodeWithTag("sales_person_net_$carlaId")
            .assertExists()
            .assertTextContains("R$ 86,40", substring = true)
    }

    @Test
    fun `02 deleting filtered sale respects active filter and deleting last sale displays vendas empty state`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (pixSaleId, cashSaleId) = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Helena")
            val prodPix = app.catalogRepository.createProduct(
                name = "Dragão Esmeralda",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodCash = app.catalogRepository.createProduct(
                name = "Estátua Cobre",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(3_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val s1 = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodPix, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                        note = "Busca alvo",
                    ),
                ),
            )
            val s2 = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodCash, 1)),
                        paymentMethod = SalePaymentMethod.CASH,
                        note = "Outro registro",
                    ),
                ),
            )
            s1.id to s2.id
        }

        // Aplica filtro de texto "Dragão" para exibir somente s1
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("sales_search_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sales_search_input").performScrollTo().performTextInput("Dragão")
        waitUiUntil {
            composeRule.onAllNodesWithTag("sale_history_card_$cashSaleId").fetchSemanticsNodes().isEmpty()
        }

        // Exclui s1 enquanto o filtro está ativo -> filtro segue ativo e mostra estado vazio do filtro (s2 intacta)
        openDeleteDialogFromVendas(pixSaleId, navigateToTab = false)
        composeRule.onNodeWithTag("delete_sale_confirm_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("delete_sale_dialog").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("vendas_filter_empty_state").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vendas_filter_empty_state").assertIsDisplayed()
        assertNotNull(runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(cashSaleId) })

        // Limpa filtro -> s2 aparece; exclui s2 -> último registro apagado mostra vendas_empty_state
        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sales_filters_card"))
        composeRule.onNodeWithTag("sales_search_input").performScrollTo().performTextClearance()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_$cashSaleId"))
                true
            }.getOrDefault(false)
        }

        openDeleteDialogFromVendas(cashSaleId, navigateToTab = false)
        composeRule.onNodeWithTag("delete_sale_confirm_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("delete_sale_dialog").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("vendas_empty_state").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("vendas_empty_state").assertIsDisplayed()
        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sales_top_summary_card"))
        composeRule.onNode(
            hasTestTag("sales_top_summary_card") and
                hasAnyDescendant(hasText("0 vendas", substring = true)) and
                hasAnyDescendant(hasText("R$ 0,00", substring = true)),
        ).assertExists()
    }

    @Test
    fun `03 delete blocks repeated confirmations while in flight, handles error with retry, handles missing sale, and persists across room reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "delete.db"
        context.deleteDatabase(dbName)

        var saleAId = ""
        var saleBId = ""
        val deleteCalls = AtomicInteger(0)

        val firstDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            val prefRepo = RoomPreferenceRepository(firstDb.preferenceDao())
            val catalogRepo = RoomCatalogRepository(firstDb.personDao(), firstDb.productDao())
            val machineRepo = RoomMachineRepository(firstDb.machineDao())
            val realSaleRepo = RoomSaleRepository(firstDb, firstDb.saleDao())

            val p1 = catalogRepo.createPerson("Lucas")
            val prod = catalogRepo.createProduct(
                name = "Chaveiro Prisma",
                price = MoneyCents(2_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(2_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val saleA = realSaleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CASH,
                        note = "Venda A",
                    ),
                ),
            )
            val saleB = realSaleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 2)),
                        paymentMethod = SalePaymentMethod.PIX,
                        note = "Venda B",
                    ),
                ),
            )
            saleAId = saleA.id
            saleBId = saleB.id

            val shouldFail = AtomicBoolean(true)
            val gateEnabled = AtomicBoolean(false)
            val gateDeferred = CompletableDeferred<Unit>()
            val enteredDelete = CompletableDeferred<Unit>()

            val controllableRepo = object : SaleRepository by realSaleRepo {
                override suspend fun deleteSale(saleId: String) {
                    deleteCalls.incrementAndGet()
                    if (shouldFail.get()) {
                        throw IllegalStateException("Falha simulada ao excluir venda")
                    }
                    if (gateEnabled.get()) {
                        enteredDelete.complete(Unit)
                        gateDeferred.await()
                    }
                    realSaleRepo.deleteSale(saleId)
                }
            }

            val vm = MixVendasShellViewModel(
                preferenceRepository = prefRepo,
                catalogRepository = catalogRepo,
                machineRepository = machineRepo,
                saleRepository = controllableRepo,
            )
            val collectJob = CoroutineScope(Dispatchers.Main).launch {
                vm.uiState.collect {}
            }

            var waited = 0
            while (vm.uiState.value.realSalesCount < 2 && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }
            assertEquals(2, vm.uiState.value.realSalesCount)

            // 1) Erro durante exclusão: mantém diálogo aberto com erro claro, não mostra sucesso, não remove venda
            vm.requestDeleteSale(saleAId)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(saleAId, vm.uiState.value.saleDeleteState.targetSale?.id)

            vm.confirmDeleteSale()
            waited = 0
            while (vm.uiState.value.saleDeleteState.errorMessage == null && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }
            val failedState = vm.uiState.value
            assertFalse(failedState.saleDeleteState.isDeleting)
            assertEquals("Falha simulada ao excluir venda", failedState.saleDeleteState.errorMessage)
            assertNull(failedState.salesFeedbackMessage)
            assertEquals(2, failedState.realSalesCount)
            assertNotNull(realSaleRepo.getSaleById(saleAId))

            // 2) Nova tentativa com trava em voo: cliques repetidos executam apenas 1 deleteSale e exibem isDeleting=true
            shouldFail.set(false)
            gateEnabled.set(true)
            deleteCalls.set(0)

            vm.confirmDeleteSale()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue(vm.uiState.value.saleDeleteState.isDeleting)
            // Tentativas concorrentes e dismiss durante processamento devem ser ignoradas
            vm.confirmDeleteSale()
            vm.confirmDeleteSale()
            vm.dismissDeleteSaleDialog()

            enteredDelete.await()
            gateDeferred.complete(Unit)

            waited = 0
            while ((vm.uiState.value.saleDeleteState.isDialogVisible || vm.uiState.value.realSalesCount != 1) && waited < 80) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }

            assertEquals(1, deleteCalls.get())
            assertEquals(1, vm.uiState.value.realSalesCount)
            assertEquals(5_000L, vm.uiState.value.realTotalNet.rawCents)
            assertEquals("Venda excluída com sucesso.", vm.uiState.value.salesFeedbackMessage)
            assertFalse(vm.uiState.value.salesFeedbackIsError)
            assertNull(realSaleRepo.getSaleById(saleAId))
            assertNotNull(realSaleRepo.getSaleById(saleBId))

            // 3) Venda já inexistente no banco: trata sem afetar venda B e sem falso sucesso
            gateEnabled.set(false)
            vm.requestDeleteSale(saleAId)
            waited = 0
            while (!vm.uiState.value.salesFeedbackIsError && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }
            assertTrue(vm.uiState.value.salesFeedbackIsError)
            assertTrue(vm.uiState.value.salesFeedbackMessage.orEmpty().contains("não existe mais", ignoreCase = true))
            assertFalse(vm.uiState.value.saleDeleteState.isDialogVisible)
            assertNotNull(realSaleRepo.getSaleById(saleBId))

            collectJob.cancel()
        } finally {
            firstDb.close()
        }

        // Reabre o Room para provar persistência real da exclusão e preservação de venda B
        val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            val saleRepo = RoomSaleRepository(reopenedDb, reopenedDb.saleDao())
            val sales = saleRepo.observeSales().first()
            assertEquals(1, sales.size)
            assertEquals(saleBId, sales.single().id)
            assertNull(saleRepo.getSaleById(saleAId))
        } finally {
            reopenedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `04 deleting one sale preserves editing of another sale and blocks updating a deleted sale`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefRepo = RoomPreferenceRepository(db.preferenceDao())
            val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao())
            val machineRepo = RoomMachineRepository(db.machineDao())
            val saleRepo = RoomSaleRepository(db, db.saleDao())

            val ana = catalogRepo.createPerson("Ana")
            val prod = catalogRepo.createProduct(
                name = "Placa Decorativa",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(4_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            val saleA = saleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                        note = "Venda para excluir",
                    ),
                ),
            )
            val saleB = saleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 2)),
                        paymentMethod = SalePaymentMethod.CASH,
                        note = "Venda para editar",
                    ),
                ),
            )

            val vm = MixVendasShellViewModel(
                preferenceRepository = prefRepo,
                catalogRepository = catalogRepo,
                machineRepository = machineRepo,
                saleRepository = saleRepo,
            )
            val collectJob = CoroutineScope(Dispatchers.Main).launch {
                vm.uiState.collect {}
            }
            var waited = 0
            while (vm.uiState.value.realSalesCount < 2 && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }

            // 1) Abre edição da venda A, exclui venda A no banco, e tenta salvar alterações -> DEVE BLOQUEAR!
            var loadedA = false
            vm.startEditingSale(saleA.id) { loadedA = true }
            waited = 0
            while (!loadedA && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }
            assertTrue(loadedA)

            saleRepo.deleteSale(saleA.id)
            vm.onNoteChanged("Tentativa pós exclusão")
            vm.saveSale()

            waited = 0
            while (vm.uiState.value.venderForm.feedbackMessage == null && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }
            assertTrue(vm.uiState.value.venderForm.feedbackIsError)
            assertTrue(
                vm.uiState.value.venderForm.feedbackMessage.orEmpty()
                    .contains("não encontrada", ignoreCase = true),
            )
            // Venda A continua excluída e não foi recriada nem contaminou venda B
            assertNull(saleRepo.getSaleById(saleA.id))
            assertEquals(1, saleRepo.observeSales().first().size)

            // Chamada direta ao repositório também rejeita atualizar venda excluída
            val repoReject = runCatching {
                saleRepo.updateSale(saleA.id, saleA.calculation)
            }.exceptionOrNull()
            assertTrue(repoReject is SalePersistenceException)

            // 2) Cancelar edição bloqueada e editar venda B normalmente após exclusão de venda A
            vm.cancelEditing()
            var loadedB = false
            vm.startEditingSale(saleB.id) { loadedB = true }
            waited = 0
            while (!loadedB && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }
            assertTrue(loadedB)

            vm.onNoteChanged("Venda B editada após excluir A")
            vm.saveSale()
            waited = 0
            while (vm.uiState.value.venderForm.isEditing && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }
            assertFalse(vm.uiState.value.venderForm.feedbackIsError)
            val updatedB = saleRepo.getSaleById(saleB.id)!!
            assertEquals("Venda B editada após excluir A", updatedB.calculation.note)
            assertEquals(8_000L, updatedB.calculation.finalAmount.rawCents)
            assertEquals(1, saleRepo.observeSales().first().size)

            collectJob.cancel()
        } finally {
            db.close()
        }
    }

    @Test
    fun `05 ui error during delete keeps dialog open for retry without showing success or removing sale`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val saleId = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Marcos")
            val prod = app.catalogRepository.createProduct(
                name = "Suporte Tablet",
                price = MoneyCents(4_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(4_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            ).id
        }

        openDeleteDialogFromVendas(saleId)

        // Simula exclusão prévia concorrente ou indisponibilidade antes de confirmar na UI:
        // remove diretamente o registro no Room e confirma pelo botão na UI -> não pode exibir falso sucesso!
        runBlocking(Dispatchers.IO) {
            app.saleRepository.deleteSale(saleId)
        }
        composeRule.onNodeWithTag("delete_sale_confirm_button").performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("delete_sale_dialog").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("vendas_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vendas_feedback_banner")
            .assertTextContains("não existe mais", substring = true)
    }
}
