package com.mixvendas3d.app

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.mixvendas3d.app.navigation.MaisSubDestination
import com.mixvendas3d.app.navigation.MixVendasAppShell
import com.mixvendas3d.app.ui.screens.CatalogViewModel
import com.mixvendas3d.app.ui.screens.ImportExportViewModel
import com.mixvendas3d.app.ui.screens.MachinesViewModel
import com.mixvendas3d.app.ui.screens.MixVendasShellViewModel
import com.mixvendas3d.app.ui.screens.SystemUpdateViewModel
import com.mixvendas3d.app.ui.theme.MixVendas3DTheme

/**
 * Fixture de teste para montar [MixVendasAppShell] com um [NavHostController] controlado pelo
 * teste, permitindo acessar a rota interna legada [MaisSubDestination.DAILY_SALES] sem expor
 * atalho visual na aba Mais opções.
 */
class MixVendasShellTestHarness(
    private val composeRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) {
    private var navController: NavHostController? = null

    fun mountAppShell() {
        val activity = composeRule.activity
        composeRule.runOnUiThread {
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            )
        }
        val app = activity.application as MixVendasApplication
        val shellViewModel = ViewModelProvider(
            activity,
            MixVendasShellViewModel.provideFactory(
                preferenceRepository = app.preferenceRepository,
                catalogRepository = app.catalogRepository,
                machineRepository = app.machineRepository,
                saleRepository = app.saleRepository,
            ),
        )[MixVendasShellViewModel::class.java]
        val catalogViewModel = ViewModelProvider(
            activity,
            CatalogViewModel.provideFactory(app.catalogRepository),
        )[CatalogViewModel::class.java]
        val machinesViewModel = ViewModelProvider(
            activity,
            MachinesViewModel.provideFactory(app.machineRepository),
        )[MachinesViewModel::class.java]
        val importExportViewModel = ViewModelProvider(
            activity,
            ImportExportViewModel.provideFactory(app.database),
        )[ImportExportViewModel::class.java]
        val systemUpdateViewModel = ViewModelProvider(
            activity,
            SystemUpdateViewModel.provideFactory(
                database = app.database,
                repository = app.systemUpdateRepository,
            ),
        )[SystemUpdateViewModel::class.java]

        composeRule.setContent {
            val controller = rememberNavController()
            navController = controller
            MixVendas3DTheme {
                MixVendasAppShell(
                    viewModel = shellViewModel,
                    catalogViewModel = catalogViewModel,
                    machinesViewModel = machinesViewModel,
                    importExportViewModel = importExportViewModel,
                    systemUpdateViewModel = systemUpdateViewModel,
                    navController = controller,
                )
            }
        }
        composeRule.waitForIdle()
    }

    fun navigateToLegacyDailySalesRoute() {
        val controller = checkNotNull(navController) {
            "MixVendasShellTestHarness.mountAppShell() must be called before navigating."
        }
        composeRule.runOnUiThread {
            if (controller.currentDestination?.route != MaisSubDestination.DAILY_SALES.route) {
                controller.navigate(MaisSubDestination.DAILY_SALES.route) {
                    launchSingleTop = true
                }
            }
        }
        composeRule.waitForIdle()
    }
}
