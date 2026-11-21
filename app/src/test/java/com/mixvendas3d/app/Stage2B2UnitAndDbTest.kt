package com.mixvendas3d.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.data.repository.RoomPreferenceRepository
import com.mixvendas3d.app.domain.calculation.CatalogValidationException
import com.mixvendas3d.app.domain.calculation.CatalogValidator
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.MachineValidationException
import com.mixvendas3d.app.domain.calculation.MachineValidator
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.InstallmentTiers
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PreviewPaymentMethod
import com.mixvendas3d.app.util.CurrencyFormatter
import com.mixvendas3d.app.util.MonetaryInputMask
import com.mixvendas3d.app.util.RateFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage2B2UnitAndDbTest {

    @Test
    fun `rate parser converts percentages to exact integer basis points and rejects invalid formats`() {
        assertEquals(0, RateFormatter.parsePercentageToBps("0"))
        assertEquals(0, RateFormatter.parsePercentageToBps("0,00%"))
        assertEquals(190, RateFormatter.parsePercentageToBps("1,90"))
        assertEquals(190, RateFormatter.parsePercentageToBps("1.9%"))
        assertEquals(280, RateFormatter.parsePercentageToBps(" 2,80 % "))
        assertEquals(10_000, RateFormatter.parsePercentageToBps("100,00"))
        assertEquals(-150, RateFormatter.parsePercentageToBps("-1,50"))
        assertEquals(10_050, RateFormatter.parsePercentageToBps("100,50"))

        assertNull(RateFormatter.parsePercentageToBps(""))
        assertNull(RateFormatter.parsePercentageToBps("abc"))
        assertNull(RateFormatter.parsePercentageToBps("1,234"))
        assertNull(RateFormatter.parsePercentageToBps("1,2,3"))
        assertNull(RateFormatter.parsePercentageToBps("1,"))
        assertEquals("1,90%", RateFormatter.formatBpsAsPercent(190))
        assertEquals("0,00%", RateFormatter.formatBpsAsPercent(0))
        assertEquals("100,00%", RateFormatter.formatBpsAsPercent(10_000))
    }

    @Test
    fun `machine validator enforces bounds 0 to 100 percent and base plus installment addon at most 100 percent`() {
        // Limites válidos 0% e 100%
        val maxValid = MachineValidator.validateMachineInput(
            MachineInputDraft(
                name = "  Ton Max ",
                debitRateBps = 0,
                creditEnabled = true,
                creditDefaultRateBps = 9_000,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.VISA to 9_500),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(1 to 0, 2 to 500),
            ),
        )
        assertEquals("Ton Max", maxValid.name)
        assertEquals(9_500, maxValid.brandRatesBps[CardBrand.VISA])

        // Rejeita taxa negativa
        assertTrue(
            runCatching {
                MachineValidator.validateMachineInput(
                    MachineInputDraft(
                        name = "Negativa",
                        debitRateBps = -10,
                        creditEnabled = false,
                        creditDefaultRateBps = 0,
                        brandRatesEnabled = false,
                        brandRatesBps = emptyMap(),
                        installmentsEnabled = false,
                        installmentAddonsBps = emptyMap(),
                    ),
                )
            }.exceptionOrNull() is MachineValidationException,
        )

        // Rejeita taxa acima de 100%
        assertTrue(
            runCatching {
                MachineValidator.validateMachineInput(
                    MachineInputDraft(
                        name = "Acima",
                        debitRateBps = 10_001,
                        creditEnabled = false,
                        creditDefaultRateBps = 0,
                        brandRatesEnabled = false,
                        brandRatesBps = emptyMap(),
                        installmentsEnabled = false,
                        installmentAddonsBps = emptyMap(),
                    ),
                )
            }.exceptionOrNull() is MachineValidationException,
        )

        // Rejeita soma taxa padrão + adicional > 100% (95% + 6% = 101%)
        assertTrue(
            runCatching {
                MachineValidator.validateMachineInput(
                    MachineInputDraft(
                        name = "Soma Padrão Estourada",
                        debitRateBps = 190,
                        creditEnabled = true,
                        creditDefaultRateBps = 9_500,
                        brandRatesEnabled = false,
                        brandRatesBps = emptyMap(),
                        installmentsEnabled = true,
                        installmentAddonsBps = mapOf(12 to 600),
                    ),
                )
            }.exceptionOrNull() is MachineValidationException,
        )

        // Rejeita soma taxa de bandeira + adicional > 100% (96% + 5% = 101%)
        assertTrue(
            runCatching {
                MachineValidator.validateMachineInput(
                    MachineInputDraft(
                        name = "Soma Bandeira Estourada",
                        debitRateBps = 190,
                        creditEnabled = true,
                        creditDefaultRateBps = 200,
                        brandRatesEnabled = true,
                        brandRatesBps = mapOf(CardBrand.AMEX to 9_600),
                        installmentsEnabled = true,
                        installmentAddonsBps = mapOf(6 to 500),
                    ),
                )
            }.exceptionOrNull() is MachineValidationException,
        )
    }

    @Test
    fun `machine crud creates, edits keeping id, blocks normalized duplicates, deletes, and resolves brand and installment rules`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RoomMachineRepository(db.machineDao())

        val created = repo.createMachine(
            MachineInputDraft(
                name = "  Ton Pro  ",
                debitRateBps = 190,
                creditEnabled = true,
                creditDefaultRateBps = 280,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(
                    CardBrand.VISA to 250,
                    CardBrand.MASTERCARD to 270,
                ),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(
                    1 to 0,
                    2 to 110,
                    3 to 160,
                    4 to 210,
                    6 to 320,
                    12 to 650,
                ),
            ),
        )
        assertEquals("Ton Pro", created.name)
        assertEquals(190, created.debitRateBps)

        // Bandeira preenchida substitui padrão; bandeira vazia (Elo) usa padrão (2,80%)
        // Parcelamento soma sobre a taxa base: 2,80% + 1,60% = 4,40% (Elo 3x) e 2,50% + 1,60% = 4,10% (Visa 3x)
        assertEquals(440, created.effectiveAutomaticRateBps(PreviewPaymentMethod.CREDIT, CardBrand.ELO, 3))
        assertEquals(410, created.effectiveAutomaticRateBps(PreviewPaymentMethod.CREDIT, CardBrand.VISA, 3))
        assertEquals(190, created.effectiveAutomaticRateBps(PreviewPaymentMethod.DEBIT, null, 1))
        assertNull(created.effectiveAutomaticRateBps(PreviewPaymentMethod.PIX, null, 1))

        // Bloqueia duplicado ignorando maiúsculas/minúsculas e espaços externos
        assertTrue(
            runCatching {
                repo.createMachine(
                    MachineInputDraft(
                        name = "  ton PRO ",
                        debitRateBps = 150,
                        creditEnabled = false,
                        creditDefaultRateBps = 0,
                        brandRatesEnabled = false,
                        brandRatesBps = emptyMap(),
                        installmentsEnabled = false,
                        installmentAddonsBps = emptyMap(),
                    ),
                )
            }.exceptionOrNull() is MachineValidationException,
        )

        // Cria segunda maquininha e impede renomear para duplicado
        val second = repo.createMachine(
            MachineInputDraft(
                name = "InfinitePay",
                debitRateBps = 138,
                creditEnabled = true,
                creditDefaultRateBps = 299,
                brandRatesEnabled = false,
                brandRatesBps = emptyMap(),
                installmentsEnabled = false,
                installmentAddonsBps = emptyMap(),
            ),
        )
        assertNotEquals(created.id, second.id)
        assertTrue(
            runCatching {
                repo.updateMachine(
                    second.id,
                    MachineInputDraft(
                        name = "TON PRO",
                        debitRateBps = 138,
                        creditEnabled = true,
                        creditDefaultRateBps = 299,
                        brandRatesEnabled = false,
                        brandRatesBps = emptyMap(),
                        installmentsEnabled = false,
                        installmentAddonsBps = emptyMap(),
                    ),
                )
            }.exceptionOrNull() is MachineValidationException,
        )

        // Editar preservando ID
        val updated = repo.updateMachine(
            created.id,
            MachineInputDraft(
                name = "Ton Mega",
                debitRateBps = 175,
                creditEnabled = true,
                creditDefaultRateBps = 290,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.VISA to 245, CardBrand.AMEX to 360),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(1 to 0, 2 to 100, 3 to 150, 4 to 200, 6 to 300, 12 to 600),
            ),
        )
        assertEquals(created.id, updated.id)
        assertEquals("Ton Mega", updated.name)
        assertEquals(175, updated.debitRateBps)
        assertEquals(510, updated.effectiveAutomaticRateBps(PreviewPaymentMethod.CREDIT, CardBrand.AMEX, 3))

        // Excluir sem dependências
        repo.deleteMachine(second.id)
        assertEquals(1, repo.observeMachines().first().size)

        db.close()
    }

    @Test
    fun `disabling credit, brand rates, or installments preserves saved rates for re-enabling and persists after reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "reopen.db"
        context.deleteDatabase(dbName)

        val db1 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        val repo1 = RoomMachineRepository(db1.machineDao())

        val initial = repo1.createMachine(
            MachineInputDraft(
                name = "Mercado Pago Point",
                debitRateBps = 199,
                creditEnabled = true,
                creditDefaultRateBps = 309,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(
                    CardBrand.VISA to 285,
                    CardBrand.ELO to 335,
                ),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(
                    1 to 0,
                    2 to 120,
                    3 to 180,
                    4 to 240,
                    6 to 360,
                    12 to 720,
                ),
            ),
        )

        // Desativar crédito deve ocultar/impedir crédito sem apagar tarifas salvas
        val creditOff = repo1.setCreditEnabled(initial.id, creditEnabled = false)
        assertFalse(creditOff.creditEnabled)
        assertNull(creditOff.effectiveAutomaticRateBps(PreviewPaymentMethod.CREDIT, CardBrand.VISA, 3))
        assertEquals(199, creditOff.effectiveAutomaticRateBps(PreviewPaymentMethod.DEBIT, null, 1))
        assertEquals(309, creditOff.creditDefaultRateBps)
        assertEquals(285, creditOff.brandRatesBps[CardBrand.VISA])
        assertEquals(720, creditOff.installmentAddonsBps[12])

        // Reativar crédito restaura tudo
        val creditOn = repo1.setCreditEnabled(initial.id, creditEnabled = true)
        assertTrue(creditOn.creditEnabled)
        assertEquals(465, creditOn.effectiveAutomaticRateBps(PreviewPaymentMethod.CREDIT, CardBrand.VISA, 3))

        // Desativar bandeiras e parcelamento ao editar preserva valores no Room
        val featuresOff = repo1.updateMachine(
            initial.id,
            MachineInputDraft(
                name = creditOn.name,
                debitRateBps = creditOn.debitRateBps,
                creditEnabled = true,
                creditDefaultRateBps = creditOn.creditDefaultRateBps,
                brandRatesEnabled = false,
                brandRatesBps = creditOn.brandRatesBps,
                installmentsEnabled = false,
                installmentAddonsBps = creditOn.installmentAddonsBps,
            ),
        )
        assertEquals(309, featuresOff.effectiveAutomaticRateBps(PreviewPaymentMethod.CREDIT, CardBrand.VISA, 6))
        db1.close()

        // Reabre banco e confirma persistência + reativação restaurando tarifas anteriores
        val db2 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        val repo2 = RoomMachineRepository(db2.machineDao())
        val reloaded = repo2.observeMachines().first().single()
        assertEquals(initial.id, reloaded.id)
        assertEquals(285, reloaded.brandRatesBps[CardBrand.VISA])
        assertEquals(335, reloaded.brandRatesBps[CardBrand.ELO])
        assertEquals(360, reloaded.installmentAddonsBps[6])

        val reEnabled = repo2.updateMachine(
            reloaded.id,
            MachineInputDraft(
                name = reloaded.name,
                debitRateBps = reloaded.debitRateBps,
                creditEnabled = true,
                creditDefaultRateBps = reloaded.creditDefaultRateBps,
                brandRatesEnabled = true,
                brandRatesBps = reloaded.brandRatesBps,
                installmentsEnabled = true,
                installmentAddonsBps = reloaded.installmentAddonsBps,
            ),
        )
        assertEquals(695, reEnabled.effectiveAutomaticRateBps(PreviewPaymentMethod.CREDIT, CardBrand.ELO, 6))

        db2.close()
        assertTrue(context.deleteDatabase(dbName))
    }

    @Test
    fun `migration 2 to 3 preserves app_preferences, people, products, and participants while adding machines`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "m23.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Cria banco exatamente na versão 2 com dados de app_preferences, people, products e product_participants
        val v2Sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v2Sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `app_preferences` (
                `pref_key` TEXT NOT NULL,
                `pref_value` TEXT NOT NULL,
                `updated_at_epoch_ms` INTEGER NOT NULL,
                PRIMARY KEY(`pref_key`)
            )
            """.trimIndent(),
        )
        v2Sqlite.execSQL(
            "INSERT INTO `app_preferences` (`pref_key`, `pref_value`, `updated_at_epoch_ms`) VALUES ('chave_v2', 'Preservado V2', 1700000100000)",
        )
        v2Sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `people` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `normalized_name` TEXT NOT NULL,
                `color_index` INTEGER NOT NULL,
                `created_at_epoch_ms` INTEGER NOT NULL,
                `updated_at_epoch_ms` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        v2Sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_people_normalized_name` ON `people` (`normalized_name`)")
        v2Sqlite.execSQL(
            "INSERT INTO `people` (`id`, `name`, `normalized_name`, `color_index`, `created_at_epoch_ms`, `updated_at_epoch_ms`) VALUES ('person-1', 'Mariana', 'mariana', 0, 1700000100000, 1700000100000)",
        )
        v2Sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `products` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `price_cents` INTEGER NOT NULL,
                `active` INTEGER NOT NULL,
                `fee_responsibility_mode` TEXT NOT NULL,
                `fee_owner_person_id` TEXT,
                `created_at_epoch_ms` INTEGER NOT NULL,
                `updated_at_epoch_ms` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`fee_owner_person_id`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        v2Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_products_fee_owner_person_id` ON `products` (`fee_owner_person_id`)")
        v2Sqlite.execSQL(
            "INSERT INTO `products` (`id`, `name`, `price_cents`, `active`, `fee_responsibility_mode`, `fee_owner_person_id`, `created_at_epoch_ms`, `updated_at_epoch_ms`) VALUES ('prod-1', 'Suporte 3D', 4500, 1, 'INDIVIDUAL', 'person-1', 1700000100000, 1700000100000)",
        )
        v2Sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `product_participants` (
                `product_id` TEXT NOT NULL,
                `person_id` TEXT NOT NULL,
                `amount_cents` INTEGER NOT NULL,
                `sort_order` INTEGER NOT NULL,
                PRIMARY KEY(`product_id`, `person_id`),
                FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`person_id`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        v2Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_product_participants_product_id` ON `product_participants` (`product_id`)")
        v2Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_product_participants_person_id` ON `product_participants` (`person_id`)")
        v2Sqlite.execSQL(
            "INSERT INTO `product_participants` (`product_id`, `person_id`, `amount_cents`, `sort_order`) VALUES ('prod-1', 'person-1', 4500, 0)",
        )
        v2Sqlite.version = 2
        v2Sqlite.close()

        // Abre com Room v3 + MIGRATION_1_2 + MIGRATION_2_3
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()

        val prefRepo = RoomPreferenceRepository(migratedDb.preferenceDao())
        assertEquals("Preservado V2", prefRepo.getPreference("chave_v2")?.value)

        val catalogRepo = RoomCatalogRepository(migratedDb.personDao(), migratedDb.productDao())
        val people = catalogRepo.observePeople().first()
        val products = catalogRepo.observeAllProducts().first()
        assertEquals(1, people.size)
        assertEquals("Mariana", people.first().name)
        assertEquals(1, products.size)
        assertEquals("Suporte 3D", products.first().name)
        assertEquals(4500L, products.first().price.rawCents)
        assertEquals("Mariana", products.first().participants.first().personName)

        // Confirma que a nova tabela de maquininhas funciona normalmente após migração
        val machineRepo = RoomMachineRepository(migratedDb.machineDao())
        val savedMachine = machineRepo.createMachine(
            MachineInputDraft(
                name = "Stone Pro",
                debitRateBps = 165,
                creditEnabled = true,
                creditDefaultRateBps = 295,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.VISA to 275),
                installmentsEnabled = true,
                installmentAddonsBps = InstallmentTiers.SUPPORTED.associateWith { it * 50 },
            ),
        )
        assertEquals(1, machineRepo.observeMachines().first().size)
        assertEquals("Stone Pro", savedMachine.name)

        migratedDb.close()
        assertTrue(context.deleteDatabase(dbName))
    }

    @Test
    fun `full migration path 1 to 2 to 3 upgrades cleanly without losing data`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "m13.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        val v1Sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v1Sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `app_preferences` (
                `pref_key` TEXT NOT NULL,
                `pref_value` TEXT NOT NULL,
                `updated_at_epoch_ms` INTEGER NOT NULL,
                PRIMARY KEY(`pref_key`)
            )
            """.trimIndent(),
        )
        v1Sqlite.execSQL(
            "INSERT INTO `app_preferences` (`pref_key`, `pref_value`, `updated_at_epoch_ms`) VALUES ('origem_v1', 'Intacto Ate V3', 1700000000001)",
        )
        v1Sqlite.version = 1
        v1Sqlite.close()

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()

        val pref = RoomPreferenceRepository(migratedDb.preferenceDao()).getPreference("origem_v1")
        assertNotNull(pref)
        assertEquals("Intacto Ate V3", pref?.value)

        val catalogRepo = RoomCatalogRepository(migratedDb.personDao(), migratedDb.productDao())
        val p = catalogRepo.createPerson("Integrado V3")
        catalogRepo.createProduct(
            name = "Teste V3",
            price = MoneyCents(1_000L),
            active = true,
            participants = listOf(ProductParticipantDraft(p.id, MoneyCents(1_000L))),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )

        val machineRepo = RoomMachineRepository(migratedDb.machineDao())
        machineRepo.createMachine(
            MachineInputDraft(
                name = "PagSeguro",
                debitRateBps = 199,
                creditEnabled = false,
                creditDefaultRateBps = 0,
                brandRatesEnabled = false,
                brandRatesBps = emptyMap(),
                installmentsEnabled = false,
                installmentAddonsBps = emptyMap(),
            ),
        )
        assertEquals(1, catalogRepo.observeAllProducts().first().size)
        assertEquals(1, machineRepo.observeMachines().first().size)

        migratedDb.close()
        assertTrue(context.deleteDatabase(dbName))
    }

    @Test
    fun `monetary input mask formats digits into exact integer brl centavos without float rounding`() {
        assertEquals("", MonetaryInputMask.formatDigitsInput(""))
        assertEquals("", MonetaryInputMask.formatDigitsInput("0"))
        assertEquals("", MonetaryInputMask.formatDigitsInput("0,0"))
        assertEquals("0,01", MonetaryInputMask.formatDigitsInput("1"))
        assertEquals("0,22", MonetaryInputMask.formatDigitsInput("22"))
        assertEquals("22,00", MonetaryInputMask.formatDigitsInput("2200"))
        assertEquals("1.250,50", MonetaryInputMask.formatDigitsInput("125050"))
        assertEquals("1.250,50", MonetaryInputMask.formatDigitsInput("R$ 1.250,50"))
        assertEquals("999.999.999,99", MonetaryInputMask.formatDigitsInput("9999999999999"))

        assertEquals(125_050L, CurrencyFormatter.parseBrlToCents(MonetaryInputMask.formatDigitsInput("125050")))
        assertEquals(2_200L, CurrencyFormatter.parseBrlToCents(MonetaryInputMask.formatCentsForMaskedInput(2_200L)))
    }

    @Test
    fun `product split validator enforces manual reais sum equal to product price without percentage conversion`() {
        val valid = CatalogValidator.validateProductInput(
            rawName = "Pastel de queijo",
            price = MoneyCents(2_200L),
            active = true,
            participants = listOf(
                ProductParticipantDraft("ana", MoneyCents(1_500L)),
                ProductParticipantDraft("carlos", MoneyCents(700L)),
            ),
            existingPersonIds = setOf("ana", "carlos"),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        assertEquals(2_200L, valid.participants.sumOf { it.amount.rawCents })
        assertEquals(1_500L, valid.participants[0].amount.rawCents)
        assertEquals(700L, valid.participants[1].amount.rawCents)

        val mismatchError = runCatching {
            CatalogValidator.validateProductInput(
                rawName = "Pastel de queijo",
                price = MoneyCents(2_200L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft("ana", MoneyCents(1_500L)),
                    ProductParticipantDraft("carlos", MoneyCents(600L)),
                ),
                existingPersonIds = setOf("ana", "carlos"),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
        }.exceptionOrNull()
        assertTrue(mismatchError is CatalogValidationException)
    }
}
