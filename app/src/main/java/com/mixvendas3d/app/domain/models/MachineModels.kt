package com.mixvendas3d.app.domain.models

enum class CardBrand(
    val storageKey: String,
    val label: String,
) {
    VISA("VISA", "Visa"),
    MASTERCARD("MASTERCARD", "Mastercard"),
    ELO("ELO", "Elo"),
    AMEX("AMEX", "American Express"),
    HIPERCARD("HIPERCARD", "Hipercard"),
    HIPER("HIPER", "Hiper"),
    DINERS("DINERS", "Diners Club"),
    DISCOVER("DISCOVER", "Discover"),
    CABAL("CABAL", "Cabal"),
    AURA("AURA", "Aura"),
    BANRICOMPRAS("BANRICOMPRAS", "Banricompras"),
    JCB("JCB", "JCB"),
    OTHER("OTHER", "Outros");

    companion object {
        fun fromStorageKey(key: String?): CardBrand? {
            return entries.firstOrNull { it.storageKey == key }
        }
    }
}

object InstallmentTiers {
    val SUPPORTED: List<Int> = (1..24).toList()
    val LEGACY_SUPPORTED: List<Int> = listOf(1, 2, 3, 4, 6, 12)

    fun allowedFor(maxInstallments: Int?): List<Int> {
        val limit = maxInstallments ?: return LEGACY_SUPPORTED
        return (1..limit.coerceIn(1, SUPPORTED.max())).toList()
    }
}

data class CustomCardBrandRate(
    val key: String,
    val name: String,
    val creditRateBps: Int? = null,
    val debitRateBps: Int? = null,
) {
    val rateBps: Int
        get() = creditRateBps ?: 0
}

data class Machine(
    val id: String,
    val name: String,
    val debitRateBps: Int,
    val creditEnabled: Boolean,
    val creditDefaultRateBps: Int,
    val brandRatesEnabled: Boolean,
    val brandRatesBps: Map<CardBrand, Int> = emptyMap(),
    val brandDebitRatesBps: Map<CardBrand, Int> = emptyMap(),
    val installmentsEnabled: Boolean,
    val installmentAddonsBps: Map<Int, Int>,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val maxInstallments: Int? = null,
    val customBrandRates: List<CustomCardBrandRate> = emptyList(),
) {
    fun allowedInstallmentTiers(): List<Int> {
        if (!creditEnabled || !installmentsEnabled) return listOf(1)
        return InstallmentTiers.allowedFor(maxInstallments)
    }

    fun isInstallmentAllowed(installments: Int): Boolean {
        return installments in allowedInstallmentTiers()
    }

    fun coerceInstallments(requested: Int): Int {
        val allowed = allowedInstallmentTiers()
        return if (requested in allowed) requested else 1
    }

    fun findCustomBrand(brandKey: String?): CustomCardBrandRate? {
        if (brandKey.isNullOrBlank()) return null
        return customBrandRates.firstOrNull { it.key == brandKey }
    }

    fun baseDebitRateBps(brand: CardBrand?): Int {
        if (!brandRatesEnabled || brand == null) {
            return debitRateBps
        }
        return brandDebitRatesBps[brand] ?: debitRateBps
    }

    fun baseDebitRateBpsForKey(brandKey: String?): Int {
        if (!brandRatesEnabled || brandKey.isNullOrBlank()) {
            return debitRateBps
        }
        val standardBrand = CardBrand.fromStorageKey(brandKey)
        if (standardBrand != null) {
            return brandDebitRatesBps[standardBrand] ?: debitRateBps
        }
        val custom = findCustomBrand(brandKey)
        return custom?.debitRateBps ?: debitRateBps
    }

    fun baseCreditRateBps(brand: CardBrand?): Int {
        if (!brandRatesEnabled || brand == null) {
            return creditDefaultRateBps
        }
        return brandRatesBps[brand] ?: creditDefaultRateBps
    }

    fun baseCreditRateBpsForKey(brandKey: String?): Int {
        if (!brandRatesEnabled || brandKey.isNullOrBlank()) {
            return creditDefaultRateBps
        }
        val standardBrand = CardBrand.fromStorageKey(brandKey)
        if (standardBrand != null) {
            return brandRatesBps[standardBrand] ?: creditDefaultRateBps
        }
        val custom = findCustomBrand(brandKey)
        return custom?.creditRateBps ?: creditDefaultRateBps
    }

    fun usesBrandSpecificRate(brand: CardBrand?): Boolean {
        return brandRatesEnabled && brand != null && (brandRatesBps.containsKey(brand) || brandDebitRatesBps.containsKey(brand))
    }

    fun usesBrandSpecificRateForKey(brandKey: String?): Boolean {
        if (!brandRatesEnabled || brandKey.isNullOrBlank()) return false
        val standardBrand = CardBrand.fromStorageKey(brandKey)
        if (standardBrand != null) {
            return brandRatesBps.containsKey(standardBrand) || brandDebitRatesBps.containsKey(standardBrand)
        }
        val custom = findCustomBrand(brandKey)
        return custom != null && (custom.creditRateBps != null || custom.debitRateBps != null)
    }

    fun installmentAddonBps(installments: Int): Int {
        if (!installmentsEnabled) return 0
        return installmentAddonsBps[installments] ?: 0
    }

    fun effectiveCreditRateBps(brand: CardBrand?, installments: Int): Int {
        val effectiveInstallments = if (installmentsEnabled) installments else 1
        return baseCreditRateBps(brand) + installmentAddonBps(effectiveInstallments)
    }

    fun effectiveCreditRateBpsForKey(brandKey: String?, installments: Int): Int {
        val effectiveInstallments = if (installmentsEnabled) installments else 1
        return baseCreditRateBpsForKey(brandKey) + installmentAddonBps(effectiveInstallments)
    }

    fun effectiveAutomaticRateBps(
        method: PreviewPaymentMethod,
        brand: CardBrand?,
        installments: Int,
    ): Int? {
        return when (method) {
            PreviewPaymentMethod.CASH,
            PreviewPaymentMethod.PIX -> null
            PreviewPaymentMethod.DEBIT -> baseDebitRateBps(brand)
            PreviewPaymentMethod.CREDIT -> {
                if (!creditEnabled) null else effectiveCreditRateBps(brand, installments)
            }
        }
    }

    fun effectiveAutomaticRateBpsForKey(
        method: PreviewPaymentMethod,
        brandKey: String?,
        installments: Int,
    ): Int? {
        return when (method) {
            PreviewPaymentMethod.CASH,
            PreviewPaymentMethod.PIX -> null
            PreviewPaymentMethod.DEBIT -> baseDebitRateBpsForKey(brandKey)
            PreviewPaymentMethod.CREDIT -> {
                if (!creditEnabled) null else effectiveCreditRateBpsForKey(brandKey, installments)
            }
        }
    }
}
