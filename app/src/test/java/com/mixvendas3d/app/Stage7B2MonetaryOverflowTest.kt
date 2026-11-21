package com.mixvendas3d.app

import com.mixvendas3d.app.domain.calculation.CatalogValidationException
import com.mixvendas3d.app.domain.calculation.CatalogValidator
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.util.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class Stage7B2MonetaryOverflowTest {

    // A. Parser monetário (CurrencyFormatter.parseBrlToCents)

    @Test
    fun parseBrlToCents_parsesStandardPositiveAndNegativeValues() {
        assertEquals(2200L, CurrencyFormatter.parseBrlToCents("22,00"))
        assertEquals(123456L, CurrencyFormatter.parseBrlToCents("1.234,56"))
        assertEquals(-550L, CurrencyFormatter.parseBrlToCents("-5,50"))
        assertEquals(123456L, CurrencyFormatter.parseBrlToCents("R$ 1.234,56"))
        assertEquals(2200L, CurrencyFormatter.parseBrlToCents("22"))
        assertEquals(2250L, CurrencyFormatter.parseBrlToCents("22,5"))
    }

    @Test
    fun parseBrlToCents_acceptsExactLongBoundariesWithoutPrecisionLoss() {
        assertEquals(Long.MAX_VALUE, CurrencyFormatter.parseBrlToCents("92233720368547758,07"))
        assertEquals(Long.MIN_VALUE, CurrencyFormatter.parseBrlToCents("-92233720368547758,08"))
        assertEquals(Long.MIN_VALUE, CurrencyFormatter.parseBrlToCents("-R$ 92233720368547758,08"))
    }

    @Test
    fun parseBrlToCents_returnsNullOnOverflowAboveMaxOrBelowMin() {
        assertNull(CurrencyFormatter.parseBrlToCents("92233720368547758,08"))
        assertNull(CurrencyFormatter.parseBrlToCents("-92233720368547758,09"))
        assertNull(CurrencyFormatter.parseBrlToCents("92233720368547759,00"))
        assertNull(CurrencyFormatter.parseBrlToCents("-92233720368547759,00"))
        assertNull(CurrencyFormatter.parseBrlToCents("99999999999999999999,00"))
        assertNull(CurrencyFormatter.parseBrlToCents("-99999999999999999999,00"))
        assertNull(CurrencyFormatter.parseBrlToCents("9223372036854775807"))
    }

    @Test
    fun parseBrlToCents_returnsNullForInvalidInputs() {
        assertNull(CurrencyFormatter.parseBrlToCents(""))
        assertNull(CurrencyFormatter.parseBrlToCents("   "))
        assertNull(CurrencyFormatter.parseBrlToCents("abc"))
        assertNull(CurrencyFormatter.parseBrlToCents("12,345"))
        assertNull(CurrencyFormatter.parseBrlToCents("12.3.4"))
        assertNull(CurrencyFormatter.parseBrlToCents("12,3,4"))
        assertNull(CurrencyFormatter.parseBrlToCents("-"))
        assertNull(CurrencyFormatter.parseBrlToCents("+"))
        assertNull(CurrencyFormatter.parseBrlToCents("12,"))
        assertNull(CurrencyFormatter.parseBrlToCents(",50"))
    }

    // B. Validação de produto (CatalogValidator.validateProductInput)

    @Test
    fun validateProductInput_acceptsValidSplitsAndFeeModes() {
        val people = setOf("p1", "p2")

        val equalValidated = CatalogValidator.validateProductInput(
            rawName = "Suporte 3D",
            price = MoneyCents(5000L),
            active = true,
            participants = listOf(
                ProductParticipantDraft("p1", MoneyCents(3000L)),
                ProductParticipantDraft("p2", MoneyCents(2000L)),
            ),
            existingPersonIds = people,
            feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
            feeOwnerPersonId = null,
        )
        assertEquals(MoneyCents(5000L), equalValidated.price)
        assertNull(equalValidated.feeOwnerPersonId)

        val individualValidated = CatalogValidator.validateProductInput(
            rawName = "Miniatura",
            price = MoneyCents(4500L),
            active = true,
            participants = listOf(
                ProductParticipantDraft("p1", MoneyCents(2500L)),
                ProductParticipantDraft("p2", MoneyCents(2000L)),
            ),
            existingPersonIds = people,
            feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerPersonId = "p1",
        )
        assertEquals("p1", individualValidated.feeOwnerPersonId)

        val proportionalValidated = CatalogValidator.validateProductInput(
            rawName = "Vaso",
            price = MoneyCents(10000L),
            active = true,
            participants = listOf(
                ProductParticipantDraft("p1", MoneyCents(7000L)),
                ProductParticipantDraft("p2", MoneyCents(3000L)),
            ),
            existingPersonIds = people,
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        assertEquals(FeeResponsibilityMode.PROPORTIONAL, proportionalValidated.feeResponsibilityMode)
    }

    @Test
    fun validateProductInput_rejectsMismatchedSumAndNegativeParticipantAmount() {
        val people = setOf("p1", "p2")

        assertThrows(CatalogValidationException::class.java) {
            CatalogValidator.validateProductInput(
                rawName = "Produto A",
                price = MoneyCents(5000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft("p1", MoneyCents(3000L)),
                    ProductParticipantDraft("p2", MoneyCents(1999L)),
                ),
                existingPersonIds = people,
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
        }

        assertThrows(CatalogValidationException::class.java) {
            CatalogValidator.validateProductInput(
                rawName = "Produto B",
                price = MoneyCents(5000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft("p1", MoneyCents(5100L)),
                    ProductParticipantDraft("p2", MoneyCents(-100L)),
                ),
                existingPersonIds = people,
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
        }
    }

    @Test
    fun validateProductInput_throwsCatalogValidationExceptionOnMonetaryOverflow() {
        val twoPeople = setOf("p1", "p2")
        assertThrows(CatalogValidationException::class.java) {
            CatalogValidator.validateProductInput(
                rawName = "Produto Overflow Simples",
                price = MoneyCents(Long.MAX_VALUE),
                active = true,
                participants = listOf(
                    ProductParticipantDraft("p1", MoneyCents(Long.MAX_VALUE)),
                    ProductParticipantDraft("p2", MoneyCents(1L)),
                ),
                existingPersonIds = twoPeople,
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
        }

        // Caso em que a soma sem verificação sofreria wraparound e empataria com price positivo (498L)
        val threePeople = setOf("p1", "p2", "p3")
        assertThrows(CatalogValidationException::class.java) {
            CatalogValidator.validateProductInput(
                rawName = "Produto Overflow Wraparound",
                price = MoneyCents(498L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft("p1", MoneyCents(Long.MAX_VALUE)),
                    ProductParticipantDraft("p2", MoneyCents(Long.MAX_VALUE)),
                    ProductParticipantDraft("p3", MoneyCents(500L)),
                ),
                existingPersonIds = threePeople,
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
        }
    }
}
