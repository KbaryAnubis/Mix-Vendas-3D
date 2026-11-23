package com.mixvendas3d.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    // Limpa fluxos transitórios de Produtos (editor de produto / gerenciador / editor de categoria)
    // ao sair definitivamente da rota Produtos, evitando reabertura involuntária ao reentrar.
    var lastRoute by remember { mutableStateOf(currentRoute) }
    LaunchedEffect(currentRoute) {
        if (lastRoute == MaisSubDestination.TYPES_PRODUCTS.route &&
            currentRoute != MaisSubDestination.TYPES_PRODUCTS.route
        ) {
            catalogViewModel.resetProductsTransientFlows()
        }
        lastRoute = currentRoute
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

    GlobalCastleWallpaper(modifier = modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                enterTransition = { fadeIn(animationSpec = tween(durationMillis = 200)) },
                exitTransition = { fadeOut(animationSpec = tween(durationMillis = 180)) },
                popEnterTransition = { fadeIn(animationSpec = tween(durationMillis = 200)) },
                popExitTransition = { fadeOut(animationSpec = tween(durationMillis = 180)) },
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (uiState.venderForm.isEditing) {
                            NamesV2TopBar(
                                title = stringResource(R.string.sell_edit_title),
                                onBackClick = {
                                    viewModel.handleEditBackRequested {
                                        navigateToTab(MainTab.VENDAS)
                                    }
                                },
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
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
                                onCustomSaleDateToggled = viewModel::onCustomSaleDateToggled,
                                onSaleDateSelected = viewModel::onSaleDateSelected,
                                onToggleDiscountProduct = viewModel::onToggleDiscountProduct,
                                onSelectAllDiscountProducts = viewModel::onSelectAllDiscountProducts,
                                onClearDiscountProducts = viewModel::onClearDiscountProducts,
                                onToggleProtectedDiscountPerson = viewModel::onToggleProtectedDiscountPerson,
                                onAuthorizeUnprotectFixedPerson = viewModel::onAuthorizeUnprotectFixedPerson,
                            )
                        }
                    }
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
                        Column(modifier = Modifier.fillMaxSize()) {
                            DestinationTopBar(
                                destination = destination,
                                catalogUiState = catalogUiState,
                                machinesUiState = machinesUiState,
                                onBackClick = {
                                    when (destination) {
                                        MaisSubDestination.MACHINES -> {
                                            if (machinesUiState.editor.isOpen) {
                                                machinesViewModel.handleEditorBackRequested()
                                            } else {
                                                navController.popBackStack()
                                            }
                                        }
                                        MaisSubDestination.TYPES_PRODUCTS -> {
                                            catalogViewModel.handleProductsBackRequested {
                                                navController.popBackStack()
                                            }
                                        }
                                        else -> {
                                            navController.popBackStack()
                                        }
                                    }
                                },
                                onNewSaleClick = { navigateToTab(MainTab.VENDER) },
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
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
    }
}

@Composable
private fun DestinationTopBar(
    destination: MaisSubDestination,
    catalogUiState: com.mixvendas3d.app.ui.screens.CatalogUiState,
    machinesUiState: com.mixvendas3d.app.ui.screens.MachinesUiState,
    onBackClick: () -> Unit,
    onNewSaleClick: () -> Unit,
) {
    when (destination) {
        MaisSubDestination.NAMES,
        MaisSubDestination.TYPES_PRODUCTS,
        MaisSubDestination.MACHINES,
        MaisSubDestination.IMPORT_EXPORT,
        MaisSubDestination.UPDATE_SYSTEM -> {
            val v2HeaderTitle = when (destination) {
                MaisSubDestination.NAMES -> stringResource(R.string.names_header_title)
                MaisSubDestination.TYPES_PRODUCTS -> {
                    val products = catalogUiState.products
                    when {
                        products.categoryEditor.isOpen && products.categoryEditor.editingCategoryId != null -> "Editar categoria"
                        products.categoryEditor.isOpen -> "Nova categoria"
                        products.categoryManagerOpen -> "Categorias"
                        products.editor.isOpen && products.editor.editingProductId != null -> stringResource(R.string.products_edit_title)
                        products.editor.isOpen -> "Novo produto"
                        else -> stringResource(R.string.products_header_title)
                    }
                }
                MaisSubDestination.MACHINES -> {
                    if (machinesUiState.editor.isOpen) {
                        if (machinesUiState.editor.editingMachineId != null) {
                            stringResource(R.string.machines_edit_title)
                        } else {
                            stringResource(R.string.machines_create_title)
                        }
                    } else {
                        stringResource(R.string.machines_header_title)
                    }
                }
                MaisSubDestination.IMPORT_EXPORT -> stringResource(R.string.mais_card_import_export_title)
                MaisSubDestination.UPDATE_SYSTEM -> stringResource(R.string.mais_card_update_system_title)
                MaisSubDestination.DAILY_SALES -> destination.title
            }
            NamesV2TopBar(
                title = v2HeaderTitle,
                onBackClick = onBackClick,
            )
        }
        MaisSubDestination.DAILY_SALES -> {
            V19TopBar(
                isHome = false,
                title = destination.title,
                overline = destination.overline,
                showBackButton = true,
                onBackClick = onBackClick,
                onNewSaleClick = onNewSaleClick,
            )
        }
    }
}
