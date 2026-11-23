package com.mixvendas3d.app

import com.mixvendas3d.app.data.local.entity.SaleItemWithParticipants
import com.mixvendas3d.app.data.local.entity.SaleWithDetails
import com.mixvendas3d.app.data.repository.SaleEntityMapper
import com.mixvendas3d.app.domain.calculation.DiscountPeopleSelectionMode
import com.mixvendas3d.app.domain.calculation.SaleCalculationException
import com.mixvendas3d.app.domain.calculation.SaleDiscountPolicy
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.calculation.SalePersistenceValidator
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.Machine
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductParticipantSplit
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleCalculationResult
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.update.SystemUpdateAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Testes unitários completos da regra revisada de descontos:
 * - Seleção independente por produto das pessoas que RECEBERÃO o desconto.
 * - Divisão igualitária em centavos entre os destinatários selecionados dentro de cada produto.
 * - Sem proteção automática de R$ 5,00/un (seleção explícita por produto é a única regra).
 * - Bloqueio rigoroso de saldo insuficiente por pessoa (sem transbordar para outras pessoas e sem negativos).
 * - Conservação determinística de centavos, taxas pós-desconto, reabertura sem duplo desconto e persistência Room.
 */
class DiscountSystemEngineTest {

    private fun makeProduct(
        id: String,
        name: String,
        priceCents: Long,
        splits: List<Pair<Pair<String, String>, Long>>,
        feeMode: FeeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
        feeOwnerId: String? = null,
    ): Product = Product(
        id = id,
        name = name,
        price = MoneyCents(priceCents),
        active = true,
        participants = splits.mapIndexed { idx, (person, cents) ->
            ProductParticipantSplit(
                personId = person.first,
                personName = person.second,
                personColorIndex = idx,
                amount = MoneyCents(cents),
            )
        },
        feeResponsibilityMode = feeMode,
        feeOwnerPersonId = feeOwnerId,
        createdAtEpochMs = 1_700_000_000_000L,
        updatedAtEpochMs = 1_700_000_000_000L,
    )

    private fun assertInvariants(result: SaleCalculationResult) {
        SalePersistenceValidator.validateForPersistence(result)
        assertEquals(
            result.originalSubtotal.rawCents - result.discountTotal.rawCents,
            result.finalAmount.rawCents,
        )
        assertEquals(
            result.finalAmount.rawCents - result.feeTotal.rawCents,
            result.netTotal.rawCents,
        )
        assertEquals(
            result.discountTotal.rawCents,
            result.items.sumOf { it.discountAmount.rawCents },
        )
        assertEquals(
            result.finalAmount.rawCents,
            result.items.sumOf { it.finalAmount.rawCents },
        )
        assertEquals(
            result.feeTotal.rawCents,
            result.items.sumOf { it.feeAmount.rawCents },
        )
        assertEquals(
            result.netTotal.rawCents,
            result.items.sumOf { it.netAmount.rawCents },
        )
        assertEquals(
            result.finalAmount.rawCents,
            result.personTotals.sumOf { it.grossAmount.rawCents },
        )
        assertEquals(
            result.feeTotal.rawCents,
            result.personTotals.sumOf { it.feeAmount.rawCents },
        )
        assertEquals(
            result.netTotal.rawCents,
            result.personTotals.sumOf { it.netAmount.rawCents },
        )
        result.items.forEach { item ->
            item.participantSplits.forEach { sp ->
                assertTrue("Bruto não pode ser negativo", sp.grossAmount.rawCents >= 0L)
                assertTrue("Líquido não pode ser negativo", sp.netAmount.rawCents >= 0L)
                assertTrue("Desconto não pode ser negativo", sp.discountAmount.rawCents >= 0L)
            }
        }
    }

    @Test
    fun `01 R$12 discount scenarios A, B, C, and D enforce exact equal division and empty-recipient block`() {
        val p1 = "p1" to "Pessoa 1"
        val p2 = "p2" to "Pessoa 2"
        val p3 = "p3" to "Pessoa 3"

        // Produto R$ 100,00: P1 = R$ 70,00 | P2 = R$ 20,00 | P3 = R$ 10,00; Desconto = R$ 12,00
        val prod = makeProduct("prod_100", "Diorama", 10_000L, listOf(p1 to 7_000L, p2 to 2_000L, p3 to 1_000L))

        // Cenário A — todas as 3 pessoas selecionadas: R$ 12,00 / 3 = R$ 4,00 cada
        val resA = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                finalAmountOverride = MoneyCents(8_800L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.PIX,
                discountRecipientPersonIdsByProduct = mapOf(prod.id to setOf(p1.first, p2.first, p3.first)),
            ),
        )
        assertInvariants(resA)
        assertEquals(6_600L, resA.personTotals.first { it.personId == p1.first }.grossAmount.rawCents)
        assertEquals(1_600L, resA.personTotals.first { it.personId == p2.first }.grossAmount.rawCents)
        assertEquals(600L, resA.personTotals.first { it.personId == p3.first }.grossAmount.rawCents)

        // Cenário B — P1 e P2 selecionadas, P3 desmarcada: R$ 12,00 / 2 = R$ 6,00 cada; P3 mantém 100%
        val resB = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                finalAmountOverride = MoneyCents(8_800L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.PIX,
                discountRecipientPersonIdsByProduct = mapOf(prod.id to setOf(p1.first, p2.first)),
            ),
        )
        assertInvariants(resB)
        assertEquals(6_400L, resB.personTotals.first { it.personId == p1.first }.grossAmount.rawCents)
        assertEquals(1_400L, resB.personTotals.first { it.personId == p2.first }.grossAmount.rawCents)
        assertEquals(1_000L, resB.personTotals.first { it.personId == p3.first }.grossAmount.rawCents)
        assertEquals(0L, resB.items.single().participantSplits.first { it.personId == p3.first }.discountAmount.rawCents)

        // Cenário C — somente P1 selecionada: absorve R$ 12,00 inteiro; P2 e P3 mantêm 100%
        val resC = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                finalAmountOverride = MoneyCents(8_800L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.PIX,
                discountRecipientPersonIdsByProduct = mapOf(prod.id to setOf(p1.first)),
            ),
        )
        assertInvariants(resC)
        assertEquals(5_800L, resC.personTotals.first { it.personId == p1.first }.grossAmount.rawCents)
        assertEquals(2_000L, resC.personTotals.first { it.personId == p2.first }.grossAmount.rawCents)
        assertEquals(1_000L, resC.personTotals.first { it.personId == p3.first }.grossAmount.rawCents)

        // Cenário D — nenhuma pessoa selecionada no produto: deve bloquear com erro claro
        try {
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 1)),
                    finalAmountOverride = MoneyCents(8_800L),
                    discountMode = SaleDiscountInput.Proportional,
                    paymentMethod = SalePaymentMethod.PIX,
                    discountRecipientPersonIdsByProduct = mapOf(prod.id to emptySet()),
                ),
            )
            fail("Deveria bloquear produto com desconto sem nenhuma pessoa selecionada")
        } catch (ex: SaleCalculationException) {
            assertTrue(ex.message.orEmpty().contains("pessoa", ignoreCase = true))
        }
    }

    @Test
    fun `02 multiple products support independent person selections even when same person participates in both`() {
        val ana = "p_ana" to "Ana"
        val bruno = "p_bruno" to "Bruno"
        val carla = "p_carla" to "Carla"

        // Prod A (R$ 60,00): Ana R$ 40,00 + Bruno R$ 20,00
        // Prod B (R$ 40,00): Ana R$ 20,00 + Bruno R$ 20,00
        // Prod C (R$ 50,00): Carla R$ 50,00 (não selecionado na Caixa 1)
        val prodA = makeProduct("prod_a", "Vaso A", 6_000L, listOf(ana to 4_000L, bruno to 2_000L))
        val prodB = makeProduct("prod_b", "Suporte B", 4_000L, listOf(ana to 2_000L, bruno to 2_000L))
        val prodC = makeProduct("prod_c", "Chaveiro C", 5_000L, listOf(carla to 5_000L))

        // Subtotal = 150,00; Desconto = 20,00 apenas em prodA (60% -> -12,00) e prodB (40% -> -8,00)
        // Em Prod A: ambos Ana e Bruno selecionados -> -6,00 cada -> Ana 34,00, Bruno 14,00
        // Em Prod B: somente Bruno selecionado -> Ana 20,00 (100% mantido), Bruno -8,00 -> 12,00
        val result = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(
                    SaleItemInput(prodA, 1),
                    SaleItemInput(prodB, 1),
                    SaleItemInput(prodC, 1),
                ),
                finalAmountOverride = MoneyCents(13_000L),
                discountMode = SaleDiscountInput.SelectedProducts(setOf(prodA.id, prodB.id)),
                paymentMethod = SalePaymentMethod.PIX,
                discountRecipientPersonIdsByProduct = mapOf(
                    prodA.id to setOf(ana.first, bruno.first),
                    prodB.id to setOf(bruno.first),
                ),
            ),
        )
        assertInvariants(result)
        assertTrue(result.discountConfig is SaleDiscountConfigSnapshot.SelectedProducts)

        val itemA = result.items.first { it.productId == prodA.id }
        val itemB = result.items.first { it.productId == prodB.id }
        val itemC = result.items.first { it.productId == prodC.id }

        assertEquals(1_200L, itemA.discountAmount.rawCents)
        assertEquals(3_400L, itemA.participantSplits.first { it.personId == ana.first }.grossAmount.rawCents)
        assertEquals(1_400L, itemA.participantSplits.first { it.personId == bruno.first }.grossAmount.rawCents)

        assertEquals(800L, itemB.discountAmount.rawCents)
        assertEquals(2_000L, itemB.participantSplits.first { it.personId == ana.first }.grossAmount.rawCents)
        assertEquals(1_200L, itemB.participantSplits.first { it.personId == bruno.first }.grossAmount.rawCents)

        assertEquals(0L, itemC.discountAmount.rawCents)
        assertEquals(5_000L, itemC.finalAmount.rawCents)

        // Totais consolidados: Ana = 34 + 20 = 54,00; Bruno = 14 + 12 = 26,00; Carla = 50,00
        assertEquals(5_400L, result.personTotals.first { it.personId == ana.first }.grossAmount.rawCents)
        assertEquals(2_600L, result.personTotals.first { it.personId == bruno.first }.grossAmount.rawCents)
        assertEquals(5_000L, result.personTotals.first { it.personId == carla.first }.grossAmount.rawCents)
    }

    @Test
    fun `03 participant with R$ 5 per unit is NOT automatically protected and obeys explicit product selection`() {
        val dono = "p_dono" to "Marcos"
        val parceiro = "p_parceiro" to "Lucas"

        // Produto R$ 25,00: Marcos R$ 20,00 + Lucas R$ 5,00; Venda de 2 unidades (R$ 50,00), desconto R$ 6,00 -> R$ 44,00
        val prod = makeProduct("prod_5", "Miniatura 3D", 2_500L, listOf(dono to 2_000L, parceiro to 500L))

        // Ambos selecionados (comportamento padrão explícito, SEM proteção automática de R$ 5,00):
        // Desconto R$ 6,00 dividido igualmente -> R$ 3,00 cada -> Marcos 37,00, Lucas 7,00
        val bothSelected = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 2)),
                finalAmountOverride = MoneyCents(4_400L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.PIX,
                discountRecipientPersonIdsByProduct = mapOf(prod.id to setOf(dono.first, parceiro.first)),
            ),
        )
        assertInvariants(bothSelected)
        assertEquals(3_700L, bothSelected.personTotals.first { it.personId == dono.first }.grossAmount.rawCents)
        assertEquals(700L, bothSelected.personTotals.first { it.personId == parceiro.first }.grossAmount.rawCents)

        // Quando usuário desmarca Lucas explicitamente: somente Marcos recebe o desconto -> Marcos 34,00, Lucas 10,00 (100%)
        val onlyDono = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 2)),
                finalAmountOverride = MoneyCents(4_400L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.PIX,
                discountRecipientPersonIdsByProduct = mapOf(prod.id to setOf(dono.first)),
            ),
        )
        assertInvariants(onlyDono)
        assertEquals(3_400L, onlyDono.personTotals.first { it.personId == dono.first }.grossAmount.rawCents)
        assertEquals(1_000L, onlyDono.personTotals.first { it.personId == parceiro.first }.grossAmount.rawCents)
    }

    @Test
    fun `04 equal division closes odd centavos deterministically without drift`() {
        val p1 = "p1" to "Alice"
        val p2 = "p2" to "Beto"
        val p3 = "p3" to "Caio"

        // Produto R$ 30,00 (10,00 cada), desconto R$ 1,00 (100 cents) dividido por 3 = 34 + 33 + 33 cents
        val prod = makeProduct(
            id = "prod_1real",
            name = "Teste Centavos",
            priceCents = 3_000L,
            splits = listOf(p1 to 1_000L, p2 to 1_000L, p3 to 1_000L),
        )
        val res = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                finalAmountOverride = MoneyCents(2_900L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.CASH,
                discountRecipientPersonIdsByProduct = mapOf(prod.id to setOf(p1.first, p2.first, p3.first)),
            ),
        )
        assertInvariants(res)
        val itemDiscounts = res.items.single().participantSplits.map { it.discountAmount.rawCents }
        assertEquals(100L, itemDiscounts.sum())
        assertEquals(listOf(33L, 33L, 34L), itemDiscounts.sorted())
        val itemGrosses = res.items.single().participantSplits.map { it.grossAmount.rawCents }
        assertEquals(2_900L, itemGrosses.sum())
        assertEquals(listOf(966L, 967L, 967L), itemGrosses.sorted())
    }

    @Test
    fun `05 strict per-person balance validation blocks excess discount without overflowing or going negative`() {
        val p1 = "p1" to "Ana"
        val p2 = "p2" to "Bruno"

        // Produto R$ 55,00: Ana R$ 50,00, Bruno R$ 5,00
        val prod = makeProduct("prod_cap", "Escultura", 5_500L, listOf(p1 to 5_000L, p2 to 500L))

        // Caso A: ambos selecionados com desconto R$ 12,00 (R$ 6,00 cada).
        // Bruno só tem R$ 5,00 -> NÃO pode ficar negativo e NÃO pode transferir R$ 1,00 para Ana! Deve bloquear!
        try {
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 1)),
                    finalAmountOverride = MoneyCents(4_300L),
                    discountMode = SaleDiscountInput.Proportional,
                    paymentMethod = SalePaymentMethod.PIX,
                    discountRecipientPersonIdsByProduct = mapOf(prod.id to setOf(p1.first, p2.first)),
                ),
            )
            fail("Deveria bloquear quando cota igualitária (R$ 6,00) ultrapassa o saldo de Bruno (R$ 5,00)")
        } catch (ex: SaleCalculationException) {
            assertTrue(ex.message.orEmpty().contains("Bruno"))
            assertTrue(ex.message.orEmpty().contains("ultrapassa", ignoreCase = true))
        }

        // Caso B: somente Bruno selecionado com desconto R$ 6,00 (> R$ 5,00 dele) -> deve bloquear
        try {
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 1)),
                    finalAmountOverride = MoneyCents(4_900L),
                    discountMode = SaleDiscountInput.Proportional,
                    paymentMethod = SalePaymentMethod.PIX,
                    discountRecipientPersonIdsByProduct = mapOf(prod.id to setOf(p2.first)),
                ),
            )
            fail("Deveria bloquear quando única pessoa selecionada não possui saldo suficiente")
        } catch (ex: SaleCalculationException) {
            assertTrue(ex.message.orEmpty().contains("Bruno"))
        }

        // Caso C: limite exato (R$ 10,00 de desconto / 2 = R$ 5,00 cada -> Bruno zera exato sem ficar negativo)
        val exactBoundary = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                finalAmountOverride = MoneyCents(4_500L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.PIX,
                discountRecipientPersonIdsByProduct = mapOf(prod.id to setOf(p1.first, p2.first)),
            ),
        )
        assertInvariants(exactBoundary)
        assertEquals(4_500L, exactBoundary.personTotals.first { it.personId == p1.first }.grossAmount.rawCents)
        assertEquals(0L, exactBoundary.personTotals.first { it.personId == p2.first }.grossAmount.rawCents)
    }

    @Test
    fun `06 machine fees apply strictly on post-discount final amount with equal division across selected recipients`() {
        val p1 = "p1" to "Alice"
        val p2 = "p2" to "Beto"
        val p3 = "p3" to "Caio"
        val p4 = "p4" to "Duda"

        // 4 pessoas: 30, 30, 30, 10 = R$ 100,00. Selecionadas p1, p2, p3 (p4 fora). Desconto R$ 10,00 -> 334, 333, 333
        val prod = makeProduct(
            id = "prod_fee",
            name = "Kit 4 Pessoas",
            priceCents = 10_000L,
            splits = listOf(p1 to 3_000L, p2 to 3_000L, p3 to 3_000L, p4 to 1_000L),
            feeMode = FeeResponsibilityMode.PROPORTIONAL,
        )
        val machine = Machine(
            id = "m_stone",
            name = "Stone",
            debitRateBps = 200,
            creditEnabled = true,
            creditDefaultRateBps = 400,
            brandRatesEnabled = true,
            brandRatesBps = mapOf(CardBrand.MASTERCARD to 450),
            installmentsEnabled = true,
            installmentAddonsBps = mapOf(1 to 0, 2 to 150),
            createdAtEpochMs = 1_700_000_000_000L,
            updatedAtEpochMs = 1_700_000_000_000L,
        )

        // Valor final R$ 90,00; Taxa 6,00% sobre R$ 90,00 = R$ 5,40 (540 cents) -> líquido R$ 84,60
        val result = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                finalAmountOverride = MoneyCents(9_000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = machine,
                cardBrand = CardBrand.MASTERCARD,
                installments = 2,
                discountRecipientPersonIdsByProduct = mapOf(prod.id to setOf(p1.first, p2.first, p3.first)),
            ),
        )
        assertInvariants(result)
        assertEquals(600, result.effectiveFeeRateBps)
        assertEquals(540L, result.feeTotal.rawCents)
        assertEquals(8_460L, result.netTotal.rawCents)

        // Duda sem desconto mantém R$ 10,00 bruto
        val dudaSplit = result.items.single().participantSplits.first { it.personId == p4.first }
        assertEquals(0L, dudaSplit.discountAmount.rawCents)
        assertEquals(1_000L, dudaSplit.grossAmount.rawCents)

        // Os 3 destinatários fecham exatamente os R$ 80,00 restantes (2667 + 2667 + 2666 = 8000)
        val recipientGrosses = listOf(p1.first, p2.first, p3.first).map { pid ->
            result.items.single().participantSplits.first { it.personId == pid }.grossAmount.rawCents
        }
        assertEquals(8_000L, recipientGrosses.sum())
        assertEquals(listOf(2_666L, 2_667L, 2_667L), recipientGrosses.sorted())
    }

    @Test
    fun `07 editing and re-editing persisted sale reconstructs per-product recipients cleanly without double discount`() {
        val ana = "p_ana" to "Ana"
        val bia = "p_bia" to "Bia"
        val carla = "p_carla" to "Carla"

        val prodA = makeProduct("prod_a", "Dragão", 4_000L, listOf(ana to 3_500L, bia to 500L))
        val prodB = makeProduct("prod_b", "Suporte", 3_000L, listOf(ana to 2_500L, bia to 500L))
        val prodC = makeProduct("prod_c", "Base", 2_000L, listOf(carla to 2_000L))

        // Desconto R$ 14,00 em prodA + prodB;
        // Em prodA: apenas Ana selecionada; Em prodB: Ana + Bia selecionadas
        val initial = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1), SaleItemInput(prodC, 1)),
                finalAmountOverride = MoneyCents(7_600L),
                discountMode = SaleDiscountInput.SelectedProducts(setOf(prodA.id, prodB.id)),
                paymentMethod = SalePaymentMethod.PIX,
                discountRecipientPersonIdsByProduct = mapOf(
                    prodA.id to setOf(ana.first),
                    prodB.id to setOf(ana.first, bia.first),
                ),
            ),
        )
        assertInvariants(initial)
        // prodA (40) -8,00 apenas Ana -> Ana 27,00, Bia 5,00 (intacta em A!)
        // prodB (30) -6,00 igual -> -3,00 cada -> Ana 22,00, Bia 2,00
        // prodC (20) sem desconto -> Carla 20,00
        assertEquals(700L, initial.personTotals.first { it.personId == bia.first }.grossAmount.rawCents)
        assertEquals(4_900L, initial.personTotals.first { it.personId == ana.first }.grossAmount.rawCents)
        assertEquals(2_000L, initial.personTotals.first { it.personId == carla.first }.grossAmount.rawCents)

        val persisted = PersistedSale(
            id = "sale_edit_1",
            soldAtEpochMs = 1_710_000_000_000L,
            updatedAtEpochMs = 1_710_000_000_000L,
            calculation = initial,
        )

        val inferredRecipients = SaleDiscountPolicy.inferDiscountRecipientsByProductFromSnapshot(initial)
        assertEquals(setOf(ana.first), inferredRecipients[prodA.id])
        assertEquals(setOf(ana.first, bia.first), inferredRecipients[prodB.id])
        assertEquals(setOf(carla.first), inferredRecipients[prodC.id])

        val editableInput = persisted.toEditableCalculationInput()
        assertEquals(inferredRecipients, editableInput.discountRecipientPersonIdsByProduct)
        assertTrue(editableInput.discountMode is SaleDiscountInput.SelectedProducts)

        // Recalcula sem alterar nada -> idêntico bit-a-bit (zero duplo desconto!)
        val recalculated = SaleFinancialEngine.calculate(editableInput)
        assertInvariants(recalculated)
        assertEquals(initial.originalSubtotal, recalculated.originalSubtotal)
        assertEquals(initial.discountTotal, recalculated.discountTotal)
        assertEquals(initial.finalAmount, recalculated.finalAmount)
        assertEquals(initial.personTotals, recalculated.personTotals)
        assertEquals(
            SystemUpdateAnalyzer.computeCalculationFingerprint(initial),
            SystemUpdateAnalyzer.computeCalculationFingerprint(recalculated),
        )
    }

    @Test
    fun `08 buildProductPeopleGroups, buildPreview, and SaleEntityMapper preserve independent per-product selection`() {
        val ana = "p_ana" to "Ana"
        val bia = "p_bia" to "Bia"
        val carla = "p_carla" to "Carla"

        val prodA = makeProduct("prod_a", "Peça A", 4_000L, listOf(ana to 3_000L, bia to 1_000L))
        val prodB = makeProduct("prod_b", "Peça B", 3_000L, listOf(ana to 2_000L, bia to 1_000L))
        val prodC = makeProduct("prod_c", "Peça C", 2_000L, listOf(carla to 2_000L))

        val selectedMap = mapOf(
            prodA.id to setOf(ana.first),
            prodB.id to setOf(ana.first, bia.first),
            prodC.id to setOf(carla.first),
        )

        val groups = SaleDiscountPolicy.buildProductPeopleGroups(
            selectedDiscountEntries = listOf(prodA to 2, prodB to 1),
            selectedPeopleByProduct = selectedMap,
        )
        assertEquals(listOf(prodA.id, prodB.id), groups.map { it.productId })
        assertEquals(1, groups[0].selectedCount)
        assertEquals(2, groups[0].totalCount)
        assertTrue(groups[0].people.first { it.personId == ana.first }.isSelected)
        assertFalse(groups[0].people.first { it.personId == bia.first }.isSelected)
        assertEquals(6_000L, groups[0].people.first { it.personId == ana.first }.lineOriginalSubtotal.rawCents)
        assertEquals(2, groups[1].selectedCount)
        assertTrue(groups[1].people.first { it.personId == bia.first }.isSelected)

        // Valida buildPreview com erro e válido
        val invalidPreview = SaleDiscountPolicy.buildPreview(
            selectedEntries = listOf(prodA to 1, prodB to 1),
            discountSelectedProductIds = setOf(prodA.id, prodB.id),
            selectedPeopleByProduct = mapOf(prodA.id to emptySet(), prodB.id to setOf(ana.first)),
            discountCents = 1_000L,
            calculationInputBuilder = {
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                    finalAmountOverride = MoneyCents(6_000L),
                    discountMode = SaleDiscountInput.Proportional,
                    paymentMethod = SalePaymentMethod.PIX,
                    discountRecipientPersonIdsByProduct = mapOf(prodA.id to emptySet(), prodB.id to setOf(ana.first)),
                )
            },
        )
        assertFalse(invalidPreview.isValid)
        assertNotNull(invalidPreview.validationError)
        assertNull(invalidPreview.calculationResult)

        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1), SaleItemInput(prodC, 1)),
                finalAmountOverride = MoneyCents(7_600L), // desconto 14,00 em prodA (8,00) + prodB (6,00)
                discountMode = SaleDiscountInput.SelectedProducts(setOf(prodA.id, prodB.id)),
                paymentMethod = SalePaymentMethod.PIX,
                discountRecipientPersonIdsByProduct = mapOf(
                    prodA.id to setOf(ana.first),
                    prodB.id to setOf(ana.first, bia.first),
                ),
            ),
        )
        var counter = 0
        val mapped = SaleEntityMapper.toEntities(
            saleId = "sale_map_1",
            soldAtEpochMs = 1_710_000_000_000L,
            updatedAtEpochMs = 1_710_000_000_000L,
            result = calc,
            itemIdGenerator = { "item_${++counter}" },
        )
        assertEquals("SELECTED_PRODUCTS", mapped.sale.discountMode)

        val details = SaleWithDetails(
            sale = mapped.sale,
            items = mapped.items.map { itemEntity ->
                SaleItemWithParticipants(
                    item = itemEntity,
                    participants = mapped.participants.filter { it.saleItemId == itemEntity.id },
                )
            },
            personTotals = mapped.personTotals,
        )
        val restored = SaleEntityMapper.toDomain(details)
        assertTrue(restored.calculation.discountConfig is SaleDiscountConfigSnapshot.SelectedProducts)
        val restoredCfg = restored.calculation.discountConfig as SaleDiscountConfigSnapshot.SelectedProducts
        assertEquals(setOf(prodA.id, prodB.id), restoredCfg.productIds.toSet())
        assertEquals(calc.personTotals, restored.calculation.personTotals)
        val restoredRecipients = SaleDiscountPolicy.inferDiscountRecipientsByProductFromSnapshot(restored.calculation)
        assertEquals(setOf(ana.first), restoredRecipients[prodA.id])
        assertEquals(setOf(ana.first, bia.first), restoredRecipients[prodB.id])
        assertEquals(setOf(carla.first), restoredRecipients[prodC.id])
    }

    @Test
    fun `09 unified people options deduplicate participants, obey product participation, and switch modes cleanly`() {
        val ana = "p_ana" to "Ana"
        val bia = "p_bia" to "Bia"
        val carla = "p_carla" to "Carla"

        val prodA = makeProduct("prod_a", "Dragão", 5_000L, listOf(ana to 4_000L, bia to 1_000L))
        val prodB = makeProduct("prod_b", "Vaso", 4_000L, listOf(ana to 2_000L, bia to 2_000L))
        val prodC = makeProduct("prod_c", "Base", 3_000L, listOf(carla to 3_000L))

        // Somente prodA e prodB marcados para desconto; ambos Ana e Bia aparecem UMA única vez
        val unified = SaleDiscountPolicy.buildUnifiedPeopleOptions(
            selectedDiscountEntries = listOf(prodA to 1, prodB to 2),
            unifiedSelectedPersonIds = setOf(ana.first),
        )
        assertEquals(listOf(ana.first, bia.first), unified.map { it.personId })
        assertTrue(unified.first { it.personId == ana.first }.isSelected)
        assertFalse(unified.first { it.personId == bia.first }.isSelected)
        // Subtotal original acumulado de Ana em 1x prodA (40) + 2x prodB (40) = R$ 80,00
        assertEquals(8_000L, unified.first { it.personId == ana.first }.totalOriginalSubtotal.rawCents)
        assertEquals(listOf("Dragão", "Vaso"), unified.first { it.personId == ana.first }.participatingProductNames)

        // Ao resolver destinatários em UNIFICADA (somente Ana), prodA e prodB recebem somente Ana;
        // prodC (não selecionado) não recebe desconto nem aparece na lista unificada!
        val resolved = SaleDiscountPolicy.resolveEffectiveRecipientsByProduct(
            mode = DiscountPeopleSelectionMode.UNIFIED,
            selectedDiscountProducts = listOf(prodA, prodB),
            unifiedSelectedPersonIds = setOf(ana.first, carla.first),
            individualSelectedPeopleByProduct = emptyMap(),
        )
        assertEquals(setOf(ana.first), resolved[prodA.id])
        assertEquals(setOf(ana.first), resolved[prodB.id])
        assertFalse(resolved.containsKey(prodC.id))

        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 2), SaleItemInput(prodC, 1)),
                finalAmountOverride = MoneyCents(14_700L), // subtotal 160,00 - desconto 13,00 em prodA (50 -> 45) + prodB (80 -> 72)
                discountMode = SaleDiscountInput.SelectedProducts(setOf(prodA.id, prodB.id)),
                paymentMethod = SalePaymentMethod.PIX,
                discountRecipientPersonIdsByProduct = resolved,
            ),
        )
        assertInvariants(calc)
        // Ana absorve -5,00 em prodA (35) e -8,00 em prodB (32) -> total Ana = 67,00
        // Bia fica 100% intacta em A (10) e B (40) -> total Bia = 50,00; Carla = 30,00
        assertEquals(6_700L, calc.personTotals.first { it.personId == ana.first }.grossAmount.rawCents)
        assertEquals(5_000L, calc.personTotals.first { it.personId == bia.first }.grossAmount.rawCents)
        assertEquals(3_000L, calc.personTotals.first { it.personId == carla.first }.grossAmount.rawCents)
    }

    @Test
    fun `10 snapshot selection mode inference distinguishes UNIFIED vs INDIVIDUAL_BY_PRODUCT`() {
        val ana = "p_ana" to "Ana"
        val bia = "p_bia" to "Bia"
        val prodA = makeProduct("prod_a", "Peça 1", 5_000L, listOf(ana to 3_000L, bia to 2_000L))
        val prodB = makeProduct("prod_b", "Peça 2", 5_000L, listOf(ana to 3_000L, bia to 2_000L))

        // Caso UNIFIED: Ana desmarcada em ambos, somente Bia selecionada nos dois produtos
        val unifiedCalc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                finalAmountOverride = MoneyCents(8_000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.PIX,
                discountRecipientPersonIdsByProduct = mapOf(
                    prodA.id to setOf(bia.first),
                    prodB.id to setOf(bia.first),
                ),
            ),
        )
        val (inferredUnifiedMode, unifiedUnion) = SaleDiscountPolicy.inferDiscountSelectionModeFromSnapshot(unifiedCalc)
        assertEquals(DiscountPeopleSelectionMode.UNIFIED, inferredUnifiedMode)
        assertEquals(setOf(bia.first), unifiedUnion)

        // Caso INDIVIDUAL_BY_PRODUCT: Bia selecionada em prodA e desmarcada em prodB
        val individualCalc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                finalAmountOverride = MoneyCents(8_000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.PIX,
                discountRecipientPersonIdsByProduct = mapOf(
                    prodA.id to setOf(ana.first, bia.first),
                    prodB.id to setOf(ana.first),
                ),
            ),
        )
        val (inferredIndividualMode, individualUnion) = SaleDiscountPolicy.inferDiscountSelectionModeFromSnapshot(individualCalc)
        assertEquals(DiscountPeopleSelectionMode.INDIVIDUAL_BY_PRODUCT, inferredIndividualMode)
        assertEquals(setOf(ana.first, bia.first), individualUnion)
    }
}
