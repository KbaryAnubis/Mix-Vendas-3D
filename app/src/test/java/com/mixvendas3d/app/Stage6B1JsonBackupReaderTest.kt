package com.mixvendas3d.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.PreferenceEntity
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.JsonBackupErrorCode
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
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
class Stage6B1JsonBackupReaderTest {

    private val app: MixVendasApplication
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
    }

    private fun createPopulatedDatabase(): Pair<AppDatabase, String> = runBlocking(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var seq = 0
        val idGen = {
            seq += 1
            "id-$seq"
        }
        val clock = { 1_730_000_100_000L }

        db.preferenceDao().upsert(PreferenceEntity("theme_pref", "dark", 1_730_000_000_000L))
        val catalogRepo = RoomCatalogRepository(
            personDao = db.personDao(),
            productDao = db.productDao(),
            idGenerator = idGen,
            clockMillis = clock,
        )
        val machineRepo = RoomMachineRepository(
            machineDao = db.machineDao(),
            idGenerator = idGen,
            clockMillis = clock,
        )
        val saleRepo = RoomSaleRepository(
            database = db,
            saleDao = db.saleDao(),
            idGenerator = idGen,
            clockMillis = clock,
        )

        val ana = catalogRepo.createPerson("Ana 'Café' & 3D")
        val bruno = catalogRepo.createPerson("Bruno Ação")

        val prod = catalogRepo.createProduct(
            name = "Dragão \"Especial\" — Ação",
            price = MoneyCents(10_000L),
            active = true,
            participants = listOf(
                ProductParticipantDraft(ana.id, MoneyCents(6_000L)),
                ProductParticipantDraft(bruno.id, MoneyCents(4_000L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerPersonId = ana.id,
        )

        val machine = machineRepo.createMachine(
            MachineInputDraft(
                name = "Maquininha Central",
                debitRateBps = 199,
                creditEnabled = true,
                creditDefaultRateBps = 450,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.VISA to 399),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(2 to 150),
            ),
        )

        val sale = saleRepo.saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 2)),
                    finalAmountOverride = MoneyCents(18_000L),
                    discountMode = SaleDiscountInput.SpecificProduct(prod.id),
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machine,
                    cardBrand = CardBrand.VISA,
                    installments = 2,
                    note = "Observação: café, coração & 'aspas'",
                ),
            ),
        )

        db to sale.id
    }

    @Test
    fun testValidEmptyBackupReadAndMetadata() = runBlocking<Unit>(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val exported = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L)
            val result = JsonBackupReader.readAndValidate(exported.jsonContent)

            assertTrue(result.isValid)
            assertTrue(result.errors.isEmpty())
            assertEquals("MIX_VENDAS_3D_BACKUP", result.format)
            assertEquals(1, result.backupSchemaVersion)
            assertEquals("com.mixvendas3d.app", result.applicationId)
            assertEquals(7, result.roomSchemaVersion)
            assertEquals(1_730_000_000_000L, result.exportedAtEpochMs)
            assertEquals(0, result.peopleCount)
            assertEquals(0, result.productsCount)
            assertEquals(0, result.machinesCount)
            assertEquals(0, result.salesCount)
            assertEquals(0, result.saleItemsCount)
            assertEquals(0, result.participantsCount)
            assertEquals(0, result.totalRecordsCount)
            assertNotNull(result.validatedData)
        } finally {
            db.close()
        }
    }

    @Test
    fun testValidFullBackupReadAndAll11CollectionsCounts() = runBlocking<Unit>(Dispatchers.IO) {
        val (db, saleId) = createPopulatedDatabase()
        try {
            val exported = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_500_000L)
            val result = JsonBackupReader.readAndValidate(exported.utf8Bytes)

            assertTrue(result.isValid)
            assertTrue(result.errors.isEmpty())
            assertEquals(1, result.backupSchemaVersion)
            assertEquals(1_730_000_500_000L, result.exportedAtEpochMs)
            assertEquals(2, result.peopleCount)
            assertEquals(1, result.productsCount)
            assertEquals(1, result.machinesCount)
            assertEquals(1, result.salesCount)
            assertEquals(1, result.saleItemsCount)
            assertEquals(4, result.participantsCount) // 2 productParticipants + 2 saleItemParticipants
            assertEquals(1, result.otherCollectionsCounts["preferences"])
            assertEquals(2, result.otherCollectionsCounts["productParticipants"])
            assertEquals(1, result.otherCollectionsCounts["machineBrandRates"])
            assertEquals(1, result.otherCollectionsCounts["machineInstallmentRates"])
            assertEquals(2, result.otherCollectionsCounts["saleItemParticipants"])
            assertEquals(2, result.otherCollectionsCounts["salePersonTotals"])
            assertEquals(15, result.totalRecordsCount)

            val data = result.validatedData
            assertNotNull(data)
            assertEquals(saleId, data!!.sales.single().id)
        } finally {
            db.close()
        }
    }

    @Test
    fun testInvalidJsonSyntaxAndTruncatedOrTrailingInputRejected() {
        val malformed = JsonBackupReader.readAndValidate("{ invalido")
        assertFalse(malformed.isValid)
        assertNull(malformed.validatedData)
        assertEquals(JsonBackupErrorCode.SYNTAX_ERROR, malformed.errors.first().code)

        val empty = JsonBackupReader.readAndValidate("   ")
        assertFalse(empty.isValid)
        assertNull(empty.validatedData)
        assertEquals(JsonBackupErrorCode.EMPTY_INPUT, empty.errors.first().code)

        val emptyBytes = JsonBackupReader.readAndValidate(ByteArray(0))
        assertFalse(emptyBytes.isValid)
        assertEquals(JsonBackupErrorCode.EMPTY_INPUT, emptyBytes.errors.first().code)

        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val validJson = runBlocking(Dispatchers.IO) {
                JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L).jsonContent
            }
            val trailingGarbage = JsonBackupReader.readAndValidate("$validJson lixo_extra")
            assertFalse(trailingGarbage.isValid)
            assertNull(trailingGarbage.validatedData)
            assertEquals(JsonBackupErrorCode.SYNTAX_ERROR, trailingGarbage.errors.first().code)

            val invalidUtf8 = JsonBackupReader.readAndValidate(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFD.toByte()))
            assertFalse(invalidUtf8.isValid)
            assertEquals(JsonBackupErrorCode.INVALID_ENCODING, invalidUtf8.errors.first().code)
        } finally {
            db.close()
        }
    }

    @Test
    fun testUnknownFormatAndWrongApplicationIdRejected() = runBlocking<Unit>(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val baseJson = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L).jsonContent

            val unknownFormatJson = JSONObject(baseJson).apply {
                put("format", "OUTRO_APP_BACKUP")
            }.toString()
            val res1 = JsonBackupReader.readAndValidate(unknownFormatJson)
            assertFalse(res1.isValid)
            assertNull(res1.validatedData)
            assertTrue(res1.errors.any { it.code == JsonBackupErrorCode.UNSUPPORTED_FORMAT })

            val wrongAppIdJson = JSONObject(baseJson).apply {
                put("applicationId", "com.outro.app")
            }.toString()
            val res2 = JsonBackupReader.readAndValidate(wrongAppIdJson)
            assertFalse(res2.isValid)
            assertNull(res2.validatedData)
            assertTrue(res2.errors.any { it.code == JsonBackupErrorCode.INCOMPATIBLE_APPLICATION_ID })
        } finally {
            db.close()
        }
    }

    @Test
    fun testIncompatibleSchemaVersionAndRoomVersionRejected() = runBlocking<Unit>(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val baseJson = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L).jsonContent

            val futureSchema = JSONObject(baseJson).apply {
                put("backupSchemaVersion", 2)
            }.toString()
            val resSchema = JsonBackupReader.readAndValidate(futureSchema)
            assertFalse(resSchema.isValid)
            assertNull(resSchema.validatedData)
            assertTrue(resSchema.errors.any { it.code == JsonBackupErrorCode.INCOMPATIBLE_SCHEMA_VERSION })

            val futureRoom = JSONObject(baseJson).apply {
                put("roomSchemaVersion", 99)
            }.toString()
            val resRoom = JsonBackupReader.readAndValidate(futureRoom)
            assertFalse(resRoom.isValid)
            assertNull(resRoom.validatedData)
            assertTrue(resRoom.errors.any { it.code == JsonBackupErrorCode.INCOMPATIBLE_ROOM_VERSION })

            // Também rejeita tipo inválido em versão (ex.: string ou decimal)
            val decimalVersion = JSONObject(baseJson).apply {
                put("backupSchemaVersion", 1.5)
            }.toString()
            val resDecimal = JsonBackupReader.readAndValidate(decimalVersion)
            assertFalse(resDecimal.isValid)
            assertNull(resDecimal.validatedData)
        } finally {
            db.close()
        }
    }

    @Test
    fun testMissingRequiredCollectionAndMissingHeaderFieldRejected() = runBlocking<Unit>(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val baseJson = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L).jsonContent

            val missingSalesCollection = JSONObject(baseJson).apply {
                getJSONObject("collections").remove("sales")
            }.toString()
            val resCol = JsonBackupReader.readAndValidate(missingSalesCollection)
            assertFalse(resCol.isValid)
            assertNull(resCol.validatedData)
            assertEquals(JsonBackupErrorCode.MISSING_COLLECTION, resCol.errors.first().code)

            // Garante que valores padrão de data class Kotlin não mascaram ausência de campos do cabeçalho
            val missingFormat = JSONObject(baseJson).apply {
                remove("format")
            }.toString()
            val resHeader = JsonBackupReader.readAndValidate(missingFormat)
            assertFalse(resHeader.isValid)
            assertNull(resHeader.validatedData)
            assertEquals(JsonBackupErrorCode.MISSING_REQUIRED_FIELD, resHeader.errors.first().code)
        } finally {
            db.close()
        }
    }

    @Test
    fun testCountMismatchAndNegativeCountRejected() = runBlocking<Unit>(Dispatchers.IO) {
        val (db, _) = createPopulatedDatabase()
        try {
            val baseJson = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L).jsonContent

            val mismatched = JSONObject(baseJson).apply {
                getJSONObject("counts").put("sales", 99)
            }.toString()
            val resMismatch = JsonBackupReader.readAndValidate(mismatched)
            assertFalse(resMismatch.isValid)
            assertNull(resMismatch.validatedData)
            assertTrue(resMismatch.errors.any { it.code == JsonBackupErrorCode.COUNT_MISMATCH })

            val negativeCount = JSONObject(baseJson).apply {
                getJSONObject("counts").put("sales", -1)
            }.toString()
            val resNegative = JsonBackupReader.readAndValidate(negativeCount)
            assertFalse(resNegative.isValid)
            assertNull(resNegative.validatedData)
        } finally {
            db.close()
        }
    }

    @Test
    fun testDuplicateIdsRejected() = runBlocking<Unit>(Dispatchers.IO) {
        val (db, _) = createPopulatedDatabase()
        try {
            val baseJson = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L).jsonContent
            val root = JSONObject(baseJson)
            val people = root.getJSONObject("collections").getJSONArray("people")
            val duplicatePerson = JSONObject(people.getJSONObject(0).toString())
            people.put(duplicatePerson)
            root.getJSONObject("counts").put("people", people.length())

            val result = JsonBackupReader.readAndValidate(root.toString())
            assertFalse(result.isValid)
            assertNull(result.validatedData)
            assertTrue(result.errors.any { it.code == JsonBackupErrorCode.RELATIONAL_OR_FINANCIAL_VALIDATION_ERROR })
        } finally {
            db.close()
        }
    }

    @Test
    fun testOrphanRecordsRejected() = runBlocking<Unit>(Dispatchers.IO) {
        val (db, _) = createPopulatedDatabase()
        try {
            val baseJson = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L).jsonContent
            val root = JSONObject(baseJson)
            val items = root.getJSONObject("collections").getJSONArray("saleItems")
            val orphanItem = JSONObject(items.getJSONObject(0).toString()).apply {
                put("id", "item-orfao")
                put("saleId", "venda-inexistente")
            }
            items.put(orphanItem)
            root.getJSONObject("counts").put("saleItems", items.length())

            val result = JsonBackupReader.readAndValidate(root.toString())
            assertFalse(result.isValid)
            assertNull(result.validatedData)
            val err = result.errors.first { it.code == JsonBackupErrorCode.RELATIONAL_OR_FINANCIAL_VALIDATION_ERROR }
            assertEquals("item-orfao", err.entityId)
        } finally {
            db.close()
        }
    }

    @Test
    fun testInconsistentSaleFinancialsRejected() = runBlocking<Unit>(Dispatchers.IO) {
        val (db, saleId) = createPopulatedDatabase()
        try {
            val baseJson = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L).jsonContent
            val root = JSONObject(baseJson)
            val sales = root.getJSONObject("collections").getJSONArray("sales")
            sales.getJSONObject(0).put("netTotalCents", "1")

            val result = JsonBackupReader.readAndValidate(root.toString())
            assertFalse(result.isValid)
            assertNull(result.validatedData)
            val err = result.errors.first { it.code == JsonBackupErrorCode.RELATIONAL_OR_FINANCIAL_VALIDATION_ERROR }
            assertEquals(saleId, err.entityId)
        } finally {
            db.close()
        }
    }

    @Test
    fun testProductWithIncorrectParticipantSplitRejected() = runBlocking<Unit>(Dispatchers.IO) {
        val (db, _) = createPopulatedDatabase()
        try {
            val baseJson = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L).jsonContent
            val root = JSONObject(baseJson)
            val productParticipants = root.getJSONObject("collections").getJSONArray("productParticipants")
            productParticipants.getJSONObject(0).put("amountCents", "5999")

            val result = JsonBackupReader.readAndValidate(root.toString())
            assertFalse(result.isValid)
            assertNull(result.validatedData)
            assertTrue(result.errors.any { it.code == JsonBackupErrorCode.RELATIONAL_OR_FINANCIAL_VALIDATION_ERROR })
        } finally {
            db.close()
        }
    }

    @Test
    fun testMachineWithInvalidRatesOrInstallmentsRejected() = runBlocking<Unit>(Dispatchers.IO) {
        val (db, _) = createPopulatedDatabase()
        try {
            val baseJson = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L).jsonContent

            val badRateRoot = JSONObject(baseJson)
            badRateRoot.getJSONObject("collections").getJSONArray("machines")
                .getJSONObject(0).put("debitRateBps", 10_001)
            val resRate = JsonBackupReader.readAndValidate(badRateRoot.toString())
            assertFalse(resRate.isValid)
            assertNull(resRate.validatedData)
            assertTrue(resRate.errors.any { it.code == JsonBackupErrorCode.RELATIONAL_OR_FINANCIAL_VALIDATION_ERROR })

            val badInstallmentRoot = JSONObject(baseJson)
            badInstallmentRoot.getJSONObject("collections").getJSONArray("machineInstallmentRates")
                .getJSONObject(0).put("installments", 25)
            val resInst = JsonBackupReader.readAndValidate(badInstallmentRoot.toString())
            assertFalse(resInst.isValid)
            assertNull(resInst.validatedData)
            assertTrue(resInst.errors.any { it.code == JsonBackupErrorCode.RELATIONAL_OR_FINANCIAL_VALIDATION_ERROR })
        } finally {
            db.close()
        }
    }

    @Test
    fun testLongPrecisionAbove2Pow53AndUnquotedPrecisionLossRejected() = runBlocking<Unit>(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val priceAbove2Pow53 = 9_007_199_254_740_993L // 2^53 + 1
            val split1 = 5_000_000_000_000_001L
            val split2 = 4_007_199_254_740_992L

            val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = { 9_007_199_254_740_995L })
            val p1 = catalogRepo.createPerson("Sócio 1")
            val p2 = catalogRepo.createPerson("Sócio 2")
            val prod = catalogRepo.createProduct(
                name = "Produto 2^53+1",
                price = MoneyCents(priceAbove2Pow53),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(split1)),
                    ProductParticipantDraft(p2.id, MoneyCents(split2)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val saleRepo = RoomSaleRepository(db, db.saleDao(), clockMillis = { 9_007_199_254_740_999L })
            val savedSale = saleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(items = listOf(SaleItemInput(prod, 1)), paymentMethod = SalePaymentMethod.PIX),
                ),
            )

            val exported = JsonBackupExporter.export(db, ZoneId.of("UTC"), 9_007_199_254_741_001L)
            val validResult = JsonBackupReader.readAndValidate(exported.jsonContent)

            assertTrue(validResult.isValid)
            val data = validResult.validatedData!!
            assertEquals(9_007_199_254_741_001L, data.exportedAtEpochMs)
            assertEquals(priceAbove2Pow53, data.products.single().priceCents)
            assertEquals(priceAbove2Pow53, data.sales.single { it.id == savedSale.id }.finalAmountCents)
            assertEquals(priceAbove2Pow53, data.sales.single { it.id == savedSale.id }.netTotalCents)

            // Rejeita número sem aspas acima de 2^53-1 (risco de perda de precisão IEEE-754)
            val unquotedLarge = exported.jsonContent.replace(
                "\"9007199254740993\"",
                "9007199254740993",
            )
            val resUnquoted = JsonBackupReader.readAndValidate(unquotedLarge)
            assertFalse(resUnquoted.isValid)
            assertEquals(JsonBackupErrorCode.INVALID_LONG_PRECISION_OR_OVERFLOW, resUnquoted.errors.first().code)

            // Rejeita centavos fracionários em string e em número
            val fractionalStr = exported.jsonContent.replace(
                "\"9007199254740993\"",
                "\"100.50\"",
            )
            val resFrac = JsonBackupReader.readAndValidate(fractionalStr)
            assertFalse(resFrac.isValid)
            assertEquals(JsonBackupErrorCode.INVALID_LONG_PRECISION_OR_OVERFLOW, resFrac.errors.first().code)
        } finally {
            db.close()
        }
    }

    @Test
    fun testLongMaxValuePreservedExact() = runBlocking<Unit>(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            db.preferenceDao().upsert(PreferenceEntity("max_ts", "ok", Long.MAX_VALUE))
            val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = { Long.MAX_VALUE })
            val p1 = catalogRepo.createPerson("Pessoa Máxima")
            val prod = catalogRepo.createProduct(
                name = "Produto MAX",
                price = MoneyCents(Long.MAX_VALUE),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(Long.MAX_VALUE))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val saleRepo = RoomSaleRepository(db, db.saleDao(), clockMillis = { Long.MAX_VALUE })
            saleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(items = listOf(SaleItemInput(prod, 1)), paymentMethod = SalePaymentMethod.CASH),
                ),
            )

            val exported = JsonBackupExporter.export(db, ZoneId.of("UTC"), Long.MAX_VALUE)
            val result = JsonBackupReader.readAndValidate(exported.jsonContent)

            assertTrue(result.isValid)
            val data = result.validatedData!!
            assertEquals(Long.MAX_VALUE, data.exportedAtEpochMs)
            assertEquals(Long.MAX_VALUE, data.preferences.single().updatedAtEpochMs)
            assertEquals(Long.MAX_VALUE, data.products.single().priceCents)
            assertEquals(Long.MAX_VALUE, data.sales.single().finalAmountCents)
            assertEquals(Long.MAX_VALUE, data.sales.single().netTotalCents)
        } finally {
            db.close()
        }
    }

    @Test
    fun testLongOverflowRejected() = runBlocking<Unit>(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val baseJson = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L).jsonContent
            val overflowJson = JSONObject(baseJson).apply {
                put("exportedAtEpochMs", "9223372036854775808") // Long.MAX_VALUE + 1
            }.toString()

            val result = JsonBackupReader.readAndValidate(overflowJson)
            assertFalse(result.isValid)
            assertNull(result.validatedData)
            assertEquals(JsonBackupErrorCode.INVALID_LONG_PRECISION_OR_OVERFLOW, result.errors.first().code)
        } finally {
            db.close()
        }
    }

    @Test
    fun testSpecialCharactersAndAccentsPreserved() = runBlocking<Unit>(Dispatchers.IO) {
        val (db, _) = createPopulatedDatabase()
        try {
            val exported = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L)
            val result = JsonBackupReader.readAndValidate(ByteArrayInputStream(exported.utf8Bytes))

            assertTrue(result.isValid)
            val data = result.validatedData!!
            assertTrue(data.people.any { it.name == "Ana 'Café' & 3D" })
            assertTrue(data.people.any { it.name == "Bruno Ação" })
            assertEquals("Dragão \"Especial\" — Ação", data.products.single().name)
            assertEquals("Observação: café, coração & 'aspas'", data.sales.single().note)
        } finally {
            db.close()
        }
    }

    @Test
    fun testHistoricalSaleWithDeletedCatalogEntriesAccepted() = runBlocking<Unit>(Dispatchers.IO) {
        val (db, saleId) = createPopulatedDatabase()
        try {
            val sqlite = db.openHelper.writableDatabase
            val machineId = db.machineDao().getAllMachines().single().id
            val productId = db.productDao().getAllProducts().single().id
            val personIds = db.personDao().getAllPeople().map { it.id }

            sqlite.execSQL("DELETE FROM machines WHERE id = ?", arrayOf(machineId))
            sqlite.execSQL("DELETE FROM products WHERE id = ?", arrayOf(productId))
            personIds.forEach { pid ->
                sqlite.execSQL("DELETE FROM people WHERE id = ?", arrayOf(pid))
            }

            val exported = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_900_000L)
            val result = JsonBackupReader.readAndValidate(exported.jsonContent)

            assertTrue(result.isValid)
            assertEquals(0, result.peopleCount)
            assertEquals(0, result.productsCount)
            assertEquals(0, result.machinesCount)
            assertEquals(1, result.salesCount)
            assertEquals(1, result.saleItemsCount)
            assertEquals(saleId, result.validatedData!!.sales.single().id)
            assertEquals("Maquininha Central", result.validatedData!!.sales.single().machineName)
        } finally {
            db.close()
        }
    }

    @Test
    fun testReadingValidAndInvalidBackupsLeavesRoomDatabaseUntouched() = runBlocking<Unit>(Dispatchers.IO) {
        val (db, _) = createPopulatedDatabase()
        try {
            val backupOfOtherData = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L)

            // Agora cria outro banco local com estado completamente diferente
            val localDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            try {
                localDb.preferenceDao().upsert(PreferenceEntity("local_only", "intacto", 1_730_000_000_000L))
                val localBefore = JsonBackupExporter.captureConsistentSnapshot(localDb)

                // Lê backup válido externo e também um backup inválido
                val validRead = JsonBackupReader.readAndValidate(backupOfOtherData.jsonContent)
                assertTrue(validRead.isValid)
                val invalidRead = JsonBackupReader.readAndValidate("{\"format\":\"INVALID\"}")
                assertFalse(invalidRead.isValid)

                // Garante que o Room local permaneceu 100% intacto (não depende sequer do Room para ler)
                val localAfter = JsonBackupExporter.captureConsistentSnapshot(localDb)
                assertEquals(localBefore, localAfter)
                assertEquals("intacto", localDb.preferenceDao().getByKey("local_only")?.value)
                assertTrue(localDb.personDao().getAllPeople().isEmpty())
                assertTrue(localDb.saleDao().getAllSales().isEmpty())
            } finally {
                localDb.close()
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun testRoundTripExportAndReadPreservesAllDataAndImmutability() = runBlocking<Unit>(Dispatchers.IO) {
        val (db, _) = createPopulatedDatabase()
        try {
            val exported = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_777_000L)
            val parsedByExporter = JsonBackupExporter.parseBackupDocument(exported.jsonContent)!!
            val readResult = JsonBackupReader.readAndValidate(exported.utf8Bytes)

            assertTrue(readResult.isValid)
            val data = readResult.validatedData!!
            assertEquals(parsedByExporter.format, data.format)
            assertEquals(parsedByExporter.backupSchemaVersion, data.backupSchemaVersion)
            assertEquals(parsedByExporter.applicationId, data.applicationId)
            assertEquals(parsedByExporter.roomSchemaVersion, data.roomSchemaVersion)
            assertEquals(parsedByExporter.exportedAtEpochMs, data.exportedAtEpochMs)
            assertEquals(parsedByExporter.collections.preferences, data.preferences)
            assertEquals(parsedByExporter.collections.people, data.people)
            assertEquals(parsedByExporter.collections.products, data.products)
            assertEquals(parsedByExporter.collections.productParticipants, data.productParticipants)
            assertEquals(parsedByExporter.collections.machines, data.machines)
            assertEquals(parsedByExporter.collections.machineBrandRates, data.machineBrandRates)
            assertEquals(parsedByExporter.collections.machineInstallmentRates, data.machineInstallmentRates)
            assertEquals(parsedByExporter.collections.sales, data.sales)
            assertEquals(parsedByExporter.collections.saleItems, data.saleItems)
            assertEquals(parsedByExporter.collections.saleItemParticipants, data.saleItemParticipants)
            assertEquals(parsedByExporter.collections.salePersonTotals, data.salePersonTotals)
            assertEquals(parsedByExporter.counts, data.declaredCounts)

            // Valida imutabilidade real das coleções retornadas para a etapa 6B-2
            val mutablePeople = data.people as MutableList
            assertThrows(UnsupportedOperationException::class.java) {
                mutablePeople.clear()
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun testOversizedBackupRejectedWithoutUnlimitedMemoryConsumption() = runBlocking<Unit>(Dispatchers.IO) {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val exported = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L)
            val tooSmallLimit = 64

            val resString = JsonBackupReader.readAndValidate(exported.jsonContent, maxBytes = tooSmallLimit)
            assertFalse(resString.isValid)
            assertEquals(JsonBackupErrorCode.FILE_TOO_LARGE, resString.errors.first().code)

            val resBytes = JsonBackupReader.readAndValidate(exported.utf8Bytes, maxBytes = tooSmallLimit)
            assertFalse(resBytes.isValid)
            assertEquals(JsonBackupErrorCode.FILE_TOO_LARGE, resBytes.errors.first().code)

            // Simula fluxo infinito que falharia com OOM se fosse lido integralmente
            val endlessZeroStream = object : InputStream() {
                var reads = 0
                override fun read(): Int = '{'.code
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    reads += 1
                    b.fill(' '.code.toByte(), off, off + len)
                    return len
                }
            }
            val resStream = JsonBackupReader.readAndValidate(endlessZeroStream, maxBytes = 1024)
            assertFalse(resStream.isValid)
            assertEquals(JsonBackupErrorCode.FILE_TOO_LARGE, resStream.errors.first().code)
            assertTrue("Leitura deve abortar logo ao ultrapassar maxBytes", endlessZeroStream.reads <= 2)
        } finally {
            db.close()
        }
    }
}
