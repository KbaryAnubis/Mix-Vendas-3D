package com.mixvendas3d.app.domain.export

import androidx.room.withTransaction
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
import com.mixvendas3d.app.data.local.entity.SaleItemWithParticipants
import com.mixvendas3d.app.data.local.entity.SalePersonTotalEntity
import com.mixvendas3d.app.data.local.entity.SaleWithDetails
import com.mixvendas3d.app.data.repository.SaleEntityMapper
import com.mixvendas3d.app.domain.calculation.FinancialOverflowException
import com.mixvendas3d.app.domain.calculation.MonetaryRounding
import com.mixvendas3d.app.domain.calculation.SalePersistenceException
import com.mixvendas3d.app.domain.calculation.SalePersistenceValidator
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.InstallmentTiers
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.util.RateFormatter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class JsonBackupValidationException(
    val entityId: String?,
    message: String,
) : RuntimeException(message)

data class JsonBackupResult(
    val jsonContent: String,
    val utf8Bytes: ByteArray,
    val suggestedFileName: String,
    val totalRecordsCount: Int,
    val collectionsCounts: Map<String, Int>,
)

internal data class JsonBackupSnapshot(
    val preferences: List<PreferenceEntity>,
    val people: List<PersonEntity>,
    val products: List<ProductEntity>,
    val productParticipants: List<ProductParticipantEntity>,
    val machines: List<MachineEntity>,
    val machineBrandRates: List<MachineBrandRateEntity>,
    val machineInstallmentRates: List<MachineInstallmentRateEntity>,
    val sales: List<SaleEntity>,
    val saleItems: List<SaleItemEntity>,
    val saleItemParticipants: List<SaleItemParticipantEntity>,
    val salePersonTotals: List<SalePersonTotalEntity>,
    val productCategories: List<com.mixvendas3d.app.data.local.entity.ProductCategoryEntity> = emptyList(),
)

object JsonBackupExporter {

    private const val MAX_SAFE_JSON_INTEGER = 9_007_199_254_740_991L // 2^53 - 1
    private val STRICT_DECIMAL_INT_REGEX = Regex("^(0|-?[1-9][0-9]*)$")

    internal val exactLongJsonAdapter: JsonAdapter<Long> = object : JsonAdapter<Long>() {
        override fun fromJson(reader: JsonReader): Long {
            val token = reader.peek()
            return when (token) {
                JsonReader.Token.STRING,
                JsonReader.Token.NUMBER -> {
                    val raw = reader.nextString()
                    if (!STRICT_DECIMAL_INT_REGEX.matches(raw)) {
                        throw JsonDataException("Representação Long inválida (esperado inteiro decimal exato): $raw")
                    }
                    val parsed = raw.toLongOrNull()
                        ?: throw JsonDataException("Overflow de Long de 64 bits no JSON: $raw")
                    if (token == JsonReader.Token.NUMBER &&
                        (parsed > MAX_SAFE_JSON_INTEGER || parsed < -MAX_SAFE_JSON_INTEGER)
                    ) {
                        throw JsonDataException(
                            "Valor Long ($raw) acima de 2^53-1 deve usar string decimal exata para evitar perda de precisão.",
                        )
                    }
                    parsed
                }
                else -> throw JsonDataException(
                    "Esperado Long como string decimal ou inteiro seguro, encontrado $token",
                )
            }
        }

        override fun toJson(writer: JsonWriter, value: Long?) {
            if (value == null) {
                writer.nullValue()
            } else {
                writer.value(value.toString())
            }
        }
    }.nullSafe()

    private val moshi: Moshi = Moshi.Builder()
        .add(Long::class.javaPrimitiveType!!, exactLongJsonAdapter)
        .add(Long::class.javaObjectType, exactLongJsonAdapter)
        .add(KotlinJsonAdapterFactory())
        .build()

    private val backupAdapter = moshi.adapter(MixVendasBackupDocument::class.java)
        .serializeNulls()
        .indent("  ")

    private val fileDateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.forLanguageTag("pt-BR"))

    suspend fun export(
        database: AppDatabase,
        zoneId: ZoneId = ZoneId.systemDefault(),
        exportedAtEpochMs: Long = System.currentTimeMillis(),
    ): JsonBackupResult = exportWithHooks(
        database = database,
        zoneId = zoneId,
        exportedAtEpochMs = exportedAtEpochMs,
    )

    internal fun parseBackupDocument(json: String): MixVendasBackupDocument? {
        return backupAdapter.fromJson(json)
    }

    internal suspend fun exportWithHooks(
        database: AppDatabase,
        zoneId: ZoneId = ZoneId.systemDefault(),
        exportedAtEpochMs: Long = System.currentTimeMillis(),
        onBeforeTransaction: suspend () -> Unit = {},
        onAfterSalesReadInsideTransaction: suspend () -> Unit = {},
        onAfterTransactionBeforeSerialization: suspend () -> Unit = {},
    ): JsonBackupResult {
        onBeforeTransaction()

        val snapshot = captureConsistentSnapshot(
            database = database,
            onAfterSalesReadInsideTransaction = onAfterSalesReadInsideTransaction,
        )

        onAfterTransactionBeforeSerialization()

        return serializeSnapshot(
            snapshot = snapshot,
            zoneId = zoneId,
            exportedAtEpochMs = exportedAtEpochMs,
        )
    }

    internal suspend fun captureConsistentSnapshot(
        database: AppDatabase,
        onAfterSalesReadInsideTransaction: suspend () -> Unit = {},
    ): JsonBackupSnapshot = database.withTransaction {
        val preferenceDao = database.preferenceDao()
        val personDao = database.personDao()
        val productDao = database.productDao()
        val machineDao = database.machineDao()
        val saleDao = database.saleDao()

        val preferences = preferenceDao.getAllPreferences().toList()
        val people = personDao.getAllPeople().toList()
        val products = productDao.getAllProducts().toList()
        val productParticipants = productDao.getAllProductParticipants().toList()
        val productCategories = productDao.getAllCategories().toList()
        val machines = machineDao.getAllMachines().toList()
        val machineBrandRates = machineDao.getAllBrandRates().toList()
        val machineInstallmentRates = machineDao.getAllInstallmentRates().toList()
        val sales = saleDao.getAllSales().toList()
        onAfterSalesReadInsideTransaction()
        val saleItems = saleDao.getAllSaleItems().toList()
        val saleItemParticipants = saleDao.getAllSaleItemParticipants().toList()
        val salePersonTotals = saleDao.getAllSalePersonTotals().toList()

        JsonBackupSnapshot(
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
            productCategories = productCategories,
        )
    }

    internal fun serializeSnapshot(
        snapshot: JsonBackupSnapshot,
        zoneId: ZoneId = ZoneId.systemDefault(),
        exportedAtEpochMs: Long = System.currentTimeMillis(),
    ): JsonBackupResult {
        if (exportedAtEpochMs < 0L) {
            throw JsonBackupValidationException(null, "Timestamp de exportação inválido: $exportedAtEpochMs")
        }
        validateSnapshot(snapshot)
        val ordered = sortSnapshotDeterministically(snapshot)

        val collections = BackupCollections(
            preferences = ordered.preferences.map { PreferenceDto(it.key, it.value, it.updatedAtEpochMs) },
            people = ordered.people.map {
                PersonDto(it.id, it.name, it.normalizedName, it.colorIndex, it.createdAtEpochMs, it.updatedAtEpochMs)
            },
            products = ordered.products.map {
                ProductDto(
                    id = it.id,
                    name = it.name,
                    priceCents = it.priceCents,
                    active = it.active,
                    feeResponsibilityMode = it.feeResponsibilityMode,
                    feeOwnerPersonId = it.feeOwnerPersonId,
                    createdAtEpochMs = it.createdAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                    categoryId = it.categoryId,
                    subcategoryId = it.subcategoryId,
                )
            },
            productParticipants = ordered.productParticipants.map {
                ProductParticipantDto(it.productId, it.personId, it.amountCents, it.sortOrder)
            },
            productCategories = ordered.productCategories.map {
                ProductCategoryDto(
                    id = it.id,
                    name = it.name,
                    normalizedName = it.normalizedName,
                    parentId = it.parentId,
                    createdAtEpochMs = it.createdAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                )
            },
            machines = ordered.machines.map {
                MachineDto(
                    id = it.id,
                    name = it.name,
                    normalizedName = it.normalizedName,
                    debitRateBps = it.debitRateBps,
                    creditEnabled = it.creditEnabled,
                    creditDefaultRateBps = it.creditDefaultRateBps,
                    brandRatesEnabled = it.brandRatesEnabled,
                    installmentsEnabled = it.installmentsEnabled,
                    createdAtEpochMs = it.createdAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                    maxInstallments = it.maxInstallments,
                )
            },
            machineBrandRates = ordered.machineBrandRates.map {
                MachineBrandRateDto(it.machineId, it.brandKey, it.rateBps, it.debitRateBps)
            },
            machineInstallmentRates = ordered.machineInstallmentRates.map {
                MachineInstallmentRateDto(it.machineId, it.installments, it.addonBps)
            },
            sales = ordered.sales.map {
                SaleDto(
                    id = it.id,
                    soldAtEpochMs = it.soldAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                    paymentMethod = it.paymentMethod,
                    originalSubtotalCents = it.originalSubtotalCents,
                    discountTotalCents = it.discountTotalCents,
                    finalAmountCents = it.finalAmountCents,
                    feeTotalCents = it.feeTotalCents,
                    netTotalCents = it.netTotalCents,
                    discountMode = it.discountMode,
                    discountTargetProductId = it.discountTargetProductId,
                    discountTargetProductName = it.discountTargetProductName,
                    machineId = it.machineId,
                    machineName = it.machineName,
                    machineDebitRateBps = it.machineDebitRateBps,
                    machineCreditDefaultRateBps = it.machineCreditDefaultRateBps,
                    machineBrandRatesEnabled = it.machineBrandRatesEnabled,
                    machineInstallmentsEnabled = it.machineInstallmentsEnabled,
                    cardBrand = it.cardBrand,
                    installments = it.installments,
                    automaticRateBps = it.automaticRateBps,
                    manualRateOverrideBps = it.manualRateOverrideBps,
                    effectiveFeeRateBps = it.effectiveFeeRateBps,
                    note = it.note,
                )
            },
            saleItems = ordered.saleItems.map {
                SaleItemDto(
                    id = it.id,
                    saleId = it.saleId,
                    sortOrder = it.sortOrder,
                    productId = it.productId,
                    productName = it.productName,
                    quantity = it.quantity,
                    unitPriceOriginalCents = it.unitPriceOriginalCents,
                    originalSubtotalCents = it.originalSubtotalCents,
                    discountAmountCents = it.discountAmountCents,
                    finalAmountCents = it.finalAmountCents,
                    appliedFeeRateBps = it.appliedFeeRateBps,
                    feeAmountCents = it.feeAmountCents,
                    netAmountCents = it.netAmountCents,
                    feeResponsibilityMode = it.feeResponsibilityMode,
                    feeOwnerPersonId = it.feeOwnerPersonId,
                    feeOwnerPersonName = it.feeOwnerPersonName,
                )
            },
            saleItemParticipants = ordered.saleItemParticipants.map {
                SaleItemParticipantDto(
                    saleItemId = it.saleItemId,
                    personId = it.personId,
                    sortOrder = it.sortOrder,
                    personName = it.personName,
                    personColorIndex = it.personColorIndex,
                    originalUnitAmountCents = it.originalUnitAmountCents,
                    originalSubtotalCents = it.originalSubtotalCents,
                    discountAmountCents = it.discountAmountCents,
                    grossAmountCents = it.grossAmountCents,
                    feeAmountCents = it.feeAmountCents,
                    netAmountCents = it.netAmountCents,
                )
            },
            salePersonTotals = ordered.salePersonTotals.map {
                SalePersonTotalDto(
                    saleId = it.saleId,
                    personId = it.personId,
                    sortOrder = it.sortOrder,
                    personName = it.personName,
                    personColorIndex = it.personColorIndex,
                    grossAmountCents = it.grossAmountCents,
                    feeAmountCents = it.feeAmountCents,
                    netAmountCents = it.netAmountCents,
                )
            },
        )

        val counts = linkedMapOf(
            "preferences" to collections.preferences.size,
            "people" to collections.people.size,
            "products" to collections.products.size,
            "productParticipants" to collections.productParticipants.size,
            "machines" to collections.machines.size,
            "machineBrandRates" to collections.machineBrandRates.size,
            "machineInstallmentRates" to collections.machineInstallmentRates.size,
            "sales" to collections.sales.size,
            "saleItems" to collections.saleItems.size,
            "saleItemParticipants" to collections.saleItemParticipants.size,
            "salePersonTotals" to collections.salePersonTotals.size,
            "productCategories" to collections.productCategories.size,
        )

        val document = MixVendasBackupDocument(
            exportedAtEpochMs = exportedAtEpochMs,
            collections = collections,
            counts = counts,
        )

        val jsonContent = backupAdapter.toJson(document)
            ?: throw IllegalStateException("Falha ao serializar documento de backup JSON.")
        val utf8Bytes = jsonContent.toByteArray(Charsets.UTF_8)
        val dateStr = Instant.ofEpochMilli(exportedAtEpochMs).atZone(zoneId).format(fileDateFormatter)
        val suggestedFileName = "mix_vendas_3d_backup_$dateStr.json"

        val totalRecords = counts.values.sum()

        return JsonBackupResult(
            jsonContent = jsonContent,
            utf8Bytes = utf8Bytes,
            suggestedFileName = suggestedFileName,
            totalRecordsCount = totalRecords,
            collectionsCounts = counts,
        )
    }

    private fun sortSnapshotDeterministically(snapshot: JsonBackupSnapshot): JsonBackupSnapshot {
        return JsonBackupSnapshot(
            preferences = snapshot.preferences.sortedBy { it.key },
            people = snapshot.people.sortedWith(compareBy<PersonEntity> { it.createdAtEpochMs }.thenBy { it.id }),
            products = snapshot.products.sortedWith(compareBy<ProductEntity> { it.createdAtEpochMs }.thenBy { it.id }),
            productParticipants = snapshot.productParticipants.sortedWith(
                compareBy<ProductParticipantEntity> { it.productId }.thenBy { it.personId },
            ),
            productCategories = snapshot.productCategories.sortedWith(
                compareBy<com.mixvendas3d.app.data.local.entity.ProductCategoryEntity> { it.parentId != null }
                    .thenBy { it.parentId }
                    .thenBy { it.normalizedName },
            ),
            machines = snapshot.machines.sortedWith(compareBy<MachineEntity> { it.createdAtEpochMs }.thenBy { it.id }),
            machineBrandRates = snapshot.machineBrandRates.sortedWith(
                compareBy<MachineBrandRateEntity> { it.machineId }.thenBy { it.brandKey },
            ),
            machineInstallmentRates = snapshot.machineInstallmentRates.sortedWith(
                compareBy<MachineInstallmentRateEntity> { it.machineId }.thenBy { it.installments },
            ),
            sales = snapshot.sales.sortedWith(compareBy<SaleEntity> { it.soldAtEpochMs }.thenBy { it.id }),
            saleItems = snapshot.saleItems.sortedWith(
                compareBy<SaleItemEntity> { it.saleId }.thenBy { it.sortOrder }.thenBy { it.id },
            ),
            saleItemParticipants = snapshot.saleItemParticipants.sortedWith(
                compareBy<SaleItemParticipantEntity> { it.saleItemId }.thenBy { it.personId },
            ),
            salePersonTotals = snapshot.salePersonTotals.sortedWith(
                compareBy<SalePersonTotalEntity> { it.saleId }.thenBy { it.personId },
            ),
        )
    }

    internal fun validateSnapshot(snapshot: JsonBackupSnapshot) {
        validatePreferences(snapshot.preferences)
        val personIds = validatePeople(snapshot.people)
        validateProductsAndParticipants(
            products = snapshot.products,
            productParticipants = snapshot.productParticipants,
            personIds = personIds,
        )
        validateMachinesAndRates(
            machines = snapshot.machines,
            brandRates = snapshot.machineBrandRates,
            installmentRates = snapshot.machineInstallmentRates,
        )
        validateSalesAndFinancials(
            sales = snapshot.sales,
            saleItems = snapshot.saleItems,
            saleItemParticipants = snapshot.saleItemParticipants,
            salePersonTotals = snapshot.salePersonTotals,
        )
    }

    private fun validatePreferences(preferences: List<PreferenceEntity>) {
        val seenKeys = HashSet<String>(preferences.size)
        preferences.forEach { pref ->
            if (pref.key.isBlank()) {
                throw JsonBackupValidationException(pref.key, "Chave de preferência em branco.")
            }
            if (!seenKeys.add(pref.key)) {
                throw JsonBackupValidationException(pref.key, "Chave de preferência duplicada: ${pref.key}")
            }
            if (pref.updatedAtEpochMs < 0L) {
                throw JsonBackupValidationException(pref.key, "Data de atualização inválida na preferência ${pref.key}.")
            }
        }
    }

    private fun validatePeople(people: List<PersonEntity>): Set<String> {
        val seenIds = HashSet<String>(people.size)
        val seenNormalized = HashSet<String>(people.size)
        people.forEach { person ->
            if (person.id.isBlank()) {
                throw JsonBackupValidationException(person.id, "ID de pessoa em branco.")
            }
            if (!seenIds.add(person.id)) {
                throw JsonBackupValidationException(person.id, "ID de pessoa duplicado: ${person.id}")
            }
            if (person.name.isBlank() || person.normalizedName.isBlank()) {
                throw JsonBackupValidationException(person.id, "Nome inválido na pessoa ${person.id}.")
            }
            if (!seenNormalized.add(person.normalizedName)) {
                throw JsonBackupValidationException(person.id, "Nome normalizado de pessoa duplicado: ${person.normalizedName}")
            }
            if (person.colorIndex < 0 || person.createdAtEpochMs < 0L || person.updatedAtEpochMs < 0L) {
                throw JsonBackupValidationException(person.id, "Metadados inválidos na pessoa ${person.id}.")
            }
        }
        return seenIds
    }

    private fun validateProductsAndParticipants(
        products: List<ProductEntity>,
        productParticipants: List<ProductParticipantEntity>,
        personIds: Set<String>,
    ) {
        val seenProductIds = HashSet<String>(products.size)
        products.forEach { product ->
            if (product.id.isBlank()) {
                throw JsonBackupValidationException(product.id, "ID de produto em branco.")
            }
            if (!seenProductIds.add(product.id)) {
                throw JsonBackupValidationException(product.id, "ID de produto duplicado: ${product.id}")
            }
            if (product.name.isBlank()) {
                throw JsonBackupValidationException(product.id, "Nome de produto em branco: ${product.id}")
            }
            if (product.priceCents <= 0L) {
                throw JsonBackupValidationException(product.id, "Preço inválido no produto ${product.id}: ${product.priceCents}")
            }
            if (product.createdAtEpochMs < 0L || product.updatedAtEpochMs < 0L) {
                throw JsonBackupValidationException(product.id, "Datas inválidas no produto ${product.id}.")
            }
            if (FeeResponsibilityMode.entries.none { it.storageKey == product.feeResponsibilityMode }) {
                throw JsonBackupValidationException(
                    product.id,
                    "Modo de responsabilidade de tarifa inválido no produto ${product.id}: ${product.feeResponsibilityMode}",
                )
            }
        }

        val seenParticipantKeys = HashSet<Pair<String, String>>(productParticipants.size)
        val participantsByProduct = HashMap<String, MutableList<ProductParticipantEntity>>()
        productParticipants.forEach { pp ->
            if (!seenProductIds.contains(pp.productId)) {
                throw JsonBackupValidationException(
                    pp.productId,
                    "Participante de produto referencia produto inexistente ${pp.productId}",
                )
            }
            if (!personIds.contains(pp.personId)) {
                throw JsonBackupValidationException(
                    pp.productId,
                    "Participante do produto ${pp.productId} referencia pessoa inexistente ${pp.personId}",
                )
            }
            if (!seenParticipantKeys.add(pp.productId to pp.personId)) {
                throw JsonBackupValidationException(
                    pp.productId,
                    "Participante duplicado (${pp.personId}) no produto ${pp.productId}",
                )
            }
            if (pp.amountCents < 0L || pp.sortOrder < 0) {
                throw JsonBackupValidationException(
                    pp.productId,
                    "Valor ou ordem inválida para participante ${pp.personId} do produto ${pp.productId}",
                )
            }
            participantsByProduct.getOrPut(pp.productId) { ArrayList() }.add(pp)
        }

        products.forEach { product ->
            val participants = participantsByProduct[product.id].orEmpty()
            if (participants.isEmpty()) {
                throw JsonBackupValidationException(
                    product.id,
                    "Produto ${product.id} não possui participantes vinculados.",
                )
            }
            var sumCents = 0L
            try {
                participants.forEach { part ->
                    sumCents = MonetaryRounding.safeAdd(sumCents, part.amountCents)
                }
            } catch (ex: FinancialOverflowException) {
                throw JsonBackupValidationException(
                    product.id,
                    "Estouro na soma dos participantes do produto ${product.id}: ${ex.message}",
                )
            }
            if (sumCents != product.priceCents) {
                throw JsonBackupValidationException(
                    product.id,
                    "Soma dos participantes ($sumCents) difere do preço (${product.priceCents}) no produto ${product.id}",
                )
            }

            if (product.feeResponsibilityMode == FeeResponsibilityMode.INDIVIDUAL.storageKey) {
                val ownerId = product.feeOwnerPersonId?.takeIf { it.isNotBlank() }
                    ?: throw JsonBackupValidationException(
                        product.id,
                        "Produto ${product.id} em modo INDIVIDUAL exige responsável pela tarifa.",
                    )
                if (!personIds.contains(ownerId)) {
                    throw JsonBackupValidationException(
                        product.id,
                        "Responsável pela tarifa $ownerId do produto ${product.id} não existe no cadastro.",
                    )
                }
                if (participants.none { it.personId == ownerId }) {
                    throw JsonBackupValidationException(
                        product.id,
                        "Responsável pela tarifa $ownerId não é participante do produto ${product.id}.",
                    )
                }
            } else if (product.feeOwnerPersonId != null) {
                throw JsonBackupValidationException(
                    product.id,
                    "Produto ${product.id} fora do modo INDIVIDUAL não pode definir feeOwnerPersonId.",
                )
            }
        }
    }

    private fun validateMachinesAndRates(
        machines: List<MachineEntity>,
        brandRates: List<MachineBrandRateEntity>,
        installmentRates: List<MachineInstallmentRateEntity>,
    ) {
        val machinesById = HashMap<String, MachineEntity>(machines.size)
        val seenNormalized = HashSet<String>(machines.size)
        machines.forEach { machine ->
            if (machine.id.isBlank()) {
                throw JsonBackupValidationException(machine.id, "ID de maquininha em branco.")
            }
            if (machinesById.put(machine.id, machine) != null) {
                throw JsonBackupValidationException(machine.id, "ID de maquininha duplicado: ${machine.id}")
            }
            if (machine.name.isBlank() || machine.normalizedName.isBlank()) {
                throw JsonBackupValidationException(machine.id, "Nome inválido na maquininha ${machine.id}.")
            }
            if (!seenNormalized.add(machine.normalizedName)) {
                throw JsonBackupValidationException(
                    machine.id,
                    "Nome normalizado de maquininha duplicado: ${machine.normalizedName}",
                )
            }
            if (machine.debitRateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS ||
                machine.creditDefaultRateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS
            ) {
                throw JsonBackupValidationException(
                    machine.id,
                    "Taxa fora do intervalo permitido na maquininha ${machine.id}.",
                )
            }
            if (machine.createdAtEpochMs < 0L || machine.updatedAtEpochMs < 0L) {
                throw JsonBackupValidationException(machine.id, "Datas inválidas na maquininha ${machine.id}.")
            }
            if (machine.maxInstallments != null && machine.maxInstallments !in 1..24) {
                throw JsonBackupValidationException(machine.id, "Limite máximo de parcelas inválido na maquininha ${machine.id}.")
            }
        }

        val seenBrandKeys = HashSet<Pair<String, String>>(brandRates.size)
        val seenNormalizedCustomNamesByMachine = HashMap<String, MutableSet<String>>()
        val brandRatesByMachine = HashMap<String, MutableList<MachineBrandRateEntity>>()
        brandRates.forEach { br ->
            if (!machinesById.containsKey(br.machineId)) {
                throw JsonBackupValidationException(
                    br.machineId,
                    "Taxa por bandeira referencia maquininha inexistente ${br.machineId}",
                )
            }
            if (!CustomBrandKeyCodec.isKnownBrandKey(br.brandKey)) {
                throw JsonBackupValidationException(
                    br.machineId,
                    "Bandeira inválida (${br.brandKey}) na maquininha ${br.machineId}",
                )
            }
            if (CardBrand.fromStorageKey(br.brandKey) == null) {
                val decodedName = CustomBrandKeyCodec.decodeNameOrNull(br.brandKey)
                    ?: throw JsonBackupValidationException(
                        br.machineId,
                        "Bandeira personalizada inválida (${br.brandKey}) na maquininha ${br.machineId}",
                    )
                val normalizedCustom = CustomBrandKeyCodec.normalizeBrandName(decodedName)
                val collidesWithStandard = CardBrand.entries.any {
                    CustomBrandKeyCodec.normalizeBrandName(it.label) == normalizedCustom ||
                        CustomBrandKeyCodec.normalizeBrandName(it.storageKey) == normalizedCustom
                }
                if (collidesWithStandard) {
                    throw JsonBackupValidationException(
                        br.machineId,
                        "Bandeira personalizada \"$decodedName\" colide com bandeira padrão na maquininha ${br.machineId}",
                    )
                }
                val seenNames = seenNormalizedCustomNamesByMachine.getOrPut(br.machineId) { HashSet() }
                if (!seenNames.add(normalizedCustom)) {
                    throw JsonBackupValidationException(
                        br.machineId,
                        "Bandeira personalizada duplicada (\"$decodedName\") na maquininha ${br.machineId}",
                    )
                }
            }
            if (!seenBrandKeys.add(br.machineId to br.brandKey)) {
                throw JsonBackupValidationException(
                    br.machineId,
                    "Bandeira duplicada (${br.brandKey}) na maquininha ${br.machineId}",
                )
            }
            if (br.rateBps != null && br.rateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS) {
                throw JsonBackupValidationException(
                    br.machineId,
                    "Taxa da bandeira ${br.brandKey} fora do intervalo na maquininha ${br.machineId}",
                )
            }
            if (br.debitRateBps != null && br.debitRateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS) {
                throw JsonBackupValidationException(
                    br.machineId,
                    "Taxa de débito da bandeira ${br.brandKey} fora do intervalo na maquininha ${br.machineId}",
                )
            }
            brandRatesByMachine.getOrPut(br.machineId) { ArrayList() }.add(br)
        }

        val seenInstallmentKeys = HashSet<Pair<String, Int>>(installmentRates.size)
        installmentRates.forEach { ir ->
            val machine = machinesById[ir.machineId]
                ?: throw JsonBackupValidationException(
                    ir.machineId,
                    "Adicional de parcelamento referencia maquininha inexistente ${ir.machineId}",
                )
            if (ir.installments !in InstallmentTiers.SUPPORTED) {
                throw JsonBackupValidationException(
                    ir.machineId,
                    "Parcelamento não suportado (${ir.installments}x) na maquininha ${ir.machineId}",
                )
            }
            if (!seenInstallmentKeys.add(ir.machineId to ir.installments)) {
                throw JsonBackupValidationException(
                    ir.machineId,
                    "Parcelamento duplicado (${ir.installments}x) na maquininha ${ir.machineId}",
                )
            }
            if (ir.addonBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS) {
                throw JsonBackupValidationException(
                    ir.machineId,
                    "Adicional de ${ir.installments}x fora do intervalo na maquininha ${ir.machineId}",
                )
            }
            val enforceSumLimit = (machine.creditEnabled && machine.installmentsEnabled) || ir.addonBps > 0
            if (enforceSumLimit) {
                if (machine.creditDefaultRateBps + ir.addonBps > RateFormatter.MAX_RATE_BPS) {
                    throw JsonBackupValidationException(
                        ir.machineId,
                        "Soma da taxa padrão e adicional de ${ir.installments}x ultrapassa 100,00% na maquininha ${ir.machineId}",
                    )
                }
                brandRatesByMachine[ir.machineId].orEmpty().forEach { br ->
                    val creditRate = br.rateBps ?: machine.creditDefaultRateBps
                    if (creditRate + ir.addonBps > RateFormatter.MAX_RATE_BPS) {
                        throw JsonBackupValidationException(
                            ir.machineId,
                            "Soma da bandeira ${br.brandKey} e adicional de ${ir.installments}x ultrapassa 100,00% na maquininha ${ir.machineId}",
                        )
                    }
                }
            }
        }
    }

    private fun validateSalesAndFinancials(
        sales: List<SaleEntity>,
        saleItems: List<SaleItemEntity>,
        saleItemParticipants: List<SaleItemParticipantEntity>,
        salePersonTotals: List<SalePersonTotalEntity>,
    ) {
        val seenSaleIds = HashSet<String>(sales.size)
        sales.forEach { sale ->
            if (sale.id.isBlank()) {
                throw JsonBackupValidationException(sale.id, "ID de venda em branco.")
            }
            if (!seenSaleIds.add(sale.id)) {
                throw JsonBackupValidationException(sale.id, "ID de venda duplicado: ${sale.id}")
            }
            if (sale.soldAtEpochMs < 0L || sale.updatedAtEpochMs < 0L) {
                throw JsonBackupValidationException(sale.id, "Datas inválidas na venda ${sale.id}.")
            }
            val method = SalePaymentMethod.fromStorageKey(sale.paymentMethod)
                ?: throw JsonBackupValidationException(
                    sale.id,
                    "Forma de pagamento inválida na venda ${sale.id}: ${sale.paymentMethod}",
                )
            if (sale.discountMode != "PROPORTIONAL" && sale.discountMode != "SPECIFIC_PRODUCT") {
                throw JsonBackupValidationException(
                    sale.id,
                    "Modo de desconto inválido na venda ${sale.id}: ${sale.discountMode}",
                )
            }
            if (sale.discountMode == "PROPORTIONAL" &&
                (sale.discountTargetProductId != null || sale.discountTargetProductName != null)
            ) {
                throw JsonBackupValidationException(
                    sale.id,
                    "Venda ${sale.id} em modo PROPORTIONAL não pode definir produto alvo de desconto.",
                )
            }
            if (!method.usesMachine) {
                val hasAnyMachineField = sale.machineId != null ||
                    sale.machineName != null ||
                    sale.machineDebitRateBps != null ||
                    sale.machineCreditDefaultRateBps != null ||
                    sale.machineBrandRatesEnabled != null ||
                    sale.machineInstallmentsEnabled != null ||
                    sale.cardBrand != null ||
                    sale.installments != null ||
                    sale.automaticRateBps != null ||
                    sale.manualRateOverrideBps != null
                if (hasAnyMachineField) {
                    throw JsonBackupValidationException(
                        sale.id,
                        "Venda ${sale.id} em ${method.label} não pode conter metadados de maquininha.",
                    )
                }
            } else {
                if (sale.machineId.isNullOrBlank() ||
                    sale.machineName.isNullOrBlank() ||
                    sale.machineDebitRateBps == null ||
                    sale.machineCreditDefaultRateBps == null ||
                    sale.machineBrandRatesEnabled == null ||
                    sale.machineInstallmentsEnabled == null ||
                    sale.automaticRateBps == null
                ) {
                    throw JsonBackupValidationException(
                        sale.id,
                        "Venda ${sale.id} em ${method.label} possui snapshot de maquininha incompleto.",
                    )
                }
                if (sale.cardBrand != null && !CustomBrandKeyCodec.isKnownBrandKey(sale.cardBrand)) {
                    throw JsonBackupValidationException(
                        sale.id,
                        "Bandeira de cartão inválida na venda ${sale.id}: ${sale.cardBrand}",
                    )
                }
            }
        }

        val seenSaleItemIds = HashSet<String>(saleItems.size)
        val seenSaleProductPairs = HashSet<Pair<String, String>>(saleItems.size)
        val itemsBySaleId = HashMap<String, MutableList<SaleItemEntity>>()
        val itemToSaleId = HashMap<String, String>(saleItems.size)

        saleItems.forEach { item ->
            if (item.id.isBlank()) {
                throw JsonBackupValidationException(item.id, "ID de item de venda em branco.")
            }
            if (!seenSaleItemIds.add(item.id)) {
                throw JsonBackupValidationException(item.id, "ID de item de venda duplicado: ${item.id}")
            }
            if (!seenSaleIds.contains(item.saleId)) {
                throw JsonBackupValidationException(
                    item.id,
                    "Item de venda ${item.id} referencia venda inexistente ${item.saleId}",
                )
            }
            if (!seenSaleProductPairs.add(item.saleId to item.productId)) {
                throw JsonBackupValidationException(
                    item.saleId,
                    "Produto ${item.productId} duplicado na venda ${item.saleId}",
                )
            }
            if (item.sortOrder < 0) {
                throw JsonBackupValidationException(
                    item.saleId,
                    "Ordem inválida no item ${item.id} da venda ${item.saleId}",
                )
            }
            if (FeeResponsibilityMode.entries.none { it.storageKey == item.feeResponsibilityMode }) {
                throw JsonBackupValidationException(
                    item.saleId,
                    "Modo de responsabilidade de tarifa inválido no item ${item.id}: ${item.feeResponsibilityMode}",
                )
            }
            if (item.feeResponsibilityMode != FeeResponsibilityMode.INDIVIDUAL.storageKey &&
                (item.feeOwnerPersonId != null || item.feeOwnerPersonName != null)
            ) {
                throw JsonBackupValidationException(
                    item.saleId,
                    "Item ${item.id} da venda ${item.saleId} fora do modo INDIVIDUAL não pode definir responsável pela tarifa.",
                )
            }
            itemToSaleId[item.id] = item.saleId
            itemsBySaleId.getOrPut(item.saleId) { ArrayList() }.add(item)
        }

        val seenItemParticipantKeys = HashSet<Pair<String, String>>(saleItemParticipants.size)
        val participantsByItemId = HashMap<String, MutableList<SaleItemParticipantEntity>>()
        saleItemParticipants.forEach { sip ->
            val parentSaleId = itemToSaleId[sip.saleItemId]
                ?: throw JsonBackupValidationException(
                    null,
                    "Participante de item referencia item de venda inexistente ${sip.saleItemId}",
                )
            if (!seenItemParticipantKeys.add(sip.saleItemId to sip.personId)) {
                throw JsonBackupValidationException(
                    parentSaleId,
                    "Participante duplicado (${sip.personId}) no item ${sip.saleItemId} da venda $parentSaleId",
                )
            }
            if (sip.personColorIndex < 0 || sip.sortOrder < 0) {
                throw JsonBackupValidationException(
                    parentSaleId,
                    "Metadados inválidos de participante ${sip.personId} no item ${sip.saleItemId}",
                )
            }
            participantsByItemId.getOrPut(sip.saleItemId) { ArrayList() }.add(sip)
        }

        val seenPersonTotalKeys = HashSet<Pair<String, String>>(salePersonTotals.size)
        val personTotalsBySaleId = HashMap<String, MutableList<SalePersonTotalEntity>>()
        salePersonTotals.forEach { spt ->
            if (!seenSaleIds.contains(spt.saleId)) {
                throw JsonBackupValidationException(
                    null,
                    "Total por pessoa referencia venda inexistente ${spt.saleId}",
                )
            }
            if (!seenPersonTotalKeys.add(spt.saleId to spt.personId)) {
                throw JsonBackupValidationException(
                    spt.saleId,
                    "Total por pessoa duplicado (${spt.personId}) na venda ${spt.saleId}",
                )
            }
            if (spt.personColorIndex < 0 || spt.sortOrder < 0) {
                throw JsonBackupValidationException(
                    spt.saleId,
                    "Metadados inválidos no total por pessoa ${spt.personId} da venda ${spt.saleId}",
                )
            }
            personTotalsBySaleId.getOrPut(spt.saleId) { ArrayList() }.add(spt)
        }

        // Conferência financeira completa por venda via SaleEntityMapper + SalePersistenceValidator
        // Preserva integralmente snapshots históricos mesmo quando cadastros foram removidos posteriormente.
        sales.forEach { sale ->
            val itemsWithParticipants = itemsBySaleId[sale.id].orEmpty().map { item ->
                SaleItemWithParticipants(
                    item = item,
                    participants = participantsByItemId[item.id].orEmpty(),
                )
            }
            val saleWithDetails = SaleWithDetails(
                sale = sale,
                items = itemsWithParticipants,
                personTotals = personTotalsBySaleId[sale.id].orEmpty(),
            )
            try {
                val persistedSale = SaleEntityMapper.toDomain(saleWithDetails)
                SalePersistenceValidator.validateForPersistence(persistedSale.calculation)
            } catch (ex: SalePersistenceException) {
                throw JsonBackupValidationException(
                    sale.id,
                    "Inconsistência financeira na venda ${sale.id}: ${ex.message}",
                )
            } catch (ex: IllegalArgumentException) {
                throw JsonBackupValidationException(
                    sale.id,
                    "Inconsistência na venda ${sale.id}: ${ex.message}",
                )
            } catch (ex: IllegalStateException) {
                throw JsonBackupValidationException(
                    sale.id,
                    "Inconsistência na venda ${sale.id}: ${ex.message}",
                )
            }
        }
    }
}
