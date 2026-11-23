package com.mixvendas3d.app

import com.mixvendas3d.app.data.local.entity.SaleItemWithParticipants
import com.mixvendas3d.app.data.local.entity.SaleWithDetails
import com.mixvendas3d.app.data.repository.SaleEntityMapper
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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Testes unitários de motor financeiro, política de desconto, arredondamento,
 * proteção de pessoas, remuneração fixa (R$ 5,00/un), reedição sem duplo desconto
 * e serialização/desserialização de entidades Room (SelectedProducts).
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
    fun `01 multiple selected products apply discount only to chosen items leaving unselected completely unchanged`() {
        val ana = "p_ana" to "Ana"
        val bruno = "p_bruno" to "Bruno"
        val carla = "p_carla" to "Carla"

        val prodA = makeProduct("prod_a", "Vaso A", 6_000L, listOf(ana to 4_000L, bruno to 2_000L))
        val prodB = makeProduct("prod_b", "Suporte B", 4_000L, listOf(ana to 2_000L, bruno to 2_000L))
        val prodC = makeProduct("prod_c", "Chaveiro C", 5_000L, listOf(carla to 5_000L))

        // Subtotal = 150,00; Desconto = 20,00 apenas em prodA (60) e prodB (40); prodC (50) inalterado
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
            ),
        )
        assertInvariants(result)
        assertTrue(result.discountConfig is SaleDiscountConfigSnapshot.SelectedProducts)

        val itemA = result.items.first { it.productId == prodA.id }
        val itemB = result.items.first { it.productId == prodB.id }
        val itemC = result.items.first { it.productId == prodC.id }

        // prodA (60% de 100) recebe -12,00 -> 48,00; prodB (40% de 100) recebe -8,00 -> 32,00; prodC recebe 0 -> 50,00
        assertEquals(1_200L, itemA.discountAmount.rawCents)
        assertEquals(4_800L, itemA.finalAmount.rawCents)
        assertEquals(800L, itemB.discountAmount.rawCents)
        assertEquals(3_200L, itemB.finalAmount.rawCents)
        assertEquals(0L, itemC.discountAmount.rawCents)
        assertEquals(5_000L, itemC.finalAmount.rawCents)

        // Carla participa apenas do produto não selecionado -> 100% intacta
        val carlaTotal = result.personTotals.first { it.personId == carla.first }
        assertEquals(5_000L, carlaTotal.grossAmount.rawCents)
        assertEquals(5_000L, carlaTotal.netAmount.rawCents)
    }

    @Test
    fun `02 protecting single and multiple people preserves 100 percent of their shares across all selected products`() {
        val ana = "p_ana" to "Ana"
        val bruno = "p_bruno" to "Bruno"
        val carla = "p_carla" to "Carla"

        val prodA = makeProduct("prod_a", "Item A", 5_000L, listOf(ana to 3_000L, bruno to 1_000L, carla to 1_000L))
        val prodB = makeProduct("prod_b", "Item B", 5_000L, listOf(ana to 2_000L, bruno to 2_000L, carla to 1_000L))

        // Caso 1: apenas Carla protegida em ambos os produtos; desconto R$ 20,00 -> Ana e Bruno absorvem
        val singleProtected = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                finalAmountOverride = MoneyCents(8_000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.CASH,
                protectedDiscountPersonIds = setOf(carla.first),
            ),
        )
        assertInvariants(singleProtected)
        // Carla tinha 10,00 em A + 10,00 em B = 20,00 -> permanece exatamente 20,00
        assertEquals(2_000L, singleProtected.personTotals.first { it.personId == carla.first }.grossAmount.rawCents)
        singleProtected.items.forEach { item ->
            val carlaSplit = item.participantSplits.first { it.personId == carla.first }
            assertEquals(0L, carlaSplit.discountAmount.rawCents)
            assertEquals(carlaSplit.originalSubtotal.rawCents, carlaSplit.grossAmount.rawCents)
        }

        // Caso 2: Bruno e Carla protegidos -> somente Ana absorve integralmente os R$ 20,00
        val multiProtected = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                finalAmountOverride = MoneyCents(8_000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.CASH,
                protectedDiscountPersonIds = setOf(bruno.first, carla.first),
            ),
        )
        assertInvariants(multiProtected)
        assertEquals(3_000L, multiProtected.personTotals.first { it.personId == bruno.first }.grossAmount.rawCents)
        assertEquals(2_000L, multiProtected.personTotals.first { it.personId == carla.first }.grossAmount.rawCents)
        // Ana tinha 50,00 -> 30,00
        assertEquals(3_000L, multiProtected.personTotals.first { it.personId == ana.first }.grossAmount.rawCents)
    }

    @Test
    fun `03 real world R$ 5 fixed unit share is detected, protected by default, and reduced only when authorized`() {
        val dono = "p_dono" to "Marcos"
        val parceiro = "p_parceiro" to "Lucas"

        // Produto de R$ 25,00: Marcos R$ 20,00 + Lucas R$ 5,00 (valor fixo por unidade)
        val prod = makeProduct("prod_fixo", "Miniatura 3D", 2_500L, listOf(dono to 2_000L, parceiro to 500L))
        val detectedFixed = SaleDiscountPolicy.detectFixedUnitSharePersonIds(listOf(prod))
        assertEquals(setOf(parceiro.first), detectedFixed)

        // Venda de 2 unidades (R$ 50,00) com desconto de R$ 10,00 -> final R$ 40,00
        // Padrão (Lucas protegido): Lucas mantém R$ 10,00 (2x R$ 5,00); Marcos cai de R$ 40,00 para R$ 30,00
        val protectedCalc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 2)),
                finalAmountOverride = MoneyCents(4_000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.PIX,
                protectedDiscountPersonIds = detectedFixed,
            ),
        )
        assertInvariants(protectedCalc)
        assertEquals(1_000L, protectedCalc.personTotals.first { it.personId == parceiro.first }.grossAmount.rawCents)
        assertEquals(3_000L, protectedCalc.personTotals.first { it.personId == dono.first }.grossAmount.rawCents)

        // Com autorização explícita (Lucas fora de protectedDiscountPersonIds): proporcional 80% / 20%
        // Desconto R$ 10,00 => Marcos -8,00 (32,00) e Lucas -2,00 (8,00)
        val authorizedCalc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 2)),
                finalAmountOverride = MoneyCents(4_000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.PIX,
                protectedDiscountPersonIds = emptySet(),
            ),
        )
        assertInvariants(authorizedCalc)
        assertEquals(800L, authorizedCalc.personTotals.first { it.personId == parceiro.first }.grossAmount.rawCents)
        assertEquals(3_200L, authorizedCalc.personTotals.first { it.personId == dono.first }.grossAmount.rawCents)
    }

    @Test
    fun `04 selectable people box deduplicates people across multiple selected products and excludes unselected-only people`() {
        val ana = "p_ana" to "Ana"
        val bruno = "p_bruno" to "Bruno"
        val carla = "p_carla" to "Carla"

        val prodA = makeProduct("prod_a", "Item A", 2_500L, listOf(ana to 2_000L, bruno to 500L))
        val prodB = makeProduct("prod_b", "Item B", 3_000L, listOf(ana to 2_500L, bruno to 500L))
        val prodC = makeProduct("prod_c", "Item C", 4_000L, listOf(carla to 4_000L))

        val people = SaleDiscountPolicy.buildSelectablePeopleForDiscount(
            selectedDiscountEntries = listOf(prodA to 2, prodB to 1),
            protectedPersonIds = setOf(bruno.first),
            unprotectedFixedPersonIds = emptySet(),
        )

        // Ana e Bruno aparecem UMA única vez cada; Carla (só em prodC não selecionado) NÃO aparece
        assertEquals(listOf(ana.first, bruno.first), people.map { it.personId })
        val anaItem = people.first { it.personId == ana.first }
        assertEquals(2, anaItem.productCount)
        assertEquals(3, anaItem.totalUnitsAcrossSelected)
        assertEquals(6_500L, anaItem.originalTotalInSelectedProducts.rawCents)
        assertFalse(anaItem.isProtected)

        val brunoItem = people.first { it.personId == bruno.first }
        assertEquals(2, brunoItem.productCount)
        assertEquals(3, brunoItem.totalUnitsAcrossSelected)
        assertEquals(1_500L, brunoItem.originalTotalInSelectedProducts.rawCents)
        assertTrue(brunoItem.isProtected)
        assertTrue(brunoItem.isFixedUnitProtectedByDefault)

        // Confirma que prodC não contaminou a lista
        assertTrue(people.none { it.personId == carla.first })
        assertEquals(setOf(ana.first, bruno.first), SaleDiscountPolicy.collectPersonIdsInProducts(listOf(prodA, prodB)))
        assertEquals(setOf(carla.first), SaleDiscountPolicy.collectPersonIdsInProducts(listOf(prodC)))
    }

    @Test
    fun `05 excessive discount exceeding eligible balance or with all participants protected is blocked cleanly`() {
        val ana = "p_ana" to "Ana"
        val bruno = "p_bruno" to "Bruno"

        // Subtotal R$ 50,00: Ana R$ 20,00 (elegível), Bruno R$ 30,00 (protegido)
        val prod = makeProduct("prod_cap", "Escultura", 5_000L, listOf(ana to 2_000L, bruno to 3_000L))

        // Desconto R$ 25,00 > saldo elegível R$ 20,00 -> deve bloquear sem tirar nada do Bruno
        try {
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 1)),
                    finalAmountOverride = MoneyCents(2_500L),
                    discountMode = SaleDiscountInput.Proportional,
                    paymentMethod = SalePaymentMethod.PIX,
                    protectedDiscountPersonIds = setOf(bruno.first),
                ),
            )
            fail("Deveria bloquear desconto maior que o saldo elegível")
        } catch (ex: IllegalArgumentException) {
            assertTrue(
                ex.message.orEmpty().contains("elegível", ignoreCase = true) ||
                    ex.message.orEmpty().contains("ultrapassa", ignoreCase = true),
            )
        }

        // Todos os participantes protegidos -> deve bloquear
        try {
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 1)),
                    finalAmountOverride = MoneyCents(4_500L),
                    discountMode = SaleDiscountInput.Proportional,
                    paymentMethod = SalePaymentMethod.PIX,
                    protectedDiscountPersonIds = setOf(ana.first, bruno.first),
                ),
            )
            fail("Deveria bloquear quando todas as pessoas estão protegidas")
        } catch (ex: IllegalArgumentException) {
            assertTrue(ex.message.orEmpty().contains("protegid", ignoreCase = true))
        }

        // No limite exato do saldo elegível (desconto R$ 20,00): Ana vai a R$ 0,00 (nunca negativo), Bruno fica R$ 30,00
        val exactCap = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                finalAmountOverride = MoneyCents(3_000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.PIX,
                protectedDiscountPersonIds = setOf(bruno.first),
            ),
        )
        assertInvariants(exactCap)
        assertEquals(0L, exactCap.personTotals.first { it.personId == ana.first }.grossAmount.rawCents)
        assertEquals(3_000L, exactCap.personTotals.first { it.personId == bruno.first }.grossAmount.rawCents)
    }

    @Test
    fun `06 Hamilton rounding closes exact last centavo and machine fee applies strictly to final post-discount amount`() {
        val p1 = "p1" to "Alice"
        val p2 = "p2" to "Beto"
        val p3 = "p3" to "Caio"
        val p4 = "p4" to "Duda"

        // 3 elegíveis com R$ 30,00 cada + 1 protegida com R$ 10,00 = R$ 100,00
        // Desconto de R$ 10,00 (1000 cents) / 3 elegíveis -> 334 + 333 + 333 = 1000 cents exatos
        val prod = makeProduct(
            id = "prod_round",
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

        // Valor final = R$ 90,00; Taxa crédito Mastercard 2x = 4,50% + 1,50% = 6,00% sobre R$ 90,00 = R$ 5,40 (540 cents)
        val result = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                finalAmountOverride = MoneyCents(9_000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = machine,
                cardBrand = CardBrand.MASTERCARD,
                installments = 2,
                protectedDiscountPersonIds = setOf(p4.first),
            ),
        )
        assertInvariants(result)
        assertEquals(600, result.effectiveFeeRateBps)
        assertEquals(540L, result.feeTotal.rawCents) // 6% de 90,00 = 5,40 (e NÃO 6% de 100,00 = 6,00)
        assertEquals(8_460L, result.netTotal.rawCents)

        // Duda protegida mantém R$ 10,00 bruto
        val dudaSplit = result.items.single().participantSplits.first { it.personId == p4.first }
        assertEquals(0L, dudaSplit.discountAmount.rawCents)
        assertEquals(1_000L, dudaSplit.grossAmount.rawCents)

        // Os 3 elegíveis fecham exatamente os R$ 80,00 restantes (2667 + 2667 + 2666 = 8000)
        val eligibleGrosses = listOf(p1.first, p2.first, p3.first).map { pid ->
            result.items.single().participantSplits.first { it.personId == pid }.grossAmount.rawCents
        }
        assertEquals(8_000L, eligibleGrosses.sum())
        assertEquals(listOf(2_667L, 2_667L, 2_666L).sorted(), eligibleGrosses.sorted())
    }

    @Test
    fun `07 editing and re-editing persisted sale reconstructs protected people and never double discounts`() {
        val ana = "p_ana" to "Ana"
        val bia = "p_bia" to "Bia"
        val carla = "p_carla" to "Carla"

        val prodA = makeProduct("prod_a", "Dragão", 4_000L, listOf(ana to 3_500L, bia to 500L))
        val prodB = makeProduct("prod_b", "Suporte", 3_000L, listOf(ana to 2_500L, bia to 500L))
        val prodC = makeProduct("prod_c", "Base", 2_000L, listOf(carla to 2_000L))

        val initial = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1), SaleItemInput(prodC, 1)),
                finalAmountOverride = MoneyCents(7_500L), // desconto 15,00 em prodA + prodB, Bia protegida
                discountMode = SaleDiscountInput.SelectedProducts(setOf(prodA.id, prodB.id)),
                paymentMethod = SalePaymentMethod.PIX,
                protectedDiscountPersonIds = setOf(bia.first),
            ),
        )
        assertInvariants(initial)
        assertEquals(1_000L, initial.personTotals.first { it.personId == bia.first }.grossAmount.rawCents)

        val persisted = PersistedSale(
            id = "sale_edit_1",
            soldAtEpochMs = 1_710_000_000_000L,
            updatedAtEpochMs = 1_710_000_000_000L,
            calculation = initial,
        )

        // Reconstrói entrada editável a partir da venda salva
        val inferredProtected = SaleDiscountPolicy.inferProtectedPersonIdsFromSnapshot(initial)
        assertEquals(setOf(bia.first), inferredProtected)

        val editableInput = persisted.toEditableCalculationInput()
        assertEquals(setOf(bia.first), editableInput.protectedDiscountPersonIds)
        assertTrue(editableInput.discountMode is SaleDiscountInput.SelectedProducts)

        // Recalcula sem alterar nada -> resultado bit-a-bit idêntico (sem duplo desconto!)
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
    fun `08 SaleEntityMapper round-trip preserves SelectedProducts discount mode and protected participant splits`() {
        val ana = "p_ana" to "Ana"
        val bia = "p_bia" to "Bia"
        val carla = "p_carla" to "Carla"

        val prodA = makeProduct("prod_a", "Peça A", 4_000L, listOf(ana to 3_500L, bia to 500L))
        val prodB = makeProduct("prod_b", "Peça B", 3_000L, listOf(ana to 2_500L, bia to 500L))
        val prodC = makeProduct("prod_c", "Peça C", 2_000L, listOf(carla to 2_000L))

        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1), SaleItemInput(prodC, 1)),
                finalAmountOverride = MoneyCents(8_000L),
                discountMode = SaleDiscountInput.SelectedProducts(setOf(prodA.id, prodB.id)),
                paymentMethod = SalePaymentMethod.PIX,
                protectedDiscountPersonIds = setOf(bia.first),
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
        assertEquals(setOf(bia.first), SaleDiscountPolicy.inferProtectedPersonIdsFromSnapshot(restored.calculation))
    }
}
