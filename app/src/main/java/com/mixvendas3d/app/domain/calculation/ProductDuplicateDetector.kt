package com.mixvendas3d.app.domain.calculation

import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductCategory
import com.mixvendas3d.app.util.CurrencyFormatter
import com.mixvendas3d.app.util.TextNormalization

/**
 * Candidato sendo cadastrado ou editado na tela de Produtos.
 *
 * A comparação de duplicidade considera exclusivamente 4 critérios:
 * 1. Nome normalizado (sem distinção de caixa, acentos e espaços extras).
 * 2. Preço total em centavos ([priceCents]).
 * 3. Categoria ([categoryId]) e subcategoria ([subcategoryId]).
 * 4. Divisão de valores por participante ([participantsByPersonId]: personId -> amountCents),
 *    independente da ordem.
 */
data class ProductDuplicateCandidate(
    val displayName: String,
    val priceCents: Long,
    val categoryId: String?,
    val subcategoryId: String?,
    val participantsByPersonId: Map<String, Long>,
    val participantNamesById: Map<String, String> = emptyMap(),
)

/**
 * Chave exata dos 4 critérios de duplicidade.
 *
 * Não inclui status ativo/inativo, regra de taxa, responsável pela taxa ou timestamps.
 */
data class ProductExactDuplicateSignature(
    val normalizedName: String,
    val priceCents: Long,
    val categoryId: String?,
    val subcategoryId: String?,
    val participantsByPersonId: Map<String, Long>,
)

/**
 * Chave de agrupamento rápido O(N) por (nome normalizado, preço total em centavos).
 */
data class ProductNamePriceKey(
    val normalizedName: String,
    val priceCents: Long,
)

enum class ParticipantDiffType {
    ADDED,
    REMOVED,
    AMOUNT_CHANGED,
    UNCHANGED,
}

data class ParticipantSplitDiff(
    val personId: String,
    val personName: String,
    val existingAmountCents: Long?,
    val candidateAmountCents: Long?,
    val diffType: ParticipantDiffType,
)

data class ProductFieldDifference(
    val fieldLabel: String,
    val existingValue: String,
    val candidateValue: String,
)

data class ProductDuplicateComparison(
    val existingProduct: Product,
    val existingCategoryName: String?,
    val existingSubcategoryName: String?,
    val candidateCategoryName: String?,
    val candidateSubcategoryName: String?,
    val isExactDuplicate: Boolean,
    val equalItems: List<String>,
    val fieldDifferences: List<ProductFieldDifference>,
    val participantDiffs: List<ParticipantSplitDiff>,
) {
    val hasParticipantDifferences: Boolean
        get() = participantDiffs.any { it.diffType != ParticipantDiffType.UNCHANGED }
}

sealed interface ProductDuplicateCheckResult {
    data object NoConflict : ProductDuplicateCheckResult

    data class ExactDuplicateBlocked(
        val matches: List<ProductDuplicateComparison>,
        val message: String = EXACT_DUPLICATE_ERROR_MESSAGE,
    ) : ProductDuplicateCheckResult

    data class SimilarRequiresConfirmation(
        val candidateDisplayName: String,
        val candidatePriceCents: Long,
        val candidateCategoryName: String?,
        val candidateSubcategoryName: String?,
        val candidateParticipants: List<Pair<String, Long>>,
        val matches: List<ProductDuplicateComparison>,
    ) : ProductDuplicateCheckResult

    companion object {
        const val EXACT_DUPLICATE_ERROR_MESSAGE: String =
            "Este produto já está cadastrado com exatamente as mesmas informações."
    }
}

data class AuditedProductEntry(
    val product: Product,
    val categoryName: String?,
    val subcategoryName: String?,
    val linkedSalesCount: Int,
) {
    val canDelete: Boolean
        get() = linkedSalesCount == 0

    val shortIdLabel: String
        get() = product.id.take(8).uppercase()
}

data class ExactDuplicateGroup(
    val groupId: String,
    val signature: ProductExactDuplicateSignature,
    val displayName: String,
    val priceCents: Long,
    val categoryName: String?,
    val subcategoryName: String?,
    val entries: List<AuditedProductEntry>,
) {
    val copyCount: Int
        get() = entries.size
}

data class SimilarProductGroup(
    val groupId: String,
    val key: ProductNamePriceKey,
    val displayName: String,
    val priceCents: Long,
    val entries: List<AuditedProductEntry>,
    val differenceSummaries: List<String>,
)

data class ProductDuplicateAuditReport(
    val totalProductsAnalyzed: Int,
    val exactDuplicateGroups: List<ExactDuplicateGroup>,
    val exactDuplicateRecordsCount: Int,
    val similarProductGroups: List<SimilarProductGroup>,
    val catalogFingerprint: String,
    val analyzedAtEpochMs: Long,
) {
    val hasFindings: Boolean
        get() = exactDuplicateGroups.isNotEmpty() || similarProductGroups.isNotEmpty()
}

object ProductDuplicateDetector {

    private const val NO_CATEGORY_LABEL = "Sem categoria"
    private const val NO_SUBCATEGORY_LABEL = "Sem subcategoria"

    fun normalizeName(rawName: String): String =
        TextNormalization.normalizeForDuplicateComparison(rawName)

    fun signatureOf(product: Product): ProductExactDuplicateSignature =
        ProductExactDuplicateSignature(
            normalizedName = normalizeName(product.name),
            priceCents = product.price.rawCents,
            categoryId = product.categoryId,
            subcategoryId = product.subcategoryId,
            participantsByPersonId = product.participants.associate { it.personId to it.amount.rawCents },
        )

    fun signatureOf(candidate: ProductDuplicateCandidate): ProductExactDuplicateSignature =
        ProductExactDuplicateSignature(
            normalizedName = normalizeName(candidate.displayName),
            priceCents = candidate.priceCents,
            categoryId = candidate.categoryId,
            subcategoryId = candidate.subcategoryId,
            participantsByPersonId = candidate.participantsByPersonId.toMap(),
        )

    /**
     * Avalia um produto em cadastro ou edição contra todos os produtos existentes no catálogo
     * (ativos e inativos), ignorando [excludingProductId] na edição.
     */
    fun evaluateCandidate(
        candidate: ProductDuplicateCandidate,
        existingProducts: List<Product>,
        categoriesById: Map<String, ProductCategory>,
        excludingProductId: String? = null,
    ): ProductDuplicateCheckResult {
        val candidateSignature = signatureOf(candidate)
        if (candidateSignature.normalizedName.isEmpty()) {
            return ProductDuplicateCheckResult.NoConflict
        }

        val sameNameAndPrice = existingProducts.filter { existing ->
            if (excludingProductId != null && existing.id == excludingProductId) {
                false
            } else {
                existing.price.rawCents == candidateSignature.priceCents &&
                    normalizeName(existing.name) == candidateSignature.normalizedName
            }
        }

        if (sameNameAndPrice.isEmpty()) {
            return ProductDuplicateCheckResult.NoConflict
        }

        val comparisons = sameNameAndPrice
            .sortedWith(compareBy<Product>({ it.createdAtEpochMs }, { it.id }))
            .map { existing ->
                buildComparison(
                    candidate = candidate,
                    candidateSignature = candidateSignature,
                    existing = existing,
                    categoriesById = categoriesById,
                )
            }

        val exactMatches = comparisons.filter { it.isExactDuplicate }
        if (exactMatches.isNotEmpty()) {
            return ProductDuplicateCheckResult.ExactDuplicateBlocked(matches = exactMatches)
        }

        val candidateParticipantsSorted = candidate.participantsByPersonId.entries
            .map { (personId, cents) ->
                val personName = candidate.participantNamesById[personId] ?: personId
                personName to cents
            }
            .sortedBy { normalizeName(it.first) }

        return ProductDuplicateCheckResult.SimilarRequiresConfirmation(
            candidateDisplayName = candidate.displayName.trim(),
            candidatePriceCents = candidate.priceCents,
            candidateCategoryName = candidate.categoryId?.let { categoriesById[it]?.name },
            candidateSubcategoryName = candidate.subcategoryId?.let { categoriesById[it]?.name },
            candidateParticipants = candidateParticipantsSorted,
            matches = comparisons,
        )
    }

    /**
     * Audita o catálogo completo (ativos e inativos) em complexidade O(N) agrupando por
     * (nome normalizado, preço total em centavos) e subdividindo pela assinatura exata dos 4 critérios.
     */
    fun auditCatalog(
        products: List<Product>,
        categoriesById: Map<String, ProductCategory>,
        salesCountByProductId: Map<String, Int>,
        analyzedAtEpochMs: Long = System.currentTimeMillis(),
    ): ProductDuplicateAuditReport {
        val sortedProducts = products.sortedWith(compareBy<Product>({ it.createdAtEpochMs }, { it.id }))
        val fingerprint = computeCatalogFingerprint(sortedProducts, salesCountByProductId)

        val buckets = LinkedHashMap<ProductNamePriceKey, MutableList<Product>>(sortedProducts.size)
        for (product in sortedProducts) {
            val key = ProductNamePriceKey(
                normalizedName = normalizeName(product.name),
                priceCents = product.price.rawCents,
            )
            buckets.getOrPut(key) { ArrayList(2) }.add(product)
        }

        val exactGroups = ArrayList<ExactDuplicateGroup>()
        val similarGroups = ArrayList<SimilarProductGroup>()

        for ((namePriceKey, bucketProducts) in buckets) {
            if (bucketProducts.size < 2) continue

            val byExactSignature = LinkedHashMap<ProductExactDuplicateSignature, MutableList<Product>>()
            for (product in bucketProducts) {
                byExactSignature.getOrPut(signatureOf(product)) { ArrayList(2) }.add(product)
            }

            var exactIndex = 0
            for ((signature, exactList) in byExactSignature) {
                if (exactList.size >= 2) {
                    val entries = exactList.map { it.toAuditedEntry(categoriesById, salesCountByProductId) }
                    val first = entries.first()
                    exactGroups.add(
                        ExactDuplicateGroup(
                            groupId = "exact_${namePriceKey.normalizedName}_${namePriceKey.priceCents}_$exactIndex",
                            signature = signature,
                            displayName = first.product.name,
                            priceCents = namePriceKey.priceCents,
                            categoryName = first.categoryName,
                            subcategoryName = first.subcategoryName,
                            entries = entries,
                        ),
                    )
                    exactIndex++
                }
            }

            if (byExactSignature.size >= 2) {
                val entries = bucketProducts.map { it.toAuditedEntry(categoriesById, salesCountByProductId) }
                val differenceSummaries = buildSimilarGroupDifferenceSummaries(entries)
                similarGroups.add(
                    SimilarProductGroup(
                        groupId = "similar_${namePriceKey.normalizedName}_${namePriceKey.priceCents}",
                        key = namePriceKey,
                        displayName = entries.first().product.name,
                        priceCents = namePriceKey.priceCents,
                        entries = entries,
                        differenceSummaries = differenceSummaries,
                    ),
                )
            }
        }

        exactGroups.sortWith(compareBy({ normalizeName(it.displayName) }, { it.priceCents }, { it.groupId }))
        similarGroups.sortWith(compareBy({ normalizeName(it.displayName) }, { it.priceCents }, { it.groupId }))

        val exactRecordsCount = exactGroups.sumOf { it.entries.size }
        return ProductDuplicateAuditReport(
            totalProductsAnalyzed = sortedProducts.size,
            exactDuplicateGroups = exactGroups,
            exactDuplicateRecordsCount = exactRecordsCount,
            similarProductGroups = similarGroups,
            catalogFingerprint = fingerprint,
            analyzedAtEpochMs = analyzedAtEpochMs,
        )
    }

    fun computeCatalogFingerprint(
        products: List<Product>,
        salesCountByProductId: Map<String, Int>,
    ): String {
        val sb = StringBuilder(products.size * 48)
        products
            .sortedWith(compareBy<Product>({ it.id }))
            .forEach { product ->
                sb.append(product.id).append('|')
                    .append(product.updatedAtEpochMs).append('|')
                    .append(product.price.rawCents).append('|')
                    .append(product.categoryId.orEmpty()).append('|')
                    .append(product.subcategoryId.orEmpty()).append('|')
                    .append(product.active).append('|')
                    .append(salesCountByProductId[product.id] ?: 0).append('|')
                product.participants
                    .sortedBy { it.personId }
                    .forEach { split ->
                        sb.append(split.personId).append(':').append(split.amount.rawCents).append(',')
                    }
                sb.append(';')
            }
        return sb.toString().hashCode().toString() + ":" + products.size
    }

    private fun Product.toAuditedEntry(
        categoriesById: Map<String, ProductCategory>,
        salesCountByProductId: Map<String, Int>,
    ): AuditedProductEntry = AuditedProductEntry(
        product = this,
        categoryName = categoryId?.let { categoriesById[it]?.name },
        subcategoryName = subcategoryId?.let { categoriesById[it]?.name },
        linkedSalesCount = salesCountByProductId[id] ?: 0,
    )

    private fun buildComparison(
        candidate: ProductDuplicateCandidate,
        candidateSignature: ProductExactDuplicateSignature,
        existing: Product,
        categoriesById: Map<String, ProductCategory>,
    ): ProductDuplicateComparison {
        val existingSignature = signatureOf(existing)
        val isExact = existingSignature == candidateSignature

        val existingCategoryName = existing.categoryId?.let { categoriesById[it]?.name }
        val existingSubcategoryName = existing.subcategoryId?.let { categoriesById[it]?.name }
        val candidateCategoryName = candidate.categoryId?.let { categoriesById[it]?.name }
        val candidateSubcategoryName = candidate.subcategoryId?.let { categoriesById[it]?.name }

        val equalItems = mutableListOf(
            "Nome: ${candidate.displayName.trim()}",
            "Preço total: ${CurrencyFormatter.formatBrl(MoneyCents(candidate.priceCents))}",
        )
        val fieldDifferences = mutableListOf<ProductFieldDifference>()

        if (existing.categoryId == candidate.categoryId) {
            equalItems.add("Categoria: ${existingCategoryName ?: NO_CATEGORY_LABEL}")
        } else {
            fieldDifferences.add(
                ProductFieldDifference(
                    fieldLabel = "Categoria",
                    existingValue = existingCategoryName ?: NO_CATEGORY_LABEL,
                    candidateValue = candidateCategoryName ?: NO_CATEGORY_LABEL,
                ),
            )
        }

        if (existing.subcategoryId == candidate.subcategoryId) {
            equalItems.add("Subcategoria: ${existingSubcategoryName ?: NO_SUBCATEGORY_LABEL}")
        } else {
            fieldDifferences.add(
                ProductFieldDifference(
                    fieldLabel = "Subcategoria",
                    existingValue = existingSubcategoryName ?: NO_SUBCATEGORY_LABEL,
                    candidateValue = candidateSubcategoryName ?: NO_SUBCATEGORY_LABEL,
                ),
            )
        }

        val existingNamesById = existing.participants.associate { it.personId to it.personName }
        val allPersonIds = (existingSignature.participantsByPersonId.keys + candidateSignature.participantsByPersonId.keys)
            .sortedBy { personId ->
                val resolvedName = candidate.participantNamesById[personId]
                    ?: existingNamesById[personId]
                    ?: personId
                normalizeName(resolvedName)
            }

        val participantDiffs = allPersonIds.map { personId ->
            val existingAmount = existingSignature.participantsByPersonId[personId]
            val candidateAmount = candidateSignature.participantsByPersonId[personId]
            val personName = candidate.participantNamesById[personId]
                ?: existingNamesById[personId]
                ?: personId

            val diffType = when {
                existingAmount == null && candidateAmount != null -> ParticipantDiffType.ADDED
                existingAmount != null && candidateAmount == null -> ParticipantDiffType.REMOVED
                existingAmount != candidateAmount -> ParticipantDiffType.AMOUNT_CHANGED
                else -> ParticipantDiffType.UNCHANGED
            }
            ParticipantSplitDiff(
                personId = personId,
                personName = personName,
                existingAmountCents = existingAmount,
                candidateAmountCents = candidateAmount,
                diffType = diffType,
            )
        }

        if (existingSignature.participantsByPersonId == candidateSignature.participantsByPersonId) {
            val formattedSplit = participantDiffs.joinToString(" • ") { diff ->
                val cents = diff.candidateAmountCents ?: 0L
                "${diff.personName}: ${CurrencyFormatter.formatBrl(MoneyCents(cents))}"
            }
            equalItems.add("Divisão de participantes: $formattedSplit")
        }

        return ProductDuplicateComparison(
            existingProduct = existing,
            existingCategoryName = existingCategoryName,
            existingSubcategoryName = existingSubcategoryName,
            candidateCategoryName = candidateCategoryName,
            candidateSubcategoryName = candidateSubcategoryName,
            isExactDuplicate = isExact,
            equalItems = equalItems,
            fieldDifferences = fieldDifferences,
            participantDiffs = participantDiffs,
        )
    }

    private fun buildSimilarGroupDifferenceSummaries(entries: List<AuditedProductEntry>): List<String> {
        val summaries = mutableListOf<String>()

        val distinctCategories = entries.map { it.categoryName ?: NO_CATEGORY_LABEL }.distinct()
        if (distinctCategories.size > 1) {
            summaries.add("Categoria diferente: ${distinctCategories.joinToString(" vs ")}")
        }

        val distinctSubcategories = entries.map { it.subcategoryName ?: NO_SUBCATEGORY_LABEL }.distinct()
        if (distinctSubcategories.size > 1) {
            summaries.add("Subcategoria diferente: ${distinctSubcategories.joinToString(" vs ")}")
        }

        val distinctParticipantSets = entries
            .map { entry -> entry.product.participants.map { it.personId }.toSet() }
            .distinct()
        val distinctSplitMaps = entries
            .map { entry -> entry.product.participants.associate { it.personId to it.amount.rawCents } }
            .distinct()

        if (distinctParticipantSets.size > 1) {
            summaries.add("Pessoas participantes diferentes entre os registros")
        } else if (distinctSplitMaps.size > 1) {
            summaries.add("Valores individuais divididos de forma diferente entre os participantes")
        }

        return summaries
    }
}
