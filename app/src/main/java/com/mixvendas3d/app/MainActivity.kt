package com.mixvendas3d.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.mixvendas3d.app.navigation.MixVendasAppShell
import com.mixvendas3d.app.ui.screens.CatalogViewModel
import com.mixvendas3d.app.ui.screens.ImportExportViewModel
import com.mixvendas3d.app.ui.screens.MachinesViewModel
import com.mixvendas3d.app.ui.screens.MixVendasShellViewModel
import com.mixvendas3d.app.ui.screens.SystemUpdateViewModel
import com.mixvendas3d.app.ui.theme.MixVendas3DTheme

class MainActivity : ComponentActivity() {

    private val shellViewModel: MixVendasShellViewModel by viewModels {
        val app = application as MixVendasApplication
        MixVendasShellViewModel.provideFactory(
            preferenceRepository = app.preferenceRepository,
            catalogRepository = app.catalogRepository,
            machineRepository = app.machineRepository,
            saleRepository = app.saleRepository,
        )
    }

    private val catalogViewModel: CatalogViewModel by viewModels {
        val app = application as MixVendasApplication
        CatalogViewModel.provideFactory(app.catalogRepository)
    }

    private val machinesViewModel: MachinesViewModel by viewModels {
        val app = application as MixVendasApplication
        MachinesViewModel.provideFactory(app.machineRepository)
    }

    private val importExportViewModel: ImportExportViewModel by viewModels {
        val app = application as MixVendasApplication
        ImportExportViewModel.provideFactory(app.database)
    }

    private val systemUpdateViewModel: SystemUpdateViewModel by viewModels {
        val app = application as MixVendasApplication
        SystemUpdateViewModel.provideFactory(
            database = app.database,
            repository = app.systemUpdateRepository,
            catalogRepository = app.catalogRepository,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            MixVendas3DTheme {
                MixVendasAppShell(
                    viewModel = shellViewModel,
                    catalogViewModel = catalogViewModel,
                    machinesViewModel = machinesViewModel,
                    importExportViewModel = importExportViewModel,
                    systemUpdateViewModel = systemUpdateViewModel,
                )
            }
        }
    }
}
