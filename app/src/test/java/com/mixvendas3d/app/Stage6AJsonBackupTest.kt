package com.mixvendas3d.app

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.MachineBrandRateEntity
import com.mixvendas3d.app.data.local.entity.MachineEntity
import com.mixvendas3d.app.data.local.entity.MachineInstallmentRateEntity
import com.mixvendas3d.app.data.local.entity.PersonEntity
import com.mixvendas3d.app.data.local.entity.PreferenceEntity
import com.mixvendas3d.app.data.local.entity.ProductEntity
import com.mixvendas3d.app.data.local.entity.ProductParticipantEntity
import com.mixvendas3d.app.data.local.entity.SaleEntity
import com.mixvendas3d.app.data.local.entity.SaleItemEntity
import com.mixvendas3d.app.data.local.entity.SaleItemParticipantEntity
import com.mixvendas3d.app.data.local.entity.SalePersonTotalEntity
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupSnapshot
import com.mixvendas3d.app.domain.export.JsonBackupValidationException
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage6AJsonBackupTest {

    private val app: MixVendasApplication
        get() = ApplicationProvider.getApplicationContext()

    private val expectedCollectionKeys = listOf(
        "preferences",
        "people",
        "products",
        "productParticipants",
        "machines",
        "machineBrandRates",
        "machineInstallmentRates",
        "sales",
        "saleItems",
        "saleItemParticipants",
        "salePersonTotals",
    )

    @After
    fun tearDown() {
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
    }

    private fun assertExactLongJsonField(json: JSONObject, key: String, expected: Long) {
        assertTrue("Campo $key ausente no JSON", json.has(key))
        val raw = json.get(key)
        assertTrue("Campo $key deve ser serializado como String decimal exata, mas foi ${raw::class.java.simpleName}", raw is String)
        assertEquals(expected.toString(), raw as String)
        assertEquals(expected, raw.toLong())
    }

    private fun baseSnapshot(
        preferences: List<PreferenceEntity> = emptyList(),
        people: List<PersonEntity> = emptyList(),
        products: List<ProductEntity> = emptyList(),
        productParticipants: List<ProductParticipantEntity> = emptyList(),
        machines: List<MachineEntity> = emptyList(),
        machineBrandRates: List<MachineBrandRateEntity> = emptyList(),
        machineInstallmentRates: List<MachineInstallmentRateEntity> = emptyList(),
        sales: List<SaleEntity> = emptyList(),
        saleItems: List<SaleItemEntity> = emptyList(),
        saleItemParticipants: List<SaleItemParticipantEntity> = emptyList(),
        salePersonTotals: List<SalePersonTotalEntity> = emptyList(),
    ): JsonBackupSnapshot = JsonBackupSnapshot(
        preferences = preferences,
        people = people,
        products = products,
        productParticipants = productParticipants,
        machines = machines,
        machineBrandRates = machineBrandRates,
        machineInstallmentRates = machineInstallmentRates,
        sales = sales,
        saleItems = saleItems,
        saleItemParticipants = saleItemParticipants,
        salePersonTotals = salePersonTotals,
    )

    private fun validSingleItemSaleFixture(
        saleId: String = "sale-1",
        itemId: String = "item-1",
        productId: String = "prod-1",
        personId: String = "person-1",
    ): Triple<SaleEntity, SaleItemEntity, Pair<SaleItemParticipantEntity, SalePersonTotalEntity>> {
        val sale = SaleEntity(
            id = saleId,
            soldAtEpochMs = 1730000000000L,
            updatedAtEpochMs = 1730000000000L,
            paymentMethod = "CASH",
            originalSubtotalCents = 10_000L,
            discountTotalCents = 0L,
            finalAmountCents = 10_000L,
            feeTotalCents = 0L,
            netTotalCents = 10_000L,
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
            note = "Fixture",
        )
        val item = SaleItemEntity(
            id = itemId,
            saleId = saleId,
            sortOrder = 0,
            productId = productId,
            productName = "Produto Teste",
            quantity = 1,
            unitPriceOriginalCents = 10_000L,
            originalSubtotalCents = 10_000L,
            discountAmountCents = 0L,
            finalAmountCents = 10_000L,
            appliedFeeRateBps = 0,
            feeAmountCents = 0L,
            netAmountCents = 10_000L,
            feeResponsibilityMode = "PROPORTIONAL",
            feeOwnerPersonId = null,
            feeOwnerPersonName = null,
        )
        val participant = SaleItemParticipantEntity(
            saleItemId = itemId,
            personId = personId,
            sortOrder = 0,
            personName = "Ana",
            personColorIndex = 0,
            originalUnitAmountCents = 10_000L,
            originalSubtotalCents = 10_000L,
            discountAmountCents = 0L,
            grossAmountCents = 10_000L,
            feeAmountCents = 0L,
            netAmountCents = 10_000L,
        )
        val personTotal = SalePersonTotalEntity(
            saleId = saleId,
            personId = personId,
            sortOrder = 0,
            personName = "Ana",
            personColorIndex = 0,
            grossAmountCents = 10_000L,
            feeAmountCents = 0L,
            netAmountCents = 10_000L,
        )
        return Triple(sale, item, participant to personTotal)
    }

    // --- Testes preservados da R1 ---

    @Test
    fun testEmptyDatabaseBackup() = runBlocking<Unit>(Dispatchers.IO) {
        val db = app.database
        val result = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1730000000000L)

        assertNotNull(result.jsonContent)
        assertEquals(0, result.totalRecordsCount)
        assertEquals("mix_vendas_3d_backup_20241027_033320.json", result.suggestedFileName)
        assertTrue(result.utf8Bytes.isNotEmpty())
        assertEquals(result.jsonContent, String(result.utf8Bytes, Charsets.UTF_8))

        val root = JSONObject(result.jsonContent)
        assertEquals("MIX_VENDAS_3D_BACKUP", root.getString("format"))
        assertEquals(1, root.getInt("backupSchemaVersion"))
        assertEquals("com.mixvendas3d.app", root.getString("applicationId"))
        assertExactLongJsonField(root, "exportedAtEpochMs", 1730000000000L)
        assertEquals(7, root.getInt("roomSchemaVersion"))

        val collections = root.getJSONObject("collections")
        val counts = root.getJSONObject("counts")
        expectedCollectionKeys.forEach { key ->
            assertTrue("Coleção ausente em collections: $key", collections.has(key))
            assertEquals(0, collections.getJSONArray(key).length())
            assertTrue("Contagem ausente em counts: $key", counts.has(key))
            assertEquals(0, counts.getInt(key))
        }
    }

    @Test
    fun testFullBackupExportWithCollections() = runBlocking<Unit>(Dispatchers.IO) {
        val db = app.database
        val prefDao = db.preferenceDao()
        prefDao.upsert(PreferenceEntity("app_theme", "dark", 1730000000000L))

        val ana = app.catalogRepository.createPerson("Ana 'Especial' & Cia")
        val bruno = app.catalogRepository.createPerson("Bruno Café")

        val prod1 = app.catalogRepository.createProduct(
            name = "Produto A \"Especial\" — Ação",
            price = MoneyCents(1234567890L),
            active = true,
            participants = listOf(
                ProductParticipantDraft(ana.id, MoneyCents(734567890L)),
                ProductParticipantDraft(bruno.id, MoneyCents(500000000L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )

        val machine = app.machineRepository.createMachine(
            MachineInputDraft(
                name = "Maquininha Top & Co",
                debitRateBps = 199,
                creditEnabled = true,
                creditDefaultRateBps = 499,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.VISA to 399),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(2 to 150),
            ),
        )

        val saleRepo = RoomSaleRepository(
            database = db,
            saleDao = db.saleDao(),
            clockMillis = { 1730000500000L },
        )

        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod1, 2)),
                finalAmountOverride = MoneyCents(2400000000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = machine,
                cardBrand = CardBrand.VISA,
                installments = 2,
                note = "Venda complexa com acentos: café, coração & 3D",
            ),
        )
        val persistedSale = saleRepo.saveSale(calc)

        val result = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1730000000000L)
        val repeatedResult = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1730000000000L)

        assertEquals("mix_vendas_3d_backup_20241027_033320.json", result.suggestedFileName)
        assertEquals(result.jsonContent, repeatedResult.jsonContent)
        assertArrayEquals(result.utf8Bytes, repeatedResult.utf8Bytes)
        assertEquals(result.collectionsCounts, repeatedResult.collectionsCounts)

        val root = JSONObject(result.jsonContent)
        assertEquals("MIX_VENDAS_3D_BACKUP", root.getString("format"))
        assertEquals(1, root.getInt("backupSchemaVersion"))
        assertEquals("com.mixvendas3d.app", root.getString("applicationId"))
        assertExactLongJsonField(root, "exportedAtEpochMs", 1730000000000L)
        assertEquals(7, root.getInt("roomSchemaVersion"))

        val collections = root.getJSONObject("collections")
        val counts = root.getJSONObject("counts")
        assertEquals(11, expectedCollectionKeys.size)
        expectedCollectionKeys.forEach { key ->
            assertTrue("Coleção ausente: $key", collections.has(key))
            assertTrue("Contagem ausente: $key", counts.has(key))
            assertEquals(collections.getJSONArray(key).length(), counts.getInt(key))
        }

        assertEquals(1, counts.getInt("preferences"))
        assertEquals(2, counts.getInt("people"))
        assertEquals(1, counts.getInt("products"))
        assertEquals(2, counts.getInt("productParticipants"))
        assertEquals(1, counts.getInt("machines"))
        assertEquals(1, counts.getInt("machineBrandRates"))
        assertEquals(1, counts.getInt("machineInstallmentRates"))
        assertEquals(1, counts.getInt("sales"))
        assertEquals(1, counts.getInt("saleItems"))
        assertEquals(2, counts.getInt("saleItemParticipants"))
        assertEquals(2, counts.getInt("salePersonTotals"))
        assertEquals(15, result.totalRecordsCount)

        val peopleJson = collections.getJSONArray("people")
        val peopleById = (0 until peopleJson.length())
            .map { peopleJson.getJSONObject(it) }
            .associateBy { it.getString("id") }
        assertEquals("Ana 'Especial' & Cia", peopleById.getValue(ana.id).getString("name"))
        assertEquals("Bruno Café", peopleById.getValue(bruno.id).getString("name"))

        val productJson = collections.getJSONArray("products").getJSONObject(0)
        assertEquals(prod1.id, productJson.getString("id"))
        assertEquals("Produto A \"Especial\" — Ação", productJson.getString("name"))
        assertExactLongJsonField(productJson, "priceCents", 1234567890L)
        assertTrue("Campo nulo feeOwnerPersonId deve existir no JSON", productJson.has("feeOwnerPersonId"))
        assertTrue("feeOwnerPersonId deve ser null", productJson.isNull("feeOwnerPersonId"))

        val ppJson = collections.getJSONArray("productParticipants")
        val ppByPersonId = (0 until ppJson.length())
            .map { ppJson.getJSONObject(it) }
            .associateBy { it.getString("personId") }
        assertExactLongJsonField(ppByPersonId.getValue(ana.id), "amountCents", 734567890L)
        assertExactLongJsonField(ppByPersonId.getValue(bruno.id), "amountCents", 500000000L)

        val machineJson = collections.getJSONArray("machines").getJSONObject(0)
        assertEquals(machine.id, machineJson.getString("id"))
        assertEquals("Maquininha Top & Co", machineJson.getString("name"))

        val saleJson = collections.getJSONArray("sales").getJSONObject(0)
        assertEquals(persistedSale.id, saleJson.getString("id"))
        assertExactLongJsonField(saleJson, "originalSubtotalCents", 2469135780L)
        assertExactLongJsonField(saleJson, "discountTotalCents", 69135780L)
        assertExactLongJsonField(saleJson, "finalAmountCents", 2400000000L)
        assertExactLongJsonField(saleJson, "feeTotalCents", calc.feeTotal.rawCents)
        assertExactLongJsonField(saleJson, "netTotalCents", calc.netTotal.rawCents)
        assertEquals("Venda complexa com acentos: café, coração & 3D", saleJson.getString("note"))
        assertTrue(saleJson.has("discountTargetProductId") && saleJson.isNull("discountTargetProductId"))
        assertTrue(saleJson.has("discountTargetProductName") && saleJson.isNull("discountTargetProductName"))
        assertTrue(saleJson.has("manualRateOverrideBps") && saleJson.isNull("manualRateOverrideBps"))

        val saleItemJson = collections.getJSONArray("saleItems").getJSONObject(0)
        assertEquals(persistedSale.id, saleItemJson.getString("saleId"))
        assertEquals(prod1.id, saleItemJson.getString("productId"))
        assertTrue(saleItemJson.has("feeOwnerPersonId") && saleItemJson.isNull("feeOwnerPersonId"))
        assertTrue(saleItemJson.has("feeOwnerPersonName") && saleItemJson.isNull("feeOwnerPersonName"))
    }

    @Test
    fun testRoomForeignKeyBlocksOrphanSaleItemInsert() {
        val db = app.database
        val saleDao = db.saleDao()

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking(Dispatchers.IO) {
                saleDao.insertSaleItems(
                    listOf(
                        SaleItemEntity(
                            id = "item_fantasma",
                            saleId = "venda_inexistente",
                            sortOrder = 1,
                            productId = "prod_x",
                            productName = "Fantasma",
                            quantity = 1,
                            unitPriceOriginalCents = 100L,
                            originalSubtotalCents = 100L,
                            discountAmountCents = 0L,
                            finalAmountCents = 100L,
                            appliedFeeRateBps = 0,
                            feeAmountCents = 0L,
                            netAmountCents = 100L,
                            feeResponsibilityMode = "PROPORTIONAL",
                            feeOwnerPersonId = null,
                            feeOwnerPersonName = null,
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun testExporterValidationRejectsOrphanSaleItemInCorruptedDatabase() {
        val isolatedDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val sqliteDb = isolatedDb.openHelper.writableDatabase
            sqliteDb.execSQL("PRAGMA foreign_keys = OFF")
            sqliteDb.execSQL(
                """
                INSERT INTO sale_items (
                    id, sale_id, sort_order, product_id, product_name, quantity,
                    unit_price_original_cents, original_subtotal_cents, discount_amount_cents,
                    final_amount_cents, applied_fee_rate_bps, fee_amount_cents, net_amount_cents,
                    fee_responsibility_mode, fee_owner_person_id, fee_owner_person_name
                ) VALUES (
                    'item_fantasma', 'venda_inexistente', 1, 'prod_x', 'Fantasma', 1,
                    100, 100, 0,
                    100, 0, 0, 100,
                    'PROPORTIONAL', NULL, NULL
                )
                """.trimIndent(),
            )
            sqliteDb.execSQL("PRAGMA foreign_keys = ON")

            val exception = assertThrows(JsonBackupValidationException::class.java) {
                runBlocking(Dispatchers.IO) {
                    JsonBackupExporter.export(isolatedDb, ZoneId.of("UTC"), 1730000000000L)
                }
            }
            assertEquals("item_fantasma", exception.entityId)
            assertTrue(exception.message.orEmpty().contains("venda_inexistente"))
        } finally {
            isolatedDb.close()
        }
    }

    // --- Testes preservados da R2 ---

    @Test
    fun testSnapshotCapturesAll11TablesAndPreservesHistoricalSalesAfterCatalogMutations() = runBlocking<Unit>(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            var fakeClock = 1_730_000_000_000L
            var seq = 0
            val idGen = {
                seq += 1
                "id-$seq"
            }
            val catalogRepo = RoomCatalogRepository(
                personDao = db.personDao(),
                productDao = db.productDao(),
                idGenerator = idGen,
                clockMillis = { fakeClock },
            )
            val machineRepo = RoomMachineRepository(
                machineDao = db.machineDao(),
                idGenerator = idGen,
                clockMillis = { fakeClock },
            )
            val saleRepo = RoomSaleRepository(
                database = db,
                saleDao = db.saleDao(),
                idGenerator = idGen,
                clockMillis = { fakeClock },
            )

            db.preferenceDao().upsert(PreferenceEntity("diag_key", "valor_ok", fakeClock))

            val ana = catalogRepo.createPerson("Ana Histórica")
            val beto = catalogRepo.createPerson("Beto Sócio")
            val produto = catalogRepo.createProduct(
                name = "Dragão Articulado v1",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(7_000L)),
                    ProductParticipantDraft(beto.id, MoneyCents(3_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = ana.id,
            )
            val machine = machineRepo.createMachine(
                MachineInputDraft(
                    name = "Stone Histórica",
                    debitRateBps = 195,
                    creditEnabled = true,
                    creditDefaultRateBps = 450,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.MASTERCARD to 420),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 3 to 200),
                ),
            )

            fakeClock = 1_730_000_100_000L
            val sale = saleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(produto, 2)),
                        finalAmountOverride = MoneyCents(18_000L),
                        discountMode = SaleDiscountInput.SpecificProduct(produto.id),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        cardBrand = CardBrand.MASTERCARD,
                        installments = 3,
                        note = "Venda histórica preservada",
                    ),
                ),
            )

            fakeClock = 1_730_000_200_000L
            catalogRepo.renamePerson(ana.id, "Ana Renomeada")
            catalogRepo.updateProduct(
                productId = produto.id,
                name = "Dragão v2 Novo Preço",
                price = MoneyCents(25_000L),
                active = false,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(12_500L)),
                    ProductParticipantDraft(beto.id, MoneyCents(12_500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
            db.machineDao().deleteById(machine.id)

            val activeMachine = machineRepo.createMachine(
                MachineInputDraft(
                    name = "Ton Atual",
                    debitRateBps = 150,
                    creditEnabled = true,
                    creditDefaultRateBps = 350,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 320),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(2 to 110),
                ),
            )

            val result = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_300_000L)
            val root = JSONObject(result.jsonContent)
            val collections = root.getJSONObject("collections")
            val counts = root.getJSONObject("counts")

            expectedCollectionKeys.forEach { key ->
                assertTrue("Todas as 11 tabelas devem ter registros neste teste ($key)", counts.getInt(key) > 0)
                assertEquals(counts.getInt(key), collections.getJSONArray(key).length())
            }

            val saleJson = collections.getJSONArray("sales").getJSONObject(0)
            assertEquals(sale.id, saleJson.getString("id"))
            assertExactLongJsonField(saleJson, "soldAtEpochMs", 1_730_000_100_000L)
            assertEquals(machine.id, saleJson.getString("machineId"))
            assertEquals("Stone Histórica", saleJson.getString("machineName"))
            assertEquals(620, saleJson.getInt("effectiveFeeRateBps"))
            assertExactLongJsonField(saleJson, "originalSubtotalCents", 20_000L)
            assertExactLongJsonField(saleJson, "discountTotalCents", 2_000L)
            assertExactLongJsonField(saleJson, "finalAmountCents", 18_000L)
            assertExactLongJsonField(saleJson, "feeTotalCents", sale.calculation.feeTotal.rawCents)
            assertExactLongJsonField(saleJson, "netTotalCents", sale.calculation.netTotal.rawCents)
            assertEquals(produto.id, saleJson.getString("discountTargetProductId"))
            assertEquals("Dragão Articulado v1", saleJson.getString("discountTargetProductName"))
            assertTrue(saleJson.has("manualRateOverrideBps") && saleJson.isNull("manualRateOverrideBps"))

            val saleItemJson = collections.getJSONArray("saleItems").getJSONObject(0)
            assertEquals(sale.id, saleItemJson.getString("saleId"))
            assertEquals("Dragão Articulado v1", saleItemJson.getString("productName"))
            assertExactLongJsonField(saleItemJson, "unitPriceOriginalCents", 10_000L)
            assertEquals("INDIVIDUAL", saleItemJson.getString("feeResponsibilityMode"))
            assertEquals(ana.id, saleItemJson.getString("feeOwnerPersonId"))
            assertEquals("Ana Histórica", saleItemJson.getString("feeOwnerPersonName"))

            val personTotals = collections.getJSONArray("salePersonTotals")
            assertEquals(2, personTotals.length())
            val totalsByPerson = (0 until personTotals.length())
                .map { personTotals.getJSONObject(it) }
                .associateBy { it.getString("personId") }
            assertEquals("Ana Histórica", totalsByPerson.getValue(ana.id).getString("personName"))
            assertExactLongJsonField(
                totalsByPerson.getValue(ana.id),
                "netAmountCents",
                sale.calculation.personTotals.first { it.personId == ana.id }.netAmount.rawCents,
            )
            assertEquals("Beto Sócio", totalsByPerson.getValue(beto.id).getString("personName"))
            assertExactLongJsonField(
                totalsByPerson.getValue(beto.id),
                "netAmountCents",
                sale.calculation.personTotals.first { it.personId == beto.id }.netAmount.rawCents,
            )

            assertEquals(activeMachine.id, collections.getJSONArray("machines").getJSONObject(0).getString("id"))
        } finally {
            db.close()
        }
    }

    @Test
    fun testTransactionClosesBeforeSerializationAndSnapshotIsImmutable() = runBlocking<Unit>(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = { 1_730_000_000_000L })
            val saleRepo = RoomSaleRepository(db, db.saleDao(), clockMillis = { 1_730_000_000_000L })

            val p1 = catalogRepo.createPerson("Pessoa Inicial")
            val prod = catalogRepo.createProduct(
                name = "Produto Snapshot",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val sale1 = saleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(items = listOf(SaleItemInput(prod, 1)), paymentMethod = SalePaymentMethod.PIX),
                ),
            )

            var insideTxFlag = false
            var outsideTxBeforeSerializationFlag = false
            var saleAddedAfterTxId: String? = null

            val firstExport = JsonBackupExporter.exportWithHooks(
                database = db,
                zoneId = ZoneId.of("UTC"),
                exportedAtEpochMs = 1_730_000_000_000L,
                onAfterSalesReadInsideTransaction = {
                    insideTxFlag = db.inTransaction()
                },
                onAfterTransactionBeforeSerialization = {
                    outsideTxBeforeSerializationFlag = !db.inTransaction()
                    val extraPerson = catalogRepo.createPerson("Pessoa Pós-Captura")
                    val extraSale = saleRepo.saveSale(
                        SaleFinancialEngine.calculate(
                            SaleCalculationInput(items = listOf(SaleItemInput(prod, 2)), paymentMethod = SalePaymentMethod.CASH),
                        ),
                    )
                    saleAddedAfterTxId = extraSale.id
                    assertTrue(extraPerson.id.isNotBlank())
                },
            )

            assertTrue("Leitura deve ocorrer dentro de uma transação Room ativa", insideTxFlag)
            assertTrue("Transação deve estar encerrada antes da serialização JSON", outsideTxBeforeSerializationFlag)

            val firstRoot = JSONObject(firstExport.jsonContent)
            val firstCounts = firstRoot.getJSONObject("counts")
            assertEquals(1, firstCounts.getInt("people"))
            assertEquals(1, firstCounts.getInt("sales"))
            assertEquals(
                sale1.id,
                firstRoot.getJSONObject("collections").getJSONArray("sales").getJSONObject(0).getString("id"),
            )

            val secondExport = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L)
            assertNotEquals(firstExport.jsonContent, secondExport.jsonContent)
            val secondRoot = JSONObject(secondExport.jsonContent)
            val secondCounts = secondRoot.getJSONObject("counts")
            assertEquals(2, secondCounts.getInt("people"))
            assertEquals(2, secondCounts.getInt("sales"))
            val exportedSaleIds = (0 until secondRoot.getJSONObject("collections").getJSONArray("sales").length())
                .map { secondRoot.getJSONObject("collections").getJSONArray("sales").getJSONObject(it).getString("id") }
                .toSet()
            assertEquals(setOf(sale1.id, saleAddedAfterTxId!!), exportedSaleIds)
        } finally {
            db.close()
        }
    }

    @Test
    fun testExportIsStrictlyReadOnlyAndDeterministicAcrossRepeatedRuns() = runBlocking<Unit>(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = { 1_730_000_000_000L })
            val machineRepo = RoomMachineRepository(db.machineDao(), clockMillis = { 1_730_000_000_000L })
            val saleRepo = RoomSaleRepository(db, db.saleDao(), clockMillis = { 1_730_000_000_000L })

            db.preferenceDao().upsert(PreferenceEntity("pref_1", "v1", 1_730_000_000_000L))
            val p1 = catalogRepo.createPerson("Camila")
            val prod = catalogRepo.createProduct(
                name = "Chaveiro 3D",
                price = MoneyCents(2_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(2_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val mach = machineRepo.createMachine(
                MachineInputDraft(
                    name = "Point Mini",
                    debitRateBps = 199,
                    creditEnabled = true,
                    creditDefaultRateBps = 399,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.ELO to 410),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(2 to 120),
                ),
            )
            saleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 3)),
                        paymentMethod = SalePaymentMethod.DEBIT,
                        machine = mach,
                        note = "Somente leitura",
                    ),
                ),
            )

            val beforeSnapshot = JsonBackupExporter.captureConsistentSnapshot(db)
            val run1 = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L)
            val run2 = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L)
            val afterSnapshot = JsonBackupExporter.captureConsistentSnapshot(db)

            assertEquals(beforeSnapshot, afterSnapshot)
            assertEquals(run1.jsonContent, run2.jsonContent)
            assertArrayEquals(run1.utf8Bytes, run2.utf8Bytes)
            assertEquals(run1.suggestedFileName, run2.suggestedFileName)
            assertEquals(run1.totalRecordsCount, run2.totalRecordsCount)
            assertEquals(run1.collectionsCounts, run2.collectionsCounts)
        } finally {
            db.close()
        }
    }

    @Test
    fun testConcurrentWriteDuringTransactionalCaptureMaintainsConsistency() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .build()
        try {
            val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = { 1_730_000_000_000L })
            val p1 = catalogRepo.createPerson("Lucas")
            val p2 = catalogRepo.createPerson("Marina")
            val prod = catalogRepo.createProduct(
                name = "Luminária 3D",
                price = MoneyCents(8_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(5_000L)),
                    ProductParticipantDraft(p2.id, MoneyCents(3_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            val baseRepo = RoomSaleRepository(db, db.saleDao(), clockMillis = { 1_730_000_000_000L })
            baseRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(items = listOf(SaleItemInput(prod, 1)), paymentMethod = SalePaymentMethod.CASH),
                ),
            )

            val writerInsideTxAfterSaleHeader = CompletableDeferred<Unit>()
            val allowWriterToFinish = CompletableDeferred<Unit>()
            val exportAboutToStartTx = CompletableDeferred<Unit>()

            val concurrentRepo = RoomSaleRepository(
                database = db,
                saleDao = db.saleDao(),
                clockMillis = { 1_730_000_500_000L },
                onBeforeInsertChildren = {
                    writerInsideTxAfterSaleHeader.complete(Unit)
                    allowWriterToFinish.await()
                },
            )

            val writerJob = async(Dispatchers.IO) {
                concurrentRepo.saveSale(
                    SaleFinancialEngine.calculate(
                        SaleCalculationInput(
                            items = listOf(SaleItemInput(prod, 2)),
                            paymentMethod = SalePaymentMethod.PIX,
                            note = "Venda concorrente",
                        ),
                    ),
                )
            }

            writerInsideTxAfterSaleHeader.await()

            val exportJob = async(Dispatchers.IO) {
                JsonBackupExporter.exportWithHooks(
                    database = db,
                    zoneId = ZoneId.of("UTC"),
                    exportedAtEpochMs = 1_730_000_600_000L,
                    onBeforeTransaction = {
                        exportAboutToStartTx.complete(Unit)
                    },
                )
            }

            exportAboutToStartTx.await()
            allowWriterToFinish.complete(Unit)

            val concurrentSale = writerJob.await()
            val exportResult = exportJob.await()

            val root = JSONObject(exportResult.jsonContent)
            val collections = root.getJSONObject("collections")
            val salesArr = collections.getJSONArray("sales")
            val itemsArr = collections.getJSONArray("saleItems")
            val participantsArr = collections.getJSONArray("saleItemParticipants")
            val totalsArr = collections.getJSONArray("salePersonTotals")

            val exportedSaleIds = (0 until salesArr.length())
                .map { salesArr.getJSONObject(it).getString("id") }
                .toSet()
            assertTrue(exportedSaleIds.contains(concurrentSale.id))
            assertEquals(2, salesArr.length())
            assertEquals(2, itemsArr.length())
            assertEquals(4, participantsArr.length())
            assertEquals(4, totalsArr.length())

            val itemSaleIds = (0 until itemsArr.length())
                .map { itemsArr.getJSONObject(it).getString("saleId") }
                .toSet()
            assertEquals(exportedSaleIds, itemSaleIds)
        } finally {
            db.close()
        }
    }

    // --- Novos testes da Etapa 6A-R3 (Precisão Long > 2^53 / MAX_VALUE, integridade 11 tabelas e conferência financeira) ---

    @Test
    fun testLongPrecisionAbove2Pow53AndLongMaxValuePreservedAsExactStrings() = runBlocking<Unit>(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val largePriceAbove2Pow53 = 9_007_199_254_740_993L // 2^53 + 1
            val splitA = 5_000_000_000_000_001L
            val splitB = 4_007_199_254_740_992L
            val maxTimestamp = Long.MAX_VALUE

            db.preferenceDao().upsert(PreferenceEntity("max_ts_pref", "ok", maxTimestamp))
            val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = { 9_007_199_254_740_995L })
            val p1 = catalogRepo.createPerson("Sócio Precisão 1")
            val p2 = catalogRepo.createPerson("Sócio Precisão 2")

            val prodAbove2Pow53 = catalogRepo.createProduct(
                name = "Produto Precisão 2^53+1",
                price = MoneyCents(largePriceAbove2Pow53),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(splitA)),
                    ProductParticipantDraft(p2.id, MoneyCents(splitB)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodMaxLong = catalogRepo.createProduct(
                name = "Produto Long MAX_VALUE",
                price = MoneyCents(Long.MAX_VALUE),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(Long.MAX_VALUE)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            val saleRepo = RoomSaleRepository(db, db.saleDao(), clockMillis = { 9_007_199_254_740_999L })
            val saleAbove = saleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodAbove2Pow53, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                        note = "Precisão exata acima de 2^53",
                    ),
                ),
            )
            val saleMax = saleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodMaxLong, 1)),
                        paymentMethod = SalePaymentMethod.CASH,
                        note = "Long.MAX_VALUE",
                    ),
                ),
            )

            val result = JsonBackupExporter.export(db, ZoneId.of("UTC"), Long.MAX_VALUE)
            assertTrue(result.suggestedFileName.startsWith("mix_vendas_3d_backup_"))
            assertTrue(result.suggestedFileName.endsWith(".json"))

            // Validação no JSON bruto sem conversão para Double
            val root = JSONObject(result.jsonContent)
            assertExactLongJsonField(root, "exportedAtEpochMs", Long.MAX_VALUE)

            val collections = root.getJSONObject("collections")
            val prefJson = collections.getJSONArray("preferences").getJSONObject(0)
            assertExactLongJsonField(prefJson, "updatedAtEpochMs", Long.MAX_VALUE)

            val productsById = (0 until collections.getJSONArray("products").length())
                .map { collections.getJSONArray("products").getJSONObject(it) }
                .associateBy { it.getString("id") }
            assertExactLongJsonField(productsById.getValue(prodAbove2Pow53.id), "priceCents", 9_007_199_254_740_993L)
            assertExactLongJsonField(productsById.getValue(prodAbove2Pow53.id), "createdAtEpochMs", 9_007_199_254_740_995L)
            assertExactLongJsonField(productsById.getValue(prodMaxLong.id), "priceCents", Long.MAX_VALUE)

            val salesById = (0 until collections.getJSONArray("sales").length())
                .map { collections.getJSONArray("sales").getJSONObject(it) }
                .associateBy { it.getString("id") }
            val jsonSaleAbove = salesById.getValue(saleAbove.id)
            assertExactLongJsonField(jsonSaleAbove, "originalSubtotalCents", 9_007_199_254_740_993L)
            assertExactLongJsonField(jsonSaleAbove, "finalAmountCents", 9_007_199_254_740_993L)
            assertExactLongJsonField(jsonSaleAbove, "netTotalCents", 9_007_199_254_740_993L)
            assertExactLongJsonField(jsonSaleAbove, "soldAtEpochMs", 9_007_199_254_740_999L)

            val jsonSaleMax = salesById.getValue(saleMax.id)
            assertExactLongJsonField(jsonSaleMax, "finalAmountCents", Long.MAX_VALUE)
            assertExactLongJsonField(jsonSaleMax, "netTotalCents", Long.MAX_VALUE)

            // Round-trip via Moshi (preserva Long exato e mantém Int em pontos-base)
            val parsedDoc = JsonBackupExporter.parseBackupDocument(result.jsonContent)
            assertNotNull(parsedDoc)
            assertEquals(Long.MAX_VALUE, parsedDoc!!.exportedAtEpochMs)
            assertEquals(9_007_199_254_740_993L, parsedDoc.collections.products.first { it.id == prodAbove2Pow53.id }.priceCents)
            assertEquals(Long.MAX_VALUE, parsedDoc.collections.products.first { it.id == prodMaxLong.id }.priceCents)
            assertEquals(splitA, parsedDoc.collections.productParticipants.first { it.productId == prodAbove2Pow53.id && it.personId == p1.id }.amountCents)
            assertEquals(splitB, parsedDoc.collections.productParticipants.first { it.productId == prodAbove2Pow53.id && it.personId == p2.id }.amountCents)
            assertEquals(0, parsedDoc.collections.sales.first { it.id == saleAbove.id }.effectiveFeeRateBps)
        } finally {
            db.close()
        }
    }

    @Test
    fun testValidationRejectsNegativeMonetaryValuesAndKeepsDatabaseIntact() = runBlocking<Unit>(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            db.preferenceDao().upsert(PreferenceEntity("valid_pref", "sim", 1_730_000_000_000L))
            val sqlite = db.openHelper.writableDatabase
            sqlite.execSQL(
                """
                INSERT INTO sales (
                    id, sold_at_epoch_ms, updated_at_epoch_ms, payment_method,
                    original_subtotal_cents, discount_total_cents, final_amount_cents,
                    fee_total_cents, net_total_cents, discount_mode,
                    discount_target_product_id, discount_target_product_name,
                    machine_id, machine_name, machine_debit_rate_bps, machine_credit_default_rate_bps,
                    machine_brand_rates_enabled, machine_installments_enabled,
                    card_brand, installments, automatic_rate_bps, manual_rate_override_bps,
                    effective_fee_rate_bps, note
                ) VALUES (
                    'sale_neg', 1730000000000, 1730000000000, 'CASH',
                    1000, 0, 1000,
                    -10, 1010, 'PROPORTIONAL',
                    NULL, NULL,
                    NULL, NULL, NULL, NULL,
                    NULL, NULL,
                    NULL, NULL, NULL, NULL,
                    0, ''
                )
                """.trimIndent(),
            )
            val beforeSnapshot = JsonBackupExporter.captureConsistentSnapshot(db)

            val ex = assertThrows(JsonBackupValidationException::class.java) {
                runBlocking(Dispatchers.IO) {
                    JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L)
                }
            }
            assertEquals("sale_neg", ex.entityId)

            val afterSnapshot = JsonBackupExporter.captureConsistentSnapshot(db)
            assertEquals(beforeSnapshot, afterSnapshot)
            assertEquals("sim", db.preferenceDao().getByKey("valid_pref")?.value)
        } finally {
            db.close()
        }

        // Também rejeita valor negativo em produto/participante no snapshot
        val person = PersonEntity("p1", "Ana", "ana", 0, 1L, 1L)
        val prodNeg = ProductEntity("prod1", "P", -500L, true, "PROPORTIONAL", null, 1L, 1L)
        val partNeg = ProductParticipantEntity("prod1", "p1", -500L, 0)
        assertThrows(JsonBackupValidationException::class.java) {
            JsonBackupExporter.serializeSnapshot(
                baseSnapshot(
                    people = listOf(person),
                    products = listOf(prodNeg),
                    productParticipants = listOf(partNeg),
                ),
            )
        }
    }

    @Test
    fun testValidationRejectsInconsistentSaleFinancialSumsAndIdentifiesSaleId() {
        val (sale, item, pair) = validSingleItemSaleFixture(saleId = "sale-inconsistente")
        val (participant, personTotal) = pair

        // 1) Subtotal - desconto != final
        val badDiscountMath = sale.copy(discountTotalCents = 500L)
        val ex1 = assertThrows(JsonBackupValidationException::class.java) {
            JsonBackupExporter.serializeSnapshot(
                baseSnapshot(
                    sales = listOf(badDiscountMath),
                    saleItems = listOf(item),
                    saleItemParticipants = listOf(participant),
                    salePersonTotals = listOf(personTotal),
                ),
            )
        }
        assertEquals("sale-inconsistente", ex1.entityId)
        assertTrue(ex1.message.orEmpty().contains("sale-inconsistente"))

        // 2) Final - tarifa != líquido
        val badNetMath = sale.copy(netTotalCents = 9_999L)
        val ex2 = assertThrows(JsonBackupValidationException::class.java) {
            JsonBackupExporter.serializeSnapshot(
                baseSnapshot(
                    sales = listOf(badNetMath),
                    saleItems = listOf(item),
                    saleItemParticipants = listOf(participant),
                    salePersonTotals = listOf(personTotal),
                ),
            )
        }
        assertEquals("sale-inconsistente", ex2.entityId)

        // 3) Soma dos itens != total da venda
        val badItemFinal = item.copy(finalAmountCents = 9_500L, netAmountCents = 9_500L)
        val ex3 = assertThrows(JsonBackupValidationException::class.java) {
            JsonBackupExporter.serializeSnapshot(
                baseSnapshot(
                    sales = listOf(sale),
                    saleItems = listOf(badItemFinal),
                    saleItemParticipants = listOf(participant.copy(grossAmountCents = 9_500L, netAmountCents = 9_500L)),
                    salePersonTotals = listOf(personTotal),
                ),
            )
        }
        assertEquals("sale-inconsistente", ex3.entityId)
    }

    @Test
    fun testValidationRejectsDuplicateParticipants() {
        // 1) Participante duplicado em produto
        val person = PersonEntity("p1", "Ana", "ana", 0, 1L, 1L)
        val product = ProductEntity("prod1", "Item", 10_000L, true, "PROPORTIONAL", null, 1L, 1L)
        val dupProdPart1 = ProductParticipantEntity("prod1", "p1", 5_000L, 0)
        val dupProdPart2 = ProductParticipantEntity("prod1", "p1", 5_000L, 1)

        assertThrows(JsonBackupValidationException::class.java) {
            JsonBackupExporter.serializeSnapshot(
                baseSnapshot(
                    people = listOf(person),
                    products = listOf(product),
                    productParticipants = listOf(dupProdPart1, dupProdPart2),
                ),
            )
        }

        // 2) Participante duplicado em item de venda
        val (sale, item, pair) = validSingleItemSaleFixture(saleId = "sale-dup")
        val (participant, personTotal) = pair
        val dupSalePart1 = participant.copy(grossAmountCents = 5_000L, netAmountCents = 5_000L)
        val dupSalePart2 = participant.copy(sortOrder = 1, grossAmountCents = 5_000L, netAmountCents = 5_000L)
        val ex = assertThrows(JsonBackupValidationException::class.java) {
            JsonBackupExporter.serializeSnapshot(
                baseSnapshot(
                    sales = listOf(sale),
                    saleItems = listOf(item),
                    saleItemParticipants = listOf(dupSalePart1, dupSalePart2),
                    salePersonTotals = listOf(personTotal),
                ),
            )
        }
        assertEquals("sale-dup", ex.entityId)
    }

    @Test
    fun testValidationRejectsInvalidProductRelationshipsAndSplitSum() {
        val ana = PersonEntity("p1", "Ana", "ana", 0, 1L, 1L)
        val beto = PersonEntity("p2", "Beto", "beto", 1, 1L, 1L)
        val product = ProductEntity("prod1", "Vaso", 10_000L, true, "PROPORTIONAL", null, 1L, 1L)

        // 1) Soma dos participantes != preço do produto
        assertThrows(JsonBackupValidationException::class.java) {
            JsonBackupExporter.serializeSnapshot(
                baseSnapshot(
                    people = listOf(ana),
                    products = listOf(product),
                    productParticipants = listOf(ProductParticipantEntity("prod1", "p1", 9_999L, 0)),
                ),
            )
        }

        // 2) Participante aponta para pessoa inexistente
        assertThrows(JsonBackupValidationException::class.java) {
            JsonBackupExporter.serializeSnapshot(
                baseSnapshot(
                    people = listOf(ana),
                    products = listOf(product),
                    productParticipants = listOf(ProductParticipantEntity("prod1", "pessoa_fantasma", 10_000L, 0)),
                ),
            )
        }

        // 3) Responsável pela tarifa INDIVIDUAL não é participante do produto
        val prodBadOwner = product.copy(
            feeResponsibilityMode = "INDIVIDUAL",
            feeOwnerPersonId = beto.id,
        )
        assertThrows(JsonBackupValidationException::class.java) {
            JsonBackupExporter.serializeSnapshot(
                baseSnapshot(
                    people = listOf(ana, beto),
                    products = listOf(prodBadOwner),
                    productParticipants = listOf(ProductParticipantEntity("prod1", ana.id, 10_000L, 0)),
                ),
            )
        }
    }

    @Test
    fun testValidationRejectsInvalidMachineRelationshipsAndRateOverflow() {
        val machine = MachineEntity("m1", "Stone", "stone", 199, true, 9_500, true, true, 1L, 1L)

        // 1) Taxa de bandeira aponta para maquininha inexistente
        assertThrows(JsonBackupValidationException::class.java) {
            JsonBackupExporter.serializeSnapshot(
                baseSnapshot(
                    machines = listOf(machine),
                    machineBrandRates = listOf(MachineBrandRateEntity("m_inexistente", "VISA", 300)),
                ),
            )
        }

        // 2) Parcelamento não suportado (ex.: 25x)
        assertThrows(JsonBackupValidationException::class.java) {
            JsonBackupExporter.serializeSnapshot(
                baseSnapshot(
                    machines = listOf(machine),
                    machineInstallmentRates = listOf(MachineInstallmentRateEntity("m1", 25, 100)),
                ),
            )
        }

        // 3) Soma taxa crédito + adicional > 10.000 bps (100%)
        assertThrows(JsonBackupValidationException::class.java) {
            JsonBackupExporter.serializeSnapshot(
                baseSnapshot(
                    machines = listOf(machine),
                    machineInstallmentRates = listOf(MachineInstallmentRateEntity("m1", 2, 600)),
                ),
            )
        }
    }

    @Test
    fun testValidationRejectsOrphanSaleItemParticipantAndOrphanSalePersonTotal() {
        val (sale, item, pair) = validSingleItemSaleFixture()
        val (participant, personTotal) = pair

        // 1) sale_item_participant órfão
        assertThrows(JsonBackupValidationException::class.java) {
            JsonBackupExporter.serializeSnapshot(
                baseSnapshot(
                    sales = listOf(sale),
                    saleItems = listOf(item),
                    saleItemParticipants = listOf(participant, participant.copy(saleItemId = "item_inexistente")),
                    salePersonTotals = listOf(personTotal),
                ),
            )
        }

        // 2) sale_person_total órfão
        assertThrows(JsonBackupValidationException::class.java) {
            JsonBackupExporter.serializeSnapshot(
                baseSnapshot(
                    sales = listOf(sale),
                    saleItems = listOf(item),
                    saleItemParticipants = listOf(participant),
                    salePersonTotals = listOf(personTotal, personTotal.copy(saleId = "venda_inexistente")),
                ),
            )
        }
    }

    @Test
    fun testValidationRejectsDivergentPersonTotalsAndIdentifiesSaleId() {
        val (sale, item, pair) = validSingleItemSaleFixture(saleId = "sale-total-divergente")
        val (participant, personTotal) = pair

        val divergentTotal = personTotal.copy(netAmountCents = 9_998L)
        val ex = assertThrows(JsonBackupValidationException::class.java) {
            JsonBackupExporter.serializeSnapshot(
                baseSnapshot(
                    sales = listOf(sale),
                    saleItems = listOf(item),
                    saleItemParticipants = listOf(participant),
                    salePersonTotals = listOf(divergentTotal),
                ),
            )
        }
        assertEquals("sale-total-divergente", ex.entityId)
        assertTrue(ex.message.orEmpty().contains("sale-total-divergente"))
    }

    @Test
    fun testHistoricalSaleWithDeletedPersonProductAndMachineRemainsExportable() = runBlocking<Unit>(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = { 1_730_000_000_000L })
            val machineRepo = RoomMachineRepository(db.machineDao(), clockMillis = { 1_730_000_000_000L })
            val saleRepo = RoomSaleRepository(db, db.saleDao(), clockMillis = { 1_730_000_100_000L })

            val ana = catalogRepo.createPerson("Ana Removida Depois")
            val produto = catalogRepo.createProduct(
                name = "Produto Removido Depois",
                price = MoneyCents(15_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(15_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = ana.id,
            )
            val machine = machineRepo.createMachine(
                MachineInputDraft(
                    name = "Maquininha Removida",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 450,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 400),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(2 to 150),
                ),
            )

            val savedSale = saleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(produto, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        cardBrand = CardBrand.VISA,
                        installments = 2,
                        note = "Histórico independente do catálogo",
                    ),
                ),
            )

            // Remove completamente maquininha, produto e pessoa do catálogo (mantendo apenas o snapshot da venda)
            db.machineDao().deleteById(machine.id)
            val sqlite = db.openHelper.writableDatabase
            sqlite.execSQL("DELETE FROM products WHERE id = ?", arrayOf(produto.id))
            catalogRepo.deletePerson(ana.id)

            val result = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_500_000L)
            val root = JSONObject(result.jsonContent)
            val counts = root.getJSONObject("counts")
            assertEquals(0, counts.getInt("people"))
            assertEquals(0, counts.getInt("products"))
            assertEquals(0, counts.getInt("machines"))
            assertEquals(1, counts.getInt("sales"))
            assertEquals(1, counts.getInt("saleItems"))

            val exportedSale = root.getJSONObject("collections").getJSONArray("sales").getJSONObject(0)
            assertEquals(savedSale.id, exportedSale.getString("id"))
            assertEquals("Maquininha Removida", exportedSale.getString("machineName"))
            assertExactLongJsonField(exportedSale, "finalAmountCents", 15_000L)
            assertExactLongJsonField(exportedSale, "feeTotalCents", savedSale.calculation.feeTotal.rawCents)
            assertExactLongJsonField(exportedSale, "netTotalCents", savedSale.calculation.netTotal.rawCents)
        } finally {
            db.close()
        }
    }
}
