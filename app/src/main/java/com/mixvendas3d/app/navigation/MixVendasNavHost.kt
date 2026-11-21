package com.mixvendas3d.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mixvendas3d.app.R
import com.mixvendas3d.app.ui.components.GlobalCastleWallpaper
import com.mixvendas3d.app.ui.components.NamesV2TopBar
import com.mixvendas3d.app.ui.components.V19BottomNavigationBar
import com.mixvendas3d.app.ui.components.V19TopBar
import com.mixvendas3d.app.ui.screens.CatalogViewModel
import com.mixvendas3d.app.ui.screens.FechamentoScreen
import com.mixvendas3d.app.ui.screens.ImportExportViewModel
import com.mixvendas3d.app.ui.screens.InicioScreen
import com.mixvendas3d.app.ui.screens.MachinesViewModel
import com.mixvendas3d.app.ui.screens.MaisDetailScreen
import com.mixvendas3d.app.ui.screens.MaisScreen
import com.mixvendas3d.app.ui.screens.MixVendasShellViewModel
import com.mixvendas3d.app.ui.screens.SystemUpdateViewModel
import com.mixvendas3d.app.ui.screens.VendasScreen
import com.mixvendas3d.app.ui.screens.VenderScreen

@Composable
fun MixVendasAppShell(
    viewModel: MixVendasShellViewModel,
    catalogViewModel: CatalogViewModel,
    machinesViewModel: MachinesViewModel,
    importExportViewModel: ImportExportViewModel,
    systemUpdateViewModel: SystemUpdateViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val catalogUiState by catalogViewModel.uiState.collectAsStateWithLifecycle()
    val machinesUiState by machinesViewModel.uiState.collectAsStateWithLifecycle()
    val importExportUiState by importExportViewModel.uiState.collectAsStateWithLifecycle()
    val systemUpdateUiState by systemUpdateViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: MainTab.INICIO.route

    val currentSubDestination = MaisSubDestination.entries.firstOrNull { it.route == currentRoute }
    val currentTab = when {
        currentSubDestination != null -> MainTab.MAIS
        else -> MainTab.entries.firstOrNull { it.route == currentRoute } ?: MainTab.INICIO
    }

    val topBarOverline: String
    val topBarTitle: String
    if (currentSubDestination != null) {
        topBarOverline = currentSubDestination.overline
        topBarTitle = currentSubDestination.title
    } else {
        when (currentTab) {
            MainTab.INICIO -> {
                topBarOverline = ""
                topBarTitle = ""
            }
            MainTab.VENDER -> {
                if (uiState.venderForm.isEditing) {
                    topBarOverline = "EDIÇÃO DE VENDA"
                    topBarTitle = stringResource(R.string.sell_edit_title)
                } else {
                    topBarOverline = "LANÇAMENTO DE VENDA"
                    topBarTitle = stringResource(R.string.sell_title)
                }
            }
            MainTab.VENDAS -> {
                topBarOverline = "HISTÓRICO DETALHADO"
                topBarTitle = stringResource(R.string.sales_title)
            }
            MainTab.FECHAMENTO -> {
                topBarOverline = "RELATÓRIO DO PERÍODO"
                topBarTitle = stringResource(R.string.closing_title)
            }
            MainTab.MAIS -> {
                topBarOverline = ""
                topBarTitle = ""
            }
        }
    }

    fun navigateToTab(tab: MainTab) {
        if (navController.currentDestination?.route == tab.route) return
        if (tab == MainTab.INICIO && navController.popBackStack(MainTab.INICIO.route, inclusive = false)) {
            return
        }
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isEditingOnVender = currentTab == MainTab.VENDER &&
        currentSubDestination == null &&
        uiState.venderForm.isEditing

    BackHandler(enabled = isEditingOnVender) {
        viewModel.handleEditBackRequested {
            navigateToTab(MainTab.VENDAS)
        }
    }

    val hideTopBar = ((currentTab == MainTab.INICIO ||
        currentTab == MainTab.VENDAS ||
        currentTab == MainTab.FECHAMENTO ||
        currentTab == MainTab.MAIS) &&
        currentSubDestination == null) ||
        (currentTab == MainTab.VENDER && currentSubDestination == null && !isEditingOnVender)

    GlobalCastleWallpaper(modifier = modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (!hideTopBar) {
                    if (isEditingOnVender) {
                        NamesV2TopBar(
                            title = stringResource(R.string.sell_edit_title),
                            onBackClick = {
                                viewModel.handleEditBackRequested {
                                    navigateToTab(MainTab.VENDAS)
                                }
                            },
                        )
                    } else if (currentSubDestination == MaisSubDestination.NAMES ||
                        currentSubDestination == MaisSubDestination.TYPES_PRODUCTS ||
                        currentSubDestination == MaisSubDestination.MACHINES ||
                        currentSubDestination == MaisSubDestination.IMPORT_EXPORT ||
                        currentSubDestination == MaisSubDestination.UPDATE_SYSTEM
                    ) {
                        val v2HeaderTitle = when (currentSubDestination) {
                            MaisSubDestination.NAMES -> stringResource(R.string.names_header_title)
                            MaisSubDestination.TYPES_PRODUCTS -> stringResource(R.string.products_header_title)
                            MaisSubDestination.MACHINES -> stringResource(R.string.machines_header_title)
                            MaisSubDestination.IMPORT_EXPORT -> stringResource(R.string.mais_card_import_export_title)
                            MaisSubDestination.UPDATE_SYSTEM -> stringResource(R.string.mais_card_update_system_title)
                            else -> topBarTitle
                        }
                        NamesV2TopBar(
                            title = v2HeaderTitle,
                            onBackClick = {
                                if (currentSubDestination == MaisSubDestination.MACHINES &&
                                    machinesUiState.editor.isOpen
                                ) {
                                    machinesViewModel.handleEditorBackRequested()
                                } else {
                                    navController.popBackStack()
                                }
                            },
                        )
                    } else {
                        V19TopBar(
                            isHome = false,
                            title = topBarTitle,
                            overline = topBarOverline,
                            showBackButton = currentSubDestination != null,
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onNewSaleClick = { navigateToTab(MainTab.VENDER) },
                        )
                    }
                }
            },
            bottomBar = {
                V19BottomNavigationBar(
                    currentTab = currentTab,
                    onTabSelected = { tab -> navigateToTab(tab) },
                )
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = MainTab.INICIO.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                composable(route = MainTab.INICIO.route) {
                    InicioScreen(
                        uiState = uiState,
                        onNewSaleClick = { navigateToTab(MainTab.VENDER) },
                        onToggleShowAllPeople = viewModel::toggleShowAllPeopleOnHome,
                    )
                }

                composable(route = MainTab.VENDER.route) {
                    VenderScreen(
                        uiState = uiState,
                        onOpenPicker = { viewModel.setProductPickerOpen(true) },
                        onClosePicker = { viewModel.setProductPickerOpen(false) },
                        onPickerSearchChanged = viewModel::onProductPickerSearchChanged,
                        onToggleProductSelection = viewModel::toggleProductSelection,
                        onIncrementProductQty = viewModel::incrementProductQuantity,
                        onDecrementProductQty = viewModel::decrementProductQuantity,
                        onRemoveProduct = viewModel::removeProductFromSale,
                        onFinalAmountChanged = viewModel::onFinalAmountInputChanged,
                        onDiscountTargetSelected = viewModel::onDiscountTargetSelected,
                        onPaymentMethodSelected = viewModel::onPaymentMethodSelected,
                        onOpenMachinePicker = { viewModel.setMachinePickerOpen(true) },
                        onCloseMachinePicker = { viewModel.setMachinePickerOpen(false) },
                        onMachinePickerSearchChanged = viewModel::onMachinePickerSearchChanged,
                        onMachineSelected = viewModel::onMachineSelected,
                        onBrandSelected = viewModel::onBrandSelected,
                        onInstallmentsSelected = viewModel::onInstallmentsSelected,
                        onFeeOverrideToggled = viewModel::onFeeOverrideToggled,
                        onManualRateChanged = viewModel::onManualRateInputChanged,
                        onNoteChanged = viewModel::onNoteChanged,
                        onSaveSale = viewModel::saveSale,
                        onCancelEditing = viewModel::cancelEditing,
                        onDismissDiscardEdit = viewModel::dismissDiscardEditDialog,
                        onConfirmDiscardEdit = {
                            viewModel.confirmDiscardEditing {
                                navigateToTab(MainTab.VENDAS)
                            }
                        },
                        onBrandKeySelected = viewModel::onBrandKeySelected,
                    )
                }

                composable(route = MainTab.VENDAS.route) {
                    VendasScreen(
                        uiState = uiState,
                        onPeriodSelected = viewModel::onPeriodFilterSelected,
                        onCustomDatesChanged = viewModel::onCustomDatesChanged,
                        onSearchChanged = viewModel::onSalesSearchChanged,
                        onMethodFilterSelected = viewModel::onSalesMethodFilterSelected,
                        onClearSelectedDay = viewModel::clearSelectedSalesDate,
                        onGoToSell = { navigateToTab(MainTab.VENDER) },
                        onEditSale = { saleId ->
                            viewModel.startEditingSale(saleId) {
                                navigateToTab(MainTab.VENDER)
                            }
                        },
                        onRequestDeleteSale = viewModel::requestDeleteSale,
                        onDismissDeleteDialog = viewModel::dismissDeleteSaleDialog,
                        onConfirmDeleteSale = viewModel::confirmDeleteSale,
                    )
                }

                composable(route = MainTab.FECHAMENTO.route) {
                    FechamentoScreen(
                        uiState = uiState,
                        onPeriodSelected = viewModel::onClosingPeriodFilterSelected,
                        onCustomDatesChanged = viewModel::onClosingCustomDatesChanged,
                        onExportCsvClick = viewModel::requestOpenCsvExportDialog,
                        onSelectCsvExportMode = viewModel::selectCsvExportMode,
                        onDismissCsvExportDialog = viewModel::dismissCsvExportDialog,
                        onConfirmCsvExport = viewModel::confirmCsvExport,
                        onCsvDocumentDestinationSelected = viewModel::onCsvDocumentDestinationSelected,
                    )
                }

                composable(route = MainTab.MAIS.route) {
                    MaisScreen(
                        onDestinationClick = { dest ->
                            navController.navigate(dest.route) {
                                launchSingleTop = true
                            }
                        },
                    )
                }

                MaisSubDestination.entries.forEach { destination ->
                    composable(route = destination.route) {
                        MaisDetailScreen(
                            destination = destination,
                            uiState = uiState,
                            catalogUiState = catalogUiState,
                            catalogViewModel = catalogViewModel,
                            machinesUiState = machinesUiState,
                            machinesViewModel = machinesViewModel,
                            importExportUiState = importExportUiState,
                            importExportViewModel = importExportViewModel,
                            systemUpdateUiState = systemUpdateUiState,
                            systemUpdateViewModel = systemUpdateViewModel,
                            onOpenDayInVendas = { date ->
                                viewModel.openSalesForDay(date)
                                navController.popBackStack()
                                navigateToTab(MainTab.VENDAS)
                            },
                            onNavigateToSubDestination = { subDest ->
                                navController.navigate(subDest.route) {
                                    launchSingleTop = true
                                }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
