package com.mixvendas3d.app.domain.calculation

import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.CustomCardBrandRate
import com.mixvendas3d.app.domain.models.InstallmentTiers
import com.mixvendas3d.app.util.RateFormatter
import java.util.Locale

class MachineValidationException(message: String) : IllegalArgumentException(message)

data class CustomCardBrandInput(
    val name: String,
    val creditRateBps: Int? = null,
    val debitRateBps: Int? = null,
    val key: String? = null,
) {
    constructor(name: String, rateBps: Int, key: String? = null) : this(
        name = name,
        creditRateBps = rateBps,
        debitRateBps = null,
        key = key,
    )
}

data class MachineInputDraft(
    val name: String,
    val debitRateBps: Int,
    val creditEnabled: Boolean,
    val creditDefaultRateBps: Int,
    val brandRatesEnabled: Boolean,
    val brandRatesBps: Map<CardBrand, Int>,
    val installmentsEnabled: Boolean,
    val installmentAddonsBps: Map<Int, Int>,
    val maxInstallments: Int? = null,
    val clearMaxInstallments: Boolean = false,
    val customBrandRates: List<CustomCardBrandInput>? = null,
    val brandDebitRatesBps: Map<CardBrand, Int> = emptyMap(),
)

data class ValidatedMachineInput(
    val name: String,
    val normalizedName: String,
    val debitRateBps: Int,
    val creditEnabled: Boolean,
    val creditDefaultRateBps: Int,
    val brandRatesEnabled: Boolean,
    val brandRatesBps: Map<CardBrand, Int>,
    val installmentsEnabled: Boolean,
    val installmentAddonsBps: Map<Int, Int>,
    val maxInstallments: Int? = null,
    val clearMaxInstallments: Boolean = false,
    val customBrandRates: List<CustomCardBrandRate>? = null,
    val brandDebitRatesBps: Map<CardBrand, Int> = emptyMap(),
)

object MachineValidator {
    private val ptBrLocale: Locale = Locale.forLanguageTag("pt-BR")

    fun normalizeMachineName(raw: String): String = raw.trim().lowercase(ptBrLocale)

    fun normalizeCustomBrandName(raw: String): String = CustomBrandKeyCodec.normalizeBrandName(raw)

    fun validateMachineName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            throw MachineValidationException("Informe o nome da maquininha.")
        }
        return trimmed
    }

    fun validateCustomBrandName(raw: String): String {
        if (CustomBrandKeyCodec.containsControlChars(raw)) {
            throw MachineValidationException(
                "O nome da bandeira personalizada não pode conter caracteres de controle.",
            )
        }
        val cleanName = CustomBrandKeyCodec.sanitizeDisplayName(raw)
        if (cleanName.isEmpty()) {
            throw MachineValidationException("Informe o nome da bandeira personalizada.")
        }
        if (cleanName.length > CustomBrandKeyCodec.MAX_CUSTOM_BRAND_NAME_LENGTH) {
            throw MachineValidationException(
                "O nome da bandeira personalizada deve ter no máximo ${CustomBrandKeyCodec.MAX_CUSTOM_BRAND_NAME_LENGTH} caracteres.",
            )
        }
        if (cleanName.toByteArray(Charsets.UTF_8).size > CustomBrandKeyCodec.MAX_CUSTOM_BRAND_UTF8_BYTES) {
            throw MachineValidationException(
                "O nome da bandeira personalizada excede o limite de ${CustomBrandKeyCodec.MAX_CUSTOM_BRAND_UTF8_BYTES} bytes UTF-8.",
            )
        }
        val normalized = normalizeCustomBrandName(cleanName)
        val standardConflict = CardBrand.entries.firstOrNull {
            normalizeCustomBrandName(it.label) == normalized ||
                normalizeCustomBrandName(it.storageKey) == normalized
        }
        if (standardConflict != null) {
            throw MachineValidationException(
                "\"$cleanName\" já faz parte das bandeiras padrão (${standardConflict.label}).",
            )
        }
        return cleanName
    }

    fun validateCustomBrands(inputs: List<CustomCardBrandInput>): List<CustomCardBrandRate> {
        val seenNormalized = HashSet<String>(inputs.size)
        val seenKeys = HashSet<String>(inputs.size)
        return inputs.map { item ->
            val cleanName = validateCustomBrandName(item.name)
            val normalized = normalizeCustomBrandName(cleanName)
            if (!seenNormalized.add(normalized)) {
                throw MachineValidationException(
                    "Já existe uma bandeira personalizada com o nome \"$cleanName\" nesta maquininha.",
                )
            }
            val cleanCreditBps = item.creditRateBps?.let { validateRateRange("A taxa de crédito da bandeira $cleanName", it) }
            val cleanDebitBps = item.debitRateBps?.let { validateRateRange("A taxa de débito da bandeira $cleanName", it) }

            val resolvedKey = if (item.key != null) {
                val decodedName = CustomBrandKeyCodec.decodeNameOrNull(item.key)
                    ?: throw MachineValidationException(
                        "Chave de bandeira personalizada inválida para \"$cleanName\".",
                    )
                if (decodedName != cleanName) {
                    throw MachineValidationException(
                        "A chave informada não corresponde ao nome \"$cleanName\".",
                    )
                }
                item.key
            } else {
                CustomBrandKeyCodec.encodeKey(cleanName)
            }
            if (resolvedKey.contains(':') || !CustomBrandKeyCodec.isValidCustomBrandKey(resolvedKey)) {
                throw MachineValidationException(
                    "Não foi possível gerar uma chave válida para a bandeira \"$cleanName\".",
                )
            }
            if (!seenKeys.add(resolvedKey)) {
                throw MachineValidationException(
                    "Já existe uma bandeira personalizada com o nome \"$cleanName\" nesta maquininha.",
                )
            }
            CustomCardBrandRate(
                key = resolvedKey,
                name = cleanName,
                creditRateBps = cleanCreditBps,
                debitRateBps = cleanDebitBps,
            )
        }
    }

    fun validateRateRange(label: String, bps: Int): Int {
        if (bps < RateFormatter.MIN_RATE_BPS) {
            throw MachineValidationException("$label não pode ser negativa.")
        }
        if (bps > RateFormatter.MAX_RATE_BPS) {
            throw MachineValidationException("$label não pode ultrapassar 100,00%.")
        }
        return bps
    }

    fun parseAndValidateRateInput(
        label: String,
        rawInput: String,
        defaultWhenBlankBps: Int? = null,
    ): Int {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            if (defaultWhenBlankBps != null) {
                return validateRateRange(label, defaultWhenBlankBps)
            }
            throw MachineValidationException("Informe $label.")
        }
        val parsed = RateFormatter.parsePercentageToBps(trimmed)
            ?: throw MachineValidationException("$label inválida. Use até 2 casas decimais (ex.: 1,90).")
        return validateRateRange(label, parsed)
    }

    fun parseOptionalRateInput(
        label: String,
        rawInput: String,
    ): Int? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        val parsed = RateFormatter.parsePercentageToBps(trimmed)
            ?: throw MachineValidationException("$label inválida. Use até 2 casas decimais (ex.: 3,20).")
        return validateRateRange(label, parsed)
    }

    fun validateInstallmentCombinations(
        creditEnabled: Boolean,
        installmentsEnabled: Boolean,
        creditDefaultRateBps: Int,
        standardBrandRatesBps: Map<CardBrand, Int>,
        customBrandRates: List<CustomCardBrandRate>,
        installmentAddonsBps: Map<Int, Int>,
    ) {
        installmentAddonsBps.forEach { (tier, addonBps) ->
            val enforceZeroAddon = creditEnabled && installmentsEnabled
            val defaultSum = creditDefaultRateBps + addonBps
            if ((enforceZeroAddon || addonBps > 0) && defaultSum > RateFormatter.MAX_RATE_BPS) {
                throw MachineValidationException(
                    "A soma da taxa padrão de crédito (${RateFormatter.formatBpsAsPercent(creditDefaultRateBps)}) com o adicional de ${tier}x (${RateFormatter.formatBpsAsPercent(addonBps)}) ultrapassa 100,00%.",
                )
            }
            standardBrandRatesBps.forEach { (brand, brandRateBps) ->
                val brandSum = brandRateBps + addonBps
                if ((enforceZeroAddon || addonBps > 0) && brandSum > RateFormatter.MAX_RATE_BPS) {
                    throw MachineValidationException(
                        "A soma da taxa de ${brand.label} (${RateFormatter.formatBpsAsPercent(brandRateBps)}) com o adicional de ${tier}x (${RateFormatter.formatBpsAsPercent(addonBps)}) ultrapassa 100,00%.",
                    )
                }
            }
            customBrandRates.forEach { customBrand ->
                val creditRate = customBrand.creditRateBps ?: creditDefaultRateBps
                val customSum = creditRate + addonBps
                if ((enforceZeroAddon || addonBps > 0) && customSum > RateFormatter.MAX_RATE_BPS) {
                    throw MachineValidationException(
                        "A soma da taxa de ${customBrand.name} (${RateFormatter.formatBpsAsPercent(creditRate)}) com o adicional de ${tier}x (${RateFormatter.formatBpsAsPercent(addonBps)}) ultrapassa 100,00%.",
                    )
                }
            }
        }
    }

    fun validateMachineInput(draft: MachineInputDraft): ValidatedMachineInput {
        val cleanName = validateMachineName(draft.name)
        val normalized = normalizeMachineName(cleanName)
        val debitBps = validateRateRange("A taxa de débito", draft.debitRateBps)
        val creditDefaultBps = validateRateRange("A taxa padrão de crédito", draft.creditDefaultRateBps)

        val cleanBrandRates = linkedMapOf<CardBrand, Int>()
        draft.brandRatesBps.forEach { (brand, rate) ->
            cleanBrandRates[brand] = validateRateRange("A taxa de crédito da bandeira ${brand.label}", rate)
        }

        val cleanBrandDebitRates = linkedMapOf<CardBrand, Int>()
        draft.brandDebitRatesBps.forEach { (brand, rate) ->
            cleanBrandDebitRates[brand] = validateRateRange("A taxa de débito da bandeira ${brand.label}", rate)
        }

        val cleanCustomBrands = draft.customBrandRates?.let { validateCustomBrands(it) }

        val cleanInstallments = linkedMapOf<Int, Int>()
        draft.installmentAddonsBps.forEach { (tier, addon) ->
            if (tier !in InstallmentTiers.SUPPORTED) {
                throw MachineValidationException("Parcelamento ${tier}x não é suportado.")
            }
            cleanInstallments[tier] = validateRateRange("O adicional de ${tier}x", addon)
        }

        // Valida combinações de taxa base + adicional de parcelamento <= 100% (10.000 pontos-base)
        validateInstallmentCombinations(
            creditEnabled = draft.creditEnabled,
            installmentsEnabled = draft.installmentsEnabled,
            creditDefaultRateBps = creditDefaultBps,
            standardBrandRatesBps = cleanBrandRates,
            customBrandRates = cleanCustomBrands.orEmpty(),
            installmentAddonsBps = cleanInstallments,
        )

        val cleanMaxInstallments = draft.maxInstallments?.let { max ->
            if (max !in 1..24) {
                throw MachineValidationException("O limite máximo de parcelas deve ser entre 1x e 24x.")
            }
            max
        }

        return ValidatedMachineInput(
            name = cleanName,
            normalizedName = normalized,
            debitRateBps = debitBps,
            creditEnabled = draft.creditEnabled,
            creditDefaultRateBps = creditDefaultBps,
            brandRatesEnabled = draft.brandRatesEnabled,
            brandRatesBps = cleanBrandRates,
            installmentsEnabled = draft.installmentsEnabled,
            installmentAddonsBps = cleanInstallments,
            maxInstallments = if (draft.clearMaxInstallments) null else cleanMaxInstallments,
            clearMaxInstallments = draft.clearMaxInstallments,
            customBrandRates = cleanCustomBrands,
            brandDebitRatesBps = cleanBrandDebitRates,
        )
    }
}
