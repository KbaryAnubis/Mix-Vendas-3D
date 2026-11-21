package com.mixvendas3d.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mixvendas3d.app.data.repository.CatalogRepository
import com.mixvendas3d.app.data.local.dao.ProductDeletionResult
import com.mixvendas3d.app.domain.calculation.CatalogValidationException
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersonWithUsage
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductCategory
import com.mixvendas3d.app.util.CurrencyFormatter
import com.mixvendas3d.app.util.MonetaryInputMask
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NamesSectionUiState(
    val people: List<PersonWithUsage> = emptyList(),
    val editingPersonId: String? = null,
    val nameInput: String = "",
    val errorMessage: String? = null,
    val feedbackMessage: String? = null,
    val isSaving: Boolean = false,
)

data class ProductEditorUiState(
    val isOpen: Boolean = false,
    val editingProductId: String? = null,
    val nameInput: String = "",
    val priceInput: String = "",
    val active: Boolean = true,
    val categoryId: String? = null,
    val subcategoryId: String? = null,
    val selectedParticipantOrder: List<String> = emptyList(),
    val participantAmountInputs: Map<String, String> = emptyMap(),
    val feeResponsibilityMode: FeeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
    val feeOwnerPersonId: String? = null,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
)

data class ProductDeletionPrompt(
    val product: Product,
    val isChecking: Boolean = true,
    val salesCount: Int? = null,
    val errorMessage: String? = null,
    val isDeleting: Boolean = false,
)

data class ProductsSectionUiState(
    val products: List<Product> = emptyList(),
    val categories: List<ProductCategory> = emptyList(),
    val searchQuery: String = "",
    val showOnlyActive: Boolean = false,
    val feedbackMessage: String? = null,
    val editor: ProductEditorUiState = ProductEditorUiState(),
    val deletionPrompt: ProductDeletionPrompt? = null,
)

data class CatalogUiState(
    val names: NamesSectionUiState = NamesSectionUiState(),
    val products: ProductsSectionUiState = ProductsSectionUiState(),
) {
    val filteredProducts: List<Product>
        get() {
            val ptBr = Locale.forLanguageTag("pt-BR")
            val query = products.searchQuery.trim().lowercase(ptBr)
            return products.products.filter { product ->
                val matchesActive = !products.showOnlyActive || product.active
                val matchesSearch = query.isEmpty() ||
                    product.name.lowercase(ptBr).contains(query) ||
                    product.participants.any { it.personName.lowercase(ptBr).contains(query) }
                matchesActive && matchesSearch
            }
        }
}

class CatalogViewModel(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val namesFormFlow = MutableStateFlow(NamesFormState())
    private val productsFormFlow = MutableStateFlow(ProductsFormState())

    val uiState: StateFlow<CatalogUiState> = combine(
        catalogRepository.observePeopleWithUsage().onStart { emit(emptyList()) },
        catalogRepository.observeAllProducts().onStart { emit(emptyList()) },
        catalogRepository.observeProductCategories().onStart { emit(emptyList()) },
        namesFormFlow,
        productsFormFlow,
    ) { people, products, categories, namesForm, productsForm ->
        CatalogUiState(
            names = NamesSectionUiState(
                people = people,
                editingPersonId = namesForm.editingPersonId,
                nameInput = namesForm.nameInput,
                errorMessage = namesForm.errorMessage,
                feedbackMessage = namesForm.feedbackMessage,
                isSaving = namesForm.isSaving,
            ),
            products = ProductsSectionUiState(
                products = products,
                categories = categories,
                searchQuery = productsForm.searchQuery,
                showOnlyActive = productsForm.showOnlyActive,
                feedbackMessage = productsForm.feedbackMessage,
                editor = productsForm.editor,
                deletionPrompt = productsForm.deletionPrompt,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CatalogUiState(),
    )

    // --- Ações de Nomes (Pessoas) ---

    fun onPersonNameInputChanged(value: String) {
        namesFormFlow.value = namesFormFlow.value.copy(
            nameInput = value,
            errorMessage = null,
            feedbackMessage = null,
        )
    }

    fun startEditingPerson(personWithUsage: PersonWithUsage) {
        namesFormFlow.value = namesFormFlow.value.copy(
            editingPersonId = personWithUsage.person.id,
            nameInput = personWithUsage.person.name,
            errorMessage = null,
            feedbackMessage = null,
        )
    }

    fun cancelEditingPerson() {
        namesFormFlow.value = NamesFormState()
    }

    fun savePerson() {
        val form = namesFormFlow.value
        if (form.isSaving) return
        namesFormFlow.value = form.copy(isSaving = true, errorMessage = null, feedbackMessage = null)

        viewModelScope.launch {
            try {
                if (form.editingPersonId == null) {
                    val created = catalogRepository.createPerson(form.nameInput)
                    namesFormFlow.value = NamesFormState(
                        feedbackMessage = "\"${created.name}\" adicionada com sucesso.",
                    )
                } else {
                    val renamed = catalogRepository.renamePerson(form.editingPersonId, form.nameInput)
                    namesFormFlow.value = NamesFormState(
                        feedbackMessage = "Nome atualizado para \"${renamed.name}\".",
                    )
                }
            } catch (ex: CatalogValidationException) {
                namesFormFlow.value = namesFormFlow.value.copy(
                    isSaving = false,
                    errorMessage = ex.message,
                )
            } catch (ex: Exception) {
                namesFormFlow.value = namesFormFlow.value.copy(
                    isSaving = false,
                    errorMessage = ex.message ?: "Erro ao salvar pessoa no banco local.",
                )
            }
        }
    }

    fun deletePerson(personId: String) {
        namesFormFlow.value = namesFormFlow.value.copy(errorMessage = null, feedbackMessage = null)
        viewModelScope.launch {
            try {
                catalogRepository.deletePerson(personId)
                val current = namesFormFlow.value
                namesFormFlow.value = if (current.editingPersonId == personId) {
                    NamesFormState(feedbackMessage = "Pessoa excluída com sucesso.")
                } else {
                    current.copy(feedbackMessage = "Pessoa excluída com sucesso.")
                }
            } catch (ex: CatalogValidationException) {
                namesFormFlow.value = namesFormFlow.value.copy(
                    errorMessage = ex.message,
                )
            } catch (ex: Exception) {
                namesFormFlow.value = namesFormFlow.value.copy(
                    errorMessage = ex.message ?: "Não foi possível excluir a pessoa.",
                )
            }
        }
    }

    // --- Ações de Tipos / Produtos ---

    fun onProductSearchChanged(query: String) {
        productsFormFlow.value = productsFormFlow.value.copy(searchQuery = query)
    }

    fun onShowOnlyActiveToggled(onlyActive: Boolean) {
        productsFormFlow.value = productsFormFlow.value.copy(showOnlyActive = onlyActive)
    }

    fun openNewProductEditor() {
        productsFormFlow.value = productsFormFlow.value.copy(
            feedbackMessage = null,
            editor = ProductEditorUiState(isOpen = true),
        )
    }

    fun openEditProductEditor(product: Product) {
        val participantIds = product.participants.map { it.personId }
        val amountInputs = product.participants.associate { split ->
            split.personId to MonetaryInputMask.formatCentsForMaskedInput(split.amount.rawCents)
        }
        productsFormFlow.value = productsFormFlow.value.copy(
            feedbackMessage = null,
            editor = ProductEditorUiState(
                isOpen = true,
                editingProductId = product.id,
                nameInput = product.name,
                priceInput = MonetaryInputMask.formatCentsForMaskedInput(product.price.rawCents),
                active = product.active,
                categoryId = product.categoryId,
                subcategoryId = product.subcategoryId,
                selectedParticipantOrder = participantIds,
                participantAmountInputs = amountInputs,
                feeResponsibilityMode = product.feeResponsibilityMode,
                feeOwnerPersonId = product.feeOwnerPersonId,
            ),
        )
    }

    fun closeProductEditor() {
        productsFormFlow.value = productsFormFlow.value.copy(
            editor = ProductEditorUiState(isOpen = false),
        )
    }

    fun onProductNameChanged(value: String) {
        updateEditor { it.copy(nameInput = value, errorMessage = null) }
    }

    fun onProductPriceChanged(value: String) {
        val masked = MonetaryInputMask.formatDigitsInput(value)
        updateEditor { it.copy(priceInput = masked, errorMessage = null) }
    }

    fun onProductActiveChanged(active: Boolean) {
        updateEditor { it.copy(active = active, errorMessage = null) }
    }

    fun onProductCategoryChanged(categoryId: String?) {
        val categories = uiState.value.products.categories
        updateEditor { editor ->
            val keepSubcategory = editor.subcategoryId?.takeIf { subId ->
                categories.any { it.id == subId && it.parentId == categoryId }
            }
            editor.copy(categoryId = categoryId, subcategoryId = keepSubcategory, errorMessage = null)
        }
    }

    fun onProductSubcategoryChanged(subcategoryId: String?) {
        updateEditor { it.copy(subcategoryId = subcategoryId, errorMessage = null) }
    }

    fun createProductCategory(name: String, parentId: String? = null) {
        viewModelScope.launch {
            try {
                catalogRepository.createProductCategory(name, parentId)
                productsFormFlow.value = productsFormFlow.value.copy(
                    feedbackMessage = if (parentId == null) "Categoria criada." else "Subcategoria criada.",
                )
            } catch (ex: Exception) {
                productsFormFlow.value = productsFormFlow.value.copy(
                    feedbackMessage = ex.message ?: "Não foi possível criar a categoria.",
                )
            }
        }
    }

    fun renameProductCategory(categoryId: String, name: String) {
        viewModelScope.launch {
            try {
                catalogRepository.renameProductCategory(categoryId, name)
                productsFormFlow.value = productsFormFlow.value.copy(feedbackMessage = "Categoria renomeada.")
            } catch (ex: Exception) {
                productsFormFlow.value = productsFormFlow.value.copy(
                    feedbackMessage = ex.message ?: "Não foi possível renomear a categoria.",
                )
            }
        }
    }

    fun deleteProductCategory(categoryId: String) {
        viewModelScope.launch {
            try {
                catalogRepository.deleteProductCategory(categoryId)
                productsFormFlow.value = productsFormFlow.value.copy(feedbackMessage = "Categoria excluída.")
            } catch (ex: Exception) {
                productsFormFlow.value = productsFormFlow.value.copy(
                    feedbackMessage = ex.message ?: "Não foi possível excluir a categoria.",
                )
            }
        }
    }

    fun toggleProductParticipant(personId: String) {
        updateEditor { editor ->
            val currentOrder = editor.selectedParticipantOrder.toMutableList()
            val currentAmounts = editor.participantAmountInputs.toMutableMap()
            var nextOwner = editor.feeOwnerPersonId

            if (currentOrder.contains(personId)) {
                currentOrder.remove(personId)
                currentAmounts.remove(personId)
                if (nextOwner == personId) {
                    nextOwner = currentOrder.firstOrNull()
                }
            } else {
                currentOrder.add(personId)
                currentAmounts.putIfAbsent(personId, "")
                if (editor.feeResponsibilityMode == FeeResponsibilityMode.INDIVIDUAL && nextOwner == null) {
                    nextOwner = personId
                }
            }

            editor.copy(
                selectedParticipantOrder = currentOrder,
                participantAmountInputs = currentAmounts,
                feeOwnerPersonId = nextOwner,
                errorMessage = null,
            )
        }
    }

    fun onParticipantAmountChanged(personId: String, amountInput: String) {
        val masked = MonetaryInputMask.formatDigitsInput(amountInput)
        updateEditor { editor ->
            val updated = editor.participantAmountInputs.toMutableMap()
            updated[personId] = masked
            editor.copy(
                participantAmountInputs = updated,
                errorMessage = null,
            )
        }
    }

    fun onFeeResponsibilityModeChanged(mode: FeeResponsibilityMode) {
        updateEditor { editor ->
            val ownerId = when (mode) {
                FeeResponsibilityMode.INDIVIDUAL -> {
                    editor.feeOwnerPersonId
                        ?.takeIf { editor.selectedParticipantOrder.contains(it) }
                        ?: editor.selectedParticipantOrder.firstOrNull()
                }
                FeeResponsibilityMode.EQUAL,
                FeeResponsibilityMode.PROPORTIONAL -> null
            }
            editor.copy(
                feeResponsibilityMode = mode,
                feeOwnerPersonId = ownerId,
                errorMessage = null,
            )
        }
    }

    fun onFeeOwnerSelected(personId: String) {
        updateEditor { it.copy(feeOwnerPersonId = personId, errorMessage = null) }
    }

    fun saveProduct() {
        val editor = productsFormFlow.value.editor
        if (editor.isSaving) return

        val parsedPriceCents = CurrencyFormatter.parseBrlToCents(editor.priceInput)
        if (parsedPriceCents == null) {
            updateEditor { it.copy(errorMessage = "Informe um preço padrão válido em reais (ex.: 22,00).") }
            return
        }

        val drafts = mutableListOf<ProductParticipantDraft>()
        for (personId in editor.selectedParticipantOrder) {
            val rawInput = editor.participantAmountInputs[personId].orEmpty()
            val parsedCents = CurrencyFormatter.parseBrlToCents(rawInput)
            if (parsedCents == null) {
                updateEditor {
                    it.copy(errorMessage = "Informe um valor em reais válido para cada participante marcado.")
                }
                return
            }
            drafts.add(ProductParticipantDraft(personId = personId, amount = MoneyCents(parsedCents)))
        }

        updateEditor { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                if (editor.editingProductId == null) {
                    val created = catalogRepository.createProduct(
                        name = editor.nameInput,
                        price = MoneyCents(parsedPriceCents),
                        active = editor.active,
                        participants = drafts,
                        feeResponsibilityMode = editor.feeResponsibilityMode,
                        feeOwnerPersonId = editor.feeOwnerPersonId,
                        categoryId = editor.categoryId,
                        subcategoryId = editor.subcategoryId,
                    )
                    productsFormFlow.value = productsFormFlow.value.copy(
                        feedbackMessage = "Produto \"${created.name}\" cadastrado no Room.",
                        editor = ProductEditorUiState(isOpen = false),
                    )
                } else {
                    val updated = catalogRepository.updateProduct(
                        productId = editor.editingProductId,
                        name = editor.nameInput,
                        price = MoneyCents(parsedPriceCents),
                        active = editor.active,
                        participants = drafts,
                        feeResponsibilityMode = editor.feeResponsibilityMode,
                        feeOwnerPersonId = editor.feeOwnerPersonId,
                        categoryId = editor.categoryId,
                        subcategoryId = editor.subcategoryId,
                    )
                    productsFormFlow.value = productsFormFlow.value.copy(
                        feedbackMessage = "Produto \"${updated.name}\" atualizado.",
                        editor = ProductEditorUiState(isOpen = false),
                    )
                }
            } catch (ex: CatalogValidationException) {
                updateEditor { it.copy(isSaving = false, errorMessage = ex.message) }
            } catch (ex: Exception) {
                updateEditor {
                    it.copy(isSaving = false, errorMessage = ex.message ?: "Erro ao salvar produto.")
                }
            }
        }
    }

    fun requestProductDeletion(product: Product) {
        productsFormFlow.value = productsFormFlow.value.copy(
            deletionPrompt = ProductDeletionPrompt(product = product),
        )
        viewModelScope.launch {
            try {
                val salesCount = catalogRepository.countSalesUsingProduct(product.id)
                val current = productsFormFlow.value
                val currentPrompt = current.deletionPrompt
                if (currentPrompt?.product?.id == product.id) {
                    productsFormFlow.value = current.copy(
                        deletionPrompt = currentPrompt.copy(
                            isChecking = false,
                            salesCount = salesCount,
                        ),
                    )
                }
            } catch (ex: Exception) {
                val current = productsFormFlow.value
                val currentPrompt = current.deletionPrompt
                if (currentPrompt?.product?.id == product.id) {
                    productsFormFlow.value = current.copy(
                        deletionPrompt = currentPrompt.copy(
                            isChecking = false,
                            errorMessage = ex.message ?: "Não foi possível verificar o histórico de vendas.",
                        ),
                    )
                }
            }
        }
    }

    fun dismissProductDeletionPrompt() {
        productsFormFlow.value = productsFormFlow.value.copy(deletionPrompt = null)
    }

    fun confirmProductDeletion() {
        val prompt = productsFormFlow.value.deletionPrompt ?: return
        if (prompt.isChecking || prompt.isDeleting || prompt.salesCount != 0 || prompt.errorMessage != null) return
        productsFormFlow.value = productsFormFlow.value.copy(
            deletionPrompt = prompt.copy(isDeleting = true),
        )
        viewModelScope.launch {
            try {
                when (val result = catalogRepository.deleteProductSafely(prompt.product.id)) {
                    ProductDeletionResult.Deleted -> {
                        productsFormFlow.value = productsFormFlow.value.copy(
                            deletionPrompt = null,
                            feedbackMessage = "Produto \"${prompt.product.name}\" excluído.",
                        )
                    }
                    ProductDeletionResult.NotFound -> {
                        productsFormFlow.value = productsFormFlow.value.copy(
                            deletionPrompt = null,
                            feedbackMessage = "O produto não foi encontrado para exclusão.",
                        )
                    }
                    is ProductDeletionResult.BlockedBySales -> {
                        productsFormFlow.value = productsFormFlow.value.copy(
                            deletionPrompt = prompt.copy(
                                isChecking = false,
                                isDeleting = false,
                                salesCount = result.salesCount,
                            ),
                        )
                    }
                }
            } catch (ex: Exception) {
                productsFormFlow.value = productsFormFlow.value.copy(
                    deletionPrompt = prompt.copy(
                        isChecking = false,
                        isDeleting = false,
                        errorMessage = ex.message ?: "Não foi possível excluir o produto.",
                    ),
                )
            }
        }
    }

    private fun updateEditor(transform: (ProductEditorUiState) -> ProductEditorUiState) {
        val current = productsFormFlow.value
        productsFormFlow.value = current.copy(editor = transform(current.editor))
    }

    private data class NamesFormState(
        val editingPersonId: String? = null,
        val nameInput: String = "",
        val errorMessage: String? = null,
        val feedbackMessage: String? = null,
        val isSaving: Boolean = false,
    )

    private data class ProductsFormState(
        val searchQuery: String = "",
        val showOnlyActive: Boolean = false,
        val feedbackMessage: String? = null,
        val editor: ProductEditorUiState = ProductEditorUiState(),
        val deletionPrompt: ProductDeletionPrompt? = null,
    )

    companion object {
        fun provideFactory(
            catalogRepository: CatalogRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CatalogViewModel(catalogRepository) as T
            }
        }
    }
}
