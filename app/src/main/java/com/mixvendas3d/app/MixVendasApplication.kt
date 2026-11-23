package com.mixvendas3d.app

import android.app.Application
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.repository.CatalogRepository
import com.mixvendas3d.app.data.repository.MachineRepository
import com.mixvendas3d.app.data.repository.PreferenceRepository
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.data.repository.RoomPreferenceRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.data.repository.RoomSystemUpdateRepository
import com.mixvendas3d.app.data.repository.SaleRepository
import com.mixvendas3d.app.data.repository.SystemUpdateRepository

class MixVendasApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val preferenceRepository: PreferenceRepository by lazy {
        RoomPreferenceRepository(database.preferenceDao())
    }

    val catalogRepository: CatalogRepository by lazy {
        RoomCatalogRepository(
            personDao = database.personDao(),
            productDao = database.productDao(),
        )
    }

    val machineRepository: MachineRepository by lazy {
        RoomMachineRepository(
            machineDao = database.machineDao(),
            dependencyCountProvider = { machineId ->
                database.saleDao().countSalesUsingMachine(machineId)
            },
        )
    }

    val saleRepository: SaleRepository by lazy {
        RoomSaleRepository(
            database = database,
            saleDao = database.saleDao(),
        )
    }

    val systemUpdateRepository: SystemUpdateRepository by lazy {
        RoomSystemUpdateRepository(database = database)
    }
}
