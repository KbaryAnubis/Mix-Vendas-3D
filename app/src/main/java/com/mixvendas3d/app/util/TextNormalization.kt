package com.mixvendas3d.app.util

import java.text.Normalizer
import java.util.Locale

/**
 * Utilitários centrais de normalização textual em Português (pt-BR).
 *
 * - [toRegistrationUppercase]: converte nomes de cadastros para MAIÚSCULAS preservando
 *   acentos, cedilha (ç -> Ç), números, espaços e símbolos.
 * - [normalizeForSearch]: remove acentos/diacríticos e converte para minúsculas para
 *   pesquisa tolerante a caixa e acentuação.
 */
object TextNormalization {
    private val PT_BR_LOCALE: Locale = Locale.forLanguageTag("pt-BR")
    private val COMBINING_MARKS_REGEX = Regex("\\p{Mn}+")
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun toRegistrationUppercase(value: String): String {
        if (value.isEmpty()) return value
        return value.uppercase(PT_BR_LOCALE)
    }

    fun normalizeForSearch(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
        return COMBINING_MARKS_REGEX.replace(decomposed, "").lowercase(PT_BR_LOCALE)
    }

    /**
     * Normalização dedicada à comparação de duplicidade de produtos:
     * remove espaços nas extremidades, colapsa múltiplos espaços internos em um único espaço,
     * remove acentos/diacríticos e converte para minúsculas (pt-BR).
     */
    fun normalizeForDuplicateComparison(value: String): String {
        val collapsed = value.trim().replace(WHITESPACE_REGEX, " ")
        if (collapsed.isEmpty()) return ""
        val decomposed = Normalizer.normalize(collapsed, Normalizer.Form.NFD)
        return COMBINING_MARKS_REGEX.replace(decomposed, "").lowercase(PT_BR_LOCALE)
    }
}
