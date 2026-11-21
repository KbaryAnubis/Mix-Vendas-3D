package com.mixvendas3d.app

import com.mixvendas3d.app.domain.calculation.MonetaryRounding
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.util.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class Stage1UnitTest {

    @Test
    fun `money cents arithmetic and rounding remain exact in integers`() {
        val unitPrice = MoneyCents(4990L)
        val total = unitPrice * 3
        val fee = MonetaryRounding.applyBasisPointsHalfUp(total, basisPoints = 315) // 3,15%
        assertEquals(MoneyCents(14_970L), total)
        assertEquals(MoneyCents(472L), fee) // 14970 * 315 / 10000 = 471.555 -> 472
        assertEquals("R$ 149,70", CurrencyFormatter.formatBrl(total))
        assertEquals("R$ 4,72", CurrencyFormatter.formatBrl(fee))
    }
}
