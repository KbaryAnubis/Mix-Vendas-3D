package com.mixvendas3d.app.util

import com.mixvendas3d.app.domain.models.MoneyCents
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object CurrencyFormatter {
    private val ptBrLocale: Locale = Locale.forLanguageTag("pt-BR")
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", ptBrLocale)

    fun formatBrl(amount: MoneyCents): String {
        val raw = amount.rawCents
        val sign = if (raw < 0L) "-" else ""
        val absValue = abs(raw)
        val reais = absValue / 100L
        val centavos = absValue % 100L
        val reaisFormatted = String.format(ptBrLocale, "%,d", reais)
        return "${sign}R$ $reaisFormatted,${centavos.toString().padStart(2, '0')}"
    }

    fun formatCentsForInput(cents: Long): String {
        if (cents <= 0L) return ""
        val reais = cents / 100L
        val rem = cents % 100L
        return "$reais,${rem.toString().padStart(2, '0')}"
    }

    fun formatCentsOrZeroForInput(cents: Long): String {
        val sign = if (cents < 0L) "-" else ""
        val absCents = abs(cents)
        val reais = absCents / 100L
        val rem = absCents % 100L
        return "$sign$reais,${rem.toString().padStart(2, '0')}"
    }

    /**
     * Converte entrada monetária digitada pelo usuário (ex.: "22", "22,50", "1.250,00", "-5,00")
     * diretamente para centavos inteiros (Long), sem usar Float/Double.
     */
    fun parseBrlToCents(input: String): Long? {
        val trimmed = input.trim()
            .replace("R$", "")
            .replace(" ", "")
        if (trimmed.isEmpty()) return null

        val isNegative = trimmed.startsWith("-")
        val unsigned = if (isNegative || trimmed.startsWith("+")) {
            trimmed.substring(1)
        } else {
            trimmed
        }
        if (unsigned.isEmpty()) return null

        val normalized = if (unsigned.contains(',')) {
            unsigned.replace(".", "").replace(',', '.')
        } else {
            unsigned
        }
        val parts = normalized.split('.')
        if (parts.size > 2) return null

        val wholePart = parts[0]
        if (wholePart.isEmpty() || !wholePart.all { it.isDigit() }) return null
        val whole = wholePart.toLongOrNull() ?: return null

        val frac = if (parts.size == 2) {
            val rawFrac = parts[1]
            if (rawFrac.isEmpty() || rawFrac.length > 2 || !rawFrac.all { it.isDigit() }) return null
            rawFrac.padEnd(2, '0').toLongOrNull() ?: return null
        } else {
            0L
        }

        return try {
            if (isNegative) {
                val signedWhole = Math.negateExact(whole)
                val wholeCents = Math.multiplyExact(signedWhole, 100L)
                Math.subtractExact(wholeCents, frac)
            } else {
                val wholeCents = Math.multiplyExact(whole, 100L)
                Math.addExact(wholeCents, frac)
            }
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun formatEpochMillis(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(timestampFormatter)
    }
}
