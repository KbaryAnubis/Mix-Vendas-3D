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
 * Testes integrados de UI (Jetpack Compose + Room + Robolectric) para o sistema revisado de descontos:
 * - Caixa 1 ("Produtos que receberão desconto") e Caixa 2 recolhível ("PESSOAS QUE RECEBERÃO O DESCONTO")
 *   visíveis imediatamente em VENDER -> NOVA VENDA -> Valor final e desconto.
 * - Agrupamento independente por produto na Caixa 2, com divisão igualitária por produto.
 * - Ausência completa de proteção automática de R$ 5,00/un ou diálogo de autorização.
 * - Prévia ao vivo por produto e por pessoa, bloqueio por ausência de selecionados ou saldo insuficiente.
 * - Sincronização dinâmica ao aumentar quantidade, adicionar ou remover produtos.
 * - Persistência, reabertura na edição sem duplo desconto, preservação histórica e backup JSON.
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
    fun `01 nova venda renders independent per-product recipient box, no R$5 auto-protection, blocks empty product recipients, and edits without double discount`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val seeded = runBlocking(Dispatchers.IO) {
            val marcos = app.catalogRepository.createPerson("Marcos")
            val lucas = app.catalogRepository.createPerson("Lucas")
            val carla = app.catalogRepository.createPerson("Carla")

            // Prod A (R$ 45,00): Marcos R$ 40,00 + Lucas R$ 5,00 (sem proteção automática)
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
            // Prod B (R$ 25,00): Marcos R$ 20,00 + Lucas R$ 5,00
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

        // 1) Abre VENDER -> NOVA VENDA: seção DESCONTOS aparece separada com APLICAR DESCONTO desmarcado por padrão
        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_header_title").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discounts_section_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("discounts_section_title").assertTextContains("DESCONTOS")
        composeRule.onNodeWithTag("apply_discount_checkbox").performScrollTo().assertIsOff()
        composeRule.onAllNodesWithTag("sale_final_value_input").assertCountEquals(0)
        composeRule.onAllNodesWithTag("discount_products_box").assertCountEquals(0)

        // Marca APLICAR DESCONTO e valida expansão das opções (inclui seletor de modo UNIFICADA / INDIVIDUAL POR PRODUTO)
        composeRule.onNodeWithTag("apply_discount_checkbox").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_products_box").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discount_products_empty").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("discount_mode_selector").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("discount_mode_individual").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_recipient_people_box").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("PESSOAS QUE RECEBERÃO O DESCONTO").assertExists()

        // 2) Adiciona os 3 produtos (Subtotal = R$ 100,00) e informa valor final R$ 86,00 (desconto = R$ 14,00)
        pickProducts(listOf(prodAId, prodBId, prodCId))
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("86,00")
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_discount_preview_card").fetchSemanticsNodes().isNotEmpty()
        }

        // 3) Limpar seleção na Caixa 1 bloqueia salvamento; Selecionar todos restaura; desmarcar Prod C deixa Prod A e Prod B
        composeRule.onNodeWithTag("discount_clear_selection_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_no_products_warning").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size })

        composeRule.onNodeWithTag("discount_select_all_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discount_product_row_$prodCId").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discount_product_checkbox_$prodCId").performScrollTo().assertIsOff()

        // 4) Caixa 2 recolhível inicia fechada e exibe grupos independentes para Prod A e Prod B (não Prod C)
        composeRule.onAllNodesWithTag("discount_recipient_product_group_$prodAId").assertCountEquals(0)
        composeRule.onNodeWithTag("discount_recipient_people_header").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_recipient_product_group_$prodAId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discount_recipient_product_group_$prodBId").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag("discount_recipient_product_group_$prodCId").assertCountEquals(0)

        // Sem proteção automática: Marcos e Lucas vêm todos marcados nos 2 produtos
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prodAId}_$marcosId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prodAId}_$lucasId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prodBId}_$marcosId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prodBId}_$lucasId").performScrollTo().assertIsOn()

        // 5) Independência por produto: desmarca Lucas APENAS em Prod B (NÃO abre diálogo de R$ 5,00!)
        // Prod A continua com Lucas selecionado!
        composeRule.onNodeWithTag("discount_recipient_person_row_${prodBId}_$lucasId").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Autorizar", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prodBId}_$lucasId").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prodAId}_$lucasId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_recipient_group_count_$prodAId").performScrollTo().assertTextContains("2 de 2 selecionada(s)")
        composeRule.onNodeWithTag("discount_recipient_group_count_$prodBId").performScrollTo().assertTextContains("1 de 2 selecionada(s)")

        // 6) Cenário D: desmarcar todas as pessoas de Prod A bloqueia com aviso explícito e impede salvamento
        composeRule.onNodeWithTag("discount_recipient_person_row_${prodAId}_$marcosId").performScrollTo().performClick()
        composeRule.onNodeWithTag("discount_recipient_person_row_${prodAId}_$lucasId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_recipient_no_people_warning_$prodAId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_discount_preview_error").performScrollTo().assertExists()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size })

        // Re-marca Marcos e Lucas em Prod A -> erro desaparece e prévia mostra valores exatos!
        // Desconto R$ 14,00 em Prod A (R$ 45 -> -R$ 9,00) + Prod B (R$ 25 -> -R$ 5,00):
        // Prod A (Marcos + Lucas): -4,50 cada -> Marcos 35,50, Lucas 0,50
        // Prod B (somente Marcos): Marcos -5,00 -> 15,00; Lucas 5,00 (100% mantido)
        // Prod C (inalterado): Carla 30,00
        composeRule.onNodeWithTag("discount_recipient_person_row_${prodAId}_$marcosId").performScrollTo().performClick()
        composeRule.onNodeWithTag("discount_recipient_person_row_${prodAId}_$lucasId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_discount_preview_error").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag("vender_discount_preview_card").performScrollTo().assertExists()
        composeRule.onNodeWithTag("preview_product_recipient_${prodAId}_$marcosId").performScrollTo()
            .assertTextContains("Marcos", substring = true)
            .assertTextContains("R$ 35,50", substring = true)
        composeRule.onNodeWithTag("preview_product_recipient_${prodAId}_$lucasId").performScrollTo()
            .assertTextContains("Lucas", substring = true)
            .assertTextContains("R$ 0,50", substring = true)
        composeRule.onNodeWithTag("preview_product_unselected_${prodBId}_$lucasId").performScrollTo()
            .assertTextContains("R$ 5,00", substring = true)
            .assertTextContains("sem desconto", substring = true)
        composeRule.onNodeWithTag("preview_person_$marcosId").performScrollTo()
            .assertTextContains("Antes: R$ 60,00", substring = true)
            .assertTextContains("Depois: R$ 50,50", substring = true)
        composeRule.onNodeWithTag("preview_person_$lucasId").performScrollTo()
            .assertTextContains("Antes: R$ 10,00", substring = true)
            .assertTextContains("Depois: R$ 5,50", substring = true)
        composeRule.onNodeWithTag("preview_person_$carlaId").performScrollTo()
            .assertTextContains("Antes: R$ 30,00", substring = true)
            .assertTextContains("Depois: R$ 30,00", substring = true)

        // Fecha caixa recolhível e salva no Room
        composeRule.onNodeWithTag("discount_recipient_people_header").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_recipient_product_group_$prodAId").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("pay_method_pix").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size == 1 }
        }

        val saved = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().single() }
        assertTrue(saved.calculation.discountConfig is SaleDiscountConfigSnapshot.SelectedProducts)
        assertEquals(5_050L, saved.calculation.personTotals.first { it.personId == marcosId }.grossAmount.rawCents)
        assertEquals(550L, saved.calculation.personTotals.first { it.personId == lucasId }.grossAmount.rawCents)
        assertEquals(3_000L, saved.calculation.personTotals.first { it.personId == carlaId }.grossAmount.rawCents)

        // 7) Reabre na edição em VENDAS -> confere reconstrução fiel por produto e salva alteração sem duplo desconto
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_${saved.id}"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("sale_edit_${saved.id}").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_editing_banner").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("apply_discount_checkbox").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_product_checkbox_$prodAId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_product_checkbox_$prodBId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_product_checkbox_$prodCId").performScrollTo().assertIsOff()

        composeRule.onNodeWithTag("discount_recipient_people_header").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_recipient_person_checkbox_${prodBId}_$lucasId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prodAId}_$lucasId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prodBId}_$lucasId").performScrollTo().assertIsOff()

        // Marca Lucas também em Prod B agora e salva -> sem duplo desconto (subtotal continua 100,00, final 86,00)
        composeRule.onNodeWithTag("discount_recipient_person_row_${prodBId}_$lucasId").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prodBId}_$lucasId").performScrollTo().assertIsOn()

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_editing_banner").fetchSemanticsNodes().isEmpty()
        }

        val edited = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saved.id)!! }
        assertEquals(10_000L, edited.calculation.originalSubtotal.rawCents)
        assertEquals(1_400L, edited.calculation.discountTotal.rawCents)
        assertEquals(8_600L, edited.calculation.finalAmount.rawCents)
        // Prod B (-5,00) agora dividido igual (-2,50 cada): Marcos 17,50, Lucas 2,50 -> totais: Marcos 53,00, Lucas 3,00
        assertEquals(5_300L, edited.calculation.personTotals.first { it.personId == marcosId }.grossAmount.rawCents)
        assertEquals(300L, edited.calculation.personTotals.first { it.personId == lucasId }.grossAmount.rawCents)
        assertEquals(3_000L, edited.calculation.personTotals.first { it.personId == carlaId }.grossAmount.rawCents)
    }

    @Test
    fun `02 increasing quantity, adding products, and removing products sync both discount boxes without losing selections`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val seeded = runBlocking(Dispatchers.IO) {
            val joao = app.catalogRepository.createPerson("João")
            val maria = app.catalogRepository.createPerson("Maria")
            val pedro = app.catalogRepository.createPerson("Pedro")

            val prod1 = app.catalogRepository.createProduct(
                name = "Luminária 3D",
                price = MoneyCents(4_500L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(joao.id, MoneyCents(4_000L)),
                    ProductParticipantDraft(maria.id, MoneyCents(500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prod2 = app.catalogRepository.createProduct(
                name = "Porta Caneta",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(joao.id, MoneyCents(2_000L)),
                    ProductParticipantDraft(pedro.id, MoneyCents(1_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prod3 = app.catalogRepository.createProduct(
                name = "Imã Coleção",
                price = MoneyCents(2_500L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(pedro.id, MoneyCents(2_500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            listOf(joao.id, maria.id, pedro.id, prod1.id, prod2.id, prod3.id)
        }
        val joaoId = seeded[0]
        val mariaId = seeded[1]
        val pedroId = seeded[2]
        val prod1Id = seeded[3]
        val prod2Id = seeded[4]
        val prod3Id = seeded[5]

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        pickProducts(listOf(prod1Id, prod2Id))

        composeRule.onNodeWithTag("apply_discount_checkbox").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_mode_individual").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discount_mode_individual").performScrollTo().performClick()
        composeRule.onNodeWithTag("discount_recipient_people_header").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_recipient_person_row_${prod1Id}_$mariaId").fetchSemanticsNodes().isNotEmpty()
        }

        // Desmarca Maria em Prod 1 ANTES de informar o desconto
        composeRule.onNodeWithTag("discount_recipient_person_row_${prod1Id}_$mariaId").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prod1Id}_$mariaId").performScrollTo().assertIsOff()

        // Aumenta quantidade de Prod 1 (1x -> 2x): Maria segue desmarcada em Prod 1 e valor original atualiza para R$ 10,00
        composeRule.onNodeWithTag("selected_inc_$prod1Id").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prod1Id}_$mariaId").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("discount_recipient_person_row_${prod1Id}_$mariaId")
            .assertTextContains("R$ 10,00", substring = true)

        // Remove Prod 2 da venda e adiciona Prod 3 -> Caixa 2 remove Prod 2, adiciona Prod 3 marcado e preserva Prod 1
        composeRule.onNodeWithTag("selected_remove_$prod2Id").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_recipient_product_group_$prod2Id").fetchSemanticsNodes().isEmpty()
        }
        pickProducts(listOf(prod3Id))
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_recipient_product_group_$prod3Id").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prod3Id}_$pedroId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prod1Id}_$mariaId").performScrollTo().assertIsOff()

        // Subtotal agora = 2x 45 + 25 = R$ 115,00. Informa final R$ 92,00 (desconto R$ 23,00: Prod1 -18,00, Prod3 -5,00)
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("92,00")
        composeRule.onNodeWithTag("pay_method_pix").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size == 1 }
        }

        val saved = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().single() }
        // Prod 1 (90,00) - 18,00 só João -> João 62,00, Maria 10,00 (inalterada!); Prod 3 (25,00) - 5,00 Pedro -> 20,00
        assertEquals(6_200L, saved.calculation.personTotals.first { it.personId == joaoId }.grossAmount.rawCents)
        assertEquals(1_000L, saved.calculation.personTotals.first { it.personId == mariaId }.grossAmount.rawCents)
        assertEquals(2_000L, saved.calculation.personTotals.first { it.personId == pedroId }.grossAmount.rawCents)
    }

    @Test
    fun `03 insufficient balance blocks discount when equal share exceeds smaller participant and unblocks when deselected`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (anaId, brunoId, prodId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")
            // Subtotal R$ 55,00: Ana R$ 50,00 | Bruno R$ 5,00
            val prod = app.catalogRepository.createProduct(
                name = "Busto Raro",
                price = MoneyCents(5_500L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(5_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            Triple(ana.id, bruno.id, prod.id)
        }

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        pickProducts(listOf(prodId))

        // Desconto R$ 12,00 -> com Ana e Bruno marcados, cota igualitária é R$ 6,00 cada > saldo de Bruno (R$ 5,00)
        composeRule.onNodeWithTag("apply_discount_checkbox").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("sale_final_value_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("43,00")
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_discount_preview_error").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_discount_preview_error")
            .assertTextContains("Bruno", substring = true)

        // Tentativa de salvar é bloqueada sem transferir R$ 1,00 para Ana nem deixar saldo negativo
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size })

        // Muda para INDIVIDUAL POR PRODUTO, abre Caixa 2 e desmarca Bruno -> apenas Ana recebe os R$ 12,00 (50 -> 38)
        composeRule.onNodeWithTag("discount_mode_individual").performScrollTo().performClick()
        composeRule.onNodeWithTag("discount_recipient_people_header").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_recipient_person_row_${prodId}_$brunoId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discount_recipient_person_row_${prodId}_$brunoId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_discount_preview_error").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag("pay_method_pix").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size == 1 }
        }

        val saved = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().single() }
        assertEquals(3_800L, saved.calculation.personTotals.first { it.personId == anaId }.grossAmount.rawCents)
        assertEquals(500L, saved.calculation.personTotals.first { it.personId == brunoId }.grossAmount.rawCents)
    }

    @Test
    fun `04 historical legacy sales remain intact and json backup round-trip succeeds with equal-division sales`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (legacySaleId, prodAId, anaId, brunoId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")
            val prodA = app.catalogRepository.createProduct(
                name = "Vaso Legado",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(4_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(1_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            // Grava venda prévia onde somente Ana recebeu o desconto (Bruno 1000 preservado)
            val legacy = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodA, 1)),
                        finalAmountOverride = MoneyCents(4_000L),
                        discountMode = SaleDiscountInput.Proportional,
                        paymentMethod = SalePaymentMethod.PIX,
                        discountRecipientPersonIdsByProduct = mapOf(prodA.id to setOf(ana.id)),
                        note = "Venda histórica",
                    ),
                ),
            )
            listOf(legacy.id, prodA.id, ana.id, bruno.id)
        }

        val legacyBefore = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(legacySaleId)!! }
        assertEquals(1_000L, legacyBefore.calculation.personTotals.first { it.personId == brunoId }.grossAmount.rawCents)

        // Cria uma nova venda pela UI com divisão igualitária entre Ana e Bruno (padrão sem proteção automática)
        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        pickProducts(listOf(prodAId))
        composeRule.onNodeWithTag("apply_discount_checkbox").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("sale_final_value_input").fetchSemanticsNodes().isNotEmpty()
        }
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
        // Nova venda dividiu R$ 10,00 igualmente (R$ 5,00 cada) -> Ana 35,00 e Bruno 5,00
        assertEquals(3_500L, newSale.calculation.personTotals.first { it.personId == anaId }.grossAmount.rawCents)
        assertEquals(500L, newSale.calculation.personTotals.first { it.personId == brunoId }.grossAmount.rawCents)

        // Exporta JSON e valida leitura + planejamento + mesclagem sem conflitos
        runBlocking(Dispatchers.IO) {
            val exported = JsonBackupExporter.export(database = app.database)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            assertTrue("Leitura do backup deve ter sucesso", readResult.isValid)
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

    @Test
    fun `05 aplicar desconto checkbox starts unchecked, hides controls, clears discount when unchecked, and restores cleanly on edit`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (anaId, brunoId, prodId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")
            val prod = app.catalogRepository.createProduct(
                name = "Luminária Estrela",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(6_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(4_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            Triple(ana.id, bruno.id, prod.id)
        }

        // 1) Nova venda inicia com APLICAR DESCONTO desmarcado e todos os campos de desconto ocultos
        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        pickProducts(listOf(prodId))

        composeRule.onNodeWithTag("discounts_section_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("apply_discount_checkbox").performScrollTo().assertIsOff()
        composeRule.onAllNodesWithTag("sale_final_value_input").assertCountEquals(0)
        composeRule.onAllNodesWithTag("discount_products_box").assertCountEquals(0)
        composeRule.onAllNodesWithTag("discount_mode_selector").assertCountEquals(0)
        composeRule.onAllNodesWithTag("discount_unified_people_box").assertCountEquals(0)
        composeRule.onAllNodesWithTag("vender_discount_preview_card").assertCountEquals(0)
        composeRule.onNodeWithTag("vender_selected_summary_bar").performScrollTo().assertTextContains("R$ 100,00", substring = true)

        // 2) Marca -> informa desconto R$ 20,00 -> depois DESMARCA -> desconto é zerado e ocultado sem resíduos
        composeRule.onNodeWithTag("apply_discount_checkbox").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("sale_final_value_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("80,00")
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_discount_preview_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_selected_summary_bar").performScrollTo().assertTextContains("R$ 80,00", substring = true)

        // Desmarca APLICAR DESCONTO: retorna ao subtotal cheio (R$ 100,00) e oculta opções
        composeRule.onNodeWithTag("apply_discount_checkbox").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("sale_final_value_input").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("vender_selected_summary_bar").performScrollTo().assertTextContains("R$ 100,00", substring = true)

        // Marca novamente -> inicia sem desconto prévio e salva com desconto válido de R$ 10,00 (sem duplicação)
        composeRule.onNodeWithTag("apply_discount_checkbox").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("sale_final_value_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().assertTextContains("100,00")
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("90,00")
        composeRule.onNodeWithTag("pay_method_pix").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size == 1 }
        }

        val savedWithDiscount = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().single() }
        assertEquals(1_000L, savedWithDiscount.calculation.discountTotal.rawCents)
        assertEquals(9_000L, savedWithDiscount.calculation.finalAmount.rawCents)

        // 3) Reabre para editar -> APLICAR DESCONTO vem marcado; desmarcar remove desconto e salva cheio (R$ 100,00)
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_${savedWithDiscount.id}"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("sale_edit_${savedWithDiscount.id}").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_editing_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("apply_discount_checkbox").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("apply_discount_checkbox").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("sale_final_value_input").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_editing_banner").fetchSemanticsNodes().isEmpty()
        }

        val clearedSale = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(savedWithDiscount.id)!! }
        assertEquals(0L, clearedSale.calculation.discountTotal.rawCents)
        assertEquals(10_000L, clearedSale.calculation.finalAmount.rawCents)
        assertEquals(6_000L, clearedSale.calculation.personTotals.first { it.personId == anaId }.grossAmount.rawCents)
        assertEquals(4_000L, clearedSale.calculation.personTotals.first { it.personId == brunoId }.grossAmount.rawCents)

        // Reabre venda SEM desconto -> APLICAR DESCONTO vem desmarcado e campos ocultos
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_${clearedSale.id}"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("sale_edit_${clearedSale.id}").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_editing_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("apply_discount_checkbox").performScrollTo().assertIsOff()
        composeRule.onAllNodesWithTag("sale_final_value_input").assertCountEquals(0)
    }

    @Test
    fun `06 UNIFICADA mode deduplicates names, syncs dynamically with products, blocks missing or low balance, and switches cleanly`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val seeded = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")
            val carla = app.catalogRepository.createPerson("Carla")

            // Prod A (R$ 60,00): Ana R$ 55,00 + Bruno R$ 5,00
            val prodA = app.catalogRepository.createProduct(
                name = "Boneco A",
                price = MoneyCents(6_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(5_500L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            // Prod B (R$ 40,00): Ana R$ 35,00 + Bruno R$ 5,00
            val prodB = app.catalogRepository.createProduct(
                name = "Chaveiro B",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(3_500L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            // Prod C (R$ 30,00): Carla R$ 30,00
            val prodC = app.catalogRepository.createProduct(
                name = "Base C",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(carla.id, MoneyCents(3_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            listOf(ana.id, bruno.id, carla.id, prodA.id, prodB.id, prodC.id)
        }
        val anaId = seeded[0]
        val brunoId = seeded[1]
        val carlaId = seeded[2]
        val prodAId = seeded[3]
        val prodBId = seeded[4]
        val prodCId = seeded[5]

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        pickProducts(listOf(prodAId, prodBId))

        composeRule.onNodeWithTag("apply_discount_checkbox").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_unified_people_box").fetchSemanticsNodes().isNotEmpty()
        }

        // Modo UNIFICADA padrão lista Ana e Bruno UMA única vez (sem repetição!)
        composeRule.onAllNodesWithTag("discount_unified_person_row_$anaId").assertCountEquals(1)
        composeRule.onAllNodesWithTag("discount_unified_person_row_$brunoId").assertCountEquals(1)
        composeRule.onAllNodesWithTag("discount_unified_person_row_$carlaId").assertCountEquals(0)

        // Adiciona Prod C -> Carla entra automaticamente na lista unificada!
        pickProducts(listOf(prodCId))
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_unified_person_row_$carlaId").fetchSemanticsNodes().isNotEmpty()
        }
        // Desmarca Prod C na Caixa 1 -> Carla sai da lista unificada imediatamente
        composeRule.onNodeWithTag("discount_product_row_$prodCId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_unified_person_row_$carlaId").fetchSemanticsNodes().isEmpty()
        }

        // Desconto R$ 20,00 em Prod A (60 -> -12) + Prod B (40 -> -8), Prod C (30) intacto -> final R$ 110,00
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("110,00")
        // Com Ana e Bruno selecionados, Prod A tenta cobrar R$ 6,00 de Bruno (saldo R$ 5,00) -> bloqueia!
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_discount_preview_error").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_discount_preview_error").assertTextContains("Bruno", substring = true)

        // Desmarca Bruno na lista UNIFICADA -> desbloqueia! Se também desmarcar Ana, bloqueia por produto sem pessoa
        composeRule.onNodeWithTag("discount_unified_person_row_$brunoId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_discount_preview_error").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("discount_unified_person_row_$anaId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_unified_missing_people_warning").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0, runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size })

        // Re-marca Ana em UNIFICADA e confere alternância UNIFICADA -> INDIVIDUAL -> UNIFICADA preservando escolha
        composeRule.onNodeWithTag("discount_unified_person_row_$anaId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_discount_preview_error").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("discount_mode_individual").performScrollTo().performClick()
        composeRule.onNodeWithTag("discount_recipient_people_header").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_recipient_person_checkbox_${prodAId}_$anaId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prodAId}_$anaId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prodAId}_$brunoId").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prodBId}_$anaId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_recipient_person_checkbox_${prodBId}_$brunoId").performScrollTo().assertIsOff()

        // Volta para UNIFICADA -> Ana marcada, Bruno desmarcado -> salva e confere igualdade prévia == salvo
        composeRule.onNodeWithTag("discount_mode_unified").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_unified_person_checkbox_$anaId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discount_unified_person_checkbox_$anaId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_unified_person_checkbox_$brunoId").performScrollTo().assertIsOff()

        composeRule.onNodeWithTag("preview_person_$anaId").performScrollTo()
            .assertTextContains("Depois: R$ 70,00", substring = true)
        composeRule.onNodeWithTag("preview_person_$brunoId").performScrollTo()
            .assertTextContains("Depois: R$ 10,00", substring = true)
        composeRule.onNodeWithTag("preview_person_$carlaId").performScrollTo()
            .assertTextContains("Depois: R$ 30,00", substring = true)

        composeRule.onNodeWithTag("pay_method_pix").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size == 1 }
        }

        val saved = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().single() }
        assertEquals(7_000L, saved.calculation.personTotals.first { it.personId == anaId }.grossAmount.rawCents)
        assertEquals(1_000L, saved.calculation.personTotals.first { it.personId == brunoId }.grossAmount.rawCents)
        assertEquals(3_000L, saved.calculation.personTotals.first { it.personId == carlaId }.grossAmount.rawCents)

        // Reabre na edição -> restaura automaticamente no modo UNIFICADA com Ana marcada e Bruno desmarcado
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_${saved.id}"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("sale_edit_${saved.id}").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_editing_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discount_unified_person_checkbox_$anaId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_unified_person_checkbox_$brunoId").performScrollTo().assertIsOff()
    }
}
