package com.mixvendas3d.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mixvendas3d.app.navigation.MaisSubDestination
import java.time.LocalDate

@Composable
fun MaisDetailScreen(
    destination: MaisSubDestination,
    uiState: MixVendasShellUiState,
    catalogUiState: CatalogUiState,
    catalogViewModel: CatalogViewModel,
    machinesUiState: MachinesUiState,
    machinesViewModel: MachinesViewModel,
    importExportUiState: ImportExportUiState,
    importExportViewModel: ImportExportViewModel,
    systemUpdateUiState: SystemUpdateUiState,
    systemUpdateViewModel: SystemUpdateViewModel,
    onOpenDayInVendas: (LocalDate) -> Unit,
    onNavigateToSubDestination: (MaisSubDestination) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backAction = if (destination == MaisSubDestination.MACHINES && machinesUiState.editor.isOpen) {
        machinesViewModel::handleEditorBackRequested
    } else {
        onBack
    }
    BackHandler(onBack = backAction)

    when (destination) {
        MaisSubDestination.NAMES -> {
            NamesScreen(
                uiState = catalogUiState.names,
                onNameInputChanged = catalogViewModel::onPersonNameInputChanged,
                onSavePerson = catalogViewModel::savePerson,
                onStartEdit = catalogViewModel::startEditingPerson,
                onCancelEdit = catalogViewModel::cancelEditingPerson,
                onDeletePerson = catalogViewModel::deletePerson,
                modifier = modifier,
            )
        }

        MaisSubDestination.TYPES_PRODUCTS -> {
            ProductsScreen(
                uiState = catalogUiState,
                onSearchChanged = catalogViewModel::onProductSearchChanged,
                onShowOnlyActiveToggled = catalogViewModel::onShowOnlyActiveToggled,
                onOpenNewProduct = catalogViewModel::openNewProductEditor,
                onOpenEditProduct = catalogViewModel::openEditProductEditor,
                onRequestProductDeletion = catalogViewModel::requestProductDeletion,
                onDismissProductDeletion = catalogViewModel::dismissProductDeletionPrompt,
                onConfirmProductDeletion = catalogViewModel::confirmProductDeletion,
                onGoToNames = { onNavigateToSubDestination(MaisSubDestination.NAMES) },
                onCloseEditor = catalogViewModel::closeProductEditor,
                onProductNameChanged = catalogViewModel::onProductNameChanged,
                onProductPriceChanged = catalogViewModel::onProductPriceChanged,
                onProductActiveChanged = catalogViewModel::onProductActiveChanged,
                onProductCategoryChanged = catalogViewModel::onProductCategoryChanged,
                onProductSubcategoryChanged = catalogViewModel::onProductSubcategoryChanged,
                onCreateCategory = catalogViewModel::createProductCategory,
                onRenameCategory = catalogViewModel::renameProductCategory,
                onDeleteCategory = catalogViewModel::deleteProductCategory,
                onToggleParticipant = catalogViewModel::toggleProductParticipant,
                onParticipantAmountChanged = catalogViewModel::onParticipantAmountChanged,
                onFeeModeChanged = catalogViewModel::onFeeResponsibilityModeChanged,
                onFeeOwnerSelected = catalogViewModel::onFeeOwnerSelected,
                onSaveProduct = catalogViewModel::saveProduct,
                modifier = modifier,
            )
        }

        MaisSubDestination.MACHINES -> {
            MachinesScreen(
                uiState = machinesUiState,
                onSearchChanged = machinesViewModel::onSearchQueryChanged,
                onOpenNewMachine = machinesViewModel::openNewMachineEditor,
                onOpenEditMachine = machinesViewModel::openEditMachineEditor,
                onToggleCreditEnabled = machinesViewModel::toggleCreditEnabled,
                onDeleteMachine = machinesViewModel::deleteMachine,
                onCloseEditor = machinesViewModel::closeMachineEditor,
                onRequestBackFromEditor = machinesViewModel::handleEditorBackRequested,
                onDismissDiscardConfirmation = machinesViewModel::dismissDiscardConfirmation,
                onConfirmDiscardChanges = machinesViewModel::confirmDiscardEditorChanges,
                onEditorNameChanged = machinesViewModel::onEditorNameChanged,
                onEditorDebitRateChanged = machinesViewModel::onEditorDebitRateChanged,
                onEditorCreditEnabledChanged = machinesViewModel::onEditorCreditEnabledChanged,
                onEditorCreditDefaultRateChanged = machinesViewModel::onEditorCreditDefaultRateChanged,
                onEditorBrandRatesEnabledChanged = machinesViewModel::onEditorBrandRatesEnabledChanged,
                onEditorBrandRateChanged = machinesViewModel::onEditorBrandRateChanged,
                onEditorInstallmentsEnabledChanged = machinesViewModel::onEditorInstallmentsEnabledChanged,
                onEditorInstallmentAddonChanged = machinesViewModel::onEditorInstallmentAddonChanged,
                onEditorMaxInstallmentsChanged = machinesViewModel::onEditorMaxInstallmentsChanged,
                onToggleBrandSectionExpanded = machinesViewModel::toggleBrandSectionExpanded,
                onToggleInstallmentsSectionExpanded = machinesViewModel::toggleInstallmentsSectionExpanded,
                onSaveMachine = machinesViewModel::saveMachine,
                modifier = modifier,
                onNewCustomBrandNameChanged = machinesViewModel::onNewCustomBrandNameChanged,
                onNewCustomBrandRateChanged = machinesViewModel::onNewCustomBrandRateChanged,
                onNewCustomBrandCreditRateChanged = machinesViewModel::onNewCustomBrandCreditRateChanged,
                onNewCustomBrandDebitRateChanged = machinesViewModel::onNewCustomBrandDebitRateChanged,
                onAddCustomBrand = machinesViewModel::onAddCustomBrand,
                onCustomBrandRateChanged = machinesViewModel::onCustomBrandRateChanged,
                onCustomBrandCreditRateChanged = machinesViewModel::onCustomBrandCreditRateChanged,
                onCustomBrandDebitRateChanged = machinesViewModel::onCustomBrandDebitRateChanged,
                onRemoveCustomBrand = machinesViewModel::onRemoveCustomBrand,
            )
        }

        MaisSubDestination.DAILY_SALES -> {
            DailySalesScreen(
                report = uiState.realDailyReport,
                onDayClick = onOpenDayInVendas,
                modifier = modifier,
            )
        }

        MaisSubDestination.IMPORT_EXPORT -> {
            ImportExportScreen(
                uiState = importExportUiState,
                onExportBackupClick = importExportViewModel::requestExportBackupJson,
                onDocumentDestinationSelected = importExportViewModel::onDocumentDestinationSelected,
                onSelectBackupClick = importExportViewModel::requestSelectBackupJson,
                onImportFileSelected = importExportViewModel::onImportFileSelected,
                onSelectConflictResolution = importExportViewModel::onSelectConflictResolution,
                onPrepareReviewRestore = importExportViewModel::onPrepareReviewRestore,
                onBackToConflicts = importExportViewModel::onBackToConflicts,
                onRequestRestoreClick = importExportViewModel::requestConfirmRestore,
                onCancelRestoreConfirmation = importExportViewModel::cancelRestoreConfirmation,
                onConfirmRestoreExecute = importExportViewModel::confirmAndExecuteRestore,
                onVerifyLocalDatabaseConsistency = importExportViewModel::verifyLocalDatabaseConsistency,
                modifier = modifier,
            )
        }

        MaisSubDestination.UPDATE_SYSTEM -> {
            UpdateSystemScreen(
                uiState = systemUpdateUiState,
                onAnalyzeClick = systemUpdateViewModel::analyzeAdjustments,
                onBackupClick = systemUpdateViewModel::requestExportBackupJson,
                onBackupDocumentDestinationSelected = systemUpdateViewModel::onBackupDocumentDestinationSelected,
                onAllowPartialChanged = systemUpdateViewModel::setAllowPartialUpdateWithConflicts,
                onRequestApplyUpdateClick = systemUpdateViewModel::requestConfirmUpdate,
                onDismissApplyDialog = systemUpdateViewModel::cancelConfirmUpdate,
                onConfirmApplyUpdate = systemUpdateViewModel::confirmAndApplyUpdate,
                onRequestUndoClick = systemUpdateViewModel::requestConfirmUndo,
                onDismissUndoDialog = systemUpdateViewModel::cancelConfirmUndo,
                onConfirmUndo = systemUpdateViewModel::confirmAndRevertLastUpdate,
                onRefreshUndoStatus = systemUpdateViewModel::refreshUndoStatus,
                modifier = modifier,
            )
        }
    }
}
