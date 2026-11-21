package com.mixvendas3d.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.MachineBrandRateEntity
import com.mixvendas3d.app.data.local.entity.MachineEntity
import com.mixvendas3d.app.data.local.entity.MachineInstallmentRateEntity
import com.mixvendas3d.app.data.local.entity.PersonEntity
import com.mixvendas3d.app.data.local.entity.PreferenceEntity
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.data.repository.RoomPreferenceRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.MachineValidationException
import com.mixvendas3d.app.domain.calculation.MachineValidator
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleCalculationException
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.BackupCollectionType
import com.mixvendas3d.app.domain.export.ExecutorTestHooks
import com.mixvendas3d.app.domain.export.JsonBackupComparator
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupMergeDecisionValidator
import com.mixvendas3d.app.domain.export.JsonBackupMergeExecutor
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanner
import com.mixvendas3d.app.domain.export.JsonBackupMergePreviewBuilder
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.export.JsonBackupSnapshot
import com.mixvendas3d.app.domain.export.JsonBackupValidationException
import com.mixvendas3d.app.domain.export.MergeConflictResolution
import com.mixvendas3d.app.domain.export.MergeExecutionFailureReason
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.InstallmentTiers
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PreviewPaymentMethod
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductParticipantSplit
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.ui.screens.MachinesViewModel
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StageV24b2aMachineMaxInstallmentsAndMigrationTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val createdDbNames = mutableListOf<String>()

    @After
    fun tearDown() {
        createdDbNames.forEach { dbName ->
            runCatching { context.deleteDatabase(dbName) }
        }
        createdDbNames.clear()
        val app = context as MixVendasApplication
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
    }

    private fun newUniqueDbName(tag: String): String {
        val name = shortTestDatabaseName(tag)
        assertNotEquals(AppDatabase.DATABASE_NAME, name)
        createdDbNames.add(name)
        context.deleteDatabase(name)
        return name
    }

    private fun openFileDb(dbName: String): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
            )
            .allowMainThreadQueries()
            .build()
    }

    private fun newInMemoryDb(): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Test
    fun `01 legacy machine with null maxInstallments preserves legacy behavior without auto 24x`() = runBlocking<Unit>(Dispatchers.IO) {
        val db = newInMemoryDb()
        try {
            val machineRepo = RoomMachineRepository(
                machineDao = db.machineDao(),
                clockMillis = { 1_730_000_000_000L },
            )
            val created = machineRepo.createMachine(
                MachineInputDraft(
                    name = "Stone Legado",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 350,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 320),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 6 to 200, 12 to 450),
                ),
            )

            assertNull(created.maxInstallments)
            assertNull(db.machineDao().getWithRatesById(created.id)!!.machine.maxInstallments)

            // Null não significa liberação automática até 24x; lista legada permanece 1, 2, 3, 4, 6, 12
            assertEquals(listOf(1, 2, 3, 4, 6, 12), InstallmentTiers.LEGACY_SUPPORTED)
            assertFalse(InstallmentTiers.LEGACY_SUPPORTED.contains(18))
            assertFalse(InstallmentTiers.LEGACY_SUPPORTED.contains(24))
            assertEquals((1..24).toList(), InstallmentTiers.SUPPORTED)

            // Motor financeiro segue aplicando taxa legada sem alteração
            val rate12x = created.effectiveAutomaticRateBps(PreviewPaymentMethod.CREDIT, CardBrand.VISA, 12)
            assertEquals(770, rate12x)

            val product = Product(
                id = "prod-legado",
                name = "Peça 3D",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantSplit(
                        personId = "p-1",
                        personName = "Ana",
                        personColorIndex = 0,
                        amount = MoneyCents(10_000L),
                    ),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                createdAtEpochMs = 1_730_000_000_000L,
                updatedAtEpochMs = 1_730_000_000_000L,
            )
            val calc = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(product, 1)),
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = created,
                    cardBrand = CardBrand.VISA,
                    installments = 12,
                ),
            )
            assertEquals(770, calc.effectiveFeeRateBps)
            assertEquals(770L, calc.feeTotal.rawCents)
            assertEquals(9_230L, calc.netTotal.rawCents)

            @OptIn(ExperimentalCoroutinesApi::class)
            run {
                Dispatchers.setMain(UnconfinedTestDispatcher())
                val vm = MachinesViewModel(machineRepo)
                val collectorJob = launch { vm.uiState.collect { } }
                try {
                    vm.openEditMachineEditor(created)
                    val openedState = withTimeout(5_000L) {
                        vm.uiState.first { it.editor.isOpen && it.editor.editingMachineId == created.id }
                    }
                    assertNull(openedState.editor.maxInstallments)
                } finally {
                    collectorJob.cancel()
                    Dispatchers.resetMain()
                }
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `02 explicit maxInstallments persists across close and reopen, survives partial edits, and clears only explicitly`() = runBlocking<Unit> {
        val dbName = newUniqueDbName("persist_edit")
        var fakeClock = 1_730_000_000_000L

        // 1. Grava maquininha com maxInstallments = 18 e fecha o banco
        val db1 = openFileDb(dbName)
        val repo1 = RoomMachineRepository(db1.machineDao(), clockMillis = { fakeClock })
        val created = repo1.createMachine(
            MachineInputDraft(
                name = "PagBank 18x",
                debitRateBps = 199,
                creditEnabled = true,
                creditDefaultRateBps = 399,
                brandRatesEnabled = false,
                brandRatesBps = emptyMap(),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(12 to 450, 18 to 720),
                maxInstallments = 18,
            ),
        )
        assertEquals(18, created.maxInstallments)
        db1.close()

        // 2. Reabre o banco e confirma persistência real
        val db2 = openFileDb(dbName)
        val repo2 = RoomMachineRepository(db2.machineDao(), clockMillis = { fakeClock })
        val reopened = repo2.observeMachines().first().single { it.id == created.id }
        assertEquals(18, reopened.maxInstallments)

        // 3. Edita outros dados via Repository sem informar maxInstallments (null) -> deve preservar 18
        fakeClock += 10_000L
        val editedRepo = repo2.updateMachine(
            machineId = reopened.id,
            draft = MachineInputDraft(
                name = "PagBank 18x Renomeada",
                debitRateBps = 175,
                creditEnabled = true,
                creditDefaultRateBps = 380,
                brandRatesEnabled = false,
                brandRatesBps = emptyMap(),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(12 to 450, 18 to 720),
                maxInstallments = null,
                clearMaxInstallments = false,
            ),
        )
        assertEquals(18, editedRepo.maxInstallments)
        db2.close()

        // 4. Reabre novamente e edita pelo MachinesViewModel sem mexer no limite -> deve preservar 18
        val db3 = openFileDb(dbName)
        try {
            val repo3 = RoomMachineRepository(db3.machineDao(), clockMillis = { fakeClock + 20_000L })
            val loadedForVm = repo3.observeMachines().first().single { it.id == created.id }
            assertEquals(18, loadedForVm.maxInstallments)

            @OptIn(ExperimentalCoroutinesApi::class)
            run {
                Dispatchers.setMain(UnconfinedTestDispatcher())
                val vm = MachinesViewModel(repo3)
                val collectorJob = launch { vm.uiState.collect { } }
                try {
                    vm.openEditMachineEditor(loadedForVm)
                    val openedEditorState = withTimeout(5_000L) {
                        vm.uiState.first { it.editor.isOpen && it.editor.editingMachineId == loadedForVm.id }
                    }
                    assertEquals(18, openedEditorState.editor.maxInstallments)
                    vm.onEditorNameChanged("PagBank Editada VM")
                    vm.onEditorDebitRateChanged("1,60")
                    vm.saveMachine()

                    withTimeout(5_000L) {
                        vm.uiState.first { !it.editor.isOpen && it.machines.any { m -> m.name == "PagBank Editada VM" } }
                    }
                    val afterVmEdit = repo3.observeMachines().first().single { it.id == created.id }
                    assertEquals("PagBank Editada VM", afterVmEdit.name)
                    assertEquals(160, afterVmEdit.debitRateBps)
                    assertEquals(18, afterVmEdit.maxInstallments)
                } finally {
                    collectorJob.cancel()
                    Dispatchers.resetMain()
                }
            }

            // 5. Limpeza explícita via clearMaxInstallments = true zera para null e persiste após reopen
            val cleared = repo3.updateMachine(
                machineId = created.id,
                draft = MachineInputDraft(
                    name = "PagBank Limpa",
                    debitRateBps = 160,
                    creditEnabled = true,
                    creditDefaultRateBps = 380,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(12 to 450),
                    maxInstallments = null,
                    clearMaxInstallments = true,
                ),
            )
            assertNull(cleared.maxInstallments)
        } finally {
            db3.close()
        }

        val db4 = openFileDb(dbName)
        try {
            val afterClearReopen = db4.machineDao().getWithRatesById(created.id)!!.machine
            assertNull(afterClearReopen.maxInstallments)
        } finally {
            db4.close()
        }
    }

    @Test
    fun `03 validator and repository accept 1 to 24 and reject 0, negative, and above 24`() = runBlocking<Unit>(Dispatchers.IO) {
        for (limit in 1..24) {
            val validated = MachineValidator.validateMachineInput(
                MachineInputDraft(
                    name = "Maquininha $limit",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 350,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = true,
                    installmentAddonsBps = emptyMap(),
                    maxInstallments = limit,
                ),
            )
            assertEquals(limit, validated.maxInstallments)
        }

        val db = newInMemoryDb()
        try {
            val repo = RoomMachineRepository(db.machineDao(), clockMillis = { 1_730_000_000_000L })
            listOf(0, -1, -12, 25, 36).forEach { invalidLimit ->
                assertThrows(MachineValidationException::class.java) {
                    MachineValidator.validateMachineInput(
                        MachineInputDraft(
                            name = "Invalida $invalidLimit",
                            debitRateBps = 190,
                            creditEnabled = true,
                            creditDefaultRateBps = 350,
                            brandRatesEnabled = false,
                            brandRatesBps = emptyMap(),
                            installmentsEnabled = false,
                            installmentAddonsBps = emptyMap(),
                            maxInstallments = invalidLimit,
                        ),
                    )
                }

                val createFailure = runCatching {
                    repo.createMachine(
                        MachineInputDraft(
                            name = "Repo Invalida $invalidLimit",
                            debitRateBps = 190,
                            creditEnabled = true,
                            creditDefaultRateBps = 350,
                            brandRatesEnabled = false,
                            brandRatesBps = emptyMap(),
                            installmentsEnabled = false,
                            installmentAddonsBps = emptyMap(),
                            maxInstallments = invalidLimit,
                        ),
                    )
                }
                assertTrue(createFailure.exceptionOrNull() is MachineValidationException)
            }
            assertEquals(0, db.machineDao().getAllMachines().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `04 json backup compatibility supports v4 without maxInstallments, v5 round-trip with maxInstallments, and rejects invalid limits`() = runBlocking<Unit>(Dispatchers.IO) {
        val db = newInMemoryDb()
        try {
            val repo = RoomMachineRepository(db.machineDao(), clockMillis = { 1_730_000_000_000L })
            val m18 = repo.createMachine(
                MachineInputDraft(
                    name = "Stone 18x",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 350,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(18 to 650),
                    maxInstallments = 18,
                ),
            )
            val mLegacy = repo.createMachine(
                MachineInputDraft(
                    name = "Ton Legada",
                    debitRateBps = 150,
                    creditEnabled = true,
                    creditDefaultRateBps = 310,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                    maxInstallments = null,
                ),
            )

            // 1. Exportação Room v7 e round-trip preservando 18 e null
            val exported = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_100_000L)
            val rootV5 = JSONObject(exported.jsonContent)
            assertEquals(7, rootV5.getInt("roomSchemaVersion"))

            val readV5 = JsonBackupReader.readAndValidate(exported.jsonContent)
            assertTrue("Backup v7 deveria ser válido: ${readV5.errors}", readV5.isValid)
            assertEquals(7, readV5.roomSchemaVersion)
            val readMachinesV5 = readV5.validatedData!!.machines.associateBy { it.id }
            assertEquals(18, readMachinesV5.getValue(m18.id).maxInstallments)
            assertNull(readMachinesV5.getValue(mLegacy.id).maxInstallments)

            // 2. Fixture explícita de backup antigo Room v4 sem campo maxInstallments nas máquinas
            val rootV4 = JSONObject(exported.jsonContent).apply {
                put("roomSchemaVersion", 4)
                val machinesArray = getJSONObject("collections").getJSONArray("machines")
                for (i in 0 until machinesArray.length()) {
                    machinesArray.getJSONObject(i).remove("maxInstallments")
                }
            }
            val readV4 = JsonBackupReader.readAndValidate(rootV4.toString())
            assertTrue("Backup legado v4 sem maxInstallments deveria ser aceito: ${readV4.errors}", readV4.isValid)
            assertEquals(4, readV4.roomSchemaVersion)
            assertTrue(readV4.validatedData!!.machines.all { it.maxInstallments == null })

            // 3. Leitor rejeita maxInstallments inválido (0, negativo, > 24, decimal ou string)
            listOf<Any>(0, -1, 25, 1.5, "18").forEach { invalidJsonValue ->
                val tampered = JSONObject(exported.jsonContent).apply {
                    getJSONObject("collections").getJSONArray("machines")
                        .getJSONObject(0)
                        .put("maxInstallments", invalidJsonValue)
                }
                val res = JsonBackupReader.readAndValidate(tampered.toString())
                assertFalse("Leitor deve rejeitar maxInstallments=$invalidJsonValue", res.isValid)
                assertNull(res.validatedData)
            }

            // 4. Exportador rejeita snapshot com maxInstallments inválido (0 ou 25)
            listOf(0, 25).forEach { invalidLimit ->
                val badEntity = MachineEntity(
                    id = "m-bad",
                    name = "Inválida",
                    normalizedName = "invalida",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 350,
                    brandRatesEnabled = false,
                    installmentsEnabled = false,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                    maxInstallments = invalidLimit,
                )
                assertThrows(JsonBackupValidationException::class.java) {
                    JsonBackupExporter.serializeSnapshot(
                        JsonBackupSnapshot(
                            preferences = emptyList(),
                            people = emptyList(),
                            products = emptyList(),
                            productParticipants = emptyList(),
                            machines = listOf(badEntity),
                            machineBrandRates = emptyList(),
                            machineInstallmentRates = emptyList(),
                            sales = emptyList(),
                            saleItems = emptyList(),
                            saleItemParticipants = emptyList(),
                            salePersonTotals = emptyList(),
                        ),
                    )
                }
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `05 real migration v4 to v5 preserves all 11 tables and sets max_installments to null on legacy machines`() = runBlocking<Unit>(Dispatchers.IO) {
        val dbName = newUniqueDbName("migration_v4_v5")
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Cria banco exatamente no esquema v4 (sem coluna max_installments em machines) com todas as 11 tabelas populadas
        val v4Sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v4Sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_preferences` (`pref_key` TEXT NOT NULL, `pref_value` TEXT NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`pref_key`))",
        )
        v4Sqlite.execSQL("INSERT INTO `app_preferences` VALUES ('tema_app', 'roxo_v4', 1730000000000)")

        v4Sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `people` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `normalized_name` TEXT NOT NULL, `color_index` INTEGER NOT NULL, `created_at_epoch_ms` INTEGER NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        v4Sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_people_normalized_name` ON `people` (`normalized_name`)")
        v4Sqlite.execSQL("INSERT INTO `people` VALUES ('p-1', 'Lucas', 'lucas', 0, 1730000000000, 1730000000000)")
        v4Sqlite.execSQL("INSERT INTO `people` VALUES ('p-2', 'Marina', 'marina', 1, 1730000000000, 1730000000000)")

        v4Sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `products` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `price_cents` INTEGER NOT NULL, `active` INTEGER NOT NULL, `fee_responsibility_mode` TEXT NOT NULL, `fee_owner_person_id` TEXT, `created_at_epoch_ms` INTEGER NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`fee_owner_person_id`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
        )
        v4Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_products_fee_owner_person_id` ON `products` (`fee_owner_person_id`)")
        v4Sqlite.execSQL("INSERT INTO `products` VALUES ('prod-1', 'Dragão 3D', 10000, 1, 'INDIVIDUAL', 'p-1', 1730000000000, 1730000000000)")

        v4Sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `product_participants` (`product_id` TEXT NOT NULL, `person_id` TEXT NOT NULL, `amount_cents` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`product_id`, `person_id`), FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`person_id`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)",
        )
        v4Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_product_participants_product_id` ON `product_participants` (`product_id`)")
        v4Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_product_participants_person_id` ON `product_participants` (`person_id`)")
        v4Sqlite.execSQL("INSERT INTO `product_participants` VALUES ('prod-1', 'p-1', 6000, 0)")
        v4Sqlite.execSQL("INSERT INTO `product_participants` VALUES ('prod-1', 'p-2', 4000, 1)")

        v4Sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `machines` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `normalized_name` TEXT NOT NULL, `debit_rate_bps` INTEGER NOT NULL, `credit_enabled` INTEGER NOT NULL, `credit_default_rate_bps` INTEGER NOT NULL, `brand_rates_enabled` INTEGER NOT NULL, `installments_enabled` INTEGER NOT NULL, `created_at_epoch_ms` INTEGER NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        v4Sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_machines_normalized_name` ON `machines` (`normalized_name`)")
        v4Sqlite.execSQL("INSERT INTO `machines` VALUES ('m-v4', 'Stone V4', 'stone v4', 199, 1, 399, 1, 1, 1730000000000, 1730000000000)")

        v4Sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `machine_brand_rates` (`machine_id` TEXT NOT NULL, `brand_key` TEXT NOT NULL, `rate_bps` INTEGER NOT NULL, PRIMARY KEY(`machine_id`, `brand_key`), FOREIGN KEY(`machine_id`) REFERENCES `machines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        v4Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_machine_brand_rates_machine_id` ON `machine_brand_rates` (`machine_id`)")
        v4Sqlite.execSQL("INSERT INTO `machine_brand_rates` VALUES ('m-v4', 'VISA', 350)")

        v4Sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `machine_installment_rates` (`machine_id` TEXT NOT NULL, `installments` INTEGER NOT NULL, `addon_bps` INTEGER NOT NULL, PRIMARY KEY(`machine_id`, `installments`), FOREIGN KEY(`machine_id`) REFERENCES `machines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        v4Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_machine_installment_rates_machine_id` ON `machine_installment_rates` (`machine_id`)")
        v4Sqlite.execSQL("INSERT INTO `machine_installment_rates` VALUES ('m-v4', 2, 150)")

        v4Sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sales` (
                `id` TEXT NOT NULL, `sold_at_epoch_ms` INTEGER NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL,
                `payment_method` TEXT NOT NULL, `original_subtotal_cents` INTEGER NOT NULL, `discount_total_cents` INTEGER NOT NULL,
                `final_amount_cents` INTEGER NOT NULL, `fee_total_cents` INTEGER NOT NULL, `net_total_cents` INTEGER NOT NULL,
                `discount_mode` TEXT NOT NULL, `discount_target_product_id` TEXT, `discount_target_product_name` TEXT,
                `machine_id` TEXT, `machine_name` TEXT, `machine_debit_rate_bps` INTEGER, `machine_credit_default_rate_bps` INTEGER,
                `machine_brand_rates_enabled` INTEGER, `machine_installments_enabled` INTEGER, `card_brand` TEXT,
                `installments` INTEGER, `automatic_rate_bps` INTEGER, `manual_rate_override_bps` INTEGER,
                `effective_fee_rate_bps` INTEGER NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        v4Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_sold_at_epoch_ms` ON `sales` (`sold_at_epoch_ms`)")
        v4Sqlite.execSQL(
            """
            INSERT INTO `sales` VALUES (
                'sale-v4', 1730000100000, 1730000100000,
                'CREDIT', 10000, 0,
                10000, 500, 9500,
                'PROPORTIONAL', NULL, NULL,
                'm-v4', 'Stone V4', 199, 399,
                1, 1, 'VISA',
                2, 500, NULL,
                500, 'Venda V4 Preservada'
            )
            """.trimIndent(),
        )

        v4Sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sale_items` (
                `id` TEXT NOT NULL, `sale_id` TEXT NOT NULL, `sort_order` INTEGER NOT NULL,
                `product_id` TEXT NOT NULL, `product_name` TEXT NOT NULL, `quantity` INTEGER NOT NULL,
                `unit_price_original_cents` INTEGER NOT NULL, `original_subtotal_cents` INTEGER NOT NULL,
                `discount_amount_cents` INTEGER NOT NULL, `final_amount_cents` INTEGER NOT NULL,
                `applied_fee_rate_bps` INTEGER NOT NULL, `fee_amount_cents` INTEGER NOT NULL,
                `net_amount_cents` INTEGER NOT NULL, `fee_responsibility_mode` TEXT NOT NULL,
                `fee_owner_person_id` TEXT, `fee_owner_person_name` TEXT,
                PRIMARY KEY(`id`), FOREIGN KEY(`sale_id`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        v4Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_sale_id` ON `sale_items` (`sale_id`)")
        v4Sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sale_items_sale_id_product_id` ON `sale_items` (`sale_id`, `product_id`)")
        v4Sqlite.execSQL(
            """
            INSERT INTO `sale_items` VALUES (
                'si-v4', 'sale-v4', 0, 'prod-1', 'Dragão 3D', 1,
                10000, 10000, 0, 10000,
                500, 500, 9500, 'INDIVIDUAL', 'p-1', 'Lucas'
            )
            """.trimIndent(),
        )

        v4Sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sale_item_participants` (
                `sale_item_id` TEXT NOT NULL, `person_id` TEXT NOT NULL, `sort_order` INTEGER NOT NULL,
                `person_name` TEXT NOT NULL, `person_color_index` INTEGER NOT NULL,
                `original_unit_amount_cents` INTEGER NOT NULL, `original_subtotal_cents` INTEGER NOT NULL,
                `discount_amount_cents` INTEGER NOT NULL, `gross_amount_cents` INTEGER NOT NULL,
                `fee_amount_cents` INTEGER NOT NULL, `net_amount_cents` INTEGER NOT NULL,
                PRIMARY KEY(`sale_item_id`, `person_id`), FOREIGN KEY(`sale_item_id`) REFERENCES `sale_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        v4Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_item_participants_sale_item_id` ON `sale_item_participants` (`sale_item_id`)")
        v4Sqlite.execSQL("INSERT INTO `sale_item_participants` VALUES ('si-v4', 'p-1', 0, 'Lucas', 0, 6000, 6000, 0, 6000, 500, 5500)")
        v4Sqlite.execSQL("INSERT INTO `sale_item_participants` VALUES ('si-v4', 'p-2', 1, 'Marina', 1, 4000, 4000, 0, 4000, 0, 4000)")

        v4Sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sale_person_totals` (
                `sale_id` TEXT NOT NULL, `person_id` TEXT NOT NULL, `sort_order` INTEGER NOT NULL,
                `person_name` TEXT NOT NULL, `person_color_index` INTEGER NOT NULL,
                `gross_amount_cents` INTEGER NOT NULL, `fee_amount_cents` INTEGER NOT NULL,
                `net_amount_cents` INTEGER NOT NULL,
                PRIMARY KEY(`sale_id`, `person_id`), FOREIGN KEY(`sale_id`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        v4Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_person_totals_sale_id` ON `sale_person_totals` (`sale_id`)")
        v4Sqlite.execSQL("INSERT INTO `sale_person_totals` VALUES ('sale-v4', 'p-1', 0, 'Lucas', 0, 6000, 500, 5500)")
        v4Sqlite.execSQL("INSERT INTO `sale_person_totals` VALUES ('sale-v4', 'p-2', 1, 'Marina', 1, 4000, 0, 4000)")

        // Confirma que no v4 a coluna max_installments ainda não existe
        val v4Columns = mutableSetOf<String>()
        v4Sqlite.rawQuery("PRAGMA table_info(`machines`)", null).use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                v4Columns.add(cursor.getString(nameIdx))
            }
        }
        assertFalse(v4Columns.contains("max_installments"))

        v4Sqlite.version = 4
        v4Sqlite.close()

        // Abre com Room v5 usando exclusivamente as migrações não destrutivas
        val migratedDb = openFileDb(dbName)
        try {
            val writableDb = migratedDb.openHelper.writableDatabase
            val userVersion = writableDb.query("PRAGMA user_version").use { c ->
                c.moveToFirst()
                c.getInt(0)
            }
            assertEquals(7, userVersion)

            val v5Columns = mutableSetOf<String>()
            writableDb.query("PRAGMA table_info(`machines`)").use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    v5Columns.add(cursor.getString(nameIdx))
                }
            }
            assertTrue("Coluna max_installments deve existir após MIGRATION_4_5", v5Columns.contains("max_installments"))

            // Verifica preservação integral de todas as 11 tabelas
            assertEquals(1, migratedDb.preferenceDao().getAllPreferences().size)
            assertEquals("roxo_v4", migratedDb.preferenceDao().getByKey("tema_app")?.value)
            assertEquals(2, migratedDb.personDao().getAllPeople().size)
            assertEquals(1, migratedDb.productDao().getAllProducts().size)
            assertEquals(2, migratedDb.productDao().getAllProductParticipants().size)

            val migratedMachine = migratedDb.machineDao().getWithRatesById("m-v4")!!
            assertEquals("Stone V4", migratedMachine.machine.name)
            assertNull("Maquininhas antigas devem receber max_installments = NULL", migratedMachine.machine.maxInstallments)
            assertEquals(1, migratedDb.machineDao().getAllBrandRates().size)
            assertEquals(1, migratedDb.machineDao().getAllInstallmentRates().size)

            val migratedSale = migratedDb.saleDao().getWithDetailsById("sale-v4")!!
            assertEquals("Venda V4 Preservada", migratedSale.sale.note)
            assertEquals(9500L, migratedSale.sale.netTotalCents)
            assertEquals(1, migratedSale.items.size)
            assertEquals(2, migratedSale.items.single().participants.size)
            assertEquals(2, migratedSale.personTotals.size)

            // Confirma gravação de nova maquininha com limite 24x no banco migrado
            val machineRepo = RoomMachineRepository(migratedDb.machineDao(), clockMillis = { 1_730_000_200_000L })
            val newMachine = machineRepo.createMachine(
                MachineInputDraft(
                    name = "InfinitePay 24x",
                    debitRateBps = 139,
                    creditEnabled = true,
                    creditDefaultRateBps = 319,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(24 to 980),
                    maxInstallments = 24,
                ),
            )
            assertEquals(24, newMachine.maxInstallments)
        } finally {
            migratedDb.close()
        }

        // Reabre após close() para confirmar persistência estável pós-migração
        val reopenedDb = openFileDb(dbName)
        try {
            assertEquals(2, reopenedDb.machineDao().getAllMachines().size)
            assertNull(reopenedDb.machineDao().getWithRatesById("m-v4")!!.machine.maxInstallments)
            assertEquals(24, reopenedDb.machineDao().getAllMachines().first { it.name == "InfinitePay 24x" }.maxInstallments)
        } finally {
            reopenedDb.close()
        }
    }

    @Test
    fun `06 comparator, planner, decision validator, preview, executor, and rollback handle maxInstallments end-to-end`() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            // Mesma maquininha (mesmo id e normalizedName) divergindo apenas em maxInstallments (18 vs 12)
            val machineId = "mach-shared-1"
            backupDb.machineDao().upsertMachineWithRates(
                machine = MachineEntity(
                    id = machineId,
                    name = "Stone Central",
                    normalizedName = "stone central",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 350,
                    brandRatesEnabled = true,
                    installmentsEnabled = true,
                    createdAtEpochMs = 1_730_000_000_000L,
                    updatedAtEpochMs = 1_730_000_100_000L,
                    maxInstallments = 18,
                ),
                brandRates = listOf(MachineBrandRateEntity(machineId, "VISA", 320)),
                installmentRates = listOf(MachineInstallmentRateEntity(machineId, 12, 400)),
                isNew = true,
            )
            localDb.machineDao().upsertMachineWithRates(
                machine = MachineEntity(
                    id = machineId,
                    name = "Stone Central",
                    normalizedName = "stone central",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 350,
                    brandRatesEnabled = true,
                    installmentsEnabled = true,
                    createdAtEpochMs = 1_730_000_000_000L,
                    updatedAtEpochMs = 1_730_000_050_000L,
                    maxInstallments = 12,
                ),
                brandRates = listOf(MachineBrandRateEntity(machineId, "VISA", 320)),
                installmentRates = listOf(MachineInstallmentRateEntity(machineId, 12, 400)),
                isNew = true,
            )

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_200_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            assertTrue(readResult.isValid)

            // 1. Comparator detecta divergência em maxInstallments e marca como financeira/taxa
            val comparison = JsonBackupComparator.compare(readResult, localDb)
            val machineSummary = comparison.summaryFor(BackupCollectionType.MACHINES)
            assertEquals(1, machineSummary.conflictingCount)
            val conflict = machineSummary.conflictingRecords.single()
            assertTrue(conflict.divergentFields.contains("maxInstallments"))
            assertTrue(conflict.hasFinancialOrRateDivergence)

            // 2. Planner + Validator rejeitam maxInstallments inválido mesmo se ValidatedBackupData vier adulterado em memória
            val tamperedValidatedData = readResult.validatedData!!.let { data ->
                val tamperedMachines = data.machines.map { it.copy(maxInstallments = 25) }
                com.mixvendas3d.app.domain.export.ValidatedBackupData(
                    format = data.format,
                    backupSchemaVersion = data.backupSchemaVersion,
                    applicationId = data.applicationId,
                    roomSchemaVersion = data.roomSchemaVersion,
                    exportedAtEpochMs = data.exportedAtEpochMs,
                    preferences = data.preferences,
                    people = data.people,
                    products = data.products,
                    productParticipants = data.productParticipants,
                    machines = tamperedMachines,
                    machineBrandRates = data.machineBrandRates,
                    machineInstallmentRates = data.machineInstallmentRates,
                    sales = data.sales,
                    saleItems = data.saleItems,
                    saleItemParticipants = data.saleItemParticipants,
                    salePersonTotals = data.salePersonTotals,
                    declaredCounts = data.declaredCounts,
                )
            }
            val invalidPlan = JsonBackupMergePlanner.plan(tamperedValidatedData, localDb)
            val invalidMachineUnit = invalidPlan.relatedUnits.single()
            assertTrue(invalidMachineUnit.blockReasons.any { it.contains("limite máximo de parcelas inválido") })
            val invalidConflictKey = invalidPlan.conflictItems.single().let { "${it.collection.key}:${it.recordId}" }
            val invalidDecisionVal = JsonBackupMergeDecisionValidator.validate(
                plan = invalidPlan,
                validatedBackup = tamperedValidatedData,
                database = localDb,
                decisionsByConflictKey = mapOf(invalidConflictKey to MergeConflictResolution.USE_BACKUP),
            )
            assertFalse(invalidDecisionVal.isReadyForNextPhase)
            assertTrue(invalidDecisionVal.blockedOperationsCount > 0)

            // 3. Planner + DecisionValidator + PreviewBuilder + Executor com USE_BACKUP (12 -> 18) e teste de falha + rollback
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            assertEquals(1, plan.conflictItems.size)
            val conflictItem = plan.conflictItems.single()
            val conflictKey = "${conflictItem.collection.key}:${conflictItem.recordId}"
            val decisions = mapOf(conflictKey to MergeConflictResolution.USE_BACKUP)
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, decisions)
            assertTrue(validation.isReadyForNextPhase)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, decisions, validation, localDb)
            assertTrue(preview.isSafeToApply)
            assertEquals(18, preview.mergedCollections.machines.single().maxInstallments)

            // 3a. Simula falha no meio da transação -> rollback completo preserva maxInstallments = 12
            val abortedResult = JsonBackupMergeExecutor.executeWithHooksForTest(
                readResult = readResult,
                plan = plan,
                decisionsByConflictKey = decisions,
                decisionValidation = validation,
                previewResult = preview,
                executionContract = preview.executionContract,
                userConfirmed = true,
                database = localDb,
                hooks = ExecutorTestHooks(
                    duringMachineReplacement = { throw IllegalStateException("Falha simulada na transação") },
                ),
            )
            assertFalse(abortedResult.success)
            assertTrue(abortedResult.rolledBack)
            assertEquals(MergeExecutionFailureReason.TRANSACTION_ABORTED_OR_FAILED, abortedResult.failureReason)
            assertEquals(12, localDb.machineDao().getWithRatesById(machineId)!!.machine.maxInstallments)

            // 3b. Execução normal conclui e grava maxInstallments = 18
            val successResult = JsonBackupMergeExecutor.execute(
                readResult = readResult,
                plan = plan,
                decisionsByConflictKey = decisions,
                decisionValidation = validation,
                previewResult = preview,
                executionContract = preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )
            assertTrue(successResult.success)
            assertEquals(18, localDb.machineDao().getWithRatesById(machineId)!!.machine.maxInstallments)

            // 4. Alterar apenas maxInstallments localmente após gerar plano deve invalidar snapshot (fingerprint)
            val plan2 = JsonBackupMergePlanner.plan(readResult, localDb)
            val prefValidation2 = JsonBackupMergeDecisionValidator.validate(plan2, readResult, localDb, emptyMap())
            val preview2 = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan2, emptyMap(), prefValidation2, localDb)
            // Altera maxInstallments localmente de 18 -> 6 depois do planejamento
            localDb.machineDao().upsertMachineWithRates(
                machine = localDb.machineDao().getWithRatesById(machineId)!!.machine.copy(maxInstallments = 6),
                brandRates = listOf(MachineBrandRateEntity(machineId, "VISA", 320)),
                installmentRates = listOf(MachineInstallmentRateEntity(machineId, 12, 400)),
                isNew = false,
            )
            val staleExecResult = JsonBackupMergeExecutor.execute(
                readResult = readResult,
                plan = plan2,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = prefValidation2,
                previewResult = preview2,
                executionContract = preview2.executionContract,
                userConfirmed = true,
                database = localDb,
            )
            assertFalse(staleExecResult.success)
            assertEquals(MergeExecutionFailureReason.LOCAL_STATE_CHANGED_SINCE_PLANNING, staleExecResult.failureReason)
            assertEquals(6, localDb.machineDao().getWithRatesById(machineId)!!.machine.maxInstallments)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun `07 allowedInstallmentTiers, engine blocking of disallowed tiers, and VM explicit return to legacy preserve saved rates`() = runBlocking<Unit>(Dispatchers.IO) {
        val db = newInMemoryDb()
        try {
            val repo = RoomMachineRepository(db.machineDao(), clockMillis = { 1_730_000_000_000L })
            val legacyMachine = repo.createMachine(
                MachineInputDraft(
                    name = "Legado",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 5 to 250, 12 to 450, 18 to 700),
                    maxInstallments = null,
                ),
            )
            val machine18 = repo.createMachine(
                MachineInputDraft(
                    name = "Custom 18x",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(5 to 210, 7 to 290, 13 to 520, 18 to 720, 24 to 990),
                    maxInstallments = 18,
                ),
            )
            val machine24 = repo.createMachine(
                MachineInputDraft(
                    name = "Custom 24x",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(24 to 990),
                    maxInstallments = 24,
                ),
            )
            val noInstallments = repo.createMachine(
                MachineInputDraft(
                    name = "Sem Parcela",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                    maxInstallments = 18,
                ),
            )

            assertEquals(listOf(1, 2, 3, 4, 6, 12), legacyMachine.allowedInstallmentTiers())
            assertEquals((1..18).toList(), machine18.allowedInstallmentTiers())
            assertEquals((1..24).toList(), machine24.allowedInstallmentTiers())
            assertEquals(listOf(1), noInstallments.allowedInstallmentTiers())

            val product = Product(
                id = "prod-f1",
                name = "Peça F1",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantSplit("p-1", "Ana", 0, MoneyCents(10_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                createdAtEpochMs = 1_730_000_000_000L,
                updatedAtEpochMs = 1_730_000_000_000L,
            )

            // Legado bloqueia 5x, 18x e 24x
            listOf(5, 7, 13, 18, 24).forEach { disallowed ->
                assertThrows(SaleCalculationException::class.java) {
                    SaleFinancialEngine.calculate(
                        SaleCalculationInput(
                            items = listOf(SaleItemInput(product, 1)),
                            paymentMethod = SalePaymentMethod.CREDIT,
                            machine = legacyMachine,
                            installments = disallowed,
                        ),
                    )
                }
            }

            // Custom 18x aceita 5x, 7x, 13x, 18x e bloqueia 19x..24x
            listOf(5, 7, 13, 18).forEach { allowed ->
                val res = SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(product, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine18,
                        installments = allowed,
                    ),
                )
                assertEquals(allowed, res.machineSnapshot?.appliedInstallments)
            }
            listOf(19, 24).forEach { disallowed ->
                assertThrows(SaleCalculationException::class.java) {
                    SaleFinancialEngine.calculate(
                        SaleCalculationInput(
                            items = listOf(SaleItemInput(product, 1)),
                            paymentMethod = SalePaymentMethod.CREDIT,
                            machine = machine18,
                            installments = disallowed,
                        ),
                    )
                }
            }

            // Parcelamento desativado permanece em 1x
            val disabledRes = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(product, 1)),
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = noInstallments,
                    installments = 12,
                ),
            )
            assertEquals(1, disabledRes.machineSnapshot?.appliedInstallments)

            // Retorno explícito ao modo legado pelo MachinesViewModel persiste null e mantém taxa de 18x/24x salva
            @OptIn(ExperimentalCoroutinesApi::class)
            run {
                Dispatchers.setMain(UnconfinedTestDispatcher())
                val vm = MachinesViewModel(repo)
                val collectorJob = launch { vm.uiState.collect { } }
                try {
                    vm.openEditMachineEditor(machine18)
                    withTimeout(5_000L) {
                        vm.uiState.first { it.editor.isOpen && it.editor.maxInstallments == 18 }
                    }
                    vm.onEditorMaxInstallmentsChanged(null)
                    val dirtyState = withTimeout(5_000L) {
                        vm.uiState.first { it.editor.maxInstallments == null }
                    }
                    assertNull(dirtyState.editor.maxInstallments)
                    vm.saveMachine()
                    withTimeout(5_000L) {
                        vm.uiState.first { !it.editor.isOpen }
                    }
                    val updated = repo.observeMachines().first().first { it.id == machine18.id }
                    assertNull(updated.maxInstallments)
                    assertEquals(listOf(1, 2, 3, 4, 6, 12), updated.allowedInstallmentTiers())
                    assertEquals(720, updated.installmentAddonsBps[18])
                    assertEquals(990, updated.installmentAddonsBps[24])
                } finally {
                    collectorJob.cancel()
                    Dispatchers.resetMain()
                }
            }
        } finally {
            db.close()
        }
    }
}
