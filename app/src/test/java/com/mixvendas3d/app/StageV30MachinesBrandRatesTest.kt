package com.mixvendas3d.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.MachineEntity
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.domain.calculation.CustomCardBrandInput
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.MachineValidator
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.CustomCardBrandRate
import com.mixvendas3d.app.domain.models.Machine
import com.mixvendas3d.app.domain.models.PreviewPaymentMethod
import com.mixvendas3d.app.util.RateFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StageV30MachinesBrandRatesTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: RoomMachineRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomMachineRepository(machineDao = db.machineDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun test01RoomMigration() {
        runBlocking(Dispatchers.IO) {
            val dbName = "test_migration_v6_v7.db"
            context.deleteDatabase(dbName)

            val v6Sqlite = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(
                    AppDatabase.MIGRATION_1_2,
                    AppDatabase.MIGRATION_2_3,
                    AppDatabase.MIGRATION_3_4,
                    AppDatabase.MIGRATION_4_5,
                    AppDatabase.MIGRATION_5_6,
                )
                .allowMainThreadQueries()
                .build()

            val now = System.currentTimeMillis()
            v6Sqlite.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO machines (id, name, normalized_name, debit_rate_bps, credit_enabled, credit_default_rate_bps, brand_rates_enabled, installments_enabled, created_at_epoch_ms, updated_at_epoch_ms)
                VALUES ('m1', 'Maquininha V6', 'maquininha v6', 150, 1, 350, 1, 1, $now, $now)
                """.trimIndent(),
            )
            v6Sqlite.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO machine_brand_rates (machine_id, brand_key, rate_bps)
                VALUES ('m1', 'VISA', 300)
                """.trimIndent(),
            )
            v6Sqlite.close()

            val v7Db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
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

            val userVersion = v7Db.openHelper.readableDatabase.version
            assertEquals(7, userVersion)

            val rates = v7Db.machineDao().getAllBrandRates()
            assertEquals(1, rates.size)
            val visaRate = rates.single()
            assertEquals("m1", visaRate.machineId)
            assertEquals("VISA", visaRate.brandKey)
            assertEquals(300, visaRate.rateBps)
            assertNull(visaRate.debitRateBps)

            v7Db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun test02CustomBrandWithCreditAndDebitRates() {
        runBlocking(Dispatchers.IO) {
            val customKey = CustomBrandKeyCodec.encodeKey("Alelo")
            val draft = MachineInputDraft(
                name = "Rede Pro",
                debitRateBps = 180,
                creditEnabled = true,
                creditDefaultRateBps = 320,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.VISA to 300),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(2 to 100),
                customBrandRates = listOf(
                    CustomCardBrandInput(
                        name = "Alelo",
                        creditRateBps = 250,
                        debitRateBps = 120,
                        key = customKey,
                    ),
                ),
            )

            val created = repo.createMachine(draft)
            assertEquals("Rede Pro", created.name)
            assertEquals(1, created.customBrandRates.size)

            val custom = created.customBrandRates.single()
            assertEquals("Alelo", custom.name)
            assertEquals(250, custom.creditRateBps)
            assertEquals(120, custom.debitRateBps)

            // Effective automatic rate check
            assertEquals(120, created.effectiveAutomaticRateBpsForKey(PreviewPaymentMethod.DEBIT, customKey, 1))
            assertEquals(250, created.effectiveAutomaticRateBpsForKey(PreviewPaymentMethod.CREDIT, customKey, 1))
            assertEquals(350, created.effectiveAutomaticRateBpsForKey(PreviewPaymentMethod.CREDIT, customKey, 2))
        }
    }

    @Test
    fun test03CustomBrandDebitOnlyFallback() {
        runBlocking(Dispatchers.IO) {
            val customKey = CustomBrandKeyCodec.encodeKey("VR Benefícios")
            val draft = MachineInputDraft(
                name = "PagBank",
                debitRateBps = 190,
                creditEnabled = true,
                creditDefaultRateBps = 380,
                brandRatesEnabled = true,
                brandRatesBps = emptyMap(),
                installmentsEnabled = false,
                installmentAddonsBps = emptyMap(),
                customBrandRates = listOf(
                    CustomCardBrandInput(
                        name = "VR Benefícios",
                        creditRateBps = null,
                        debitRateBps = 110,
                        key = customKey,
                    ),
                ),
            )

            val created = repo.createMachine(draft)
            val custom = created.customBrandRates.single()
            assertNull(custom.creditRateBps)
            assertEquals(110, custom.debitRateBps)

            assertEquals(110, created.effectiveAutomaticRateBpsForKey(PreviewPaymentMethod.DEBIT, customKey, 1))
            assertEquals(380, created.effectiveAutomaticRateBpsForKey(PreviewPaymentMethod.CREDIT, customKey, 1))
        }
    }

    @Test
    fun test04CustomBrandCreditOnlyFallback() {
        runBlocking(Dispatchers.IO) {
            val customKey = CustomBrandKeyCodec.encodeKey("Hipercard Especial")
            val draft = MachineInputDraft(
                name = "Getnet",
                debitRateBps = 175,
                creditEnabled = true,
                creditDefaultRateBps = 350,
                brandRatesEnabled = true,
                brandRatesBps = emptyMap(),
                installmentsEnabled = false,
                installmentAddonsBps = emptyMap(),
                customBrandRates = listOf(
                    CustomCardBrandInput(
                        name = "Hipercard Especial",
                        creditRateBps = 290,
                        debitRateBps = null,
                        key = customKey,
                    ),
                ),
            )

            val created = repo.createMachine(draft)
            val custom = created.customBrandRates.single()
            assertEquals(290, custom.creditRateBps)
            assertNull(custom.debitRateBps)

            assertEquals(175, created.effectiveAutomaticRateBpsForKey(PreviewPaymentMethod.DEBIT, customKey, 1))
            assertEquals(290, created.effectiveAutomaticRateBpsForKey(PreviewPaymentMethod.CREDIT, customKey, 1))
        }
    }

    @Test
    fun test05ZeroRateHandling() {
        runBlocking(Dispatchers.IO) {
            val zeroRateBps = MachineValidator.parseOptionalRateInput("Taxa Zera", "0,00")
            assertNotNull(zeroRateBps)
            assertEquals(0, zeroRateBps)

            val blankRateBps = MachineValidator.parseOptionalRateInput("Taxa Vazia", "  ")
            assertNull(blankRateBps)
        }
    }

    @Test
    fun test06UncheckedBrandRatesAppliesDefaults() {
        runBlocking(Dispatchers.IO) {
            val customKey = CustomBrandKeyCodec.encodeKey("Sodexo")
            val draft = MachineInputDraft(
                name = "Stone Pro",
                debitRateBps = 140,
                creditEnabled = true,
                creditDefaultRateBps = 310,
                brandRatesEnabled = false,
                brandRatesBps = emptyMap(),
                installmentsEnabled = false,
                installmentAddonsBps = emptyMap(),
                customBrandRates = listOf(
                    CustomCardBrandInput(
                        name = "Sodexo",
                        creditRateBps = 100,
                        debitRateBps = 50,
                        key = customKey,
                    ),
                ),
            )

            val created = repo.createMachine(draft)
            assertFalse(created.brandRatesEnabled)

            assertEquals(1, created.customBrandRates.size)

            assertEquals(140, created.effectiveAutomaticRateBpsForKey(PreviewPaymentMethod.DEBIT, customKey, 1))
            assertEquals(310, created.effectiveAutomaticRateBpsForKey(PreviewPaymentMethod.CREDIT, customKey, 1))
        }
    }

    @Test
    fun test07JsonBackupExportAndImportDebitRates() {
        runBlocking(Dispatchers.IO) {
            val customKey = CustomBrandKeyCodec.encodeKey("Ticket")
            val draft = MachineInputDraft(
                name = "Cielo Lio",
                debitRateBps = 160,
                creditEnabled = true,
                creditDefaultRateBps = 340,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.MASTERCARD to 280),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(2 to 80),
                customBrandRates = listOf(
                    CustomCardBrandInput(
                        name = "Ticket",
                        creditRateBps = 210,
                        debitRateBps = 90,
                        key = customKey,
                    ),
                ),
            )
            repo.createMachine(draft)

            val exportResult = JsonBackupExporter.export(db)
            val readResult = JsonBackupReader.readAndValidate(exportResult.utf8Bytes)
            assertTrue(readResult.isValid)

            val data = readResult.validatedData!!
            val brandRateDto = data.machineBrandRates.firstOrNull { it.brandKey == customKey }
            assertNotNull(brandRateDto)
            assertEquals(210, brandRateDto?.rateBps)
            assertEquals(90, brandRateDto?.debitRateBps)
        }
    }
}
