package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupMergeDecisionValidator
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanner
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Testes integrados de UI (Jetpack Compose + Room + Robolectric) para o sistema corrigido de descontos:
 * - Caixa 1: seleção múltipla de produtos, Selecionar todos e Limpar seleção.
 * - Caixa 2: caixa recolhível independente "PESSOAS QUE NÃO RECEBERÃO O DESCONTO", sem duplicação,
 *   sincronizada em tempo real com a Caixa 1 e com proteção padrão + diálogo de autorização para R$ 5,00/un.
 * - Prévia detalhada em tempo real antes de salvar e bloqueio de descontos inválidos.
 * - Persistência, reabertura na edição sem duplo desconto e preservação de vendas históricas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscountSystemUiIntegrationTest {

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

    private fun pickProducts(productIds: List<String>) {
        composeRule.onNodeWithTag("open_product_picker_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("product_picker_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        productIds.forEach { prodId ->
            waitUiUntil {
                runCatching {
                    composeRule.onNodeWithTag("product_picker_list")
                        .performScrollToNode(hasTestTag("picker_row_$prodId"))
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag("picker_checkbox_$prodId").performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("close_product_picker_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("product_picker_dialog").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `01 multi-product box select all, clear selection block, collapsible protected box deduplication, R$5 default protection and live preview`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val seeded = runBlocking(Dispatchers.IO) {
            val marcos = app.catalogRepository.createPerson("Marcos")
            val lucas = app.catalogRepository.createPerson("Lucas")
            val carla = app.catalogRepository.createPerson("Carla")

            // Prod A (R$ 45,00): Marcos R$ 40,00 + Lucas R$ 5,00 (valor fixo)
            val prodA = app.catalogRepository.createProduct(
                name = "Boneco A",
                price = MoneyCents(4_500L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(marcos.id, MoneyCents(4_000L)),
                    ProductParticipantDraft(lucas.id, MoneyCents(500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            // Prod B (R$ 25,00): Marcos R$ 20,00 + Lucas R$ 5,00 (valor fixo)
            val prodB = app.catalogRepository.createProduct(
                name = "Chaveiro B",
                price = MoneyCents(2_500L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(marcos.id, MoneyCents(2_000L)),
                    ProductParticipantDraft(lucas.id, MoneyCents(500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            // Prod C (R$ 30,00): somente Carla (será desmarcado na Caixa 1)
            val prodC = app.catalogRepository.createProduct(
                name = "Suporte C",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(carla.id, MoneyCents(3_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            listOf(marcos.id, lucas.id, carla.id, prodA.id, prodB.id, prodC.id)
        }
        val marcosId = seeded[0]
        val lucasId = seeded[1]
        val carlaId = seeded[2]
        val prodAId = seeded[3]
        val prodBId = seeded[4]
        val prodCId = seeded[5]

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        pickProducts(listOf(prodAId, prodBId, prodCId))

        // Subtotal = 45 + 25 + 30 = R$ 100,00. Informa final R$ 85,00 (desconto R$ 15,00)
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("85,00")
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_products_box").fetchSemanticsNodes().isNotEmpty()
        }

        // 1) "Limpar seleção" desmarca todos e bloqueia salvamento
        composeRule.onNodeWithTag("discount_clear_selection_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_no_products_warning").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_discount_preview_error").performScrollTo().assertExists()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size })

        // 2) "Selecionar todos" marca novamente os 3 produtos
        composeRule.onNodeWithTag("discount_select_all_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discount_product_checkbox_$prodAId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_product_checkbox_$prodBId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_product_checkbox_$prodCId").performScrollTo().assertIsOn()

        // 3) Desmarca Prod C -> somente Prod A e Prod B recebem desconto; Prod C fica inalterado
        composeRule.onNodeWithTag("discount_product_row_$prodCId").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discount_product_checkbox_$prodCId").performScrollTo().assertIsOff()

        // 4) Caixa 2 independente ("PESSOAS QUE NÃO RECEBERÃO O DESCONTO") inicia recolhida e abre ao tocar
        composeRule.onNodeWithTag("discount_protected_people_box").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("PESSOAS QUE NÃO RECEBERÃO O DESCONTO").assertExists()
        composeRule.onAllNodesWithTag("discount_protected_person_row_$lucasId").assertCountEquals(0)

        composeRule.onNodeWithTag("discount_protected_people_header").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_protected_person_row_$lucasId").fetchSemanticsNodes().isNotEmpty()
        }

        // Marcos e Lucas participam de Prod A e Prod B, mas aparecem UMA única vez cada!
        composeRule.onAllNodesWithTag("discount_protected_person_row_$marcosId").assertCountEquals(1)
        composeRule.onAllNodesWithTag("discount_protected_person_row_$lucasId").assertCountEquals(1)
        // Carla participa apenas de Prod C (desmarcado) -> NÃO aparece na Caixa 2!
        composeRule.onAllNodesWithTag("discount_protected_person_row_$carlaId").assertCountEquals(0)

        // Lucas (valor fixo R$ 5,00/un) já vem protegido por padrão com indicação explícita
        composeRule.onNodeWithTag("discount_protected_person_checkbox_$lucasId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_protected_person_checkbox_$marcosId").performScrollTo().assertIsOff()
        composeRule.onNodeWithText("Valor fixo (R$ 5,00/un) · Protegida por padrão").assertExists()

        // 5) Conferência na prévia ao vivo antes de salvar:
        // Lucas: Antes R$ 10,00 -> Depois R$ 10,00 (intacto!)
        // Marcos: Antes R$ 60,00 -> Depois R$ 45,00 (-R$ 15,00)
        // Carla: Antes R$ 30,00 -> Depois R$ 30,00 (Fora do desconto)
        composeRule.onNodeWithTag("vender_discount_preview_card").performScrollTo().assertExists()
        composeRule.onNodeWithTag("preview_selected_product_$prodAId").performScrollTo().assertExists()
        composeRule.onNodeWithTag("preview_selected_product_$prodBId").performScrollTo().assertExists()
        composeRule.onNodeWithTag("preview_unselected_product_$prodCId").performScrollTo().assertExists()
        composeRule.onNodeWithTag("preview_person_$lucasId").performScrollTo()
            .assertTextContains("Antes: R$ 10,00", substring = true)
            .assertTextContains("Depois: R$ 10,00", substring = true)
        composeRule.onNodeWithTag("preview_person_$marcosId").performScrollTo()
            .assertTextContains("Antes: R$ 60,00", substring = true)
            .assertTextContains("Depois: R$ 45,00", substring = true)
        composeRule.onNodeWithTag("preview_person_$carlaId").performScrollTo()
            .assertTextContains("Antes: R$ 30,00", substring = true)
            .assertTextContains("Depois: R$ 30,00", substring = true)

        // 6) Testar diálogo de autorização explícita ao tentar desmarcar Lucas (valor fixo R$ 5,00/un)
        composeRule.onNodeWithTag("discount_protected_person_row_$lucasId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("fixed_share_unprotect_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        // Recusar autorização ("Manter protegida") preserva proteção
        composeRule.onNodeWithTag("fixed_share_keep_protected_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("fixed_share_unprotect_dialog").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("discount_protected_person_checkbox_$lucasId").performScrollTo().assertIsOn()

        // 7) Tentar proteger Marcos também (todos protegidos) -> prévia bloqueia e salvar bloqueia
        composeRule.onNodeWithTag("discount_protected_person_row_$marcosId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_discount_preview_error").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size })

        // Desmarca Marcos para voltar ao cenário válido (Lucas protegido, Marcos absorve R$ 15,00)
        composeRule.onNodeWithTag("discount_protected_person_row_$marcosId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_discount_preview_error").fetchSemanticsNodes().isEmpty()
        }

        // Fecha a caixa recolhível ao tocar no cabeçalho
        composeRule.onNodeWithTag("discount_protected_people_header").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_protected_person_row_$lucasId").fetchSemanticsNodes().isEmpty()
        }

        // Salva a venda no Room
        composeRule.onNodeWithTag("pay_method_pix").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size == 1 }
        }

        val saved = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().single() }
        assertTrue(saved.calculation.discountConfig is SaleDiscountConfigSnapshot.SelectedProducts)
        val cfg = saved.calculation.discountConfig as SaleDiscountConfigSnapshot.SelectedProducts
        assertEquals(setOf(prodAId, prodBId), cfg.productIds.toSet())
        assertEquals(1_000L, saved.calculation.personTotals.first { it.personId == lucasId }.grossAmount.rawCents)
        assertEquals(4_500L, saved.calculation.personTotals.first { it.personId == marcosId }.grossAmount.rawCents)
        assertEquals(3_000L, saved.calculation.personTotals.first { it.personId == carlaId }.grossAmount.rawCents)
        assertEquals(0L, saved.calculation.items.first { it.productId == prodCId }.discountAmount.rawCents)

        // 8) Reabre a venda na tela VENDAS para editar e confere reconstrução fiel + reedição sem duplo desconto
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_${saved.id}"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Em 2 produtos", substring = true).assertExists()
        composeRule.onNodeWithTag("sale_edit_${saved.id}").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_editing_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Caixa 1 reconstruiu exatamente prodA e prodB marcados, prodC desmarcado
        composeRule.onNodeWithTag("discount_product_checkbox_$prodAId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_product_checkbox_$prodBId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_product_checkbox_$prodCId").performScrollTo().assertIsOff()

        // Abre Caixa 2 e confirma que Lucas segue protegido
        composeRule.onNodeWithTag("discount_protected_people_header").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_protected_person_checkbox_$lucasId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discount_protected_person_checkbox_$lucasId").performScrollTo().assertIsOn()

        // Agora autoriza explicitamente a redução do valor fixo de Lucas e salva alteração
        composeRule.onNodeWithTag("discount_protected_person_row_$lucasId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("fixed_share_unprotect_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("fixed_share_authorize_reduction_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("fixed_share_unprotect_dialog").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("discount_protected_person_checkbox_$lucasId").performScrollTo().assertIsOff()
        composeRule.onNodeWithText("Valor fixo (R$ 5,00/un) · Redução autorizada").assertExists()

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_editing_banner").fetchSemanticsNodes().isEmpty()
        }

        val edited = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saved.id)!! }
        // Subtotal original preservado em R$ 100,00 (sem duplo desconto!), final R$ 85,00, Prod C ainda R$ 30,00 intacto
        assertEquals(10_000L, edited.calculation.originalSubtotal.rawCents)
        assertEquals(1_500L, edited.calculation.discountTotal.rawCents)
        assertEquals(8_500L, edited.calculation.finalAmount.rawCents)
        assertEquals(3_000L, edited.calculation.personTotals.first { it.personId == carlaId }.grossAmount.rawCents)
        // Como a redução foi autorizada, Lucas agora absorveu parte proporcional (< 1000 cents)
        assertTrue(edited.calculation.personTotals.first { it.personId == lucasId }.grossAmount.rawCents < 1_000L)
    }

    @Test
    fun `02 historical legacy sales remain intact and untampered alongside new multi-product protected sales`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (legacySaleId, prodAId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")
            val prodA = app.catalogRepository.createProduct(
                name = "Vaso Legado",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(4_500L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            // Venda histórica antiga (antes da proteção) onde Bruno absorveu parte proporcional do desconto
            val legacy = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodA, 1)),
                        finalAmountOverride = MoneyCents(4_000L),
                        discountMode = SaleDiscountInput.Proportional,
                        paymentMethod = SalePaymentMethod.PIX,
                        protectedDiscountPersonIds = emptySet(),
                        note = "Venda histórica",
                    ),
                ),
            )
            legacy.id to prodA.id
        }

        val legacyBefore = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(legacySaleId)!! }
        // Na venda histórica, Bruno ficou com 400 cents (500 - 100)
        assertEquals(400L, legacyBefore.calculation.personTotals.first { it.personName == "Bruno" }.grossAmount.rawCents)

        // Cria uma nova venda com o mesmo produto (agora com Bruno protegido por padrão!)
        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        pickProducts(listOf(prodAId))
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("40,00")
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_discount_preview_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("pay_method_pix").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size == 2 }
        }

        val sales = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first() }
        val legacyAfter = sales.first { it.id == legacySaleId }
        val newSale = sales.first { it.id != legacySaleId }

        // Venda histórica permanece 100% inalterada
        assertEquals(legacyBefore.calculation, legacyAfter.calculation)
        // Nova venda protegeu Bruno (R$ 5,00 intactos) e aplicou R$ 10,00 apenas em Ana (R$ 35,00)
        assertEquals(500L, newSale.calculation.personTotals.first { it.personName == "Bruno" }.grossAmount.rawCents)
        assertEquals(3_500L, newSale.calculation.personTotals.first { it.personName == "Ana" }.grossAmount.rawCents)
    }

    @Test
    fun `03 json backup export, read, plan and validate succeeds for SelectedProducts protected discount sale`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bia = app.catalogRepository.createPerson("Bia")
            val carla = app.catalogRepository.createPerson("Carla")

            val prodA = app.catalogRepository.createProduct(
                name = "Peça A",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(3_500L)),
                    ProductParticipantDraft(bia.id, MoneyCents(500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodB = app.catalogRepository.createProduct(
                name = "Peça B",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(2_500L)),
                    ProductParticipantDraft(bia.id, MoneyCents(500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodC = app.catalogRepository.createProduct(
                name = "Peça C",
                price = MoneyCents(2_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(carla.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(
                            SaleItemInput(prodA, 1),
                            SaleItemInput(prodB, 1),
                            SaleItemInput(prodC, 1),
                        ),
                        finalAmountOverride = MoneyCents(8_000L),
                        discountMode = SaleDiscountInput.SelectedProducts(setOf(prodA.id, prodB.id)),
                        paymentMethod = SalePaymentMethod.PIX,
                        protectedDiscountPersonIds = setOf(bia.id),
                    ),
                ),
            )

            val exported = JsonBackupExporter.export(database = app.database)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            assertTrue("Leitura do backup deve ter sucesso", readResult.isValid)
            val validatedData = readResult.validatedData!!
            assertEquals("SELECTED_PRODUCTS", validatedData.sales.single().discountMode)

            // Limpa banco local e valida planejamento + validação de mesclagem do backup
            app.database.clearAllTables()
            val plan = JsonBackupMergePlanner.plan(
                readResult = readResult,
                database = app.database,
            )
            val validation = JsonBackupMergeDecisionValidator.validate(
                plan = plan,
                readResult = readResult,
                database = app.database,
            )
            assertEquals(0, validation.blockedOperationsCount)
            assertEquals(0, validation.incompatibleDecisionsCount)
            assertEquals(0, validation.unresolvedConflictsCount)
            assertTrue(validation.localStateMatchesPlan)
            assertTrue(validation.preconditions.canExecuteAdditiveWithoutUserDecisions)
        }
    }
}
