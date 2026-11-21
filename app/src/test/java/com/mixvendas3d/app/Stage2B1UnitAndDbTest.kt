package com.mixvendas3d.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.dao.ProductDeletionResult
import com.mixvendas3d.app.data.local.entity.SaleEntity
import com.mixvendas3d.app.data.local.entity.SaleItemEntity
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomPreferenceRepository
import com.mixvendas3d.app.domain.calculation.CatalogValidationException
import com.mixvendas3d.app.domain.calculation.CatalogValidator
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.util.CurrencyFormatter
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
class Stage2B1UnitAndDbTest {

    @Test
    fun `brl parser converts monetary strings to exact integer cents without float`() {
        assertEquals(2_200L, CurrencyFormatter.parseBrlToCents("22,00"))
        assertEquals(2_200L, CurrencyFormatter.parseBrlToCents("R$ 22"))
        assertEquals(123_456L, CurrencyFormatter.parseBrlToCents("1.234,56"))
        assertEquals(500L, CurrencyFormatter.parseBrlToCents("5,0"))
        assertEquals(-550L, CurrencyFormatter.parseBrlToCents("-5,50"))
        assertNull(CurrencyFormatter.parseBrlToCents(""))
        assertNull(CurrencyFormatter.parseBrlToCents("abc"))
        assertNull(CurrencyFormatter.parseBrlToCents("10,123"))
    }

    @Test
    fun `people crud allows unlimited people, blocks duplicates ignoring case and spaces, preserves id on rename, and blocks unsafe delete`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RoomCatalogRepository(db.personDao(), db.productDao())

        val p1 = repo.createPerson("  Carlos Silva  ")
        val p2 = repo.createPerson("Ana Paula")
        val p3 = repo.createPerson("João Pedro")
        val p4 = repo.createPerson("Mariana")

        assertEquals(4, repo.observePeople().first().size)
        assertEquals("Carlos Silva", p1.name)
        assertNotEquals(p1.id, p2.id)

        // Bloqueia duplicados ignorando maiúsculas/minúsculas e espaços externos
        val duplicateResult = runCatching { repo.createPerson("  carlos silva ") }
        assertTrue(duplicateResult.exceptionOrNull() is CatalogValidationException)

        // Permite criar produto vinculado a p1 e p2 (Exemplo 1: R$ 22,00 = 17,00 + 5,00)
        val prodA = repo.createProduct(
            name = "Produto A",
            price = MoneyCents(2_200L),
            active = true,
            participants = listOf(
                ProductParticipantDraft(p1.id, MoneyCents(1_700L)),
                ProductParticipantDraft(p2.id, MoneyCents(500L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerPersonId = p1.id,
        )

        // Bloqueia exclusão de pessoa vinculada a produto existente
        val unsafeDelete = runCatching { repo.deletePerson(p1.id) }
        assertTrue(unsafeDelete.exceptionOrNull() is CatalogValidationException)
        assertEquals(4, repo.observePeople().first().size)

        // Permite excluir pessoa sem vínculo (p4)
        repo.deletePerson(p4.id)
        assertEquals(3, repo.observePeople().first().size)

        // Renomear preserva ID e propaga automaticamente para produtos vinculados
        val renamed = repo.renamePerson(p1.id, "Carlos Eduardo")
        assertEquals(p1.id, renamed.id)
        assertEquals("Carlos Eduardo", renamed.name)

        val refreshedProducts = repo.observeAllProducts().first()
        val refreshedProdA = refreshedProducts.first { it.id == prodA.id }
        assertEquals(p1.id, refreshedProdA.participants[0].personId)
        assertEquals("Carlos Eduardo", refreshedProdA.participants[0].personName)

        // Bloqueia renomear para nome já usado por outro cadastro
        val duplicateRename = runCatching { repo.renamePerson(p3.id, "ANA PAULA") }
        assertTrue(duplicateRename.exceptionOrNull() is CatalogValidationException)

        db.close()
    }

    @Test
    fun `products validate example 1, example 2, negative values, duplicates, missing people, fee owner, and active toggle`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RoomCatalogRepository(db.personDao(), db.productDao())

        val p1 = repo.createPerson("Pessoa 1")
        val p2 = repo.createPerson("Pessoa 2")
        val p3 = repo.createPerson("Pessoa 3")

        // Exemplo 1: Produto A R$ 22,00 -> Pessoa 1 R$ 17,00, Pessoa 2 R$ 5,00
        val prodA = repo.createProduct(
            name = "Produto A",
            price = MoneyCents(2_200L),
            active = true,
            participants = listOf(
                ProductParticipantDraft(p1.id, MoneyCents(1_700L)),
                ProductParticipantDraft(p2.id, MoneyCents(500L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerPersonId = p2.id,
        )
        assertEquals(2_200L, prodA.price.rawCents)
        assertEquals(1_700L, prodA.participants[0].amount.rawCents)
        assertEquals(500L, prodA.participants[1].amount.rawCents)
        assertEquals(FeeResponsibilityMode.INDIVIDUAL, prodA.feeResponsibilityMode)
        assertEquals(p2.id, prodA.feeOwnerPersonId)

        // Exemplo 2: Produto B R$ 22,00 -> Pessoa 1 R$ 5,00, Pessoa 2 R$ 17,00
        val prodB = repo.createProduct(
            name = "Produto B",
            price = MoneyCents(2_200L),
            active = true,
            participants = listOf(
                ProductParticipantDraft(p1.id, MoneyCents(500L)),
                ProductParticipantDraft(p2.id, MoneyCents(1_700L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
            feeOwnerPersonId = null,
        )
        assertEquals(500L, prodB.participants[0].amount.rawCents)
        assertEquals(1_700L, prodB.participants[1].amount.rawCents)
        assertEquals(FeeResponsibilityMode.EQUAL, prodB.feeResponsibilityMode)

        // Regras inválidas obrigatórias
        // 1) Soma inconsistente
        assertTrue(
            runCatching {
                repo.createProduct(
                    name = "Inconsistente",
                    price = MoneyCents(2_200L),
                    active = true,
                    participants = listOf(
                        ProductParticipantDraft(p1.id, MoneyCents(1_000L)),
                        ProductParticipantDraft(p2.id, MoneyCents(1_000L)),
                    ),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )
            }.exceptionOrNull() is CatalogValidationException,
        )

        // 2) Valor negativo
        assertTrue(
            runCatching {
                CatalogValidator.validateProductInput(
                    rawName = "Negativo",
                    price = MoneyCents(1_000L),
                    active = true,
                    participants = listOf(
                        ProductParticipantDraft(p1.id, MoneyCents(1_200L)),
                        ProductParticipantDraft(p2.id, MoneyCents(-200L)),
                    ),
                    existingPersonIds = setOf(p1.id, p2.id),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )
            }.exceptionOrNull() is CatalogValidationException,
        )

        // 3) Sem nome
        assertTrue(
            runCatching {
                repo.createProduct(
                    name = "   ",
                    price = MoneyCents(1_000L),
                    active = true,
                    participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(1_000L))),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )
            }.exceptionOrNull() is CatalogValidationException,
        )

        // 4) Sem participantes
        assertTrue(
            runCatching {
                repo.createProduct(
                    name = "Sem gente",
                    price = MoneyCents(1_000L),
                    active = true,
                    participants = emptyList(),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )
            }.exceptionOrNull() is CatalogValidationException,
        )

        // 5) Participantes duplicados
        assertTrue(
            runCatching {
                repo.createProduct(
                    name = "Duplicado",
                    price = MoneyCents(2_000L),
                    active = true,
                    participants = listOf(
                        ProductParticipantDraft(p1.id, MoneyCents(1_000L)),
                        ProductParticipantDraft(p1.id, MoneyCents(1_000L)),
                    ),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )
            }.exceptionOrNull() is CatalogValidationException,
        )

        // 6) Pessoa inexistente
        assertTrue(
            runCatching {
                repo.createProduct(
                    name = "Pessoa fantasma",
                    price = MoneyCents(1_000L),
                    active = true,
                    participants = listOf(ProductParticipantDraft("id-inexistente", MoneyCents(1_000L))),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )
            }.exceptionOrNull() is CatalogValidationException,
        )

        // 7) Responsável individual precisa ser participante do produto
        assertTrue(
            runCatching {
                repo.createProduct(
                    name = "Tarifa errada",
                    price = MoneyCents(1_000L),
                    active = true,
                    participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(1_000L))),
                    feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                    feeOwnerPersonId = p3.id,
                )
            }.exceptionOrNull() is CatalogValidationException,
        )

        // Alteração de produto preservando ID e ativar/desativar
        val updatedA = repo.updateProduct(
            productId = prodA.id,
            name = "Produto A Editado",
            price = MoneyCents(3_000L),
            active = true,
            participants = listOf(
                ProductParticipantDraft(p1.id, MoneyCents(1_000L)),
                ProductParticipantDraft(p2.id, MoneyCents(1_000L)),
                ProductParticipantDraft(p3.id, MoneyCents(1_000L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        assertEquals(prodA.id, updatedA.id)
        assertEquals("Produto A Editado", updatedA.name)
        assertEquals(3, updatedA.participants.size)
        assertEquals(FeeResponsibilityMode.PROPORTIONAL, updatedA.feeResponsibilityMode)

        repo.setProductActive(prodB.id, active = false)
        val activeOnly = repo.observeActiveProducts().first()
        assertEquals(1, activeOnly.size)
        assertEquals(prodA.id, activeOnly.first().id)
        assertFalse(repo.observeAllProducts().first().first { it.id == prodB.id }.active)

        db.close()
    }

    @Test
    fun `room persists people and products across close and reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "reopen.db"
        context.deleteDatabase(dbName)

        val db1 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        val repo1 = RoomCatalogRepository(db1.personDao(), db1.productDao())

        val p1 = repo1.createPerson("Lucas")
        val p2 = repo1.createPerson("Rafael")
        val product = repo1.createProduct(
            name = "Suporte 3D",
            price = MoneyCents(4_500L),
            active = true,
            participants = listOf(
                ProductParticipantDraft(p1.id, MoneyCents(2_500L)),
                ProductParticipantDraft(p2.id, MoneyCents(2_000L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerPersonId = p1.id,
        )
        db1.close()

        val db2 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        val repo2 = RoomCatalogRepository(db2.personDao(), db2.productDao())

        val restoredPeople = repo2.observePeople().first()
        val restoredProducts = repo2.observeAllProducts().first()
        assertEquals(2, restoredPeople.size)
        assertEquals(1, restoredProducts.size)

        val restoredProduct = restoredProducts.first()
        assertEquals(product.id, restoredProduct.id)
        assertEquals("Suporte 3D", restoredProduct.name)
        assertEquals(4_500L, restoredProduct.price.rawCents)
        assertEquals(p1.id, restoredProduct.feeOwnerPersonId)
        assertEquals(2_500L, restoredProduct.participants[0].amount.rawCents)
        assertEquals("Lucas", restoredProduct.participants[0].personName)
        assertEquals(2_000L, restoredProduct.participants[1].amount.rawCents)
        assertEquals("Rafael", restoredProduct.participants[1].personName)

        db2.close()
        assertTrue(context.deleteDatabase(dbName))
    }

    @Test
    fun `migration 1 to 2 preserves existing app_preferences data and initializes new catalog tables`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "m12.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Cria banco v1 exatamente como na Etapa 1 e grava dados em app_preferences
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
            "INSERT INTO `app_preferences` (`pref_key`, `pref_value`, `updated_at_epoch_ms`) VALUES ('diagnostico_etapa_1', 'Valor Preservado V1', 1700000123456)",
        )
        v1Sqlite.version = 1
        v1Sqlite.close()

        // Abre com Room v2 + MIGRATION_1_2 (sem fallback destrutivo)
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()

        val prefRepo = RoomPreferenceRepository(migratedDb.preferenceDao())
        val preserved = prefRepo.getPreference("diagnostico_etapa_1")
        assertNotNull(preserved)
        assertEquals("Valor Preservado V1", preserved?.value)
        assertEquals(1700000123456L, preserved?.updatedAtEpochMs)

        // Confirma que as novas tabelas v2 funcionam normalmente após migração
        val catalogRepo = RoomCatalogRepository(migratedDb.personDao(), migratedDb.productDao())
        val pessoa = catalogRepo.createPerson("Pessoa Migrada")
        val produto = catalogRepo.createProduct(
            name = "Produto Pós Migração",
            price = MoneyCents(2_200L),
            active = true,
            participants = listOf(ProductParticipantDraft(pessoa.id, MoneyCents(2_200L))),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        assertEquals(1, catalogRepo.observeAllProducts().first().size)
        assertEquals(2_200L, produto.price.rawCents)

        migratedDb.close()
        assertTrue(context.deleteDatabase(dbName))
    }

    @Test
    fun `product deletion removes unused products and preserves products referenced by sale history`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = RoomCatalogRepository(db.personDao(), db.productDao())
            val person = repo.createPerson("Pessoa histórico")
            suspend fun newProduct(name: String) = repo.createProduct(
                name = name,
                price = MoneyCents(1_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(person.id, MoneyCents(1_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            val unused = newProduct("Sem vendas")
            assertEquals(ProductDeletionResult.Deleted, repo.deleteProductSafely(unused.id))
            assertTrue(repo.observeAllProducts().first().none { it.id == unused.id })

            val historical = newProduct("Com histórico")
            db.saleDao().insertSale(
                SaleEntity(
                    id = "sale-history-1",
                    soldAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                    paymentMethod = "PIX",
                    originalSubtotalCents = 1_000L,
                    discountTotalCents = 0L,
                    finalAmountCents = 1_000L,
                    feeTotalCents = 0L,
                    netTotalCents = 1_000L,
                    discountMode = "PROPORTIONAL",
                    discountTargetProductId = null,
                    discountTargetProductName = null,
                    machineId = null,
                    machineName = null,
                    machineDebitRateBps = null,
                    machineCreditDefaultRateBps = null,
                    machineBrandRatesEnabled = null,
                    machineInstallmentsEnabled = null,
                    cardBrand = null,
                    installments = null,
                    automaticRateBps = null,
                    manualRateOverrideBps = null,
                    effectiveFeeRateBps = 0,
                    note = "",
                ),
            )
            db.saleDao().insertSaleItems(
                listOf(
                    SaleItemEntity(
                        id = "sale-item-history-1",
                        saleId = "sale-history-1",
                        sortOrder = 0,
                        productId = historical.id,
                        productName = historical.name,
                        quantity = 1,
                        unitPriceOriginalCents = 1_000L,
                        originalSubtotalCents = 1_000L,
                        discountAmountCents = 0L,
                        finalAmountCents = 1_000L,
                        appliedFeeRateBps = 0,
                        feeAmountCents = 0L,
                        netAmountCents = 1_000L,
                        feeResponsibilityMode = "PROPORTIONAL",
                        feeOwnerPersonId = null,
                        feeOwnerPersonName = null,
                    ),
                ),
            )

            assertEquals(1, repo.countSalesUsingProduct(historical.id))
            repo.setProductActive(historical.id, active = false)
            assertTrue(repo.observeAllProducts().first().any { it.id == historical.id && !it.active })
            assertTrue(repo.observeActiveProducts().first().none { it.id == historical.id })
            assertEquals(ProductDeletionResult.BlockedBySales(1), repo.deleteProductSafely(historical.id))
            assertEquals(historical.id, db.saleDao().getAllSaleItems().single().productId)
            assertEquals("Com histórico", db.saleDao().getAllSaleItems().single().productName)
            assertEquals(1, db.saleDao().countSales())
        } finally {
            db.close()
        }
    }
}
