package com.mixvendas3d.app.domain.models

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.Locale

object CustomBrandKeyCodec {
    const val CUSTOM_BRAND_PREFIX = "CUSTOM_"
    const val MAX_CUSTOM_BRAND_NAME_LENGTH = 60
    const val MAX_CUSTOM_BRAND_UTF8_BYTES = 256

    private val PT_BR_LOCALE: Locale = Locale.forLanguageTag("pt-BR")
    private val COMBINING_MARKS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val MULTIPLE_SPACES_REGEX = Regex("\\s+")
    private const val HEX_CHARS = "0123456789abcdef"

    fun containsControlChars(rawName: String): Boolean {
        return rawName.any { it.isISOControl() }
    }

    fun sanitizeDisplayName(rawName: String): String {
        return rawName.trim().replace(MULTIPLE_SPACES_REGEX, " ")
    }

    fun normalizeBrandName(rawName: String): String {
        val cleaned = sanitizeDisplayName(rawName)
        if (cleaned.isEmpty()) return ""
        val decomposed = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
        return COMBINING_MARKS_REGEX.replace(decomposed, "")
            .lowercase(PT_BR_LOCALE)
    }

    fun encodeKey(displayName: String): String {
        require(!containsControlChars(displayName)) {
            "Nome da bandeira personalizada não pode conter caracteres de controle."
        }
        val cleanName = sanitizeDisplayName(displayName)
        require(cleanName.isNotEmpty()) { "Nome da bandeira personalizada não pode ser vazio." }
        require(cleanName.length <= MAX_CUSTOM_BRAND_NAME_LENGTH) {
            "Nome da bandeira personalizada deve ter no máximo $MAX_CUSTOM_BRAND_NAME_LENGTH caracteres."
        }
        val bytes = cleanName.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_CUSTOM_BRAND_UTF8_BYTES) {
            "Nome da bandeira personalizada excede o limite de $MAX_CUSTOM_BRAND_UTF8_BYTES bytes UTF-8."
        }
        val hex = buildString(bytes.size * 2) {
            for (byte in bytes) {
                val value = byte.toInt() and 0xFF
                append(HEX_CHARS[value ushr 4])
                append(HEX_CHARS[value and 0x0F])
            }
        }
        return CUSTOM_BRAND_PREFIX + hex
    }

    fun decodeNameOrNull(key: String?): String? {
        if (key.isNullOrEmpty() || key.contains(':')) return null
        if (!key.startsWith(CUSTOM_BRAND_PREFIX)) return null

        val hex = key.substring(CUSTOM_BRAND_PREFIX.length)
        if (hex.isEmpty() || hex.length % 2 != 0 || hex.length > MAX_CUSTOM_BRAND_UTF8_BYTES * 2) {
            return null
        }

        val bytes = ByteArray(hex.length / 2)
        for (i in bytes.indices) {
            val hi = hexNibble(hex[i * 2])
            val lo = hexNibble(hex[i * 2 + 1])
            if (hi < 0 || lo < 0) return null
            bytes[i] = ((hi shl 4) or lo).toByte()
        }

        val decoded = try {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            return null
        }

        if (containsControlChars(decoded)) return null
        if (decoded.length > MAX_CUSTOM_BRAND_NAME_LENGTH) return null
        val sanitized = sanitizeDisplayName(decoded)
        if (sanitized.isEmpty() || sanitized != decoded) return null
        if (encodeKey(decoded) != key) return null
        return decoded
    }

    fun isValidCustomBrandKey(key: String?): Boolean {
        return decodeNameOrNull(key) != null
    }

    fun isKnownBrandKey(key: String?): Boolean {
        return CardBrand.fromStorageKey(key) != null || isValidCustomBrandKey(key)
    }

    fun resolveDisplayLabel(key: String?): String? {
        return CardBrand.fromStorageKey(key)?.label ?: decodeNameOrNull(key)
    }

    private fun hexNibble(ch: Char): Int = when (ch) {
        in '0'..'9' -> ch - '0'
        in 'a'..'f' -> ch - 'a' + 10
        else -> -1
    }
}
