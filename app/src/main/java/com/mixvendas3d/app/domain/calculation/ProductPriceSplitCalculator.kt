package com.mixvendas3d.app.domain.calculation

import com.mixvendas3d.app.util.CurrencyFormatter
import com.mixvendas3d.app.util.MonetaryInputMask

/**
 * Calculadora pura (baseada exclusivamente em centavos inteiros [Long]) para divisão
 * automática bidirecional do preço do produto entre exatamente 2 participantes.
 *
 * Regras garantidas:
 * - Bidirecional: editar Pessoa 1 calcula e preenche Pessoa 2; editar Pessoa 2 calcula e preenche Pessoa 1.
 * - Sem arredondamento flutuante (apenas subtração inteira exata em centavos).
 * - Sem ciclos infinitos: execução determinística em passagem única.
 * - Nunca produz valores negativos nem corrompe entradas quando o valor digitado excede o preço total
 *   ou quando o preço total é zero/vazio.
 */
object ProductPriceSplitCalculator {

    data class SplitUpdateResult(
        val updatedInputs: Map<String, String>,
        val lastEditedParticipantId: String?,
    )

    fun formatCentsAllowingZero(cents: Long): String {
        if (cents <= 0L) return "0,00"
        return MonetaryInputMask.formatCentsForMaskedInput(cents)
    }

    /**
     * Atualiza o campo do participante editado e, quando há exatamente 2 participantes e um preço
     * total positivo, calcula e preenche diretamente o campo do outro participante com o complemento exato.
     */
    fun onParticipantAmountEdited(
        priceInput: String,
        selectedParticipantOrder: List<String>,
        currentInputs: Map<String, String>,
        editedPersonId: String,
        rawInput: String,
    ): SplitUpdateResult {
        val masked = MonetaryInputMask.formatDigitsInput(rawInput)
        val updated = currentInputs.toMutableMap()
        updated[editedPersonId] = masked

        if (selectedParticipantOrder.size != 2 || editedPersonId !in selectedParticipantOrder) {
            return SplitUpdateResult(
                updatedInputs = updated,
                lastEditedParticipantId = if (masked.isNotEmpty()) editedPersonId else null,
            )
        }

        val otherPersonId = selectedParticipantOrder.first { it != editedPersonId }
        val totalCents = CurrencyFormatter.parseBrlToCents(priceInput)

        // Campo limpo manualmente: não sobrescreve o outro participante nem gera lixo.
        if (masked.isEmpty()) {
            val remainingAnchor = otherPersonId.takeIf { !updated[it].isNullOrBlank() }
            return SplitUpdateResult(
                updatedInputs = updated,
                lastEditedParticipantId = remainingAnchor,
            )
        }

        if (totalCents != null && totalCents > 0L) {
            val editedCents = CurrencyFormatter.parseBrlToCents(masked)
            if (editedCents != null && editedCents in 0L..totalCents) {
                val complementCents = totalCents - editedCents
                updated[otherPersonId] = formatCentsAllowingZero(complementCents)
            }
        }

        return SplitUpdateResult(
            updatedInputs = updated,
            lastEditedParticipantId = editedPersonId,
        )
    }

    /**
     * Recalcula o complemento quando o preço total do produto muda com exatamente 2 participantes
     * selecionados, preservando o último participante editado manualmente como âncora.
     */
    fun onTotalPriceChanged(
        newPriceInput: String,
        selectedParticipantOrder: List<String>,
        currentInputs: Map<String, String>,
        lastEditedParticipantId: String?,
    ): Map<String, String> {
        if (selectedParticipantOrder.size != 2) return currentInputs

        val newTotalCents = CurrencyFormatter.parseBrlToCents(newPriceInput)
        if (newTotalCents == null || newTotalCents <= 0L) return currentInputs

        val anchorId = resolveAnchorParticipant(
            selectedParticipantOrder = selectedParticipantOrder,
            currentInputs = currentInputs,
            lastEditedParticipantId = lastEditedParticipantId,
        ) ?: return currentInputs

        val anchorCents = CurrencyFormatter.parseBrlToCents(currentInputs[anchorId].orEmpty())
            ?: return currentInputs
        if (anchorCents !in 0L..newTotalCents) return currentInputs

        val otherId = selectedParticipantOrder.first { it != anchorId }
        val updated = currentInputs.toMutableMap()
        updated[otherId] = formatCentsAllowingZero(newTotalCents - anchorCents)
        return updated
    }

    /**
     * Quando a seleção passa a ter exatamente 2 participantes e apenas um deles já possui valor válido
     * (enquanto o outro está vazio), preenche o campo vazio com o complemento do preço total.
     */
    fun onParticipantsSelectionChanged(
        priceInput: String,
        selectedParticipantOrder: List<String>,
        currentInputs: Map<String, String>,
        lastEditedParticipantId: String?,
    ): SplitUpdateResult {
        val nextAnchor = lastEditedParticipantId?.takeIf { it in selectedParticipantOrder }
        if (selectedParticipantOrder.size != 2) {
            return SplitUpdateResult(
                updatedInputs = currentInputs,
                lastEditedParticipantId = nextAnchor,
            )
        }

        val totalCents = CurrencyFormatter.parseBrlToCents(priceInput)
        if (totalCents == null || totalCents <= 0L) {
            return SplitUpdateResult(
                updatedInputs = currentInputs,
                lastEditedParticipantId = nextAnchor,
            )
        }

        val firstId = selectedParticipantOrder[0]
        val secondId = selectedParticipantOrder[1]
        val firstRaw = currentInputs[firstId].orEmpty()
        val secondRaw = currentInputs[secondId].orEmpty()

        val filledId = when {
            firstRaw.isNotBlank() && secondRaw.isBlank() -> firstId
            secondRaw.isNotBlank() && firstRaw.isBlank() -> secondId
            else -> null
        } ?: return SplitUpdateResult(
            updatedInputs = currentInputs,
            lastEditedParticipantId = nextAnchor,
        )

        val emptyId = if (filledId == firstId) secondId else firstId
        val filledCents = CurrencyFormatter.parseBrlToCents(currentInputs[filledId].orEmpty())
            ?: return SplitUpdateResult(
                updatedInputs = currentInputs,
                lastEditedParticipantId = nextAnchor,
            )

        if (filledCents !in 0L..totalCents) {
            return SplitUpdateResult(
                updatedInputs = currentInputs,
                lastEditedParticipantId = nextAnchor,
            )
        }

        val updated = currentInputs.toMutableMap()
        updated[emptyId] = formatCentsAllowingZero(totalCents - filledCents)
        return SplitUpdateResult(
            updatedInputs = updated,
            lastEditedParticipantId = filledId,
        )
    }

    private fun resolveAnchorParticipant(
        selectedParticipantOrder: List<String>,
        currentInputs: Map<String, String>,
        lastEditedParticipantId: String?,
    ): String? {
        if (lastEditedParticipantId != null &&
            lastEditedParticipantId in selectedParticipantOrder &&
            !currentInputs[lastEditedParticipantId].isNullOrBlank()
        ) {
            return lastEditedParticipantId
        }
        val firstId = selectedParticipantOrder[0]
        val secondId = selectedParticipantOrder[1]
        val firstFilled = !currentInputs[firstId].isNullOrBlank()
        val secondFilled = !currentInputs[secondId].isNullOrBlank()
        return when {
            firstFilled && !secondFilled -> firstId
            secondFilled && !firstFilled -> secondId
            firstFilled && secondFilled -> firstId
            else -> null
        }
    }
}
