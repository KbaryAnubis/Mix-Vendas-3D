package com.mixvendas3d.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mixvendas3d.app.data.repository.MachineRepository
import com.mixvendas3d.app.domain.calculation.CustomCardBrandInput
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.MachineValidationException
import com.mixvendas3d.app.domain.calculation.MachineValidator
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.InstallmentTiers
import com.mixvendas3d.app.domain.models.Machine
import com.mixvendas3d.app.util.RateFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CustomBrandDraftItem(
    val localId: String,
    val persistedKey: String?,
    val name: String,
    val creditRateInput: String = "",
    val debitRateInput: String = "",
) {
    constructor(localId: String, persistedKey: String?, name: String, rateInput: String) : this(
        localId = localId,
        persistedKey = persistedKey,
        name = name,
        creditRateInput = rateInput,
        debitRateInput = "",
    )

    val rateInput: String
        get() = creditRateInput
}

data class MachineEditorUiState(
    val isOpen: Boolean = false,
    val editingMachineId: String? = null,
    val nameInput: String = "",
    val debitRateInput: String = "",
    val creditEnabled: Boolean = true,
    val creditDefaultRateInput: String = "",
    val brandRatesEnabled: Boolean = false,
    val brandRateInputs: Map<CardBrand, String> = emptyMap(),
    val installmentsEnabled: Boolean = false,
    val installmentAddonInputs: Map<Int, String> = emptyMap(),
    val isBrandSectionExpanded: Boolean = true,
    val isInstallmentsSectionExpanded: Boolean = true,
    val showDiscardConfirmation: Boolean = false,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val maxInstallments: Int? = null,
    val customBrandDrafts: List<CustomBrandDraftItem> = emptyList(),
    val newCustomBrandNameInput: String = "",
    val newCustomBrandCreditRateInput: String = "",
    val newCustomBrandDebitRateInput: String = "",
    val customBrandErrorMessage: String? = null,
) {
    val newCustomBrandRateInput: String
        get() = newCustomBrandCreditRateInput
}

data class MachinesUiState(
    val machines: List<Machine> = emptyList(),
    val searchQuery: String = "",
    val feedbackMessage: String? = null,
    val errorMessage: String? = null,
    val editor: MachineEditorUiState = MachineEditorUiState(),
) {
    val filteredMachines: List<Machine>
        get() {
            val ptBr = Locale.forLanguageTag("pt-BR")
            val query = searchQuery.trim().lowercase(ptBr)
            if (query.isEmpty()) return machines
            return machines.filter { it.name.lowercase(ptBr).contains(query) }
        }
}

private data class MachinesFormState(
    val searchQuery: String = "",
    val feedbackMessage: String? = null,
    val errorMessage: String? = null,
    val editor: MachineEditorUiState = MachineEditorUiState(),
)

class MachinesViewModel(
    private val machineRepository: MachineRepository,
) : ViewModel() {

    private val formFlow = MutableStateFlow(MachinesFormState())
    private val creditToggleMutex = Mutex()
    private var baselineEditorSnapshot: MachineEditorUiState? = null

    val uiState: StateFlow<MachinesUiState> = combine(
        machineRepository.observeMachines().onStart { emit(emptyList()) },
        formFlow,
    ) { machines, form ->
        MachinesUiState(
            machines = machines,
            searchQuery = form.searchQuery,
            feedbackMessage = form.feedbackMessage,
            errorMessage = form.errorMessage,
            editor = form.editor,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MachinesUiState(),
    )

    fun onSearchQueryChanged(query: String) {
        formFlow.value = formFlow.value.copy(
            searchQuery = query,
            feedbackMessage = null,
            errorMessage = null,
        )
    }

    fun openNewMachineEditor() {
        val initialEditor = MachineEditorUiState(
            isOpen = true,
            creditEnabled = true,
            isBrandSectionExpanded = true,
            isInstallmentsSectionExpanded = true,
        )
        baselineEditorSnapshot = initialEditor
        formFlow.value = formFlow.value.copy(
            feedbackMessage = null,
            errorMessage = null,
            editor = initialEditor,
        )
    }

    fun openEditMachineEditor(machine: Machine) {
        val brandInputs = linkedMapOf<CardBrand, String>()
        CardBrand.entries.forEach { brand ->
            val saved = machine.brandRatesBps[brand]
            if (saved != null) {
                brandInputs[brand] = RateFormatter.formatBpsForInput(saved)
            }
        }
        val installmentInputs = linkedMapOf<Int, String>()
        InstallmentTiers.SUPPORTED.forEach { tier ->
            val saved = machine.installmentAddonsBps[tier]
            if (saved != null) {
                installmentInputs[tier] = RateFormatter.formatBpsForInput(saved)
            }
        }

        val customDrafts = machine.customBrandRates.map { custom ->
            CustomBrandDraftItem(
                localId = custom.key,
                persistedKey = custom.key,
                name = custom.name,
                rateInput = RateFormatter.formatBpsForInput(custom.rateBps),
            )
        }

        val initialEditor = MachineEditorUiState(
            isOpen = true,
            editingMachineId = machine.id,
            nameInput = machine.name,
            debitRateInput = RateFormatter.formatBpsForInput(machine.debitRateBps),
            creditEnabled = machine.creditEnabled,
            creditDefaultRateInput = RateFormatter.formatBpsForInput(machine.creditDefaultRateBps),
            brandRatesEnabled = machine.brandRatesEnabled,
            brandRateInputs = brandInputs,
            installmentsEnabled = machine.installmentsEnabled,
            installmentAddonInputs = installmentInputs,
            isBrandSectionExpanded = true,
            isInstallmentsSectionExpanded = true,
            maxInstallments = machine.maxInstallments,
            customBrandDrafts = customDrafts,
        )
        baselineEditorSnapshot = initialEditor
        formFlow.value = formFlow.value.copy(
            feedbackMessage = null,
            errorMessage = null,
            editor = initialEditor,
        )
    }

    fun closeMachineEditor() {
        baselineEditorSnapshot = null
        formFlow.value = formFlow.value.copy(
            editor = MachineEditorUiState(),
        )
    }

    fun handleEditorBackRequested() {
        val editor = formFlow.value.editor
        if (!editor.isOpen) return
        if (hasUnsavedEditorChanges(editor)) {
            updateEditor { it.copy(showDiscardConfirmation = true) }
        } else {
            closeMachineEditor()
        }
    }

    fun dismissDiscardConfirmation() {
        updateEditor { it.copy(showDiscardConfirmation = false) }
    }

    fun confirmDiscardEditorChanges() {
        closeMachineEditor()
    }

    fun toggleBrandSectionExpanded() {
        updateEditor { it.copy(isBrandSectionExpanded = !it.isBrandSectionExpanded) }
    }

    fun toggleInstallmentsSectionExpanded() {
        updateEditor { it.copy(isInstallmentsSectionExpanded = !it.isInstallmentsSectionExpanded) }
    }

    private fun hasUnsavedEditorChanges(editor: MachineEditorUiState): Boolean {
        val baseline = baselineEditorSnapshot ?: return false
        return editor.nameInput != baseline.nameInput ||
            editor.debitRateInput != baseline.debitRateInput ||
            editor.creditEnabled != baseline.creditEnabled ||
            editor.creditDefaultRateInput != baseline.creditDefaultRateInput ||
            editor.brandRatesEnabled != baseline.brandRatesEnabled ||
            editor.brandRateInputs != baseline.brandRateInputs ||
            editor.installmentsEnabled != baseline.installmentsEnabled ||
            editor.installmentAddonInputs != baseline.installmentAddonInputs ||
            editor.maxInstallments != baseline.maxInstallments ||
            editor.customBrandDrafts != baseline.customBrandDrafts ||
            editor.newCustomBrandNameInput != baseline.newCustomBrandNameInput ||
            editor.newCustomBrandRateInput != baseline.newCustomBrandRateInput
    }

    fun onEditorNameChanged(value: String) {
        updateEditor { it.copy(nameInput = value, errorMessage = null) }
    }

    fun onEditorDebitRateChanged(value: String) {
        updateEditor { it.copy(debitRateInput = value, errorMessage = null) }
    }

    fun onEditorCreditEnabledChanged(enabled: Boolean) {
        // Preserva valores de crédito, bandeiras e parcelamento ao desativar/reativar
        updateEditor { it.copy(creditEnabled = enabled, errorMessage = null) }
    }

    fun onEditorCreditDefaultRateChanged(value: String) {
        updateEditor { it.copy(creditDefaultRateInput = value, errorMessage = null) }
    }

    fun onEditorBrandRatesEnabledChanged(enabled: Boolean) {
        // Preserva taxas por bandeira ao desativar/reativar
        updateEditor { it.copy(brandRatesEnabled = enabled, errorMessage = null) }
    }

    fun onEditorBrandRateChanged(brand: CardBrand, value: String) {
        updateEditor { editor ->
            val nextMap = editor.brandRateInputs.toMutableMap()
            if (value.isEmpty()) {
                nextMap.remove(brand)
            } else {
                nextMap[brand] = value
            }
            editor.copy(brandRateInputs = nextMap, errorMessage = null)
        }
    }

    fun onEditorInstallmentsEnabledChanged(enabled: Boolean) {
        // Preserva adicionais de parcelamento ao desativar/reativar
        updateEditor { it.copy(installmentsEnabled = enabled, errorMessage = null) }
    }

    fun onEditorInstallmentAddonChanged(tier: Int, value: String) {
        updateEditor { editor ->
            val nextMap = editor.installmentAddonInputs.toMutableMap()
            if (value.isEmpty()) {
                nextMap.remove(tier)
            } else {
                nextMap[tier] = value
            }
            editor.copy(installmentAddonInputs = nextMap, errorMessage = null)
        }
    }

    fun onEditorMaxInstallmentsChanged(maxInstallments: Int?) {
        updateEditor { it.copy(maxInstallments = maxInstallments, errorMessage = null) }
    }

    fun onNewCustomBrandNameChanged(value: String) {
        updateEditor {
            it.copy(
                newCustomBrandNameInput = value,
                customBrandErrorMessage = null,
                errorMessage = null,
            )
        }
    }

    fun onNewCustomBrandCreditRateChanged(value: String) {
        updateEditor {
            it.copy(
                newCustomBrandCreditRateInput = value,
                customBrandErrorMessage = null,
                errorMessage = null,
            )
        }
    }

    fun onNewCustomBrandDebitRateChanged(value: String) {
        updateEditor {
            it.copy(
                newCustomBrandDebitRateInput = value,
                customBrandErrorMessage = null,
                errorMessage = null,
            )
        }
    }

    fun onNewCustomBrandRateChanged(value: String) {
        onNewCustomBrandCreditRateChanged(value)
    }

    fun onAddCustomBrand() {
        val editor = formFlow.value.editor
        if (!editor.isOpen) return
        try {
            val cleanName = MachineValidator.validateCustomBrandName(editor.newCustomBrandNameInput)
            val normalizedNew = MachineValidator.normalizeCustomBrandName(cleanName)
            val duplicate = editor.customBrandDrafts.any {
                MachineValidator.normalizeCustomBrandName(it.name) == normalizedNew
            }
            if (duplicate) {
                throw MachineValidationException(
                    "Já existe uma bandeira personalizada com o nome \"$cleanName\" nesta maquininha.",
                )
            }
            val creditBps = MachineValidator.parseOptionalRateInput(
                label = "A taxa de crédito da bandeira personalizada $cleanName",
                rawInput = editor.newCustomBrandCreditRateInput,
            )
            val debitBps = MachineValidator.parseOptionalRateInput(
                label = "A taxa de débito da bandeira personalizada $cleanName",
                rawInput = editor.newCustomBrandDebitRateInput,
            )
            val generatedKey = CustomBrandKeyCodec.encodeKey(cleanName)
            val newItem = CustomBrandDraftItem(
                localId = generatedKey,
                persistedKey = null,
                name = cleanName,
                creditRateInput = creditBps?.let { RateFormatter.formatBpsForInput(it) }.orEmpty(),
                debitRateInput = debitBps?.let { RateFormatter.formatBpsForInput(it) }.orEmpty(),
            )
            updateEditor {
                it.copy(
                    customBrandDrafts = it.customBrandDrafts + newItem,
                    newCustomBrandNameInput = "",
                    newCustomBrandCreditRateInput = "",
                    newCustomBrandDebitRateInput = "",
                    customBrandErrorMessage = null,
                    errorMessage = null,
                )
            }
        } catch (ex: MachineValidationException) {
            updateEditor { it.copy(customBrandErrorMessage = ex.message) }
        }
    }

    fun onCustomBrandCreditRateChanged(localId: String, value: String) {
        updateEditor { editor ->
            editor.copy(
                customBrandDrafts = editor.customBrandDrafts.map { item ->
                    if (item.localId == localId) item.copy(creditRateInput = value) else item
                },
                customBrandErrorMessage = null,
                errorMessage = null,
            )
        }
    }

    fun onCustomBrandDebitRateChanged(localId: String, value: String) {
        updateEditor { editor ->
            editor.copy(
                customBrandDrafts = editor.customBrandDrafts.map { item ->
                    if (item.localId == localId) item.copy(debitRateInput = value) else item
                },
                customBrandErrorMessage = null,
                errorMessage = null,
            )
        }
    }

    fun onCustomBrandRateChanged(localId: String, value: String) {
        onCustomBrandCreditRateChanged(localId, value)
    }

    fun onRemoveCustomBrand(localId: String) {
        updateEditor { editor ->
            editor.copy(
                customBrandDrafts = editor.customBrandDrafts.filterNot { it.localId == localId },
                customBrandErrorMessage = null,
                errorMessage = null,
            )
        }
    }

    fun saveMachine() {
        val editor = formFlow.value.editor
        if (!editor.isOpen || editor.isSaving) return

        val draft = try {
            val debitBps = MachineValidator.parseAndValidateRateInput(
                label = "A taxa de débito (%)",
                rawInput = editor.debitRateInput,
                defaultWhenBlankBps = null,
            )
            val creditDefaultBps = if (editor.creditEnabled) {
                MachineValidator.parseAndValidateRateInput(
                    label = "A taxa padrão de crédito (%)",
                    rawInput = editor.creditDefaultRateInput,
                    defaultWhenBlankBps = null,
                )
            } else {
                MachineValidator.parseAndValidateRateInput(
                    label = "A taxa padrão de crédito (%)",
                    rawInput = editor.creditDefaultRateInput,
                    defaultWhenBlankBps = 0,
                )
            }

            val parsedBrands = linkedMapOf<CardBrand, Int>()
            CardBrand.entries.forEach { brand ->
                val raw = editor.brandRateInputs[brand].orEmpty()
                val parsed = MachineValidator.parseOptionalRateInput(
                    label = "A taxa da bandeira ${brand.label}",
                    rawInput = raw,
                )
                if (parsed != null) {
                    parsedBrands[brand] = parsed
                }
            }

            val parsedInstallments = linkedMapOf<Int, Int>()
            InstallmentTiers.SUPPORTED.forEach { tier ->
                val raw = editor.installmentAddonInputs[tier].orEmpty()
                val parsed = MachineValidator.parseOptionalRateInput(
                    label = "O adicional de ${tier}x",
                    rawInput = raw,
                )
                if (parsed != null) {
                    parsedInstallments[tier] = parsed
                }
            }

            val parsedCustomBrands = editor.customBrandDrafts.map { customItem ->
                val creditBps = MachineValidator.parseOptionalRateInput(
                    label = "A taxa de crédito da bandeira personalizada ${customItem.name}",
                    rawInput = customItem.creditRateInput,
                )
                val debitBps = MachineValidator.parseOptionalRateInput(
                    label = "A taxa de débito da bandeira personalizada ${customItem.name}",
                    rawInput = customItem.debitRateInput,
                )
                CustomCardBrandInput(
                    name = customItem.name,
                    creditRateBps = creditBps,
                    debitRateBps = debitBps,
                    key = customItem.persistedKey,
                )
            }

            val shouldClearMaxInstallments = editor.editingMachineId != null &&
                baselineEditorSnapshot?.maxInstallments != null &&
                editor.maxInstallments == null

            MachineInputDraft(
                name = editor.nameInput,
                debitRateBps = debitBps,
                creditEnabled = editor.creditEnabled,
                creditDefaultRateBps = creditDefaultBps,
                brandRatesEnabled = editor.brandRatesEnabled,
                brandRatesBps = parsedBrands,
                installmentsEnabled = editor.installmentsEnabled,
                installmentAddonsBps = parsedInstallments,
                maxInstallments = editor.maxInstallments,
                clearMaxInstallments = shouldClearMaxInstallments,
                customBrandRates = parsedCustomBrands,
            )
        } catch (ex: MachineValidationException) {
            updateEditor { it.copy(errorMessage = ex.message) }
            return
        }

        updateEditor { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                if (editor.editingMachineId == null) {
                    val created = machineRepository.createMachine(draft)
                    baselineEditorSnapshot = null
                    formFlow.value = formFlow.value.copy(
                        feedbackMessage = "Maquininha \"${created.name}\" salva com sucesso.",
                        errorMessage = null,
                        editor = MachineEditorUiState(),
                    )
                } else {
                    val updated = machineRepository.updateMachine(editor.editingMachineId, draft)
                    baselineEditorSnapshot = null
                    formFlow.value = formFlow.value.copy(
                        feedbackMessage = "Maquininha \"${updated.name}\" atualizada com sucesso.",
                        errorMessage = null,
                        editor = MachineEditorUiState(),
                    )
                }
            } catch (ex: MachineValidationException) {
                updateEditor { it.copy(isSaving = false, errorMessage = ex.message) }
            } catch (ex: Exception) {
                updateEditor {
                    it.copy(
                        isSaving = false,
                        errorMessage = ex.message ?: "Erro ao salvar maquininha no banco local.",
                    )
                }
            }
        }
    }

    fun toggleCreditEnabled(machine: Machine) {
        viewModelScope.launch {
            creditToggleMutex.withLock {
                try {
                    val current = machineRepository.observeMachines()
                        .first()
                        .firstOrNull { it.id == machine.id }
                    val updated = machineRepository.setCreditEnabled(
                        machineId = machine.id,
                        creditEnabled = !(current?.creditEnabled ?: machine.creditEnabled),
                    )
                    val status = if (updated.creditEnabled) "ativado" else "desativado (tarifas preservadas)"
                    formFlow.value = formFlow.value.copy(
                        feedbackMessage = "Crédito de \"${updated.name}\" $status.",
                        errorMessage = null,
                    )
                } catch (ex: Exception) {
                    formFlow.value = formFlow.value.copy(
                        errorMessage = ex.message ?: "Erro ao alterar o recurso de crédito.",
                        feedbackMessage = null,
                    )
                }
            }
        }
    }

    fun deleteMachine(machineId: String) {
        viewModelScope.launch {
            try {
                machineRepository.deleteMachine(machineId)
                formFlow.value = formFlow.value.copy(
                    feedbackMessage = "Maquininha excluída com sucesso.",
                    errorMessage = null,
                )
            } catch (ex: MachineValidationException) {
                formFlow.value = formFlow.value.copy(
                    errorMessage = ex.message,
                    feedbackMessage = null,
                )
            } catch (ex: Exception) {
                formFlow.value = formFlow.value.copy(
                    errorMessage = ex.message ?: "Erro ao excluir maquininha.",
                    feedbackMessage = null,
                )
            }
        }
    }

    private inline fun updateEditor(
        transform: (MachineEditorUiState) -> MachineEditorUiState,
    ) {
        val current = formFlow.value
        formFlow.value = current.copy(editor = transform(current.editor))
    }

    companion object {
        fun provideFactory(
            machineRepository: MachineRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MachinesViewModel(machineRepository) as T
            }
        }
    }
}
