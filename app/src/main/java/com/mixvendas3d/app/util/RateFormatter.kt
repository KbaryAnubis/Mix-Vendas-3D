package com.mixvendas3d.app.util

import kotlin.math.abs

object RateFormatter {
    const val MIN_RATE_BPS: Int = 0
    const val MAX_RATE_BPS: Int = 10_000

    /**
     * Converte entrada percentual (ex.: "1,90", "1.90%", "2,8", "100") para pontos-base inteiros (Int),
     * onde 1,00% = 100 pontos-base e 100,00% = 10.000 pontos-base, sem usar Float ou Double.
     * Retorna null para texto vazio ou formato inválido.
     */
    fun parsePercentageToBps(input: String): Int? {
        val cleaned = input.trim()
            .replace("%", "")
            .replace(" ", "")
        if (cleaned.isEmpty()) return null

        val isNegative = cleaned.startsWith("-")
        val unsigned = if (isNegative || cleaned.startsWith("+")) {
            cleaned.substring(1)
        } else {
            cleaned
        }
        if (unsigned.isEmpty()) return null

        val normalized = unsigned.replace(',', '.')
        val parts = normalized.split('.')
        if (parts.size > 2) return null

        val wholePart = parts[0]
        if (wholePart.isEmpty() || !wholePart.all { it.isDigit() }) return null
        val whole = wholePart.toLongOrNull() ?: return null
        if (whole > 1_000_000L) return null

        val frac = if (parts.size == 2) {
            val rawFrac = parts[1]
            if (rawFrac.isEmpty() || rawFrac.length > 2 || !rawFrac.all { it.isDigit() }) return null
            rawFrac.padEnd(2, '0').toLongOrNull() ?: return null
        } else {
            0L
        }

        val totalAbs = whole * 100L + frac
        if (totalAbs > Int.MAX_VALUE.toLong()) return null
        val signed = if (isNegative) -totalAbs else totalAbs
        return signed.toInt()
    }

    fun formatBpsAsPercent(bps: Int): String {
        return "${formatBpsForInput(bps)}%"
    }

    fun formatBpsForInput(bps: Int): String {
        val sign = if (bps < 0) "-" else ""
        val absBps = abs(bps)
        val whole = absBps / 100
        val frac = absBps % 100
        return "$sign$whole,${frac.toString().padStart(2, '0')}"
    }
}
