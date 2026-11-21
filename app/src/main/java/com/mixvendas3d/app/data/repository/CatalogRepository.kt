package com.mixvendas3d.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.mixvendas3d.app.data.local.dao.PersonDao
import com.mixvendas3d.app.data.local.dao.PersonDeletionResult
import com.mixvendas3d.app.data.local.dao.PersonWithCountRow
import com.mixvendas3d.app.data.local.dao.ProductDao
import com.mixvendas3d.app.data.local.dao.ProductCategoryDeletionResult
import com.mixvendas3d.app.data.local.dao.ProductDeletionResult
import com.mixvendas3d.app.data.local.dao.isForeignKeyConstraintViolation
import com.mixvendas3d.app.data.local.entity.PersonEntity
import com.mixvendas3d.app.data.local.entity.ProductCategoryEntity
import com.mixvendas3d.app.data.local.entity.ProductEntity
import com.mixvendas3d.app.data.local.entity.ProductParticipantEntity
import com.mixvendas3d.app.data.local.entity.ProductWithParticipants
import com.mixvendas3d.app.domain.calculation.CatalogValidationException
import com.mixvendas3d.app.domain.calculation.CatalogValidator
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.Person
import com.mixvendas3d.app.domain.models.PersonWithUsage
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductCategory
import com.mixvendas3d.app.domain.models.ProductParticipantSplit
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CatalogRepository {
    fun observePeople(): Flow<List<Person>>
    fun observePeopleWithUsage(): Flow<List<PersonWithUsage>>
    fun observeAllProducts(): Flow<List<Product>>
    fun observeActiveProducts(): Flow<List<Product>>
    fun observeProductCategories(): Flow<List<ProductCategory>>

    suspend fun createProductCategory(name: String, parentId: String? = null): ProductCategory
    suspend fun renameProductCategory(categoryId: String, newName: String): ProductCategory
    suspend fun deleteProductCategory(categoryId: String)

    suspend fun createPerson(name: String): Person
    suspend fun renamePerson(personId: String, newName: String): Person
    suspend fun deletePerson(personId: String)

    suspend fun createProduct(
        name: String,
        price: MoneyCents,
        active: Boolean,
        participants: List<ProductParticipantDraft>,
        feeResponsibilityMode: FeeResponsibilityMode,
        feeOwnerPersonId: String?,
        categoryId: String? = null,
        subcategoryId: String? = null,
    ): Product

    suspend fun updateProduct(
        productId: String,
        name: String,
        price: MoneyCents,
        active: Boolean,
        participants: List<ProductParticipantDraft>,
        feeResponsibilityMode: FeeResponsibilityMode,
        feeOwnerPersonId: String?,
        categoryId: String? = null,
        subcategoryId: String? = null,
    ): Product

    suspend fun setProductActive(productId: String, active: Boolean)
    suspend fun countSalesUsingProduct(productId: String): Int
    suspend fun deleteProductSafely(productId: String): ProductDeletionResult
}

class RoomCatalogRepository(
    private val personDao: PersonDao,
    private val productDao: ProductDao,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) : CatalogRepository {

    override fun observePeople(): Flow<List<Person>> =
        personDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observePeopleWithUsage(): Flow<List<PersonWithUsage>> =
        personDao.observeAllWithUsageCount().map { rows -> rows.map { it.toDomain() } }

    override fun observeAllProducts(): Flow<List<Product>> =
        productDao.observeAllWithParticipants().map { list -> list.map { it.toDomain() } }

    override fun observeActiveProducts(): Flow<List<Product>> =
        productDao.observeActiveWithParticipants().map { list -> list.map { it.toDomain() } }

    override fun observeProductCategories(): Flow<List<ProductCategory>> =
        productDao.observeCategories().map { rows -> rows.map { it.toDomain() } }

    override suspend fun createProductCategory(name: String, parentId: String?): ProductCategory {
        val cleanName = CatalogValidator.validateCategoryName(name)
        validateCategoryParent(parentId)
        val now = clockMillis()
        val entity = ProductCategoryEntity(
            id = idGenerator(),
            name = cleanName,
            normalizedName = CatalogValidator.normalizePersonName(cleanName),
            parentId = parentId,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        if (!productDao.insertCategoryIfUnique(entity)) {
            throw CatalogValidationException("Já existe uma categoria com esse nome neste nível.")
        }
        return entity.toDomain()
    }

    override suspend fun renameProductCategory(categoryId: String, newName: String): ProductCategory {
        val existing = productDao.getCategoryById(categoryId)
            ?: throw CatalogValidationException("Categoria não encontrada para edição.")
        val cleanName = CatalogValidator.validateCategoryName(newName)
        val updated = existing.copy(
            name = cleanName,
            normalizedName = CatalogValidator.normalizePersonName(cleanName),
            updatedAtEpochMs = clockMillis(),
        )
        if (!productDao.renameCategoryIfUnique(updated)) {
            throw CatalogValidationException("Já existe uma categoria com esse nome neste nível.")
        }
        return updated.toDomain()
    }

    override suspend fun deleteProductCategory(categoryId: String) {
        when (productDao.deleteCategorySafely(categoryId)) {
            ProductCategoryDeletionResult.Deleted -> Unit
            ProductCategoryDeletionResult.NotFound ->
                throw CatalogValidationException("Categoria não encontrada para exclusão.")
        }
    }

    private suspend fun validateCategoryParent(parentId: String?) {
        if (parentId == null) return
        val parent = productDao.getCategoryById(parentId)
            ?: throw CatalogValidationException("A categoria principal selecionada não existe.")
        if (parent.parentId != null) {
            throw CatalogValidationException("Subcategorias não podem ter um terceiro nível.")
        }
    }

    private suspend fun validateProductCategorySelection(categoryId: String?, subcategoryId: String?) {
        if (subcategoryId != null && categoryId == null) {
            throw CatalogValidationException("Selecione uma categoria principal para usar uma subcategoria.")
        }
        if (categoryId == null) return
        val category = productDao.getCategoryById(categoryId)
            ?: throw CatalogValidationException("A categoria selecionada não existe.")
        if (category.parentId != null) {
            throw CatalogValidationException("Selecione uma categoria principal, não uma subcategoria.")
        }
        if (subcategoryId != null) {
            val subcategory = productDao.getCategoryById(subcategoryId)
                ?: throw CatalogValidationException("A subcategoria selecionada não existe.")
            if (subcategory.parentId != categoryId) {
                throw CatalogValidationException("A subcategoria não pertence à categoria principal selecionada.")
            }
        }
    }

    override suspend fun createPerson(name: String): Person {
        val cleanName = CatalogValidator.validatePersonName(name)
        val normalized = CatalogValidator.normalizePersonName(cleanName)
        val duplicate = personDao.getByNormalizedName(normalized)
        if (duplicate != null) {
            throw CatalogValidationException("Já existe uma pessoa cadastrada com o nome \"${duplicate.name}\".")
        }

        val now = clockMillis()
        val nextColorIndex = personDao.getMaxColorIndex() + 1
        val entity = PersonEntity(
            id = idGenerator(),
            name = cleanName,
            normalizedName = normalized,
            colorIndex = nextColorIndex.coerceAtLeast(0),
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        personDao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun renamePerson(personId: String, newName: String): Person {
        val existing = personDao.getById(personId)
            ?: throw CatalogValidationException("Pessoa não encontrada para edição.")
        val cleanName = CatalogValidator.validatePersonName(newName)
        val normalized = CatalogValidator.normalizePersonName(cleanName)
        val duplicate = personDao.getByNormalizedName(normalized)
        if (duplicate != null && duplicate.id != personId) {
            throw CatalogValidationException("Já existe outra pessoa cadastrada com o nome \"${duplicate.name}\".")
        }

        val now = clockMillis()
        personDao.updateName(
            id = personId,
            name = cleanName,
            normalizedName = normalized,
            updatedAtEpochMs = now,
        )
        return existing.copy(
            name = cleanName,
            normalizedName = normalized,
            updatedAtEpochMs = now,
        ).toDomain()
    }

    override suspend fun deletePerson(personId: String) {
        val result = try {
            personDao.deletePersonSafely(personId)
        } catch (e: SQLiteConstraintException) {
            if (isForeignKeyConstraintViolation(e)) {
                val existing = personDao.getById(personId)
                val message = if (existing != null) {
                    "Não é possível excluir \"${existing.name}\" porque ela está vinculada a produto(s) existente(s)."
                } else {
                    "Não foi possível excluir a pessoa selecionada."
                }
                throw CatalogValidationException(message)
            }
            throw e
        }

        when (result) {
            PersonDeletionResult.Deleted -> Unit
            PersonDeletionResult.NotFound -> {
                throw CatalogValidationException("Pessoa não encontrada para exclusão.")
            }
            is PersonDeletionResult.BlockedByLinks -> {
                throw CatalogValidationException(
                    "Não é possível excluir \"${result.personName}\" porque ela está vinculada a produto(s) existente(s).",
                )
            }
            PersonDeletionResult.DeleteFailed -> {
                throw CatalogValidationException("Não foi possível excluir a pessoa selecionada.")
            }
        }
    }

    override suspend fun createProduct(
        name: String,
        price: MoneyCents,
        active: Boolean,
        participants: List<ProductParticipantDraft>,
        feeResponsibilityMode: FeeResponsibilityMode,
        feeOwnerPersonId: String?,
        categoryId: String?,
        subcategoryId: String?,
    ): Product {
        validateProductCategorySelection(categoryId, subcategoryId)
        val existingPersonIds = personDao.getAllIds().toSet()
        val validated = CatalogValidator.validateProductInput(
            rawName = name,
            price = price,
            active = active,
            participants = participants,
            existingPersonIds = existingPersonIds,
            feeResponsibilityMode = feeResponsibilityMode,
            feeOwnerPersonId = feeOwnerPersonId,
        )

        val now = clockMillis()
        val productId = idGenerator()
        val productEntity = ProductEntity(
            id = productId,
            name = validated.name,
            priceCents = validated.price.rawCents,
            active = validated.active,
            feeResponsibilityMode = validated.feeResponsibilityMode.storageKey,
            feeOwnerPersonId = validated.feeOwnerPersonId,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            categoryId = categoryId,
            subcategoryId = subcategoryId,
        )
        val participantEntities = validated.participants.mapIndexed { index, split ->
            ProductParticipantEntity(
                productId = productId,
                personId = split.personId,
                amountCents = split.amount.rawCents,
                sortOrder = index,
            )
        }
        productDao.upsertProductWithParticipants(
            product = productEntity,
            participants = participantEntities,
            isNew = true,
        )
        return productDao.getWithParticipantsById(productId)?.toDomain()
            ?: error("Produto recém-salvo não encontrado no banco.")
    }

    override suspend fun updateProduct(
        productId: String,
        name: String,
        price: MoneyCents,
        active: Boolean,
        participants: List<ProductParticipantDraft>,
        feeResponsibilityMode: FeeResponsibilityMode,
        feeOwnerPersonId: String?,
        categoryId: String?,
        subcategoryId: String?,
    ): Product {
        validateProductCategorySelection(categoryId, subcategoryId)
        val current = productDao.getWithParticipantsById(productId)
            ?: throw CatalogValidationException("Produto não encontrado para edição.")
        val existingPersonIds = personDao.getAllIds().toSet()
        val validated = CatalogValidator.validateProductInput(
            rawName = name,
            price = price,
            active = active,
            participants = participants,
            existingPersonIds = existingPersonIds,
            feeResponsibilityMode = feeResponsibilityMode,
            feeOwnerPersonId = feeOwnerPersonId,
        )

        val now = clockMillis()
        val updatedEntity = current.product.copy(
            name = validated.name,
            priceCents = validated.price.rawCents,
            active = validated.active,
            feeResponsibilityMode = validated.feeResponsibilityMode.storageKey,
            feeOwnerPersonId = validated.feeOwnerPersonId,
            updatedAtEpochMs = now,
            categoryId = categoryId,
            subcategoryId = subcategoryId,
        )
        val participantEntities = validated.participants.mapIndexed { index, split ->
            ProductParticipantEntity(
                productId = productId,
                personId = split.personId,
                amountCents = split.amount.rawCents,
                sortOrder = index,
            )
        }
        productDao.upsertProductWithParticipants(
            product = updatedEntity,
            participants = participantEntities,
            isNew = false,
        )
        return productDao.getWithParticipantsById(productId)?.toDomain()
            ?: error("Produto atualizado não encontrado no banco.")
    }

    override suspend fun countSalesUsingProduct(productId: String): Int =
        productDao.countSalesUsingProduct(productId)

    override suspend fun deleteProductSafely(productId: String): ProductDeletionResult =
        productDao.deleteProductSafely(productId)

    override suspend fun setProductActive(productId: String, active: Boolean) {
        productDao.getWithParticipantsById(productId)
            ?: throw CatalogValidationException("Produto não encontrado.")
        productDao.updateActiveStatus(
            productId = productId,
            active = active,
            updatedAtEpochMs = clockMillis(),
        )
    }

    private fun PersonEntity.toDomain(): Person = Person(
        id = id,
        name = name,
        colorIndex = colorIndex,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun PersonWithCountRow.toDomain(): PersonWithUsage = PersonWithUsage(
        person = Person(
            id = id,
            name = name,
            colorIndex = colorIndex,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
        ),
        linkedProductCount = linkedProductCount,
    )

    private fun ProductWithParticipants.toDomain(): Product = Product(
        id = product.id,
        name = product.name,
        price = MoneyCents(product.priceCents),
        active = product.active,
        feeResponsibilityMode = FeeResponsibilityMode.fromStorageKeyOrNull(product.feeResponsibilityMode)
            ?: error(
                "Modo de responsabilidade de tarifa inválido (${product.feeResponsibilityMode}) " +
                    "no produto \"${product.name}\" (id=${product.id}).",
            ),
        feeOwnerPersonId = product.feeOwnerPersonId,
        participants = participants
            .sortedBy { it.participant.sortOrder }
            .map { rel ->
                ProductParticipantSplit(
                    personId = rel.person.id,
                    personName = rel.person.name,
                    personColorIndex = rel.person.colorIndex,
                    amount = MoneyCents(rel.participant.amountCents),
                )
            },
        createdAtEpochMs = product.createdAtEpochMs,
        updatedAtEpochMs = product.updatedAtEpochMs,
        categoryId = product.categoryId,
        subcategoryId = product.subcategoryId,
    )

    private fun ProductCategoryEntity.toDomain(): ProductCategory = ProductCategory(
        id = id,
        name = name,
        normalizedName = normalizedName,
        parentId = parentId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}
