package com.mixvendas3d.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mixvendas3d.app.data.repository.CatalogRepository
import com.mixvendas3d.app.data.local.dao.ProductDeletionResult
import com.mixvendas3d.app.domain.calculation.CatalogValidationException
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.ProductPriceSplitCalculator
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersonWithUsage
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductCategory
import com.mixvendas3d.app.util.CurrencyFormatter
import com.mixvendas3d.app.util.MonetaryInputMask
import com.mixvendas3d.app.util.TextNormalization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val lastEditedParticipantId: String? = null,
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

data class CategoryEditorUiState(
    val isOpen: Boolean = false,
    val editingCategoryId: String? = null,
    val isSubcategory: Boolean = false,
    val parentId: String? = null,
    val nameInput: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

data class ProductsSectionUiState(
    val products: List<Product> = emptyList(),
    val categories: List<ProductCategory> = emptyList(),
    val searchQuery: String = "",
    val showOnlyActive: Boolean = false,
    val selectedCategoryFilterId: String? = null,
    val feedbackMessage: String? = null,
    val feedbackIsError: Boolean = false,
    val editor: ProductEditorUiState = ProductEditorUiState(),
    val categoryManagerOpen: Boolean = false,
    val categoryEditor: CategoryEditorUiState = CategoryEditorUiState(),
    val deletionPrompt: ProductDeletionPrompt? = null,
)

data class CatalogUiState(
    val names: NamesSectionUiState = NamesSectionUiState(),
    val products: ProductsSectionUiState = ProductsSectionUiState(),
    val precomputedFilteredProducts: List<Product>? = null,
) {
    val filteredProducts: List<Product>
        get() = precomputedFilteredProducts ?: computeFilteredProducts(products)

    companion object {
        fun computeFilteredProducts(productsState: ProductsSectionUiState): List<Product> {
            val query = TextNormalization.normalizeForSearch(productsState.searchQuery)
            val onlyActive = productsState.showOnlyActive
            if (query.isEmpty() && !onlyActive) return productsState.products
            return productsState.products.filter { product ->
                val matchesActive = !onlyActive || product.active
                val matchesSearch = query.isEmpty() ||
                    TextNormalization.normalizeForSearch(product.name).contains(query) ||
                    product.participants.any {
                        TextNormalization.normalizeForSearch(it.personName).contains(query)
                    }
                matchesActive && matchesSearch
            }
        }
    }
}

class CatalogViewModel(
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val namesFormFlow = MutableStateFlow(NamesFormState())
    private val productsFormFlow = MutableStateFlow(ProductsFormState())

    private val productsSourceFlow = catalogRepository.observeAllProducts().onStart { emit(emptyList()) }

    private val filteredProductsFlow = combine(
        productsSourceFlow,
        productsFormFlow
            .map { it.searchQuery to it.showOnlyActive }
            .distinctUntilChanged(),
    ) { products, (searchQuery, showOnlyActive) ->
        products to CatalogUiState.computeFilteredProducts(
            ProductsSectionUiState(
                products = products,
                searchQuery = searchQuery,
                showOnlyActive = showOnlyActive,
            ),
        )
    }

    val uiState: StateFlow<CatalogUiState> = combine(
        catalogRepository.observePeopleWithUsage().onStart { emit(emptyList()) },
        filteredProductsFlow,
        catalogRepository.observeProductCategories().onStart { emit(emptyList()) },
        namesFormFlow,
        productsFormFlow,
    ) { people, (products, filteredProducts), categories, namesForm, productsForm ->
        val validSelectedCategoryFilterId = when (val filterId = productsForm.selectedCategoryFilterId) {
            null, "uncategorized" -> filterId
            else -> if (categories.any { it.id == filterId }) filterId else null
        }
        val productsSection = ProductsSectionUiState(
            products = products,
            categories = categories,
            searchQuery = productsForm.searchQuery,
            showOnlyActive = productsForm.showOnlyActive,
            selectedCategoryFilterId = validSelectedCategoryFilterId,
            feedbackMessage = productsForm.feedbackMessage,
            feedbackIsError = productsForm.feedbackIsError,
            editor = productsForm.editor,
            categoryManagerOpen = productsForm.categoryManagerOpen,
            categoryEditor = productsForm.categoryEditor,
            deletionPrompt = productsForm.deletionPrompt,
        )
        CatalogUiState(
            names = NamesSectionUiState(
                people = people,
                editingPersonId = namesForm.editingPersonId,
                nameInput = namesForm.nameInput,
                errorMessage = namesForm.errorMessage,
                feedbackMessage = namesForm.feedbackMessage,
                isSaving = namesForm.isSaving,
            ),
            products = productsSection,
            precomputedFilteredProducts = filteredProducts,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = CatalogUiState(),
        )

    // --- Ações de Nomes (Pessoas) ---

    fun onPersonNameInputChanged(value: String) {
        namesFormFlow.value = namesFormFlow.value.copy(
            nameInput = TextNormalization.toRegistrationUppercase(value),
            errorMessage = null,
            feedbackMessage = null,
        )
    }

    fun startEditingPerson(personWithUsage: PersonWithUsage) {
        namesFormFlow.value = namesFormFlow.value.copy(
            editingPersonId = personWithUsage.person.id,
            nameInput = TextNormalization.toRegistrationUppercase(personWithUsage.person.name),
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
                val normalizedUpperName = TextNormalization.toRegistrationUppercase(form.nameInput)
                if (form.editingPersonId == null) {
                    val created = catalogRepository.createPerson(normalizedUpperName)
                    namesFormFlow.value = NamesFormState(
                        feedbackMessage = "\"${created.name}\" adicionada com sucesso.",
                    )
                } else {
                    val renamed = catalogRepository.renamePerson(form.editingPersonId, normalizedUpperName)
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

    fun onCategoryFilterSelected(categoryId: String?) {
        productsFormFlow.value = productsFormFlow.value.copy(selectedCategoryFilterId = categoryId)
    }

    fun openNewProductEditor() {
        productsFormFlow.value = productsFormFlow.value.copy(
            feedbackMessage = null,
            feedbackIsError = false,
            categoryManagerOpen = false,
            categoryEditor = CategoryEditorUiState(),
            editor = ProductEditorUiState(isOpen = true),
        )
    }

    fun openEditProductEditor(product: Product) {
        val participantIds = product.participants.map { it.personId }
        val amountInputs = product.participants.associate { split ->
            split.personId to ProductPriceSplitCalculator.formatCentsAllowingZero(split.amount.rawCents)
        }
        productsFormFlow.value = productsFormFlow.value.copy(
            feedbackMessage = null,
            feedbackIsError = false,
            categoryManagerOpen = false,
            categoryEditor = CategoryEditorUiState(),
            editor = ProductEditorUiState(
                isOpen = true,
                editingProductId = product.id,
                nameInput = TextNormalization.toRegistrationUppercase(product.name),
                priceInput = MonetaryInputMask.formatCentsForMaskedInput(product.price.rawCents),
                active = product.active,
                categoryId = product.categoryId,
                subcategoryId = product.subcategoryId,
                selectedParticipantOrder = participantIds,
                participantAmountInputs = amountInputs,
                lastEditedParticipantId = null,
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

    fun openCategoryManager() {
        productsFormFlow.value = productsFormFlow.value.copy(
            feedbackMessage = null,
            feedbackIsError = false,
            categoryManagerOpen = true,
            categoryEditor = CategoryEditorUiState(),
        )
    }

    fun closeCategoryManager() {
        productsFormFlow.value = productsFormFlow.value.copy(
            categoryManagerOpen = false,
            categoryEditor = CategoryEditorUiState(),
        )
    }

    fun openNewCategoryEditor() {
        productsFormFlow.value = productsFormFlow.value.copy(
            feedbackMessage = null,
            feedbackIsError = false,
            categoryManagerOpen = true,
            categoryEditor = CategoryEditorUiState(
                isOpen = true,
                editingCategoryId = null,
                isSubcategory = false,
                parentId = null,
                nameInput = "",
            ),
        )
    }

    fun openNewSubcategoryEditor(parentCatId: String) {
        productsFormFlow.value = productsFormFlow.value.copy(
            feedbackMessage = null,
            feedbackIsError = false,
            categoryManagerOpen = true,
            categoryEditor = CategoryEditorUiState(
                isOpen = true,
                editingCategoryId = null,
                isSubcategory = true,
                parentId = parentCatId,
                nameInput = "",
            ),
        )
    }

    fun openEditCategoryEditor(category: ProductCategory) {
        productsFormFlow.value = productsFormFlow.value.copy(
            feedbackMessage = null,
            feedbackIsError = false,
            categoryManagerOpen = true,
            categoryEditor = CategoryEditorUiState(
                isOpen = true,
                editingCategoryId = category.id,
                isSubcategory = category.parentId != null,
                parentId = category.parentId,
                nameInput = TextNormalization.toRegistrationUppercase(category.name),
            ),
        )
    }

    fun closeCategoryEditor() {
        productsFormFlow.value = productsFormFlow.value.copy(
            categoryEditor = CategoryEditorUiState(),
        )
    }

    fun onCategoryEditorIsSubcategoryChanged(isSubcategory: Boolean) {
        updateCategoryEditor { editor ->
            if (editor.isSaving || editor.editingCategoryId != null) {
                editor
            } else {
                editor.copy(
                    isSubcategory = isSubcategory,
                    parentId = if (isSubcategory) editor.parentId else null,
                    errorMessage = null,
                )
            }
        }
    }

    fun onCategoryEditorParentChanged(parentId: String?) {
        updateCategoryEditor { editor ->
            if (editor.isSaving || editor.editingCategoryId != null) {
                editor
            } else {
                editor.copy(parentId = parentId, errorMessage = null)
            }
        }
    }

    fun onCategoryEditorNameChanged(value: String) {
        val upper = TextNormalization.toRegistrationUppercase(value)
        updateCategoryEditor { editor ->
            if (editor.isSaving) editor else editor.copy(nameInput = upper, errorMessage = null)
        }
    }

    fun saveCategory() {
        val editor = productsFormFlow.value.categoryEditor
        if (!editor.isOpen || editor.isSaving) return

        val cleanName = TextNormalization.toRegistrationUppercase(editor.nameInput).trim()
        if (cleanName.isBlank()) {
            updateCategoryEditor { it.copy(errorMessage = "Informe o nome da categoria.") }
            return
        }
        val isEditing = editor.editingCategoryId != null
        if (!isEditing && editor.isSubcategory && editor.parentId == null) {
            updateCategoryEditor { it.copy(errorMessage = "Selecione uma categoria principal.") }
            return
        }

        updateCategoryEditor { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                if (!isEditing) {
                    val parentId = if (editor.isSubcategory) editor.parentId else null
                    catalogRepository.createProductCategory(cleanName, parentId)
                    productsFormFlow.value = productsFormFlow.value.copy(
                        feedbackMessage = if (parentId == null) "Categoria criada." else "Subcategoria criada.",
                        feedbackIsError = false,
                        categoryEditor = CategoryEditorUiState(),
                    )
                } else {
                    catalogRepository.renameProductCategory(editor.editingCategoryId!!, cleanName)
                    productsFormFlow.value = productsFormFlow.value.copy(
                        feedbackMessage = "Categoria renomeada.",
                        feedbackIsError = false,
                        categoryEditor = CategoryEditorUiState(),
                    )
                }
            } catch (ex: Exception) {
                updateCategoryEditor {
                    it.copy(
                        isSaving = false,
                        errorMessage = ex.message ?: if (isEditing) {
                            "Não foi possível renomear a categoria."
                        } else {
                            "Não foi possível criar a categoria."
                        },
                    )
                }
            }
        }
    }

    fun handleProductsBackRequested(onExitProducts: () -> Unit) {
        val form = productsFormFlow.value
        when {
            form.categoryEditor.isOpen -> {
                if (!form.categoryEditor.isSaving) {
                    closeCategoryEditor()
                }
            }
            form.categoryManagerOpen -> closeCategoryManager()
            form.editor.isOpen -> closeProductEditor()
            else -> onExitProducts()
        }
    }

    fun resetProductsTransientFlows() {
        val current = productsFormFlow.value
        if (current.editor.isOpen ||
            current.categoryManagerOpen ||
            current.categoryEditor.isOpen ||
            current.deletionPrompt != null ||
            current.feedbackMessage != null
        ) {
            productsFormFlow.value = current.copy(
                feedbackMessage = null,
                feedbackIsError = false,
                editor = ProductEditorUiState(),
                categoryManagerOpen = false,
                categoryEditor = CategoryEditorUiState(),
                deletionPrompt = null,
            )
        }
    }

    fun restoreProductsFlowStateIfIdle(
        selectedCategoryFilterId: String?,
        categoryManagerOpen: Boolean,
        categoryEditorOpen: Boolean,
        categoryEditorEditingId: String?,
        categoryEditorIsSub: Boolean,
        categoryEditorParentId: String?,
        categoryEditorName: String,
    ) {
        val current = productsFormFlow.value
        val isIdle = current.selectedCategoryFilterId == null &&
            !current.categoryManagerOpen &&
            !current.categoryEditor.isOpen &&
            !current.editor.isOpen
        val hasSavedState = selectedCategoryFilterId != null ||
            categoryManagerOpen ||
            categoryEditorOpen
        if (isIdle && hasSavedState) {
            productsFormFlow.value = current.copy(
                selectedCategoryFilterId = selectedCategoryFilterId,
                categoryManagerOpen = categoryManagerOpen || categoryEditorOpen,
                categoryEditor = if (categoryEditorOpen) {
                    CategoryEditorUiState(
                        isOpen = true,
                        editingCategoryId = categoryEditorEditingId,
                        isSubcategory = categoryEditorIsSub,
                        parentId = categoryEditorParentId,
                        nameInput = categoryEditorName,
                    )
                } else {
                    CategoryEditorUiState()
                },
            )
        }
    }

    fun onProductNameChanged(value: String) {
        val upper = TextNormalization.toRegistrationUppercase(value)
        updateEditor { it.copy(nameInput = upper, errorMessage = null) }
    }

    fun onProductPriceChanged(value: String) {
        val masked = MonetaryInputMask.formatDigitsInput(value)
        updateEditor { editor ->
            val recalculatedInputs = ProductPriceSplitCalculator.onTotalPriceChanged(
                newPriceInput = masked,
                selectedParticipantOrder = editor.selectedParticipantOrder,
                currentInputs = editor.participantAmountInputs,
                lastEditedParticipantId = editor.lastEditedParticipantId,
            )
            editor.copy(
                priceInput = masked,
                participantAmountInputs = recalculatedInputs,
                errorMessage = null,
            )
        }
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
        val upperName = TextNormalization.toRegistrationUppercase(name)
        viewModelScope.launch {
            try {
                catalogRepository.createProductCategory(upperName, parentId)
                productsFormFlow.value = productsFormFlow.value.copy(
                    feedbackMessage = if (parentId == null) "Categoria criada." else "Subcategoria criada.",
                    feedbackIsError = false,
                )
            } catch (ex: Exception) {
                productsFormFlow.value = productsFormFlow.value.copy(
                    feedbackMessage = ex.message ?: "Não foi possível criar a categoria.",
                    feedbackIsError = true,
                )
            }
        }
    }

    fun renameProductCategory(categoryId: String, name: String) {
        val upperName = TextNormalization.toRegistrationUppercase(name)
        viewModelScope.launch {
            try {
                catalogRepository.renameProductCategory(categoryId, upperName)
                productsFormFlow.value = productsFormFlow.value.copy(
                    feedbackMessage = "Categoria renomeada.",
                    feedbackIsError = false,
                )
            } catch (ex: Exception) {
                productsFormFlow.value = productsFormFlow.value.copy(
                    feedbackMessage = ex.message ?: "Não foi possível renomear a categoria.",
                    feedbackIsError = true,
                )
            }
        }
    }

    fun deleteProductCategory(categoryId: String) {
        viewModelScope.launch {
            try {
                catalogRepository.deleteProductCategory(categoryId)
                val current = productsFormFlow.value
                val nextFilterId = if (current.selectedCategoryFilterId == categoryId) {
                    null
                } else {
                    current.selectedCategoryFilterId
                }
                productsFormFlow.value = current.copy(
                    selectedCategoryFilterId = nextFilterId,
                    feedbackMessage = "Categoria excluída.",
                    feedbackIsError = false,
                )
            } catch (ex: Exception) {
                productsFormFlow.value = productsFormFlow.value.copy(
                    feedbackMessage = ex.message ?: "Não foi possível excluir a categoria.",
                    feedbackIsError = true,
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

            val splitResult = ProductPriceSplitCalculator.onParticipantsSelectionChanged(
                priceInput = editor.priceInput,
                selectedParticipantOrder = currentOrder,
                currentInputs = currentAmounts,
                lastEditedParticipantId = editor.lastEditedParticipantId,
            )

            editor.copy(
                selectedParticipantOrder = currentOrder,
                participantAmountInputs = splitResult.updatedInputs,
                lastEditedParticipantId = splitResult.lastEditedParticipantId,
                feeOwnerPersonId = nextOwner,
                errorMessage = null,
            )
        }
    }

    fun onParticipantAmountChanged(personId: String, amountInput: String) {
        updateEditor { editor ->
            val splitResult = ProductPriceSplitCalculator.onParticipantAmountEdited(
                priceInput = editor.priceInput,
                selectedParticipantOrder = editor.selectedParticipantOrder,
                currentInputs = editor.participantAmountInputs,
                editedPersonId = personId,
                rawInput = amountInput,
            )
            editor.copy(
                participantAmountInputs = splitResult.updatedInputs,
                lastEditedParticipantId = splitResult.lastEditedParticipantId,
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
                val normalizedUpperName = TextNormalization.toRegistrationUppercase(editor.nameInput)
                if (editor.editingProductId == null) {
                    val created = catalogRepository.createProduct(
                        name = normalizedUpperName,
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
                        feedbackIsError = false,
                        editor = ProductEditorUiState(isOpen = false),
                    )
                } else {
                    val updated = catalogRepository.updateProduct(
                        productId = editor.editingProductId,
                        name = normalizedUpperName,
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
                        feedbackIsError = false,
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
                            feedbackIsError = false,
                        )
                    }
                    ProductDeletionResult.NotFound -> {
                        productsFormFlow.value = productsFormFlow.value.copy(
                            deletionPrompt = null,
                            feedbackMessage = "O produto não foi encontrado para exclusão.",
                            feedbackIsError = true,
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

    private fun updateCategoryEditor(transform: (CategoryEditorUiState) -> CategoryEditorUiState) {
        val current = productsFormFlow.value
        productsFormFlow.value = current.copy(categoryEditor = transform(current.categoryEditor))
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
        val selectedCategoryFilterId: String? = null,
        val feedbackMessage: String? = null,
        val feedbackIsError: Boolean = false,
        val editor: ProductEditorUiState = ProductEditorUiState(),
        val categoryManagerOpen: Boolean = false,
        val categoryEditor: CategoryEditorUiState = CategoryEditorUiState(),
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
