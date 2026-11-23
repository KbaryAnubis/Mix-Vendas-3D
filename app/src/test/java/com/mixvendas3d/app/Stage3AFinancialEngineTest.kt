package com.mixvendas3d.app

import com.mixvendas3d.app.domain.calculation.SaleCalculationException
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.Machine
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductParticipantSplit
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleCalculationResult
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage3AFinancialEngineTest {

    private fun product(
        id: String,
        name: String,
        priceCents: Long,
        feeMode: FeeResponsibilityMode,
        feeOwnerId: String? = null,
        vararg splits: Triple<String, String, Long>,
    ): Product {
        return Product(
            id = id,
            name = name,
            price = MoneyCents(priceCents),
            active = true,
            feeResponsibilityMode = feeMode,
            feeOwnerPersonId = feeOwnerId,
            participants = splits.mapIndexed { idx, (pid, pname, amount) ->
                ProductParticipantSplit(
                    personId = pid,
                    personName = pname,
                    personColorIndex = idx,
                    amount = MoneyCents(amount),
                )
            },
            createdAtEpochMs = 1_000L,
            updatedAtEpochMs = 1_000L,
        )
    }

    private fun machine(
        id: String = "mac-1",
        name: String = "Ton Pro",
        debitBps: Int = 190,
        creditEnabled: Boolean = true,
        creditDefaultBps: Int = 280,
        brandRatesEnabled: Boolean = false,
        brandRates: Map<CardBrand, Int> = emptyMap(),
        installmentsEnabled: Boolean = false,
        installmentAddons: Map<Int, Int> = emptyMap(),
    ): Machine {
        return Machine(
            id = id,
            name = name,
            debitRateBps = debitBps,
            creditEnabled = creditEnabled,
            creditDefaultRateBps = creditDefaultBps,
            brandRatesEnabled = brandRatesEnabled,
            brandRatesBps = brandRates,
            installmentsEnabled = installmentsEnabled,
            installmentAddonsBps = installmentAddons,
            createdAtEpochMs = 1_000L,
            updatedAtEpochMs = 1_000L,
        )
    }

    private fun assertMandatoryInvariants(result: SaleCalculationResult) {
        assertEquals(
            result.originalSubtotal.rawCents - result.discountTotal.rawCents,
            result.finalAmount.rawCents,
        )
        assertEquals(
            result.items.sumOf { it.finalAmount.rawCents },
            result.finalAmount.rawCents,
        )
        assertEquals(
            result.items.sumOf { it.feeAmount.rawCents },
            result.feeTotal.rawCents,
        )
        assertEquals(
            result.finalAmount.rawCents - result.feeTotal.rawCents,
            result.netTotal.rawCents,
        )
        assertEquals(
            result.personTotals.sumOf { it.grossAmount.rawCents },
            result.finalAmount.rawCents,
        )
        assertEquals(
            result.personTotals.sumOf { it.feeAmount.rawCents },
            result.feeTotal.rawCents,
        )
        assertEquals(
            result.personTotals.sumOf { it.netAmount.rawCents },
            result.netTotal.rawCents,
        )
        result.items.forEach { item ->
            assertEquals(item.finalAmount.rawCents, item.participantSplits.sumOf { it.grossAmount.rawCents })
            assertEquals(item.feeAmount.rawCents, item.participantSplits.sumOf { it.feeAmount.rawCents })
            assertEquals(item.netAmount.rawCents, item.participantSplits.sumOf { it.netAmount.rawCents })
            item.participantSplits.forEach { split ->
                assertTrue("Líquido negativo não permitido", split.netAmount.rawCents >= 0L)
            }
        }
    }

    @Test
    fun `mandatory section 11 example produces exact values to the cent`() {
        val prodA = product(
            id = "prod-a",
            name = "Produto A",
            priceCents = 2_200L,
            feeMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerId = "p1",
            Triple("p1", "Pessoa 1", 1_700L),
            Triple("p2", "Pessoa 2", 500L),
        )
        val prodB = product(
            id = "prod-b",
            name = "Produto B",
            priceCents = 1_800L,
            feeMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerId = "p2",
            Triple("p1", "Pessoa 1", 300L),
            Triple("p2", "Pessoa 2", 1_500L),
        )
        val ton = machine(creditDefaultBps = 280)

        val result = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = ton,
            ),
        )

        assertMandatoryInvariants(result)
        assertEquals(4_000L, result.finalAmount.rawCents)
        assertEquals(62L, result.items.first { it.productId == "prod-a" }.feeAmount.rawCents)
        assertEquals(50L, result.items.first { it.productId == "prod-b" }.feeAmount.rawCents)
        assertEquals(112L, result.feeTotal.rawCents)
        assertEquals(3_888L, result.netTotal.rawCents)

        val p1Total = result.personTotals.first { it.personId == "p1" }
        val p2Total = result.personTotals.first { it.personId == "p2" }
        assertEquals(2_000L, p1Total.grossAmount.rawCents)
        assertEquals(62L, p1Total.feeAmount.rawCents)
        assertEquals(1_938L, p1Total.netAmount.rawCents)
        assertEquals(2_000L, p2Total.grossAmount.rawCents)
        assertEquals(50L, p2Total.feeAmount.rawCents)
        assertEquals(1_950L, p2Total.netAmount.rawCents)
    }

    @Test
    fun `single product, cash, and pix have zero fees and preserve exact person totals`() {
        val single = product(
            id = "prod-1",
            name = "Miniatura",
            priceCents = 3_500L,
            feeMode = FeeResponsibilityMode.PROPORTIONAL,
            null,
            Triple("p1", "Alice", 2_000L),
            Triple("p2", "Bruno", 1_500L),
        )

        listOf(SalePaymentMethod.CASH, SalePaymentMethod.PIX).forEach { method ->
            val res = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(single, 1)),
                    paymentMethod = method,
                ),
            )
            assertMandatoryInvariants(res)
            assertNull(res.machineSnapshot)
            assertEquals(0, res.effectiveFeeRateBps)
            assertEquals(0L, res.feeTotal.rawCents)
            assertEquals(3_500L, res.finalAmount.rawCents)
            assertEquals(3_500L, res.netTotal.rawCents)
            assertEquals(2_000L, res.personTotals.first { it.personId == "p1" }.netAmount.rawCents)
            assertEquals(1_500L, res.personTotals.first { it.personId == "p2" }.netAmount.rawCents)
        }
    }

    @Test
    fun `multiple products with different quantities consolidate duplicate product lines and support 3 or more participants`() {
        val prod1 = product(
            id = "p-a",
            name = "Dragão",
            priceCents = 6_000L,
            feeMode = FeeResponsibilityMode.PROPORTIONAL,
            null,
            Triple("u1", "Ana", 3_000L),
            Triple("u2", "Bia", 2_000L),
            Triple("u3", "Caio", 1_000L),
        )
        val prod2 = product(
            id = "p-b",
            name = "Base",
            priceCents = 1_500L,
            feeMode = FeeResponsibilityMode.EQUAL,
            null,
            Triple("u2", "Bia", 500L),
            Triple("u4", "Duda", 1_000L),
        )
        val debitMachine = machine(debitBps = 190)

        // Passa prod1 duplicado (1 + 2 = 3 unidades) e prod2 (4 unidades)
        val res = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(
                    SaleItemInput(prod1, 1),
                    SaleItemInput(prod2, 4),
                    SaleItemInput(prod1, 2),
                ),
                paymentMethod = SalePaymentMethod.DEBIT,
                machine = debitMachine,
            ),
        )

        assertMandatoryInvariants(res)
        assertEquals(2, res.items.size)
        val item1 = res.items.first { it.productId == "p-a" }
        val item2 = res.items.first { it.productId == "p-b" }
        assertEquals(3, item1.quantity)
        assertEquals(18_000L, item1.finalAmount.rawCents)
        assertEquals(4, item2.quantity)
        assertEquals(6_000L, item2.finalAmount.rawCents)
        assertEquals(24_000L, res.finalAmount.rawCents)
        assertEquals(4, res.personTotals.size)
        assertEquals(190, res.effectiveFeeRateBps)

        // Verifica limites de quantidade (0 e >99 após consolidação)
        assertTrue(
            runCatching {
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod1, 0)),
                        paymentMethod = SalePaymentMethod.CASH,
                    ),
                )
            }.exceptionOrNull() is SaleCalculationException,
        )
        assertTrue(
            runCatching {
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod1, 50), SaleItemInput(prod1, 50)),
                        paymentMethod = SalePaymentMethod.CASH,
                    ),
                )
            }.exceptionOrNull() is SaleCalculationException,
        )
    }

    @Test
    fun `opposite product splits allow second person to receive more than first person`() {
        val prodA = product(
            id = "a",
            name = "A",
            priceCents = 2_200L,
            feeMode = FeeResponsibilityMode.EQUAL,
            null,
            Triple("p1", "Pessoa 1", 200L),
            Triple("p2", "Pessoa 2", 2_000L),
        )
        val prodB = product(
            id = "b",
            name = "B",
            priceCents = 2_200L,
            feeMode = FeeResponsibilityMode.EQUAL,
            null,
            Triple("p1", "Pessoa 1", 500L),
            Triple("p2", "Pessoa 2", 1_700L),
        )

        val res = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                paymentMethod = SalePaymentMethod.PIX,
            ),
        )
        assertMandatoryInvariants(res)
        val p1 = res.personTotals.first { it.personId == "p1" }
        val p2 = res.personTotals.first { it.personId == "p2" }
        assertEquals(700L, p1.netAmount.rawCents)
        assertEquals(3_700L, p2.netAmount.rawCents)
        assertTrue(p2.netAmount > p1.netAmount)
    }

    @Test
    fun `brand rates replace default credit rate, installments add on top, and manual rate overrides automatic completely`() {
        val prod = product(
            id = "p1",
            name = "Peça 3D",
            priceCents = 10_000L,
            feeMode = FeeResponsibilityMode.PROPORTIONAL,
            null,
            Triple("a", "Ana", 6_000L),
            Triple("b", "Bruno", 4_000L),
        )
        val ton = machine(
            creditEnabled = true,
            creditDefaultBps = 280,
            brandRatesEnabled = true,
            brandRates = mapOf(CardBrand.VISA to 250),
            installmentsEnabled = true,
            installmentAddons = mapOf(1 to 0, 2 to 110, 3 to 160, 6 to 320),
        )

        // Visa 3x = 2,50% + 1,60% = 4,10% (410 bps -> R$ 4,10)
        val visa3x = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = ton,
                cardBrand = CardBrand.VISA,
                installments = 3,
            ),
        )
        assertMandatoryInvariants(visa3x)
        assertEquals(410, visa3x.effectiveFeeRateBps)
        assertEquals(410L, visa3x.feeTotal.rawCents)
        assertEquals(CardBrand.VISA, visa3x.machineSnapshot?.appliedBrand)
        assertEquals(3, visa3x.machineSnapshot?.appliedInstallments)

        // Elo sem taxa específica cai na taxa padrão (2,80%) + 3x (1,60%) = 4,40%
        val elo3x = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = ton,
                cardBrand = CardBrand.ELO,
                installments = 3,
            ),
        )
        assertMandatoryInvariants(elo3x)
        assertEquals(440, elo3x.effectiveFeeRateBps)
        assertEquals(440L, elo3x.feeTotal.rawCents)

        // Tarifa manual 3,50% (350 bps) SUBSTITUI integralmente a automática (não soma 410 + 350)
        val manual = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = ton,
                cardBrand = CardBrand.VISA,
                installments = 3,
                manualRateOverrideBps = 350,
            ),
        )
        assertMandatoryInvariants(manual)
        assertEquals(350, manual.effectiveFeeRateBps)
        assertEquals(410, manual.machineSnapshot?.automaticRateBps)
        assertEquals(350, manual.machineSnapshot?.manualRateOverrideBps)
        assertEquals(350L, manual.feeTotal.rawCents)
        assertEquals(9_650L, manual.netTotal.rawCents)
    }

    @Test
    fun `equal, proportional, and individual fee responsibility distribute odd cents deterministically`() {
        // Valor R$ 10,00 (1000 cents), taxa 3,33% -> 33 cents de tarifa para 3 pessoas
        val equalProd = product(
            id = "eq",
            name = "Igualitário",
            priceCents = 1_000L,
            feeMode = FeeResponsibilityMode.EQUAL,
            null,
            Triple("p-b", "Beto", 400L),
            Triple("p-c", "Caio", 300L),
            Triple("p-a", "Ana", 300L),
        )
        // Taxa 1,00% -> 10 cents / 3 pessoas = 4, 3, 3 (resto 1 para "p-a")
        val eqRes = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(equalProd, 1)),
                paymentMethod = SalePaymentMethod.DEBIT,
                machine = machine(debitBps = 100),
            ),
        )
        assertMandatoryInvariants(eqRes)
        assertEquals(10L, eqRes.feeTotal.rawCents)
        assertEquals(4L, eqRes.personTotals.first { it.personId == "p-a" }.feeAmount.rawCents)
        assertEquals(3L, eqRes.personTotals.first { it.personId == "p-b" }.feeAmount.rawCents)
        assertEquals(3L, eqRes.personTotals.first { it.personId == "p-c" }.feeAmount.rawCents)

        // Proporcional: 1000 cents (700 vs 200 vs 100) com taxa 3,50% = 35 cents
        // 35*700/1000=24.5, 35*200/1000=7.0, 35*100/1000=3.5 -> restos 500 para p1 e p3 -> desempate p1 (+1) -> 25, 7, 3
        val propProd = product(
            id = "pr",
            name = "Proporcional",
            priceCents = 1_000L,
            feeMode = FeeResponsibilityMode.PROPORTIONAL,
            null,
            Triple("p1", "Ana", 700L),
            Triple("p2", "Beto", 200L),
            Triple("p3", "Caio", 100L),
        )
        val propRes = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(propProd, 1)),
                paymentMethod = SalePaymentMethod.DEBIT,
                machine = machine(debitBps = 350),
            ),
        )
        assertMandatoryInvariants(propRes)
        assertEquals(35L, propRes.feeTotal.rawCents)
        assertEquals(25L, propRes.personTotals.first { it.personId == "p1" }.feeAmount.rawCents)
        assertEquals(7L, propRes.personTotals.first { it.personId == "p2" }.feeAmount.rawCents)
        assertEquals(3L, propRes.personTotals.first { it.personId == "p3" }.feeAmount.rawCents)
    }

    @Test
    fun `specific product discount and proportional discount adjust splits without losing a single cent`() {
        val prodA = product(
            id = "prod-a",
            name = "Produto A",
            priceCents = 3_000L,
            feeMode = FeeResponsibilityMode.PROPORTIONAL,
            null,
            Triple("p1", "Ana", 2_000L),
            Triple("p2", "Bruno", 1_000L),
        )
        val prodB = product(
            id = "prod-b",
            name = "Produto B",
            priceCents = 2_000L,
            feeMode = FeeResponsibilityMode.PROPORTIONAL,
            null,
            Triple("p1", "Ana", 1_000L),
            Triple("p2", "Bruno", 1_000L),
        )
        val card = machine(creditDefaultBps = 300)

        // A) Desconto específico de R$ 5,00 (final R$ 45,00) somente no Produto A
        // Produto A cai para R$ 25,00 (desconto de 500 dividido igualmente: -250 cada -> 1750 e 750)
        // Produto B permanece R$ 20,00 (1000 e 1000)
        val specificRes = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                finalAmountOverride = MoneyCents(4_500L),
                discountMode = SaleDiscountInput.SpecificProduct("prod-a"),
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = card,
            ),
        )
        assertMandatoryInvariants(specificRes)
        assertTrue(specificRes.discountConfig is SaleDiscountConfigSnapshot.SpecificProduct)
        val itemASpec = specificRes.items.first { it.productId == "prod-a" }
        val itemBSpec = specificRes.items.first { it.productId == "prod-b" }
        assertEquals(500L, itemASpec.discountAmount.rawCents)
        assertEquals(2_500L, itemASpec.finalAmount.rawCents)
        assertEquals(0L, itemBSpec.discountAmount.rawCents)
        assertEquals(2_000L, itemBSpec.finalAmount.rawCents)
        assertEquals(1_750L, itemASpec.participantSplits.first { it.personId == "p1" }.grossAmount.rawCents)
        assertEquals(750L, itemASpec.participantSplits.first { it.personId == "p2" }.grossAmount.rawCents)

        // B) Desconto proporcional com sobra de centavos: R$ 50,00 -> R$ 49,99 (1 cent de desconto)
        // Pesos 3000 e 2000: restos 3000 e 2000 -> prod-a absorve o 1 cent -> 2999 e 2000
        val propRes = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                finalAmountOverride = MoneyCents(4_999L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = card,
            ),
        )
        assertMandatoryInvariants(propRes)
        assertEquals(SaleDiscountConfigSnapshot.Proportional, propRes.discountConfig)
        assertEquals(1L, propRes.discountTotal.rawCents)
        assertEquals(1L, propRes.items.first { it.productId == "prod-a" }.discountAmount.rawCents)
        assertEquals(0L, propRes.items.first { it.productId == "prod-b" }.discountAmount.rawCents)
    }

    @Test
    fun `validation rejects excessive discounts, invalid participants, incompatible machines, and negative net`() {
        val validProd = product(
            id = "p1",
            name = "Produto 1",
            priceCents = 2_000L,
            feeMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerId = "owner",
            Triple("owner", "Dono", 100L),
            Triple("other", "Outro", 1_900L),
        )
        val otherProd = product(
            id = "p2",
            name = "Produto 2",
            priceCents = 1_000L,
            feeMode = FeeResponsibilityMode.PROPORTIONAL,
            null,
            Triple("other", "Outro", 1_000L),
        )

        // 1) Desconto maior que o permitido (final <= 0 ou final > subtotal)
        assertTrue(
            runCatching {
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(validProd, 1)),
                        finalAmountOverride = MoneyCents.ZERO,
                        paymentMethod = SalePaymentMethod.CASH,
                    ),
                )
            }.exceptionOrNull() is SaleCalculationException,
        )
        assertTrue(
            runCatching {
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(validProd, 1)),
                        finalAmountOverride = MoneyCents(2_001L),
                        paymentMethod = SalePaymentMethod.CASH,
                    ),
                )
            }.exceptionOrNull() is SaleCalculationException,
        )

        // 2) Desconto em produto específico que zera esse produto (desconto = R$ 10,00 no Produto 2 de R$ 10,00)
        assertTrue(
            runCatching {
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(validProd, 1), SaleItemInput(otherProd, 1)),
                        finalAmountOverride = MoneyCents(2_000L),
                        discountMode = SaleDiscountInput.SpecificProduct("p2"),
                        paymentMethod = SalePaymentMethod.CASH,
                    ),
                )
            }.exceptionOrNull() is SaleCalculationException,
        )

        // 3) Produto com participante inválido (soma não bate com preço)
        val brokenProd = product(
            id = "broken",
            name = "Quebrado",
            priceCents = 1_000L,
            feeMode = FeeResponsibilityMode.PROPORTIONAL,
            null,
            Triple("a", "Ana", 700L),
        )
        assertTrue(
            runCatching {
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(brokenProd, 1)),
                        paymentMethod = SalePaymentMethod.CASH,
                    ),
                )
            }.exceptionOrNull() is SaleCalculationException,
        )

        // 4) Maquininha incompatível (nula no débito, ou crédito desativado no crédito)
        assertTrue(
            runCatching {
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(otherProd, 1)),
                        paymentMethod = SalePaymentMethod.DEBIT,
                        machine = null,
                    ),
                )
            }.exceptionOrNull() is SaleCalculationException,
        )
        assertTrue(
            runCatching {
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(otherProd, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine(creditEnabled = false),
                    ),
                )
            }.exceptionOrNull() is SaleCalculationException,
        )

        // 5) Taxa maior que o valor recebido por uma pessoa (owner recebe R$ 1,00 e tarifa 10% sobre R$ 20,00 = R$ 2,00)
        val negativeNetError = runCatching {
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(validProd, 1)),
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machine(creditDefaultBps = 1_000), // 10,00% -> 200 cents > 100 cents de "owner"
                ),
            )
        }.exceptionOrNull()
        assertNotNull(negativeNetError)
        assertTrue(negativeNetError is SaleCalculationException)
    }

    @Test
    fun `large monetary amounts compute without intermediate overflow and true Long overflow fails gracefully`() {
        val hugeAmount = 4_000_000_000_000_000_000L // R$ 40 quatrilhões em centavos (seguro em Long, mas estouraria Long sem BigInteger em * 280)
        val hugeProd = product(
            id = "huge",
            name = "Projeto Industrial",
            priceCents = hugeAmount,
            feeMode = FeeResponsibilityMode.PROPORTIONAL,
            null,
            Triple("p1", "Sócio 1", 2_500_000_000_000_000_000L),
            Triple("p2", "Sócio 2", 1_500_000_000_000_000_000L),
        )
        val res = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(hugeProd, 2)), // 8e18 < Long.MAX_VALUE
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = machine(creditDefaultBps = 280),
            ),
        )
        assertMandatoryInvariants(res)
        assertEquals(8_000_000_000_000_000_000L, res.finalAmount.rawCents)
        assertEquals(224_000_000_000_000_000L, res.feeTotal.rawCents)
        assertEquals(7_776_000_000_000_000_000L, res.netTotal.rawCents)

        // Estouro real de Long na multiplicação por quantidade retorna erro controlado
        assertTrue(
            runCatching {
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(hugeProd, 3)),
                        paymentMethod = SalePaymentMethod.CASH,
                    ),
                )
            }.exceptionOrNull() is SaleCalculationException,
        )
    }

    @Test
    fun `engine is strictly deterministic regardless of input ordering and yields immutable snapshots`() {
        val prod1 = product(
            id = "id-1",
            name = "Item Alfa",
            priceCents = 1_001L,
            feeMode = FeeResponsibilityMode.EQUAL,
            null,
            Triple("p-b", "Bruno", 501L),
            Triple("p-a", "Ana", 500L),
        )
        val prod2 = product(
            id = "id-2",
            name = "Item Beta",
            priceCents = 1_001L,
            feeMode = FeeResponsibilityMode.PROPORTIONAL,
            null,
            Triple("p-c", "Carla", 334L),
            Triple("p-b", "Bruno", 334L),
            Triple("p-a", "Ana", 333L),
        )
        val prod1ShuffledParticipants = prod1.copy(participants = prod1.participants.reversed())
        val prod2ShuffledParticipants = prod2.copy(participants = prod2.participants.reversed())
        val card = machine(
            creditDefaultBps = 299,
            brandRatesEnabled = true,
            brandRates = mapOf(CardBrand.MASTERCARD to 279),
            installmentsEnabled = true,
            installmentAddons = mapOf(1 to 0, 2 to 111),
        )

        val result1 = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod1, 1), SaleItemInput(prod2, 1)),
                finalAmountOverride = MoneyCents(1_955L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = card,
                cardBrand = CardBrand.MASTERCARD,
                installments = 2,
                note = "  Observação preservada  ",
            ),
        )
        val result2 = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod2ShuffledParticipants, 1), SaleItemInput(prod1ShuffledParticipants, 1)),
                finalAmountOverride = MoneyCents(1_955L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = card,
                cardBrand = CardBrand.MASTERCARD,
                installments = 2,
                note = "Observação preservada",
            ),
        )

        assertMandatoryInvariants(result1)
        assertEquals(result1, result2)
        assertEquals("Item Alfa", result1.items[0].productName)
        assertEquals("Ton Pro", result1.machineSnapshot?.machineName)
        assertEquals("Observação preservada", result1.note)
    }
}
