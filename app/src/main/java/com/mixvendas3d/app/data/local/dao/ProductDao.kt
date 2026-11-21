package com.mixvendas3d.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mixvendas3d.app.data.local.entity.ProductCategoryEntity
import com.mixvendas3d.app.data.local.entity.ProductEntity
import com.mixvendas3d.app.data.local.entity.ProductParticipantEntity
import com.mixvendas3d.app.data.local.entity.ProductWithParticipants
import kotlinx.coroutines.flow.Flow

sealed interface ProductCategoryDeletionResult {
    data object Deleted : ProductCategoryDeletionResult
    data object NotFound : ProductCategoryDeletionResult
}

sealed interface ProductDeletionResult {
    data object Deleted : ProductDeletionResult
    data object NotFound : ProductDeletionResult
    data class BlockedBySales(val salesCount: Int) : ProductDeletionResult
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM product_categories ORDER BY parent_id IS NOT NULL, parent_id, normalized_name")
    fun observeCategories(): Flow<List<ProductCategoryEntity>>

    @Query("SELECT * FROM product_categories ORDER BY parent_id IS NOT NULL, parent_id, normalized_name")
    suspend fun getAllCategories(): List<ProductCategoryEntity>

    @Query("SELECT * FROM product_categories WHERE id = :categoryId LIMIT 1")
    suspend fun getCategoryById(categoryId: String): ProductCategoryEntity?

    @Query("SELECT * FROM product_categories WHERE parent_id IS :parentId AND normalized_name = :normalizedName LIMIT 1")
    suspend fun getCategoryByParentAndNormalizedName(
        parentId: String?,
        normalizedName: String,
    ): ProductCategoryEntity?

    @Query("SELECT * FROM product_categories WHERE parent_id = :parentId ORDER BY normalized_name")
    suspend fun getSubcategories(parentId: String): List<ProductCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: ProductCategoryEntity)

    @Update
    suspend fun updateCategory(category: ProductCategoryEntity)

    @Query("DELETE FROM product_categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: String): Int

    @Query("UPDATE products SET category_id = NULL, subcategory_id = NULL WHERE category_id = :categoryId")
    suspend fun clearProductsForCategory(categoryId: String)

    @Query("UPDATE products SET subcategory_id = NULL WHERE subcategory_id = :subcategoryId")
    suspend fun clearProductsForSubcategory(subcategoryId: String)

    @Transaction
    suspend fun insertCategoryIfUnique(category: ProductCategoryEntity): Boolean {
        if (category.parentId != null) {
            val parent = getCategoryById(category.parentId) ?: return false
            if (parent.parentId != null) return false
        }
        if (getCategoryByParentAndNormalizedName(category.parentId, category.normalizedName) != null) {
            return false
        }
        insertCategory(category)
        return true
    }

    @Transaction
    suspend fun renameCategoryIfUnique(category: ProductCategoryEntity): Boolean {
        val current = getCategoryById(category.id) ?: return false
        if (current.parentId != category.parentId) return false
        if (getCategoryByParentAndNormalizedName(category.parentId, category.normalizedName)
                ?.let { it.id != category.id } == true
        ) {
            return false
        }
        updateCategory(category)
        return true
    }

    @Transaction
    suspend fun deleteCategorySafely(categoryId: String): ProductCategoryDeletionResult {
        val category = getCategoryById(categoryId) ?: return ProductCategoryDeletionResult.NotFound
        if (category.parentId == null) {
            getSubcategories(categoryId).forEach { clearProductsForSubcategory(it.id) }
            clearProductsForCategory(categoryId)
        } else {
            clearProductsForSubcategory(categoryId)
        }
        return if (deleteCategoryById(categoryId) > 0) {
            ProductCategoryDeletionResult.Deleted
        } else {
            ProductCategoryDeletionResult.NotFound
        }
    }
    @Transaction
    @Query("SELECT * FROM products ORDER BY active DESC, name COLLATE NOCASE ASC")
    fun observeAllWithParticipants(): Flow<List<ProductWithParticipants>>

    @Transaction
    @Query("SELECT * FROM products WHERE active = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeActiveWithParticipants(): Flow<List<ProductWithParticipants>>

    @Transaction
    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getWithParticipantsById(productId: String): ProductWithParticipants?

    @Query("SELECT * FROM products ORDER BY created_at_epoch_ms ASC, id ASC")
    suspend fun getAllProducts(): List<ProductEntity>

    @Query("SELECT * FROM product_participants ORDER BY product_id ASC, person_id ASC")
    suspend fun getAllProductParticipants(): List<ProductParticipantEntity>

    @Query("SELECT COUNT(*) FROM product_participants WHERE person_id = :personId")
    suspend fun countParticipantLinksForPerson(personId: String): Int

    @Query("SELECT COUNT(DISTINCT sale_id) FROM sale_items WHERE product_id = :productId")
    suspend fun countSalesUsingProduct(productId: String): Int

    @Query("SELECT id FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductId(productId: String): String?

    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteProductById(productId: String): Int

    @Query("SELECT COUNT(*) FROM products WHERE fee_owner_person_id = :personId")
    suspend fun countFeeOwnerLinksForPerson(personId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: ProductEntity)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertParticipants(participants: List<ProductParticipantEntity>)

    @Query("DELETE FROM product_participants WHERE product_id = :productId")
    suspend fun deleteParticipantsByProductId(productId: String)

    @Query("UPDATE products SET active = :active, updated_at_epoch_ms = :updatedAtEpochMs WHERE id = :productId")
    suspend fun updateActiveStatus(
        productId: String,
        active: Boolean,
        updatedAtEpochMs: Long,
    )

    @Transaction
    suspend fun deleteProductSafely(productId: String): ProductDeletionResult {
        if (getProductId(productId) == null) return ProductDeletionResult.NotFound
        val linkedSales = countSalesUsingProduct(productId)
        if (linkedSales > 0) return ProductDeletionResult.BlockedBySales(linkedSales)
        return if (deleteProductById(productId) > 0) {
            ProductDeletionResult.Deleted
        } else {
            ProductDeletionResult.NotFound
        }
    }

    @Transaction
    suspend fun upsertProductWithParticipants(
        product: ProductEntity,
        participants: List<ProductParticipantEntity>,
        isNew: Boolean,
    ) {
        if (isNew) {
            insertProduct(product)
        } else {
            updateProduct(product)
            deleteParticipantsByProductId(product.id)
        }
        insertParticipants(participants)
    }
}
