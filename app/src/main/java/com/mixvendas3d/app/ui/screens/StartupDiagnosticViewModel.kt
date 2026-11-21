package com.mixvendas3d.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mixvendas3d.app.data.repository.PreferenceRepository
import com.mixvendas3d.app.domain.models.AppPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StartupDiagnosticUiState(
    val diagnosticKey: String = StartupDiagnosticViewModel.DEFAULT_DIAGNOSTIC_KEY,
    val draftValue: String = "",
    val persistedPreference: AppPreference? = null,
    val totalStoredPreferences: Int = 0,
    val isSaving: Boolean = false,
)

class StartupDiagnosticViewModel(
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {

    private val draftValueFlow = MutableStateFlow("")
    private val isSavingFlow = MutableStateFlow(false)

    val uiState: StateFlow<StartupDiagnosticUiState> = combine(
        preferenceRepository.observePreference(DEFAULT_DIAGNOSTIC_KEY).onStart { emit(null) },
        preferenceRepository.observePreferenceCount().onStart { emit(0) },
        draftValueFlow,
        isSavingFlow,
    ) { persisted, count, draft, isSaving ->
        StartupDiagnosticUiState(
            diagnosticKey = DEFAULT_DIAGNOSTIC_KEY,
            draftValue = draft,
            persistedPreference = persisted,
            totalStoredPreferences = count,
            isSaving = isSaving,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = StartupDiagnosticUiState(),
    )

    fun onDraftValueChanged(newValue: String) {
        draftValueFlow.value = newValue
    }

    fun saveDiagnosticPreference() {
        val valueToSave = draftValueFlow.value.trim()
        if (valueToSave.isEmpty() || isSavingFlow.value) return
        viewModelScope.launch {
            isSavingFlow.value = true
            try {
                preferenceRepository.savePreference(
                    key = DEFAULT_DIAGNOSTIC_KEY,
                    value = valueToSave,
                )
            } finally {
                isSavingFlow.value = false
            }
        }
    }

    fun clearDiagnosticPreference() {
        if (isSavingFlow.value) return
        viewModelScope.launch {
            isSavingFlow.value = true
            try {
                preferenceRepository.deletePreference(DEFAULT_DIAGNOSTIC_KEY)
                draftValueFlow.value = ""
            } finally {
                isSavingFlow.value = false
            }
        }
    }

    companion object {
        const val DEFAULT_DIAGNOSTIC_KEY = "diagnostico_etapa_1"

        fun provideFactory(
            preferenceRepository: PreferenceRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StartupDiagnosticViewModel(preferenceRepository) as T
            }
        }
    }
}
