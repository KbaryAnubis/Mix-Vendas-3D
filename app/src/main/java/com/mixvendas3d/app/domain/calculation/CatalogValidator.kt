package com.mixvendas3d.app.domain.calculation

import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import java.util.Locale

data class ProductParticipantDraft(
    val personId: String,
    val amount: MoneyCents,
)

data class ValidatedProductInput(
    val name: String,
    val price: MoneyCents,
    val active: Boolean,
    val feeResponsibilityMode: FeeResponsibilityMode,
    val feeOwnerPersonId: String?,
    val participants: List<ProductParticipantDraft>,
)

class CatalogValidationException(message: String) : IllegalArgumentException(message)

object CatalogValidator {
    private val ptBrLocale: Locale = Locale.forLanguageTag("pt-BR")

    fun normalizePersonName(rawName: String): String {
        return rawName.trim().lowercase(ptBrLocale)
    }

    fun validatePersonName(rawName: String): String {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) {
            throw CatalogValidationException("Informe o nome da pessoa.")
        }
        return trimmed
    }

    fun validateCategoryName(rawName: String): String {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) {
            throw CatalogValidationException("Informe o nome da categoria.")
        }
        return trimmed
    }

    fun validateProductInput(
        rawName: String,
        price: MoneyCents,
        active: Boolean,
        participants: List<ProductParticipantDraft>,
        existingPersonIds: Set<String>,
        feeResponsibilityMode: FeeResponsibilityMode,
        feeOwnerPersonId: String?,
    ): ValidatedProductInput {
        val trimmedName = rawName.trim()
        if (trimmedName.isEmpty()) {
            throw CatalogValidationException("Informe o nome do produto.")
        }
        if (price.rawCents <= 0L) {
            throw CatalogValidationException("O preço padrão do produto deve ser maior que zero.")
        }
        if (participants.isEmpty()) {
            throw CatalogValidationException("Selecione pelo menos uma pessoa participante.")
        }

        val seenPersonIds = HashSet<String>(participants.size)
        var sumCents = 0L
        participants.forEach { split ->
            if (!seenPersonIds.add(split.personId)) {
                throw CatalogValidationException("Não é permitido repetir o mesmo participante no produto.")
            }
            if (!existingPersonIds.contains(split.personId)) {
                throw CatalogValidationException("Participante selecionado não existe no cadastro de pessoas.")
            }
            if (split.amount.rawCents < 0L) {
                throw CatalogValidationException("Os valores individuais não podem ser negativos.")
            }
            sumCents = try {
                MonetaryRounding.safeAdd(sumCents, split.amount.rawCents)
            } catch (_: FinancialOverflowException) {
                throw CatalogValidationException(
                    "A soma dos valores individuais dos participantes excede o limite monetário suportado.",
                )
            }
        }

        if (sumCents != price.rawCents) {
            throw CatalogValidationException(
                "A soma dos valores individuais deve ser exatamente igual ao preço do produto.",
            )
        }

        val resolvedOwnerId = when (feeResponsibilityMode) {
            FeeResponsibilityMode.INDIVIDUAL -> {
                val ownerId = feeOwnerPersonId?.takeIf { it.isNotBlank() }
                    ?: throw CatalogValidationException("Selecione qual participante é responsável pelas tarifas.")
                if (!existingPersonIds.contains(ownerId)) {
                    throw CatalogValidationException("O responsável pelas tarifas selecionado não existe.")
                }
                if (!seenPersonIds.contains(ownerId)) {
                    throw CatalogValidationException(
                        "O responsável pelas tarifas deve ser um dos participantes do produto.",
                    )
                }
                ownerId
            }
            FeeResponsibilityMode.EQUAL,
            FeeResponsibilityMode.PROPORTIONAL -> null
        }

        return ValidatedProductInput(
            name = trimmedName,
            price = price,
            active = active,
            feeResponsibilityMode = feeResponsibilityMode,
            feeOwnerPersonId = resolvedOwnerId,
            participants = participants,
        )
    }
}
