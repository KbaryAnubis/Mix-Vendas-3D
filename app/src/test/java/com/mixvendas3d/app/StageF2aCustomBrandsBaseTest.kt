package com.mixvendas3d.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.MachineBrandRateEntity
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.domain.calculation.CustomCardBrandInput
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.MachineValidationException
import com.mixvendas3d.app.domain.calculation.MachineValidator
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.PreviewPaymentMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class StageF2aCustomBrandsBaseTest {

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
    fun `01 key codec generates deterministic colon-free reversible keys for accented names and rejects malformed keys`() {
        val encodedAccented = CustomBrandKeyCodec.encodeKey("  Cartão   VIP São Paulo ")
        assertTrue(encodedAccented.startsWith(CustomBrandKeyCodec.CUSTOM_BRAND_PREFIX))
        assertFalse(encodedAccented.contains(':'))
        assertEquals("Cartão VIP São Paulo", CustomBrandKeyCodec.decodeNameOrNull(encodedAccented))
        assertTrue(CustomBrandKeyCodec.isValidCustomBrandKey(encodedAccented))
        assertTrue(CustomBrandKeyCodec.isKnownBrandKey(encodedAccented))

        val credzKey = CustomBrandKeyCodec.encodeKey("Crédz")
        assertEquals("Crédz", CustomBrandKeyCodec.decodeNameOrNull(credzKey))
        assertEquals(credzKey, CustomBrandKeyCodec.encodeKey("Crédz"))
        assertFalse(credzKey.contains(':'))

        // Normalização ignora maiúsculas, acentos e espaços múltiplos
        assertEquals(
            CustomBrandKeyCodec.normalizeBrandName("Crédz"),
            CustomBrandKeyCodec.normalizeBrandName("  crEdz  "),
        )
        assertEquals(
            CustomBrandKeyCodec.normalizeBrandName("Cartão   São  Paulo"),
            CustomBrandKeyCodec.normalizeBrandName("cartao sao paulo"),
        )

        // Todas as 13 bandeiras padrão são chaves conhecidas e não são chaves personalizadas
        assertEquals(13, CardBrand.entries.size)
        CardBrand.entries.forEach { brand ->
            assertTrue(CustomBrandKeyCodec.isKnownBrandKey(brand.storageKey))
            assertFalse(CustomBrandKeyCodec.isValidCustomBrandKey(brand.storageKey))
        }

        // Rejeita chaves inválidas, nulas, com ':', hex maiúsculo, bytes UTF-8 inválidos ou não canônicas
        assertNull(CustomBrandKeyCodec.decodeNameOrNull(null))
        assertNull(CustomBrandKeyCodec.decodeNameOrNull(""))
        assertNull(CustomBrandKeyCodec.decodeNameOrNull("CUSTOM_"))
        assertNull(CustomBrandKeyCodec.decodeNameOrNull("CUSTOM:76616c65"))
        assertEquals(":", CustomBrandKeyCodec.decodeNameOrNull("CUSTOM_3a"))
        assertFalse("CUSTOM_3a".contains(':'))
        assertNull(CustomBrandKeyCodec.decodeNameOrNull("CUSTOM_766")) // comprimento ímpar
        assertNull(CustomBrandKeyCodec.decodeNameOrNull("CUSTOM_76ZZ")) // hex inválido
        assertEquals("AB", CustomBrandKeyCodec.decodeNameOrNull("CUSTOM_4142"))
        assertNull(CustomBrandKeyCodec.decodeNameOrNull("CUSTOM_4A4B")) // hex maiúsculo não permitido
        assertNull(CustomBrandKeyCodec.decodeNameOrNull("CUSTOM_ff")) // UTF-8 malformado
        assertNull(CustomBrandKeyCodec.decodeNameOrNull("CUSTOM_2061")) // espaço inicial não sanitizado (" a")
        assertNull(CustomBrandKeyCodec.decodeNameOrNull("OTHER"))
        assertFalse(CustomBrandKeyCodec.isKnownBrandKey("CUSTOM_ff"))
        assertFalse(CustomBrandKeyCodec.isKnownBrandKey("LEGACY_UNKNOWN:1"))

        assertThrows(IllegalArgumentException::class.java) {
            CustomBrandKeyCodec.encodeKey("   ")
        }
    }

    @Test
    fun `02 validator enforces accent-insensitive uniqueness, standard brand collisions, rate bounds and installment sum`() {
        // Rejeita nome vazio
        assertThrows(MachineValidationException::class.java) {
            MachineValidator.validateCustomBrands(
                listOf(CustomCardBrandInput(name = "   ", rateBps = 250)),
            )
        }

        // Rejeita duplicadas ignorando maiúsculas/minúsculas, espaços e acentos
        assertThrows(MachineValidationException::class.java) {
            MachineValidator.validateCustomBrands(
                listOf(
                    CustomCardBrandInput(name = "Crédz", rateBps = 310),
                    CustomCardBrandInput(name = "  credz ", rateBps = 350),
                ),
            )
        }
        assertThrows(MachineValidationException::class.java) {
            MachineValidator.validateCustomBrands(
                listOf(
                    CustomCardBrandInput(name = "Cartão   Loja", rateBps = 300),
                    CustomCardBrandInput(name = "CARTAO LOJA", rateBps = 310),
                ),
            )
        }

        // Rejeita colisão com qualquer uma das 13 bandeiras padrão
        assertThrows(MachineValidationException::class.java) {
            MachineValidator.validateCustomBrands(
                listOf(CustomCardBrandInput(name = "VÍSA", rateBps = 250)),
            )
        }
        assertThrows(MachineValidationException::class.java) {
            MachineValidator.validateCustomBrands(
                listOf(CustomCardBrandInput(name = "american   express", rateBps = 390)),
            )
        }
        assertThrows(MachineValidationException::class.java) {
            MachineValidator.validateCustomBrands(
                listOf(CustomCardBrandInput(name = "Outros", rateBps = 300)),
            )
        }

        // Rejeita taxa fora dos limites 0..10.000 bps
        assertThrows(MachineValidationException::class.java) {
            MachineValidator.validateCustomBrands(
                listOf(CustomCardBrandInput(name = "Credz", rateBps = -1)),
            )
        }
        assertThrows(MachineValidationException::class.java) {
            MachineValidator.validateCustomBrands(
                listOf(CustomCardBrandInput(name = "Credz", rateBps = 10_001)),
            )
        }

        // Rejeita chave explícita inválida ou incompatível com o nome
        assertThrows(MachineValidationException::class.java) {
            MachineValidator.validateCustomBrands(
                listOf(CustomCardBrandInput(name = "Credz", rateBps = 300, key = "CUSTOM:1234")),
            )
        }
        assertThrows(MachineValidationException::class.java) {
            MachineValidator.validateCustomBrands(
                listOf(
                    CustomCardBrandInput(
                        name = "Credz",
                        rateBps = 300,
                        key = CustomBrandKeyCodec.encodeKey("Vale Loja"),
                    ),
                ),
            )
        }

        // Rejeita combinação de taxa personalizada + adicional > 100% (10.000 bps)
        assertThrows(MachineValidationException::class.java) {
            MachineValidator.validateMachineInput(
                MachineInputDraft(
                    name = "Stone",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = true,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 12 to 600),
                    customBrandRates = listOf(
                        CustomCardBrandInput(name = "Crédz", rateBps = 9_500),
                    ),
                ),
            )
        }

        // Aceita limites válidos exatos (0% e 100% combinado)
        val valid = MachineValidator.validateMachineInput(
            MachineInputDraft(
                name = "Stone",
                debitRateBps = 190,
                creditEnabled = true,
                creditDefaultRateBps = 300,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.VISA to 250),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(1 to 0, 12 to 500),
                customBrandRates = listOf(
                    CustomCardBrandInput(name = "Taxa Zero", rateBps = 0),
                    CustomCardBrandInput(name = "Crédz", rateBps = 9_500),
                ),
            ),
        )
        val custom = requireNotNull(valid.customBrandRates)
        assertEquals(2, custom.size)
        assertEquals("Taxa Zero", custom[0].name)
        assertEquals(0, custom[0].rateBps)
        assertEquals("Crédz", custom[1].name)
        assertEquals(9_500, custom[1].rateBps)
    }

    @Test
    fun `03 persists two custom brands alongside all 13 standard brands across db reopen without converting custom to OTHER`() = runBlocking<Unit>(Dispatchers.IO) {
        val dbName = newUniqueDbName("persist_reopen")
        val all13Rates = CardBrand.entries.mapIndexed { index, brand ->
            brand to (210 + index * 15)
        }.toMap()
        assertEquals(13, all13Rates.size)

        val db1 = openFileDb(dbName)
        val repo1 = RoomMachineRepository(db1.machineDao())
        val created = repo1.createMachine(
            MachineInputDraft(
                name = "Ton Custom",
                debitRateBps = 175,
                creditEnabled = true,
                creditDefaultRateBps = 350,
                brandRatesEnabled = true,
                brandRatesBps = all13Rates,
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(1 to 0, 2 to 120, 6 to 340),
                maxInstallments = 12,
                customBrandRates = listOf(
                    CustomCardBrandInput(name = "Crédz", rateBps = 415),
                    CustomCardBrandInput(name = "Cartão VIP São Paulo", rateBps = 289),
                ),
            ),
        )
        assertEquals(13, created.brandRatesBps.size)
        assertEquals(2, created.customBrandRates.size)

        // Insere diretamente um registro desconhecido/malformado para garantir que nunca seja convertido para CardBrand.OTHER
        db1.machineDao().insertBrandRates(
            listOf(
                MachineBrandRateEntity(
                    machineId = created.id,
                    brandKey = "LEGACY_BROKEN:KEY",
                    rateBps = 999,
                ),
            ),
        )
        db1.close()

        val db2 = openFileDb(dbName)
        try {
            val repo2 = RoomMachineRepository(db2.machineDao())
            val reloadedList = repo2.observeMachines().first()
            assertEquals(1, reloadedList.size)
            val reloaded = reloadedList.first()

            // Todas as 13 bandeiras padrão mantêm seus valores originais (OTHER não foi sobrescrita pela chave desconhecida)
            assertEquals(13, reloaded.brandRatesBps.size)
            all13Rates.forEach { (brand, expectedRate) ->
                assertEquals(expectedRate, reloaded.brandRatesBps[brand])
            }
            assertEquals(210 + CardBrand.OTHER.ordinal * 15, reloaded.brandRatesBps[CardBrand.OTHER])

            // As 2 bandeiras personalizadas voltam íntegras com nomes acentuados e chaves válidas sem ':'
            assertEquals(2, reloaded.customBrandRates.size)
            val vip = reloaded.customBrandRates.first { it.name == "Cartão VIP São Paulo" }
            val credz = reloaded.customBrandRates.first { it.name == "Crédz" }
            assertEquals(289, vip.rateBps)
            assertEquals(415, credz.rateBps)
            assertFalse(vip.key.contains(':'))
            assertFalse(credz.key.contains(':'))

            // Cálculo automático por chave funciona para padrão e personalizada
            assertEquals(210, reloaded.effectiveAutomaticRateBpsForKey(PreviewPaymentMethod.CREDIT, CardBrand.VISA.storageKey, 1))
            assertEquals(289 + 120, reloaded.effectiveAutomaticRateBpsForKey(PreviewPaymentMethod.CREDIT, vip.key, 2))
            assertEquals(415 + 340, reloaded.effectiveAutomaticRateBpsForKey(PreviewPaymentMethod.CREDIT, credz.key, 6))
            assertTrue(reloaded.usesBrandSpecificRateForKey(credz.key))
            assertFalse(reloaded.usesBrandSpecificRateForKey("CUSTOM_696e6578697374656e74"))
        } finally {
            db2.close()
        }
    }

    @Test
    fun `04 updateMachine preserves custom brands when null and validates combinations, edits explicitly and isolates per machine`() = runBlocking<Unit>(Dispatchers.IO) {
        val db = newInMemoryDb()
        try {
            val repo = RoomMachineRepository(db.machineDao())

            val machineA = repo.createMachine(
                MachineInputDraft(
                    name = "Maquininha A",
                    debitRateBps = 180,
                    creditEnabled = true,
                    creditDefaultRateBps = 310,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 240, CardBrand.OTHER to 390),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 3 to 200),
                    maxInstallments = 10,
                    customBrandRates = listOf(
                        CustomCardBrandInput(name = "Crédz", rateBps = 450),
                        CustomCardBrandInput(name = "Vale Loja", rateBps = 500),
                    ),
                ),
            )

            // Segunda maquininha pode cadastrar o mesmo nome "Crédz" com taxa independente (isolamento por maquininha)
            val machineB = repo.createMachine(
                MachineInputDraft(
                    name = "Maquininha B",
                    debitRateBps = 150,
                    creditEnabled = true,
                    creditDefaultRateBps = 290,
                    brandRatesEnabled = true,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                    customBrandRates = listOf(
                        CustomCardBrandInput(name = "Crédz", rateBps = 320),
                    ),
                ),
            )

            assertEquals(2, machineA.customBrandRates.size)
            assertEquals(1, machineB.customBrandRates.size)
            assertEquals(450, machineA.customBrandRates.first { it.name == "Crédz" }.rateBps)
            assertEquals(320, machineB.customBrandRates.first { it.name == "Crédz" }.rateBps)

            // Edição da maquininha A sem informar customBrandRates (null) deve preservar integralmente suas 2 bandeiras personalizadas
            val updatedPreserve = repo.updateMachine(
                machineId = machineA.id,
                draft = MachineInputDraft(
                    name = "Maquininha A Editada",
                    debitRateBps = 170,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 230),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 3 to 250),
                    customBrandRates = null,
                ),
            )
            assertEquals("Maquininha A Editada", updatedPreserve.name)
            assertEquals(2, updatedPreserve.customBrandRates.size)
            assertEquals(450, updatedPreserve.customBrandRates.first { it.name == "Crédz" }.rateBps)
            assertEquals(500, updatedPreserve.customBrandRates.first { it.name == "Vale Loja" }.rateBps)
            assertEquals(10, updatedPreserve.maxInstallments)

            // Edição com customBrandRates = null também deve bloquear adicional que faça bandeira preservada ultrapassar 100%
            var combinationBlocked = false
            try {
                repo.updateMachine(
                    machineId = machineA.id,
                    draft = MachineInputDraft(
                        name = "Maquininha A Editada",
                        debitRateBps = 170,
                        creditEnabled = true,
                        creditDefaultRateBps = 300,
                        brandRatesEnabled = true,
                        brandRatesBps = mapOf(CardBrand.VISA to 230),
                        installmentsEnabled = true,
                        installmentAddonsBps = mapOf(1 to 0, 12 to 9_600), // 500 + 9.600 = 10.100 > 10.000
                        customBrandRates = null,
                    ),
                )
            } catch (_: MachineValidationException) {
                combinationBlocked = true
            }
            assertTrue(combinationBlocked)

            // Edição explícita mantendo Crédz (mantendo chave e nova taxa) e removendo apenas Vale Loja
            val credzKey = updatedPreserve.customBrandRates.first { it.name == "Crédz" }.key
            val updatedExplicit = repo.updateMachine(
                machineId = machineA.id,
                draft = MachineInputDraft(
                    name = "Maquininha A Editada",
                    debitRateBps = 170,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 230),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 3 to 250),
                    customBrandRates = listOf(
                        CustomCardBrandInput(name = "Crédz", rateBps = 465, key = credzKey),
                        CustomCardBrandInput(name = "Banese Card", rateBps = 395),
                    ),
                ),
            )
            assertEquals(2, updatedExplicit.customBrandRates.size)
            assertNotNull(updatedExplicit.findCustomBrand(credzKey))
            assertEquals(465, updatedExplicit.findCustomBrand(credzKey)?.rateBps)
            assertNull(updatedExplicit.customBrandRates.firstOrNull { it.name == "Vale Loja" })
            assertNotNull(updatedExplicit.customBrandRates.firstOrNull { it.name == "Banese Card" })

            // Maquininha B permanece completamente intacta
            val machineBAfter = repo.observeMachines().first().first { it.id == machineB.id }
            assertEquals(1, machineBAfter.customBrandRates.size)
            assertEquals("Crédz", machineBAfter.customBrandRates.first().name)
            assertEquals(320, machineBAfter.customBrandRates.first().rateBps)
        } finally {
            db.close()
        }
    }
}
