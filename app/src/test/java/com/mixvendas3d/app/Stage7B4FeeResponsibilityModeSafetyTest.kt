package com.mixvendas3d.app

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.ProductEntity
import com.mixvendas3d.app.data.local.entity.ProductParticipantEntity
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.data.repository.SaleEntityMapper
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.JsonBackupErrorCode
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.export.JsonBackupValidationException
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.Machine
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import java.util.Collections
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class Stage7B4FeeResponsibilityModeSafetyTest {

    private fun openInMemoryDb(
        context: Context,
        queryCallback: RoomDatabase.QueryCallback? = null,
    ): AppDatabase {
        val builder = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
        if (queryCallback != null) {
            builder.setQueryCallback(queryCallback, Runnable::run)
        }
        return builder.build()
    }

    @Test
    fun `fromStorageKeyOrNull and fromStorageKey accept valid modes and reject unknown empty or null`() {
        assertEquals(FeeResponsibilityMode.INDIVIDUAL, FeeResponsibilityMode.fromStorageKeyOrNull("INDIVIDUAL"))
        assertEquals(FeeResponsibilityMode.EQUAL, FeeResponsibilityMode.fromStorageKeyOrNull("EQUAL"))
        assertEquals(FeeResponsibilityMode.PROPORTIONAL, FeeResponsibilityMode.fromStorageKeyOrNull("PROPORTIONAL"))

        assertEquals(FeeResponsibilityMode.INDIVIDUAL, FeeResponsibilityMode.fromStorageKey("INDIVIDUAL"))
        assertEquals(FeeResponsibilityMode.EQUAL, FeeResponsibilityMode.fromStorageKey("EQUAL"))
        assertEquals(FeeResponsibilityMode.PROPORTIONAL, FeeResponsibilityMode.fromStorageKey("PROPORTIONAL"))

        assertNull(FeeResponsibilityMode.fromStorageKeyOrNull("DESCONHECIDO"))
        assertNull(FeeResponsibilityMode.fromStorageKeyOrNull("proportional"))
        assertNull(FeeResponsibilityMode.fromStorageKeyOrNull(""))
        assertNull(FeeResponsibilityMode.fromStorageKeyOrNull("   "))
        assertNull(FeeResponsibilityMode.fromStorageKeyOrNull(null))

        assertThrows(IllegalArgumentException::class.java) {
            FeeResponsibilityMode.fromStorageKey("DESCONHECIDO")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FeeResponsibilityMode.fromStorageKey("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FeeResponsibilityMode.fromStorageKey(null)
        }
    }

    @Test
    fun `product with invalid feeResponsibilityMode in Room is rejected without conversion or database writes`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executedSqls = Collections.synchronizedList(mutableListOf<String>())
        val db = openInMemoryDb(
            context = context,
            queryCallback = RoomDatabase.QueryCallback { sqlQuery, _ ->
                executedSqls.add(sqlQuery)
            },
        )
        try {
            val repo = RoomCatalogRepository(db.personDao(), db.productDao())
            val ana = repo.createPerson("Ana")
            val bruno = repo.createPerson("Bruno")

            val invalidProductId = "prod-invalid-mode"
            db.productDao().upsertProductWithParticipants(
                product = ProductEntity(
                    id = invalidProductId,
                    name = "Suporte Corrompido",
                    priceCents = 4_500L,
                    active = true,
                    feeResponsibilityMode = "MODO_INVALIDO_ROOM",
                    feeOwnerPersonId = null,
                    createdAtEpochMs = 1_730_000_000_000L,
                    updatedAtEpochMs = 1_730_000_000_000L,
                ),
                participants = listOf(
                    ProductParticipantEntity(invalidProductId, ana.id, 3_000L, 0),
                    ProductParticipantEntity(invalidProductId, bruno.id, 1_500L, 1),
                ),
                isNew = true,
            )

            executedSqls.clear()
            val allError = runCatching { repo.observeAllProducts().first() }.exceptionOrNull()
            assertTrue(allError is IllegalStateException)
            val msgAll = allError?.message.orEmpty()
            assertTrue(msgAll.contains("MODO_INVALIDO_ROOM"))
            assertTrue(msgAll.contains(invalidProductId))
            assertTrue(msgAll.contains("Suporte Corrompido"))

            val activeError = runCatching { repo.observeActiveProducts().first() }.exceptionOrNull()
            assertTrue(activeError is IllegalStateException)
            assertTrue(activeError?.message.orEmpty().contains("MODO_INVALIDO_ROOM"))

            // Nenhuma escrita ou alteração ocorre durante a leitura inválida
            val writeQueriesDuringRead = executedSqls.filter { sql ->
                val upper = sql.trim().uppercase()
                !upper.contains("ROOM_TABLE_MODIFICATION_LOG") &&
                    (upper.startsWith("INSERT") || upper.startsWith("UPDATE") || upper.startsWith("DELETE"))
            }
            assertTrue("Nenhuma escrita deve ocorrer na leitura inválida: $writeQueriesDuringRead", writeQueriesDuringRead.isEmpty())

            // Registro original preservado integralmente no Room
            val persisted = db.productDao().getWithParticipantsById(invalidProductId)
            assertNotNull(persisted)
            assertEquals("MODO_INVALIDO_ROOM", persisted!!.product.feeResponsibilityMode)
            assertEquals(4_500L, persisted.product.priceCents)
            assertEquals(2, persisted.participants.size)
            assertEquals(3_000L, persisted.participants.first { it.person.id == ana.id }.participant.amountCents)
            assertEquals(1_500L, persisted.participants.first { it.person.id == bruno.id }.participant.amountCents)
        } finally {
            db.close()
        }
    }

    @Test
    fun `historical sale with invalid feeResponsibilityMode is rejected while preserving stored snapshot`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executedSqls = Collections.synchronizedList(mutableListOf<String>())
        val db = openInMemoryDb(
            context = context,
            queryCallback = RoomDatabase.QueryCallback { sqlQuery, _ ->
                executedSqls.add(sqlQuery)
            },
        )
        try {
            val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao())
            val saleRepo = RoomSaleRepository(db, db.saleDao())
            val ana = catalogRepo.createPerson("Ana")
            val bruno = catalogRepo.createPerson("Bruno")

            val prod = catalogRepo.createProduct(
                name = "Boneco 3D",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(6_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(4_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
            val machine = Machine(
                id = "mach-1",
                name = "Maquininha Teste",
                debitRateBps = 200,
                creditEnabled = true,
                creditDefaultRateBps = 500,
                brandRatesEnabled = false,
                brandRatesBps = emptyMap(),
                installmentsEnabled = false,
                installmentAddonsBps = emptyMap(),
                createdAtEpochMs = 1_730_000_000_000L,
                updatedAtEpochMs = 1_730_000_000_000L,
            )
            val calculation = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 1)),
                    paymentMethod = SalePaymentMethod.DEBIT,
                    machine = machine,
                ),
            )
            val savedSale = saleRepo.saveSale(calculation)

            // Corrompe diretamente fee_responsibility_mode do item salvo simulando dado inválido no Room
            db.openHelper.writableDatabase.execSQL(
                "UPDATE sale_items SET fee_responsibility_mode = 'LEGADO_DESCONHECIDO' WHERE sale_id = ?",
                arrayOf(savedSale.id),
            )

            val rawDetailsBefore = db.saleDao().getWithDetailsById(savedSale.id)
            assertNotNull(rawDetailsBefore)
            val rawItemId = rawDetailsBefore!!.items.single().item.id

            executedSqls.clear()

            val mapperError = runCatching { SaleEntityMapper.toDomain(rawDetailsBefore) }.exceptionOrNull()
            assertTrue(mapperError is IllegalStateException)
            val msg = mapperError?.message.orEmpty()
            assertTrue(msg.contains("LEGADO_DESCONHECIDO"))
            assertTrue(msg.contains(savedSale.id))
            assertTrue(msg.contains(rawItemId))
            assertTrue(msg.contains("Boneco 3D"))

            val observeError = runCatching { saleRepo.observeSales().first() }.exceptionOrNull()
            assertTrue(observeError is IllegalStateException)
            assertTrue(observeError?.message.orEmpty().contains("LEGADO_DESCONHECIDO"))

            val getByIdError = runCatching { saleRepo.getSaleById(savedSale.id) }.exceptionOrNull()
            assertTrue(getByIdError is IllegalStateException)

            val writeQueriesDuringRead = executedSqls.filter { sql ->
                val upper = sql.trim().uppercase()
                !upper.contains("ROOM_TABLE_MODIFICATION_LOG") &&
                    (upper.startsWith("INSERT") || upper.startsWith("UPDATE") || upper.startsWith("DELETE"))
            }
            assertTrue("Nenhuma escrita deve ocorrer na leitura de venda inválida: $writeQueriesDuringRead", writeQueriesDuringRead.isEmpty())

            // Snapshot financeiro histórico preservado intacto no Room
            val rawDetailsAfter = db.saleDao().getWithDetailsById(savedSale.id)!!
            assertEquals(10_000L, rawDetailsAfter.sale.finalAmountCents)
            assertEquals(200L, rawDetailsAfter.sale.feeTotalCents)
            assertEquals(9_800L, rawDetailsAfter.sale.netTotalCents)
            assertEquals("LEGADO_DESCONHECIDO", rawDetailsAfter.items.single().item.feeResponsibilityMode)
        } finally {
            db.close()
        }
    }

    @Test
    fun `valid INDIVIDUAL EQUAL and PROPORTIONAL products and sales preserve all financial values`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = openInMemoryDb(context)
        try {
            val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao())
            val saleRepo = RoomSaleRepository(db, db.saleDao())
            val ana = catalogRepo.createPerson("Ana")
            val bruno = catalogRepo.createPerson("Bruno")

            val pInd = catalogRepo.createProduct(
                name = "Prod Individual",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(7_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(3_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = ana.id,
            )
            val pEq = catalogRepo.createProduct(
                name = "Prod Equal",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(7_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(3_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
            val pProp = catalogRepo.createProduct(
                name = "Prod Proportional",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(7_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(3_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            val loadedProducts = catalogRepo.observeAllProducts().first()
            assertEquals(3, loadedProducts.size)
            assertEquals(FeeResponsibilityMode.INDIVIDUAL, loadedProducts.first { it.id == pInd.id }.feeResponsibilityMode)
            assertEquals(ana.id, loadedProducts.first { it.id == pInd.id }.feeOwnerPersonId)
            assertEquals(FeeResponsibilityMode.EQUAL, loadedProducts.first { it.id == pEq.id }.feeResponsibilityMode)
            assertEquals(FeeResponsibilityMode.PROPORTIONAL, loadedProducts.first { it.id == pProp.id }.feeResponsibilityMode)

            val machine = Machine(
                id = "mach-1",
                name = "Maquininha",
                debitRateBps = 200,
                creditEnabled = true,
                creditDefaultRateBps = 500,
                brandRatesEnabled = false,
                brandRatesBps = emptyMap(),
                installmentsEnabled = false,
                installmentAddonsBps = emptyMap(),
                createdAtEpochMs = 1_730_000_000_000L,
                updatedAtEpochMs = 1_730_000_000_000L,
            )
            val calc = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(
                        SaleItemInput(pInd, 1),
                        SaleItemInput(pEq, 1),
                        SaleItemInput(pProp, 1),
                    ),
                    paymentMethod = SalePaymentMethod.DEBIT,
                    machine = machine,
                ),
            )
            val saved = saleRepo.saveSale(calc)
            val reloaded = saleRepo.getSaleById(saved.id)
            assertNotNull(reloaded)
            assertEquals(calc, reloaded!!.calculation)
            assertEquals(30_000L, reloaded.calculation.finalAmount.rawCents)
            assertEquals(600L, reloaded.calculation.feeTotal.rawCents)
            assertEquals(29_400L, reloaded.calculation.netTotal.rawCents)
        } finally {
            db.close()
        }
    }

    @Test
    fun `json backup rejects invalid feeResponsibilityMode on products and sale items`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = openInMemoryDb(context)
        try {
            val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao())
            val saleRepo = RoomSaleRepository(db, db.saleDao())
            val ana = catalogRepo.createPerson("Ana")
            val bruno = catalogRepo.createPerson("Bruno")

            val prod = catalogRepo.createProduct(
                name = "Peça Padrão",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(3_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
            val calc = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 1)),
                    paymentMethod = SalePaymentMethod.PIX,
                ),
            )
            saleRepo.saveSale(calc)

            val validBackup = JsonBackupExporter.export(db, exportedAtEpochMs = 1_730_000_100_000L)

            // 1) Produto com modo inválido no JSON
            val invalidProductJson = validBackup.jsonContent.replaceFirst(
                "\"feeResponsibilityMode\": \"EQUAL\"",
                "\"feeResponsibilityMode\": \"MODO_PRODUTO_INVALIDO\"",
            )
            assertFalse(invalidProductJson == validBackup.jsonContent)
            val productReadResult = JsonBackupReader.readAndValidate(invalidProductJson)
            assertFalse(productReadResult.isValid)
            assertNull(productReadResult.validatedData)
            assertTrue(
                productReadResult.errors.any {
                    it.code == JsonBackupErrorCode.RELATIONAL_OR_FINANCIAL_VALIDATION_ERROR &&
                        it.message.contains("MODO_PRODUTO_INVALIDO")
                },
            )

            // 2) Item de venda com modo inválido no JSON
            val lastIndex = validBackup.jsonContent.lastIndexOf("\"feeResponsibilityMode\": \"EQUAL\"")
            assertTrue(lastIndex > 0)
            val invalidSaleItemJson = buildString {
                append(validBackup.jsonContent.substring(0, lastIndex))
                append("\"feeResponsibilityMode\": \"MODO_ITEM_INVALIDO\"")
                append(validBackup.jsonContent.substring(lastIndex + "\"feeResponsibilityMode\": \"EQUAL\"".length))
            }
            val saleItemReadResult = JsonBackupReader.readAndValidate(invalidSaleItemJson)
            assertFalse(saleItemReadResult.isValid)
            assertNull(saleItemReadResult.validatedData)
            assertTrue(
                saleItemReadResult.errors.any {
                    it.code == JsonBackupErrorCode.RELATIONAL_OR_FINANCIAL_VALIDATION_ERROR &&
                        it.message.contains("MODO_ITEM_INVALIDO")
                },
            )

            // 3) Snapshot direto também é rejeitado pelo JsonBackupExporter
            val baseSnapshot = JsonBackupExporter.captureConsistentSnapshot(db)
            val corruptedSnapshot = baseSnapshot.copy(
                saleItems = baseSnapshot.saleItems.map { it.copy(feeResponsibilityMode = "INVALID_SNAPSHOT_MODE") },
            )
            val snapshotError = assertThrows(JsonBackupValidationException::class.java) {
                JsonBackupExporter.validateSnapshot(corruptedSnapshot)
            }
            assertTrue(snapshotError.message.orEmpty().contains("INVALID_SNAPSHOT_MODE"))
        } finally {
            db.close()
        }
    }
}
