package com.mixvendas3d.app.util

import java.util.Locale

/**
 * Máscara monetária brasileira automática baseada exclusivamente em centavos inteiros (Long).
 * Converte sequências numéricas digitadas da direita para a esquerda em formato BRL sem símbolo
 * (ex.: "1" -> "0,01", "22" -> "0,22", "2200" -> "22,00", "125050" -> "1.250,50").
 * Nunca utiliza Float ou Double e aplica proteção contra overflow.
 */
object MonetaryInputMask {
    const val MAX_CENTS: Long = 999_999_999_99L // R$ 999.999.999,99 (11 dígitos)
    private const val MAX_DIGITS: Int = 11
    private val ptBrLocale: Locale = Locale.forLanguageTag("pt-BR")

    fun formatDigitsInput(rawInput: String): String {
        val digitsOnly = rawInput.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return ""

        val trimmed = digitsOnly.trimStart('0')
        if (trimmed.isEmpty()) return ""

        val bounded = if (trimmed.length > MAX_DIGITS) {
            trimmed.take(MAX_DIGITS)
        } else {
            trimmed
        }

        val cents = bounded.toLongOrNull()?.coerceAtMost(MAX_CENTS) ?: MAX_CENTS
        return formatCentsForMaskedInput(cents)
    }

    fun formatCentsForMaskedInput(cents: Long): String {
        if (cents <= 0L) return ""
        val safeCents = cents.coerceAtMost(MAX_CENTS)
        val reais = safeCents / 100L
        val centavos = safeCents % 100L
        val reaisFormatted = String.format(ptBrLocale, "%,d", reais)
        return "$reaisFormatted,${centavos.toString().padStart(2, '0')}"
    }
}
