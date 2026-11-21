package com.mixvendas3d.app.domain.models

enum class FeeResponsibilityMode(
    val storageKey: String,
    val label: String,
    val description: String,
) {
    INDIVIDUAL(
        storageKey = "INDIVIDUAL",
        label = "Pessoa específica",
        description = "Um participante selecionado assume integralmente as tarifas deste produto.",
    ),
    EQUAL(
        storageKey = "EQUAL",
        label = "Divisão igualitária",
        description = "Prepara a divisão das tarifas em partes iguais entre os participantes.",
    ),
    PROPORTIONAL(
        storageKey = "PROPORTIONAL",
        label = "Divisão proporcional",
        description = "Prepara a divisão das tarifas proporcionalmente ao valor de cada participante.",
    );

    companion object {
        fun fromStorageKeyOrNull(key: String?): FeeResponsibilityMode? {
            return entries.firstOrNull { it.storageKey == key }
        }

        fun fromStorageKey(key: String?): FeeResponsibilityMode {
            return fromStorageKeyOrNull(key)
                ?: throw IllegalArgumentException("Modo de responsabilidade de tarifa inválido ou desconhecido: $key")
        }
    }
}

data class Person(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class PersonWithUsage(
    val person: Person,
    val linkedProductCount: Int,
)

data class ProductParticipantSplit(
    val personId: String,
    val personName: String,
    val personColorIndex: Int,
    val amount: MoneyCents,
)

data class ProductCategory(
    val id: String,
    val name: String,
    val normalizedName: String,
    val parentId: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class Product(
    val id: String,
    val name: String,
    val price: MoneyCents,
    val active: Boolean,
    val feeResponsibilityMode: FeeResponsibilityMode,
    val feeOwnerPersonId: String?,
    val participants: List<ProductParticipantSplit>,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val categoryId: String? = null,
    val subcategoryId: String? = null,
)
