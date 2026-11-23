package com.mixvendas3d.app.domain.export

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
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Collections

object JsonBackupReader {

    const val EXPECTED_FORMAT = "MIX_VENDAS_3D_BACKUP"
    const val SUPPORTED_BACKUP_SCHEMA_VERSION = 1
    const val EXPECTED_APPLICATION_ID = "com.mixvendas3d.app"
    const val MAX_SUPPORTED_ROOM_SCHEMA_VERSION = 7
    const val DEFAULT_MAX_BACKUP_BYTES = 16 * 1024 * 1024 // 16 MiB

    private val STRICT_INT_REGEX = Regex("^(0|-?[1-9][0-9]*)$")

    private val COLLECTION_KEYS = listOf(
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

    private val KNOWN_COLLECTION_KEYS = COLLECTION_KEYS + "productCategories"

    private class ReaderRejectException(
        val code: JsonBackupErrorCode,
        override val message: String,
        val field: String? = null,
    ) : RuntimeException(message)

    private val strictStringAdapter: JsonAdapter<String> = object : JsonAdapter<String>() {
        override fun fromJson(reader: JsonReader): String {
            val token = reader.peek()
            if (token != JsonReader.Token.STRING) {
                throw JsonDataException("Esperado texto (String), encontrado $token em ${reader.path}.")
            }
            return reader.nextString()
        }

        override fun toJson(writer: JsonWriter, value: String?) {
            if (value == null) writer.nullValue() else writer.value(value)
        }
    }.nullSafe()

    private val strictIntAdapter: JsonAdapter<Int> = object : JsonAdapter<Int>() {
        override fun fromJson(reader: JsonReader): Int {
            val token = reader.peek()
            if (token != JsonReader.Token.NUMBER) {
                throw JsonDataException("Esperado inteiro numérico (Int), encontrado $token em ${reader.path}.")
            }
            val raw = reader.nextString()
            if (!STRICT_INT_REGEX.matches(raw)) {
                throw JsonDataException("Número inteiro inválido em ${reader.path}: $raw")
            }
            return raw.toIntOrNull()
                ?: throw JsonDataException("Valor Int fora do intervalo em ${reader.path}: $raw")
        }

        override fun toJson(writer: JsonWriter, value: Int?) {
            if (value == null) writer.nullValue() else writer.value(value)
        }
    }.nullSafe()

    private val baseMoshi: Moshi = Moshi.Builder()
        .add(Long::class.javaPrimitiveType!!, JsonBackupExporter.exactLongJsonAdapter)
        .add(Long::class.javaObjectType, JsonBackupExporter.exactLongJsonAdapter)
        .add(Int::class.javaPrimitiveType!!, strictIntAdapter)
        .add(Int::class.javaObjectType, strictIntAdapter)
        .add(String::class.java, strictStringAdapter)
        .add(KotlinJsonAdapterFactory())
        .build()

    private val preferenceAdapter = baseMoshi.adapter(PreferenceDto::class.java).failOnUnknown()
    private val personAdapter = baseMoshi.adapter(PersonDto::class.java).failOnUnknown()
    private val productAdapter = baseMoshi.adapter(ProductDto::class.java).failOnUnknown()
    private val productParticipantAdapter = baseMoshi.adapter(ProductParticipantDto::class.java).failOnUnknown()
    private val productCategoryAdapter = baseMoshi.adapter(ProductCategoryDto::class.java).failOnUnknown()
    private val machineAdapter = baseMoshi.adapter(MachineDto::class.java).failOnUnknown()
    private val machineBrandRateAdapter = baseMoshi.adapter(MachineBrandRateDto::class.java).failOnUnknown()
    private val machineInstallmentRateAdapter = baseMoshi.adapter(MachineInstallmentRateDto::class.java).failOnUnknown()
    private val saleAdapter = baseMoshi.adapter(SaleDto::class.java).failOnUnknown()
    private val saleItemAdapter = baseMoshi.adapter(SaleItemDto::class.java).failOnUnknown()
    private val saleItemParticipantAdapter = baseMoshi.adapter(SaleItemParticipantDto::class.java).failOnUnknown()
    private val salePersonTotalAdapter = baseMoshi.adapter(SalePersonTotalDto::class.java).failOnUnknown()

    private val countsStrictAdapter: JsonAdapter<Map<String, Int>> = object : JsonAdapter<Map<String, Int>>() {
        override fun fromJson(reader: JsonReader): Map<String, Int> {
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
                throw ReaderRejectException(
                    code = JsonBackupErrorCode.INVALID_FIELD_TYPE,
                    message = "O campo 'counts' deve ser um objeto JSON.",
                    field = "counts",
                )
            }
            val result = LinkedHashMap<String, Int>(KNOWN_COLLECTION_KEYS.size)
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                if (key !in KNOWN_COLLECTION_KEYS) {
                    throw ReaderRejectException(
                        code = JsonBackupErrorCode.SYNTAX_ERROR,
                        message = "Chave desconhecida em 'counts': '$key'.",
                        field = "counts.$key",
                    )
                }
                if (result.containsKey(key)) {
                    throw ReaderRejectException(
                        code = JsonBackupErrorCode.SYNTAX_ERROR,
                        message = "Chave duplicada em 'counts': '$key'.",
                        field = "counts.$key",
                    )
                }
                if (reader.peek() == JsonReader.Token.NULL) {
                    throw ReaderRejectException(
                        code = JsonBackupErrorCode.INVALID_FIELD_TYPE,
                        message = "Contagem nula em 'counts.$key'.",
                        field = "counts.$key",
                    )
                }
                val value = strictIntAdapter.fromJson(reader)
                    ?: throw ReaderRejectException(
                        code = JsonBackupErrorCode.INVALID_FIELD_TYPE,
                        message = "Contagem nula em 'counts.$key'.",
                        field = "counts.$key",
                    )
                if (value < 0) {
                    throw ReaderRejectException(
                        code = JsonBackupErrorCode.COUNT_MISMATCH,
                        message = "Contagem negativa em 'counts.$key': $value.",
                        field = "counts.$key",
                    )
                }
                result[key] = value
            }
            reader.endObject()

            val missingKey = COLLECTION_KEYS.firstOrNull { !result.containsKey(it) }
            if (missingKey != null) {
                throw ReaderRejectException(
                    code = JsonBackupErrorCode.MISSING_REQUIRED_FIELD,
                    message = "Contagem obrigatória ausente em 'counts': '$missingKey'.",
                    field = "counts.$missingKey",
                )
            }
            if (!result.containsKey("productCategories")) {
                result["productCategories"] = 0
            }
            return result
        }

        override fun toJson(writer: JsonWriter, value: Map<String, Int>?) {
            throw UnsupportedOperationException("JsonBackupReader é somente leitura.")
        }
    }

    private val collectionsStrictAdapter: JsonAdapter<BackupCollections> = object : JsonAdapter<BackupCollections>() {
        override fun fromJson(reader: JsonReader): BackupCollections {
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
                throw ReaderRejectException(
                    code = JsonBackupErrorCode.INVALID_FIELD_TYPE,
                    message = "O campo 'collections' deve ser um objeto JSON.",
                    field = "collections",
                )
            }
            val seen = HashSet<String>(COLLECTION_KEYS.size)
            var preferences: List<PreferenceDto>? = null
            var people: List<PersonDto>? = null
            var products: List<ProductDto>? = null
            var productParticipants: List<ProductParticipantDto>? = null
            var productCategories: List<ProductCategoryDto>? = null
            var machines: List<MachineDto>? = null
            var machineBrandRates: List<MachineBrandRateDto>? = null
            var machineInstallmentRates: List<MachineInstallmentRateDto>? = null
            var sales: List<SaleDto>? = null
            var saleItems: List<SaleItemDto>? = null
            var saleItemParticipants: List<SaleItemParticipantDto>? = null
            var salePersonTotals: List<SalePersonTotalDto>? = null

            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                if (!seen.add(key)) {
                    throw ReaderRejectException(
                        code = JsonBackupErrorCode.SYNTAX_ERROR,
                        message = "Coleção duplicada em 'collections': '$key'.",
                        field = "collections.$key",
                    )
                }
                when (key) {
                    "preferences" -> preferences = readDtoList(reader, preferenceAdapter, "collections.preferences")
                    "people" -> people = readDtoList(reader, personAdapter, "collections.people")
                    "products" -> products = readDtoList(reader, productAdapter, "collections.products")
                    "productParticipants" -> productParticipants = readDtoList(reader, productParticipantAdapter, "collections.productParticipants")
                    "productCategories" -> productCategories = readDtoList(reader, productCategoryAdapter, "collections.productCategories")
                    "machines" -> machines = readDtoList(reader, machineAdapter, "collections.machines")
                    "machineBrandRates" -> machineBrandRates = readDtoList(reader, machineBrandRateAdapter, "collections.machineBrandRates")
                    "machineInstallmentRates" -> machineInstallmentRates = readDtoList(reader, machineInstallmentRateAdapter, "collections.machineInstallmentRates")
                    "sales" -> sales = readDtoList(reader, saleAdapter, "collections.sales")
                    "saleItems" -> saleItems = readDtoList(reader, saleItemAdapter, "collections.saleItems")
                    "saleItemParticipants" -> saleItemParticipants = readDtoList(reader, saleItemParticipantAdapter, "collections.saleItemParticipants")
                    "salePersonTotals" -> salePersonTotals = readDtoList(reader, salePersonTotalAdapter, "collections.salePersonTotals")
                    else -> throw ReaderRejectException(
                        code = JsonBackupErrorCode.SYNTAX_ERROR,
                        message = "Coleção desconhecida em 'collections': '$key'.",
                        field = "collections.$key",
                    )
                }
            }
            reader.endObject()

            val missingKey = COLLECTION_KEYS.firstOrNull { !seen.contains(it) }
            if (missingKey != null) {
                throw ReaderRejectException(
                    code = JsonBackupErrorCode.MISSING_COLLECTION,
                    message = "Coleção obrigatória ausente: '$missingKey'.",
                    field = "collections.$missingKey",
                )
            }

            return BackupCollections(
                preferences = preferences!!,
                people = people!!,
                products = products!!,
                productParticipants = productParticipants!!,
                machines = machines!!,
                machineBrandRates = machineBrandRates!!,
                machineInstallmentRates = machineInstallmentRates!!,
                sales = sales!!,
                saleItems = saleItems!!,
                saleItemParticipants = saleItemParticipants!!,
                salePersonTotals = salePersonTotals!!,
                productCategories = productCategories ?: emptyList(),
            )
        }

        override fun toJson(writer: JsonWriter, value: BackupCollections?) {
            throw UnsupportedOperationException("JsonBackupReader é somente leitura.")
        }
    }

    private val documentStrictAdapter: JsonAdapter<MixVendasBackupDocument> = object : JsonAdapter<MixVendasBackupDocument>() {
        override fun fromJson(reader: JsonReader): MixVendasBackupDocument {
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
                throw ReaderRejectException(
                    code = JsonBackupErrorCode.INVALID_FIELD_TYPE,
                    message = "A raiz do arquivo de backup deve ser um objeto JSON.",
                )
            }
            val requiredRootKeys = listOf(
                "format",
                "backupSchemaVersion",
                "applicationId",
                "exportedAtEpochMs",
                "roomSchemaVersion",
                "collections",
                "counts",
            )
            val seen = HashSet<String>(requiredRootKeys.size)
            var format: String? = null
            var backupSchemaVersion: Int? = null
            var applicationId: String? = null
            var exportedAtEpochMs: Long? = null
            var roomSchemaVersion: Int? = null
            var collections: BackupCollections? = null
            var counts: Map<String, Int>? = null

            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                if (!seen.add(key)) {
                    throw ReaderRejectException(
                        code = JsonBackupErrorCode.SYNTAX_ERROR,
                        message = "Campo raiz duplicado no JSON: '$key'.",
                        field = key,
                    )
                }
                when (key) {
                    "format" -> format = readRequiredValue(reader, strictStringAdapter, "format")
                    "backupSchemaVersion" -> backupSchemaVersion = readRequiredValue(reader, strictIntAdapter, "backupSchemaVersion")
                    "applicationId" -> applicationId = readRequiredValue(reader, strictStringAdapter, "applicationId")
                    "exportedAtEpochMs" -> exportedAtEpochMs = readRequiredValue(reader, JsonBackupExporter.exactLongJsonAdapter, "exportedAtEpochMs")
                    "roomSchemaVersion" -> roomSchemaVersion = readRequiredValue(reader, strictIntAdapter, "roomSchemaVersion")
                    "collections" -> collections = readRequiredValue(reader, collectionsStrictAdapter, "collections")
                    "counts" -> counts = readRequiredValue(reader, countsStrictAdapter, "counts")
                    else -> throw ReaderRejectException(
                        code = JsonBackupErrorCode.SYNTAX_ERROR,
                        message = "Campo raiz desconhecido no backup JSON: '$key'.",
                        field = key,
                    )
                }
            }
            reader.endObject()

            val missingKey = requiredRootKeys.firstOrNull { !seen.contains(it) }
            if (missingKey != null) {
                throw ReaderRejectException(
                    code = JsonBackupErrorCode.MISSING_REQUIRED_FIELD,
                    message = "Campo obrigatório ausente no cabeçalho do backup: '$missingKey'.",
                    field = missingKey,
                )
            }

            return MixVendasBackupDocument(
                format = format!!,
                backupSchemaVersion = backupSchemaVersion!!,
                applicationId = applicationId!!,
                exportedAtEpochMs = exportedAtEpochMs!!,
                roomSchemaVersion = roomSchemaVersion!!,
                collections = collections!!,
                counts = counts!!,
            )
        }

        override fun toJson(writer: JsonWriter, value: MixVendasBackupDocument?) {
            throw UnsupportedOperationException("JsonBackupReader é somente leitura.")
        }
    }

    fun readAndValidate(
        jsonContent: String,
        maxBytes: Int = DEFAULT_MAX_BACKUP_BYTES,
    ): JsonBackupReadResult {
        if (maxBytes <= 0) {
            return invalidResult(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.FILE_TOO_LARGE,
                    message = "Limite máximo de tamanho inválido: $maxBytes bytes.",
                ),
            )
        }
        if (jsonContent.length > maxBytes || estimateUtf8BytesExceeds(jsonContent, maxBytes)) {
            return invalidResult(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.FILE_TOO_LARGE,
                    message = "Conteúdo JSON excede o limite seguro máximo de $maxBytes bytes.",
                ),
            )
        }
        if (jsonContent.isBlank()) {
            return invalidResult(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.EMPTY_INPUT,
                    message = "O arquivo de backup JSON está vazio.",
                ),
            )
        }
        return parseAndValidateDocument(jsonContent)
    }

    fun readAndValidate(
        utf8Bytes: ByteArray,
        maxBytes: Int = DEFAULT_MAX_BACKUP_BYTES,
    ): JsonBackupReadResult {
        if (maxBytes <= 0) {
            return invalidResult(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.FILE_TOO_LARGE,
                    message = "Limite máximo de tamanho inválido: $maxBytes bytes.",
                ),
            )
        }
        if (utf8Bytes.size > maxBytes) {
            return invalidResult(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.FILE_TOO_LARGE,
                    message = "Arquivo de backup excede o limite seguro máximo de $maxBytes bytes.",
                ),
            )
        }
        if (utf8Bytes.isEmpty()) {
            return invalidResult(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.EMPTY_INPUT,
                    message = "O arquivo de backup JSON está vazio.",
                ),
            )
        }
        val decoded = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(utf8Bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            return invalidResult(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.INVALID_ENCODING,
                    message = "Codificação UTF-8 inválida no arquivo de backup.",
                ),
            )
        }
        if (decoded.isBlank()) {
            return invalidResult(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.EMPTY_INPUT,
                    message = "O arquivo de backup JSON está vazio.",
                ),
            )
        }
        return parseAndValidateDocument(decoded)
    }

    fun readAndValidate(
        inputStream: InputStream,
        maxBytes: Int = DEFAULT_MAX_BACKUP_BYTES,
    ): JsonBackupReadResult {
        if (maxBytes <= 0) {
            return invalidResult(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.FILE_TOO_LARGE,
                    message = "Limite máximo de tamanho inválido: $maxBytes bytes.",
                ),
            )
        }
        val boundedBytes = try {
            readBoundedBytes(inputStream, maxBytes)
        } catch (ex: ReaderRejectException) {
            return invalidResult(
                JsonBackupValidationError(
                    code = ex.code,
                    message = ex.message,
                    field = ex.field,
                ),
            )
        } catch (_: IOException) {
            return invalidResult(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.SYNTAX_ERROR,
                    message = "Falha de leitura ao acessar o fluxo do backup JSON.",
                ),
            )
        }
        return readAndValidate(boundedBytes, maxBytes)
    }

    private fun parseAndValidateDocument(jsonContent: String): JsonBackupReadResult {
        val document = try {
            documentStrictAdapter.fromJson(jsonContent)
                ?: return invalidResult(
                    JsonBackupValidationError(
                        code = JsonBackupErrorCode.EMPTY_INPUT,
                        message = "Documento JSON nulo ou vazio.",
                    ),
                )
        } catch (ex: ReaderRejectException) {
            return invalidResult(
                JsonBackupValidationError(
                    code = ex.code,
                    message = ex.message,
                    field = ex.field,
                ),
            )
        } catch (ex: JsonEncodingException) {
            return invalidResult(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.SYNTAX_ERROR,
                    message = "Erro de sintaxe JSON: ${ex.message ?: "estrutura inválida"}",
                ),
            )
        } catch (ex: EOFException) {
            return invalidResult(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.SYNTAX_ERROR,
                    message = "Arquivo JSON incompleto ou truncado.",
                ),
            )
        } catch (ex: JsonDataException) {
            return invalidResult(classifyJsonDataException(ex))
        } catch (_: IOException) {
            return invalidResult(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.SYNTAX_ERROR,
                    message = "Falha ao ler a estrutura do backup JSON.",
                ),
            )
        }

        val cols = document.collections
        val summary = JsonBackupCollectionSummary(
            preferencesCount = cols.preferences.size,
            peopleCount = cols.people.size,
            productsCount = cols.products.size,
            productParticipantsCount = cols.productParticipants.size,
            machinesCount = cols.machines.size,
            machineBrandRatesCount = cols.machineBrandRates.size,
            machineInstallmentRatesCount = cols.machineInstallmentRates.size,
            salesCount = cols.sales.size,
            saleItemsCount = cols.saleItems.size,
            saleItemParticipantsCount = cols.saleItemParticipants.size,
            salePersonTotalsCount = cols.salePersonTotals.size,
            productCategoriesCount = cols.productCategories.size,
        )

        val errors = ArrayList<JsonBackupValidationError>()

        if (document.format != EXPECTED_FORMAT) {
            errors.add(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.UNSUPPORTED_FORMAT,
                    message = "Formato de backup desconhecido ou inválido: '${document.format}'.",
                    field = "format",
                ),
            )
        }
        if (document.backupSchemaVersion != SUPPORTED_BACKUP_SCHEMA_VERSION) {
            errors.add(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.INCOMPATIBLE_SCHEMA_VERSION,
                    message = "Versão de esquema de backup incompatível: ${document.backupSchemaVersion} (versão suportada: $SUPPORTED_BACKUP_SCHEMA_VERSION).",
                    field = "backupSchemaVersion",
                ),
            )
        }
        if (document.applicationId != EXPECTED_APPLICATION_ID) {
            errors.add(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.INCOMPATIBLE_APPLICATION_ID,
                    message = "Application ID incompatível: '${document.applicationId}' (esperado: '$EXPECTED_APPLICATION_ID').",
                    field = "applicationId",
                ),
            )
        }
        if (document.roomSchemaVersion !in 1..MAX_SUPPORTED_ROOM_SCHEMA_VERSION) {
            errors.add(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.INCOMPATIBLE_ROOM_VERSION,
                    message = "Versão Room informada no backup incompatível: ${document.roomSchemaVersion} (suportada: 1..$MAX_SUPPORTED_ROOM_SCHEMA_VERSION).",
                    field = "roomSchemaVersion",
                ),
            )
        }
        if (document.exportedAtEpochMs < 0L) {
            errors.add(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.INVALID_TIMESTAMP,
                    message = "Timestamp de exportação inválido: ${document.exportedAtEpochMs}.",
                    field = "exportedAtEpochMs",
                ),
            )
        }

        val actualCounts = summary.allCollectionsCounts
        KNOWN_COLLECTION_KEYS.forEach { key ->
            val declared = document.counts[key]
            val actual = actualCounts.getValue(key)
            if (declared == null) {
                errors.add(
                    JsonBackupValidationError(
                        code = JsonBackupErrorCode.COUNT_MISMATCH,
                        message = "Contagem ausente para coleção '$key'.",
                        field = "counts.$key",
                    ),
                )
            } else if (declared != actual) {
                errors.add(
                    JsonBackupValidationError(
                        code = JsonBackupErrorCode.COUNT_MISMATCH,
                        message = "Contagem divergente na coleção '$key': declarado=$declared, encontrado=$actual.",
                        field = "counts.$key",
                    ),
                )
            }
        }

        val snapshot = toSnapshot(cols)
        try {
            JsonBackupExporter.validateSnapshot(snapshot)
        } catch (ex: JsonBackupValidationException) {
            errors.add(
                JsonBackupValidationError(
                    code = JsonBackupErrorCode.RELATIONAL_OR_FINANCIAL_VALIDATION_ERROR,
                    message = ex.message ?: "Erro de validação relacional ou financeira no backup.",
                    entityId = ex.entityId,
                ),
            )
        }

        val immutableErrors = Collections.unmodifiableList(errors.toList())
        val isValid = immutableErrors.isEmpty()

        val validatedData = if (isValid) {
            ValidatedBackupData(
                format = document.format,
                backupSchemaVersion = document.backupSchemaVersion,
                applicationId = document.applicationId,
                roomSchemaVersion = document.roomSchemaVersion,
                exportedAtEpochMs = document.exportedAtEpochMs,
                preferences = cols.preferences,
                people = cols.people,
                products = cols.products,
                productParticipants = cols.productParticipants,
                machines = cols.machines,
                machineBrandRates = cols.machineBrandRates,
                machineInstallmentRates = cols.machineInstallmentRates,
                sales = cols.sales,
                saleItems = cols.saleItems,
                saleItemParticipants = cols.saleItemParticipants,
                salePersonTotals = cols.salePersonTotals,
                declaredCounts = document.counts,
                productCategories = cols.productCategories,
            )
        } else {
            null
        }

        return JsonBackupReadResult(
            isValid = isValid,
            format = document.format,
            backupSchemaVersion = document.backupSchemaVersion,
            applicationId = document.applicationId,
            roomSchemaVersion = document.roomSchemaVersion,
            exportedAtEpochMs = document.exportedAtEpochMs,
            peopleCount = summary.peopleCount,
            productsCount = summary.productsCount,
            machinesCount = summary.machinesCount,
            salesCount = summary.salesCount,
            saleItemsCount = summary.saleItemsCount,
            participantsCount = summary.totalParticipantsCount,
            otherCollectionsCounts = summary.otherCollectionsCounts,
            collectionSummary = summary,
            errors = immutableErrors,
            validatedData = validatedData,
        )
    }

    private fun toSnapshot(cols: BackupCollections): JsonBackupSnapshot {
        return JsonBackupSnapshot(
            preferences = cols.preferences.map {
                PreferenceEntity(
                    key = it.key,
                    value = it.value,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                )
            },
            people = cols.people.map {
                PersonEntity(
                    id = it.id,
                    name = it.name,
                    normalizedName = it.normalizedName,
                    colorIndex = it.colorIndex,
                    createdAtEpochMs = it.createdAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                )
            },
            products = cols.products.map {
                ProductEntity(
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
            productParticipants = cols.productParticipants.map {
                ProductParticipantEntity(
                    productId = it.productId,
                    personId = it.personId,
                    amountCents = it.amountCents,
                    sortOrder = it.sortOrder,
                )
            },
            productCategories = cols.productCategories.map {
                com.mixvendas3d.app.data.local.entity.ProductCategoryEntity(
                    id = it.id,
                    name = it.name,
                    normalizedName = it.normalizedName,
                    parentId = it.parentId,
                    createdAtEpochMs = it.createdAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                )
            },
            machines = cols.machines.map {
                MachineEntity(
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
            machineBrandRates = cols.machineBrandRates.map {
                MachineBrandRateEntity(
                    machineId = it.machineId,
                    brandKey = it.brandKey,
                    rateBps = it.rateBps,
                    debitRateBps = it.debitRateBps,
                )
            },
            machineInstallmentRates = cols.machineInstallmentRates.map {
                MachineInstallmentRateEntity(
                    machineId = it.machineId,
                    installments = it.installments,
                    addonBps = it.addonBps,
                )
            },
            sales = cols.sales.map {
                SaleEntity(
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
            saleItems = cols.saleItems.map {
                SaleItemEntity(
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
            saleItemParticipants = cols.saleItemParticipants.map {
                SaleItemParticipantEntity(
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
            salePersonTotals = cols.salePersonTotals.map {
                SalePersonTotalEntity(
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
    }

    private fun <T> readRequiredValue(
        reader: JsonReader,
        adapter: JsonAdapter<T>,
        fieldName: String,
    ): T {
        if (reader.peek() == JsonReader.Token.NULL) {
            throw ReaderRejectException(
                code = JsonBackupErrorCode.MISSING_REQUIRED_FIELD,
                message = "Campo obrigatório '$fieldName' não pode ser nulo.",
                field = fieldName,
            )
        }
        return adapter.fromJson(reader)
            ?: throw ReaderRejectException(
                code = JsonBackupErrorCode.MISSING_REQUIRED_FIELD,
                message = "Campo obrigatório '$fieldName' está nulo.",
                field = fieldName,
            )
    }

    private fun <T> readDtoList(
        reader: JsonReader,
        itemAdapter: JsonAdapter<T>,
        fieldPath: String,
    ): List<T> {
        if (reader.peek() != JsonReader.Token.BEGIN_ARRAY) {
            throw ReaderRejectException(
                code = JsonBackupErrorCode.INVALID_FIELD_TYPE,
                message = "A coleção '$fieldPath' deve ser um array JSON.",
                field = fieldPath,
            )
        }
        val items = ArrayList<T>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
                throw ReaderRejectException(
                    code = JsonBackupErrorCode.INVALID_FIELD_TYPE,
                    message = "Cada elemento em '$fieldPath' deve ser um objeto JSON válido.",
                    field = fieldPath,
                )
            }
            val parsed = itemAdapter.fromJson(reader)
                ?: throw ReaderRejectException(
                    code = JsonBackupErrorCode.INVALID_FIELD_TYPE,
                    message = "Elemento nulo não permitido em '$fieldPath'.",
                    field = fieldPath,
                )
            items.add(parsed)
        }
        reader.endArray()
        return items
    }

    private fun classifyJsonDataException(ex: JsonDataException): JsonBackupValidationError {
        val msg = ex.message ?: "Dados JSON incompatíveis com o formato."
        val code = when {
            msg.contains("Overflow de Long", ignoreCase = true) ||
                msg.contains("Representação Long inválida", ignoreCase = true) ||
                msg.contains("acima de 2^53-1", ignoreCase = true) ||
                msg.contains("Esperado Long", ignoreCase = true) -> {
                JsonBackupErrorCode.INVALID_LONG_PRECISION_OR_OVERFLOW
            }
            msg.contains("Required value", ignoreCase = true) ||
                msg.contains("was null", ignoreCase = true) -> {
                JsonBackupErrorCode.MISSING_REQUIRED_FIELD
            }
            msg.contains("Unknown name", ignoreCase = true) ||
                msg.contains("not fully consumed", ignoreCase = true) -> {
                JsonBackupErrorCode.SYNTAX_ERROR
            }
            else -> JsonBackupErrorCode.INVALID_FIELD_TYPE
        }
        return JsonBackupValidationError(
            code = code,
            message = msg,
        )
    }

    private fun invalidResult(error: JsonBackupValidationError): JsonBackupReadResult {
        return JsonBackupReadResult(
            isValid = false,
            errors = listOf(error),
            validatedData = null,
        )
    }

    private fun readBoundedBytes(inputStream: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        var totalRead = 0
        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) break
            totalRead += read
            if (totalRead > maxBytes) {
                throw ReaderRejectException(
                    code = JsonBackupErrorCode.FILE_TOO_LARGE,
                    message = "Fluxo de backup excede o limite seguro máximo de $maxBytes bytes.",
                )
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun estimateUtf8BytesExceeds(text: String, maxBytes: Int): Boolean {
        var utf8Bytes = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            utf8Bytes += when {
                c.code < 0x80 -> 1
                c.code < 0x800 -> 2
                Character.isHighSurrogate(c) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1]) -> {
                    i += 1
                    4
                }
                else -> 3
            }
            if (utf8Bytes > maxBytes) return true
            i += 1
        }
        return false
    }
}
