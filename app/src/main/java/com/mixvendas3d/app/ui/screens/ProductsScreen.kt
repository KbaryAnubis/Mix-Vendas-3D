package com.mixvendas3d.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersonWithUsage
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductCategory
import com.mixvendas3d.app.ui.components.Mv3dIcon
import com.mixvendas3d.app.ui.components.ProductsDropdownField
import com.mixvendas3d.app.ui.components.ProductsFieldLabel
import com.mixvendas3d.app.ui.components.ProductsGlassCard
import com.mixvendas3d.app.ui.components.ProductsMonetaryTextField
import com.mixvendas3d.app.ui.components.ProductsPrimaryCtaButton
import com.mixvendas3d.app.ui.components.ProductsRegisteredRow
import com.mixvendas3d.app.ui.components.ProductsSecondaryOutlinedButton
import com.mixvendas3d.app.ui.components.ProductsSectionHeading
import com.mixvendas3d.app.ui.components.namesDarkInputColors
import com.mixvendas3d.app.ui.theme.GlassInnerBoxBg
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeCardLabelInk
import com.mixvendas3d.app.ui.theme.HomeCardNeonBorder
import com.mixvendas3d.app.ui.theme.HomeCardSubtleDivider
import com.mixvendas3d.app.ui.theme.HomeCtaBorderGlow
import com.mixvendas3d.app.ui.theme.PlusJakartaSansFontFamily
import com.mixvendas3d.app.ui.theme.V19ErrorContainer
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import com.mixvendas3d.app.ui.theme.V19GoldAccent
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19MintSuccess
import com.mixvendas3d.app.ui.theme.V19PurplePrimary
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SuccessContainer
import com.mixvendas3d.app.ui.theme.V19SuccessInk
import com.mixvendas3d.app.util.CurrencyFormatter

@Composable
fun ProductsScreen(
    uiState: CatalogUiState,
    onSearchChanged: (String) -> Unit,
    onShowOnlyActiveToggled: (Boolean) -> Unit,
    onOpenNewProduct: () -> Unit,
    onOpenEditProduct: (Product) -> Unit,
    onRequestProductDeletion: (Product) -> Unit,
    onDismissProductDeletion: () -> Unit,
    onConfirmProductDeletion: () -> Unit,
    onGoToNames: () -> Unit,
    onCloseEditor: () -> Unit,
    onProductNameChanged: (String) -> Unit,
    onProductPriceChanged: (String) -> Unit,
    onProductActiveChanged: (Boolean) -> Unit,
    onProductCategoryChanged: (String?) -> Unit = {},
    onProductSubcategoryChanged: (String?) -> Unit = {},
    onCreateCategory: (String, String?) -> Unit = { _, _ -> },
    onRenameCategory: (String, String) -> Unit = { _, _ -> },
    onDeleteCategory: (String) -> Unit = {},
    onToggleParticipant: (String) -> Unit,
    onParticipantAmountChanged: (String, String) -> Unit,
    onFeeModeChanged: (FeeResponsibilityMode) -> Unit,
    onFeeOwnerSelected: (String) -> Unit,
    onSaveProduct: () -> Unit,
    modifier: Modifier = Modifier,
    onCategoryFilterSelected: (String?) -> Unit = {},
    onOpenCategoryManager: () -> Unit = {},
    onCloseCategoryManager: () -> Unit = {},
    onOpenNewCategoryEditor: () -> Unit = {},
    onOpenNewSubcategoryEditor: (String) -> Unit = {},
    onOpenEditCategoryEditor: (ProductCategory) -> Unit = {},
    onCloseCategoryEditor: () -> Unit = {},
    onCategoryEditorIsSubcategoryChanged: (Boolean) -> Unit = {},
    onCategoryEditorParentChanged: (String?) -> Unit = {},
    onCategoryEditorNameChanged: (String) -> Unit = {},
    onSaveCategory: () -> Unit = {},
    onRestoreProductsFlowState: (
        selectedCategoryFilterId: String?,
        categoryManagerOpen: Boolean,
        categoryEditorOpen: Boolean,
        categoryEditorEditingId: String?,
        categoryEditorIsSub: Boolean,
        categoryEditorParentId: String?,
        categoryEditorName: String,
    ) -> Unit = { _, _, _, _, _, _, _ -> },
) {
    val productsState = uiState.products
    val people = uiState.names.people

    // Sincronização leve com rememberSaveable para preservação de IDs/flags após recriação de Activity
    var savedFilterId by rememberSaveable { mutableStateOf(productsState.selectedCategoryFilterId) }
    var savedCategoryManagerOpen by rememberSaveable { mutableStateOf(productsState.categoryManagerOpen) }
    var savedCategoryEditorOpen by rememberSaveable { mutableStateOf(productsState.categoryEditor.isOpen) }
    var savedCategoryEditorEditingId by rememberSaveable { mutableStateOf(productsState.categoryEditor.editingCategoryId) }
    var savedCategoryEditorIsSub by rememberSaveable { mutableStateOf(productsState.categoryEditor.isSubcategory) }
    var savedCategoryEditorParentId by rememberSaveable { mutableStateOf(productsState.categoryEditor.parentId) }
    var savedCategoryEditorName by rememberSaveable { mutableStateOf(productsState.categoryEditor.nameInput) }

    LaunchedEffect(Unit) {
        onRestoreProductsFlowState(
            savedFilterId,
            savedCategoryManagerOpen,
            savedCategoryEditorOpen,
            savedCategoryEditorEditingId,
            savedCategoryEditorIsSub,
            savedCategoryEditorParentId,
            savedCategoryEditorName,
        )
    }

    LaunchedEffect(
        productsState.selectedCategoryFilterId,
        productsState.categoryManagerOpen,
        productsState.categoryEditor,
    ) {
        savedFilterId = productsState.selectedCategoryFilterId
        savedCategoryManagerOpen = productsState.categoryManagerOpen
        savedCategoryEditorOpen = productsState.categoryEditor.isOpen
        savedCategoryEditorEditingId = productsState.categoryEditor.editingCategoryId
        savedCategoryEditorIsSub = productsState.categoryEditor.isSubcategory
        savedCategoryEditorParentId = productsState.categoryEditor.parentId
        savedCategoryEditorName = productsState.categoryEditor.nameInput
    }

    when {
        // TELA 1: Criar / Editar Categoria
        productsState.categoryEditor.isOpen -> {
            CreateEditCategoryScreen(
                editor = productsState.categoryEditor,
                categories = productsState.categories,
                onIsSubcategoryChanged = onCategoryEditorIsSubcategoryChanged,
                onParentChanged = onCategoryEditorParentChanged,
                onNameChanged = onCategoryEditorNameChanged,
                onSave = onSaveCategory,
                onCancel = onCloseCategoryEditor,
                modifier = modifier,
            )
        }

        // TELA 2: Gerenciamento de Categorias
        productsState.categoryManagerOpen -> {
            CategoryManagementScreen(
                categories = productsState.categories,
                feedbackMessage = productsState.feedbackMessage,
                feedbackIsError = productsState.feedbackIsError,
                onOpenNewCategory = onOpenNewCategoryEditor,
                onOpenNewSubcategory = onOpenNewSubcategoryEditor,
                onEditCategory = onOpenEditCategoryEditor,
                onDeleteCategory = onDeleteCategory,
                modifier = modifier,
            )
        }

        // TELA 3: Cadastro / Edição de Produto
        productsState.editor.isOpen -> {
            ProductEditorScreen(
                editor = productsState.editor,
                categories = productsState.categories,
                people = people,
                onGoToNames = onGoToNames,
                onBack = onCloseEditor,
                onNameChanged = onProductNameChanged,
                onPriceChanged = onProductPriceChanged,
                onActiveChanged = onProductActiveChanged,
                onCategoryChanged = onProductCategoryChanged,
                onSubcategoryChanged = onProductSubcategoryChanged,
                onToggleParticipant = onToggleParticipant,
                onParticipantAmountChanged = onParticipantAmountChanged,
                onFeeModeChanged = onFeeModeChanged,
                onFeeOwnerSelected = onFeeOwnerSelected,
                onSave = onSaveProduct,
                modifier = modifier,
            )
        }

        // TELA 4: Lista Principal de Produtos
        else -> {
            ProductsMainContent(
                uiState = uiState,
                selectedCategoryFilterId = productsState.selectedCategoryFilterId,
                onCategoryFilterSelected = onCategoryFilterSelected,
                onSearchChanged = onSearchChanged,
                onShowOnlyActiveToggled = onShowOnlyActiveToggled,
                onOpenNewProduct = onOpenNewProduct,
                onOpenCategoryManager = onOpenCategoryManager,
                onOpenEditProduct = onOpenEditProduct,
                onRequestProductDeletion = onRequestProductDeletion,
                onGoToNames = onGoToNames,
                modifier = modifier,
            )
        }
    }

    // Diálogo de confirmação para exclusão de produto
    productsState.deletionPrompt?.let { prompt ->
        val linkedSalesCount = prompt.salesCount
        val canDelete = !prompt.isChecking && !prompt.isDeleting && linkedSalesCount == 0 && prompt.errorMessage == null
        AlertDialog(
            onDismissRequest = {
                if (!prompt.isDeleting) onDismissProductDeletion()
            },
            title = {
                Text(
                    text = when {
                        prompt.isChecking -> "Verificando vendas vinculadas"
                        prompt.errorMessage != null -> "Não foi possível verificar"
                        linkedSalesCount != null && linkedSalesCount > 0 -> "Produto vinculado a vendas"
                        else -> "Excluir produto?"
                    },
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = when {
                        prompt.isChecking -> "Aguarde enquanto verificamos o histórico de vendas de ${prompt.product.name}."
                        prompt.errorMessage != null -> prompt.errorMessage
                        linkedSalesCount != null && linkedSalesCount > 0 ->
                            "O produto ${prompt.product.name} aparece em $linkedSalesCount venda(s). O histórico será preservado. Desative-o para impedir novas vendas."
                        else -> "Confirma a exclusão do produto ${prompt.product.name}?"
                    },
                    fontFamily = PlusJakartaSansFontFamily,
                )
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissProductDeletion,
                    enabled = !prompt.isDeleting,
                    modifier = Modifier.testTag("cancel_delete_product"),
                ) {
                    Text(if (canDelete) "CANCELAR" else "FECHAR", fontFamily = PlusJakartaSansFontFamily)
                }
            },
            confirmButton = {
                if (canDelete) {
                    TextButton(
                        onClick = onConfirmProductDeletion,
                        modifier = Modifier.testTag("confirm_delete_product"),
                    ) {
                        Text("EXCLUIR", fontFamily = PlusJakartaSansFontFamily, color = V19ErrorSoft)
                    }
                }
            },
            modifier = Modifier.testTag("delete_product_dialog"),
        )
    }
}

/**
 * TELA PRINCIPAL DE PRODUTOS (referência: 01_produtos.png)
 */
@Composable
private fun ProductsMainContent(
    uiState: CatalogUiState,
    selectedCategoryFilterId: String?,
    onCategoryFilterSelected: (String?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onShowOnlyActiveToggled: (Boolean) -> Unit,
    onOpenNewProduct: () -> Unit,
    onOpenCategoryManager: () -> Unit,
    onOpenEditProduct: (Product) -> Unit,
    onRequestProductDeletion: (Product) -> Unit,
    onGoToNames: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val productsState = uiState.products
    val filteredProducts = uiState.filteredProducts
    val people = uiState.names.people
    val listState = rememberLazyListState()

    val categoryFilteredProducts = remember(filteredProducts, selectedCategoryFilterId, productsState.categories) {
        if (selectedCategoryFilterId == null) {
            filteredProducts
        } else if (selectedCategoryFilterId == "uncategorized") {
            filteredProducts.filter { it.categoryId == null }
        } else {
            val selectedCat = productsState.categories.firstOrNull { it.id == selectedCategoryFilterId }
            if (selectedCat != null && selectedCat.parentId == null) {
                val subIds = productsState.categories.filter { it.parentId == selectedCat.id }.map { it.id }.toSet()
                filteredProducts.filter { it.categoryId == selectedCat.id || subIds.contains(it.subcategoryId) }
            } else {
                filteredProducts.filter { it.subcategoryId == selectedCategoryFilterId }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_products"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "products_actions_card") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
            ) {
                ProductsGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProductsSectionHeading(
                            text = stringResource(R.string.mais_card_products_title),
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0x66492075),
                            border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.48f)),
                        ) {
                            Text(
                                text = "${productsState.products.size} cadastrado(s)",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = V19PurpleSoftText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ProductsPrimaryCtaButton(
                                text = "Novo produto",
                                enabled = true,
                                onClick = onOpenNewProduct,
                                testTag = "new_product_button",
                                iconRes = R.drawable.mv3d_produtos,
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            ProductsSecondaryOutlinedButton(
                                text = "Categorias",
                                onClick = onOpenCategoryManager,
                                testTag = "manage_categories_button",
                            )
                        }
                    }

                    if (people.isEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        MissingPeopleWarningCard(onGoToNames = onGoToNames)
                    }
                }
            }
        }

        item(key = "products_registered_card") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
            ) {
                ProductsGlassCard {
                    if (!productsState.feedbackMessage.isNullOrBlank()) {
                        ProductsFeedbackBanner(
                            message = productsState.feedbackMessage,
                            isError = productsState.feedbackIsError,
                            testTag = "products_feedback_banner",
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    ProductsSectionHeading(
                        text = stringResource(R.string.products_registered_title),
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = productsState.searchQuery,
                        onValueChange = onSearchChanged,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = Color.White,
                        ),
                        leadingIcon = {
                            Mv3dIcon(
                                iconRes = R.drawable.mv3d_buscar,
                                contentDescription = null,
                                size = 32.dp,
                            )
                        },
                        placeholder = {
                            Text(
                                text = "Buscar produto ou participante...",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 14.sp,
                                color = V19InkMuted,
                            )
                        },
                        colors = namesDarkInputColors(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("products_search_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onShowOnlyActiveToggled(!productsState.showOnlyActive) }
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Mostrar apenas produtos ativos",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = HomeCardLabelInk,
                        )
                        Switch(
                            checked = productsState.showOnlyActive,
                            onCheckedChange = onShowOnlyActiveToggled,
                            colors = SwitchDefaults.colors(checkedTrackColor = V19PurplePrimary),
                            modifier = Modifier.testTag("products_active_filter_switch"),
                        )
                    }

                    if (productsState.categories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val filterOptions = listOf(
                            null to "Todas as categorias",
                            "uncategorized" to "Sem categoria",
                        ) + productsState.categories.filter { it.parentId == null }.sortedBy { it.name }.map { it.id to it.name }

                        val currentFilterName = when (selectedCategoryFilterId) {
                            null -> "Todas as categorias"
                            "uncategorized" -> "Sem categoria"
                            else -> productsState.categories.firstOrNull { it.id == selectedCategoryFilterId }?.name ?: "Todas as categorias"
                        }

                        ProductsDropdownField(
                            label = "Filtrar por Categoria",
                            selectedText = currentFilterName,
                            options = filterOptions,
                            onOptionSelected = onCategoryFilterSelected,
                            testTag = "products_category_filter",
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (categoryFilteredProducts.isEmpty()) {
                        Text(
                            text = if (productsState.products.isEmpty()) {
                                "Nenhum produto cadastrado ainda. Os produtos cadastrados aqui aparecerão automaticamente na tela Vender."
                            } else {
                                "Nenhum produto encontrado para o filtro aplicado."
                            },
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = V19InkMuted,
                            modifier = Modifier
                                .padding(top = 8.dp, bottom = 4.dp)
                                .testTag("products_empty_state"),
                        )
                    } else {
                        categoryFilteredProducts.forEachIndexed { index, product ->
                            val mainCat = productsState.categories.firstOrNull { it.id == product.categoryId }
                            val subCat = productsState.categories.firstOrNull { it.id == product.subcategoryId }
                            ProductsRegisteredRow(
                                product = product,
                                categoryName = mainCat?.name,
                                subcategoryName = subCat?.name,
                                onEdit = { onOpenEditProduct(product) },
                                editIconScalePercent = if (index < 3) 160 else null,
                                onDelete = { onRequestProductDeletion(product) },
                            )
                            if (index < categoryFilteredProducts.lastIndex) {
                                HorizontalDivider(
                                    color = HomeCardSubtleDivider.copy(alpha = 0.28f),
                                    thickness = 1.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * TELA DEDICADA DE CADASTRO / EDIÇÃO DE PRODUTO (referência: 02_cadastro_produto.png)
 */
@Composable
private fun ProductEditorScreen(
    editor: ProductEditorUiState,
    categories: List<ProductCategory>,
    people: List<PersonWithUsage>,
    onGoToNames: () -> Unit,
    onBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit,
    onActiveChanged: (Boolean) -> Unit,
    onCategoryChanged: (String?) -> Unit,
    onSubcategoryChanged: (String?) -> Unit,
    onToggleParticipant: (String) -> Unit,
    onParticipantAmountChanged: (String, String) -> Unit,
    onFeeModeChanged: (FeeResponsibilityMode) -> Unit,
    onFeeOwnerSelected: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEditing = editor.editingProductId != null
    val parsedPriceCents = CurrencyFormatter.parseBrlToCents(editor.priceInput) ?: 0L
    val allocatedCents = editor.selectedParticipantOrder.sumOf { personId ->
        CurrencyFormatter.parseBrlToCents(editor.participantAmountInputs[personId].orEmpty()) ?: 0L
    }
    val diffCents = parsedPriceCents - allocatedCents
    val sumsMatch = parsedPriceCents > 0L &&
        editor.selectedParticipantOrder.isNotEmpty() &&
        diffCents == 0L

    val feeModeDisplayOrder = listOf(
        FeeResponsibilityMode.PROPORTIONAL,
        FeeResponsibilityMode.EQUAL,
        FeeResponsibilityMode.INDIVIDUAL,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("product_editor_dialog"),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "editor_form_fields") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                ) {
                    ProductsGlassCard {
                        ProductsFieldLabel(text = stringResource(R.string.products_name_label))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editor.nameInput,
                            onValueChange = onNameChanged,
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = Color.White,
                            ),
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.products_name_placeholder),
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 15.sp,
                                    color = V19InkMuted,
                                )
                            },
                            colors = namesDarkInputColors(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_name_input"),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                ProductsFieldLabel(text = stringResource(R.string.products_price_label))
                                Spacer(modifier = Modifier.height(8.dp))
                                ProductsMonetaryTextField(
                                    maskedValue = editor.priceInput,
                                    onMaskedInputChanged = onPriceChanged,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("product_price_input"),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                val mainCategories = categories.filter { it.parentId == null }.sortedBy { it.name }
                                val selectedMainCat = mainCategories.firstOrNull { it.id == editor.categoryId }
                                val categoryOptions = listOf(null to "Nenhuma") + mainCategories.map { it.id to it.name }

                                ProductsDropdownField(
                                    label = "Categoria principal",
                                    selectedText = selectedMainCat?.name ?: "Nenhuma",
                                    options = categoryOptions,
                                    onOptionSelected = onCategoryChanged,
                                    testTag = "product_category_dropdown",
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val subcategories = categories.filter { editor.categoryId != null && it.parentId == editor.categoryId }.sortedBy { it.name }
                        val selectedSubCat = subcategories.firstOrNull { it.id == editor.subcategoryId }
                        val subcategoryOptions = listOf(null to "Nenhuma") + subcategories.map { it.id to it.name }

                        ProductsDropdownField(
                            label = "Subcategoria (opcional)",
                            selectedText = selectedSubCat?.name ?: "Nenhuma",
                            options = subcategoryOptions,
                            onOptionSelected = onSubcategoryChanged,
                            testTag = "product_subcategory_dropdown",
                            enabled = editor.categoryId != null,
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        ProductsFieldLabel(text = stringResource(R.string.products_participants_title))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.products_participants_helper),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = V19InkMuted,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (people.isEmpty()) {
                            MissingPeopleWarningCard(onGoToNames = onGoToNames)
                        } else {
                            people.forEachIndexed { index, personWithUsage ->
                                val person = personWithUsage.person
                                val isChecked = editor.selectedParticipantOrder.contains(person.id)
                                val amountValue = editor.participantAmountInputs[person.id].orEmpty()

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 58.dp)
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onToggleParticipant(person.id) },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { onToggleParticipant(person.id) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = V19PurplePrimary,
                                                uncheckedColor = Color.White,
                                                checkmarkColor = Color.White,
                                            ),
                                            modifier = Modifier.testTag("product_participant_check_${person.id}"),
                                        )
                                        Text(
                                            text = person.name,
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = if (isChecked) Color.White else HomeCardLabelInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }

                                    Text(
                                        text = stringResource(R.string.products_participant_value_label),
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = if (isChecked) HomeCardLabelInk else V19InkMuted,
                                    )

                                    ProductsMonetaryTextField(
                                        maskedValue = amountValue,
                                        onMaskedInputChanged = { onParticipantAmountChanged(person.id, it) },
                                        enabled = isChecked,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier
                                            .width(126.dp)
                                            .testTag("product_participant_amount_${person.id}"),
                                    )
                                }

                                if (index < people.lastIndex) {
                                    HorizontalDivider(
                                        color = HomeCardSubtleDivider.copy(alpha = 0.25f),
                                        thickness = 1.dp,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Banner do saldo distribuído em R$
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_split_balance_banner"),
                            shape = RoundedCornerShape(16.dp),
                            color = if (sumsMatch) V19SuccessContainer.copy(alpha = 0.72f) else GlassInnerBoxBg,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (sumsMatch) V19MintSuccess.copy(alpha = 0.62f) else GlassNeonBorder15,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = if (parsedPriceCents > 0L || allocatedCents > 0L) {
                                        "Total distribuído: ${CurrencyFormatter.formatBrl(MoneyCents(allocatedCents))}"
                                    } else {
                                        stringResource(R.string.products_split_balance_hint)
                                    },
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = if (sumsMatch) V19SuccessInk else HomeCardLabelInk,
                                )
                                if (parsedPriceCents > 0L && allocatedCents > 0L && !sumsMatch) {
                                    Text(
                                        text = if (diffCents > 0L) {
                                            "Faltam ${CurrencyFormatter.formatBrl(MoneyCents(diffCents))} para atingir o preço de ${editor.priceInput}."
                                        } else {
                                            "A soma dos participantes excede o preço do produto em ${CurrencyFormatter.formatBrl(MoneyCents(-diffCents))}."
                                        },
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = V19GoldAccent,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        ProductsFieldLabel(text = stringResource(R.string.products_fee_mode_label))
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            feeModeDisplayOrder.forEach { mode ->
                                val selected = editor.feeResponsibilityMode == mode
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onFeeModeChanged(mode) }
                                        .testTag("fee_mode_${mode.storageKey.lowercase()}"),
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) Color(0x66492075) else GlassInnerBoxBg,
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) HomeCtaBorderGlow else GlassNeonBorder15,
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = { onFeeModeChanged(mode) },
                                            colors = RadioButtonDefaults.colors(selectedColor = V19PurplePrimary),
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = when (mode) {
                                                    FeeResponsibilityMode.PROPORTIONAL -> "Divisão proporcional"
                                                    FeeResponsibilityMode.EQUAL -> "Divisão igualitária"
                                                    FeeResponsibilityMode.INDIVIDUAL -> "Pessoa específica"
                                                },
                                                fontFamily = PlusJakartaSansFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color.White,
                                            )
                                            Text(
                                                text = when (mode) {
                                                    FeeResponsibilityMode.PROPORTIONAL -> "Prepara a divisão das tarifas proporcionalmente ao valor de cada participante."
                                                    FeeResponsibilityMode.EQUAL -> "Prepara a divisão das tarifas em partes iguais entre os participantes."
                                                    FeeResponsibilityMode.INDIVIDUAL -> "Um participante selecionado assume integralmente as tarifas deste produto."
                                                },
                                                fontFamily = PlusJakartaSansFontFamily,
                                                fontSize = 12.sp,
                                                color = V19InkMuted,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (editor.feeResponsibilityMode == FeeResponsibilityMode.INDIVIDUAL) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val selectedPeople = people.filter { editor.selectedParticipantOrder.contains(it.person.id) }
                            val options = selectedPeople.map { it.person.id to it.person.name }
                            val selectedName = selectedPeople.firstOrNull { it.person.id == editor.feeOwnerPersonId }?.person?.name ?: "Selecione"

                            ProductsDropdownField(
                                label = stringResource(R.string.products_fee_owner_label),
                                selectedText = selectedName,
                                options = options,
                                onOptionSelected = { if (it != null) onFeeOwnerSelected(it) },
                                testTag = "product_fee_owner_dropdown",
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onActiveChanged(!editor.active) }
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.products_active_checkbox_label),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = HomeCardLabelInk,
                            )
                            Switch(
                                checked = editor.active,
                                onCheckedChange = onActiveChanged,
                                colors = SwitchDefaults.colors(checkedTrackColor = V19PurplePrimary),
                                modifier = Modifier.testTag("product_active_switch"),
                            )
                        }

                        if (!editor.errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("product_form_error"),
                                shape = RoundedCornerShape(14.dp),
                                color = V19ErrorContainer,
                                border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.58f)),
                            ) {
                                Text(
                                    text = editor.errorMessage,
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = V19ErrorSoft,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        ProductsPrimaryCtaButton(
                            text = if (isEditing) {
                                stringResource(R.string.products_save_edit)
                            } else {
                                stringResource(R.string.products_save_create)
                            },
                            enabled = !editor.isSaving,
                            onClick = onSave,
                            testTag = "save_product_button",
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        ProductsSecondaryOutlinedButton(
                            text = if (isEditing) {
                                stringResource(R.string.products_cancel_edit)
                            } else {
                                stringResource(R.string.products_cancel_create)
                            },
                            onClick = onBack,
                            testTag = "cancel_product_button",
                        )
                    }
                }
            }
        }
    }
}

/**
 * TELA DEDICADA DE GERENCIAMENTO DE CATEGORIAS (referência: 03_gerenciamento_categorias.png)
 */
@Composable
private fun CategoryManagementScreen(
    categories: List<ProductCategory>,
    feedbackMessage: String?,
    feedbackIsError: Boolean,
    onOpenNewCategory: () -> Unit,
    onOpenNewSubcategory: (parentCatId: String) -> Unit,
    onEditCategory: (ProductCategory) -> Unit,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var deletingCat by remember { mutableStateOf<ProductCategory?>(null) }
    val mainCategories = remember(categories) {
        categories.filter { it.parentId == null }.sortedBy { it.name }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("category_management_dialog"),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "categories_header_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                ) {
                    ProductsGlassCard {
                        if (!feedbackMessage.isNullOrBlank()) {
                            ProductsFeedbackBanner(
                                message = feedbackMessage,
                                isError = feedbackIsError,
                                testTag = "categories_feedback_banner",
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0x66492075),
                                border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.5f)),
                                modifier = Modifier.size(48.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Mv3dIcon(
                                        iconRes = R.drawable.mv3d_produtos,
                                        contentDescription = null,
                                        size = 28.dp,
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Categorias",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = Color.White,
                                )
                                Text(
                                    text = "Organize categorias e subcategorias dos produtos.",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 13.sp,
                                    color = V19InkMuted,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        ProductsPrimaryCtaButton(
                            text = "+ Nova categoria",
                            enabled = true,
                            onClick = onOpenNewCategory,
                            testTag = "new_category_button",
                        )
                    }
                }
            }

            item(key = "categories_list_section") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = "Categorias (${mainCategories.size})",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White,
                        )

                        if (mainCategories.isEmpty()) {
                            ProductsGlassCard {
                                Text(
                                    text = "Nenhuma categoria cadastrada ainda. Toque em \"+ Nova categoria\" acima para começar.",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 14.sp,
                                    color = V19InkMuted,
                                )
                            }
                        } else {
                            mainCategories.forEach { mainCat ->
                                val subcategories = categories.filter { it.parentId == mainCat.id }.sortedBy { it.name }
                                CategoryTreeCard(
                                    mainCategory = mainCat,
                                    subcategories = subcategories,
                                    onAddSubcategory = { onOpenNewSubcategory(mainCat.id) },
                                    onEditCategory = onEditCategory,
                                    onDeleteCategory = { deletingCat = it },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deletingCat?.let { cat ->
        val isSub = cat.parentId != null
        AlertDialog(
            onDismissRequest = { deletingCat = null },
            title = { Text(if (isSub) "Excluir Subcategoria?" else "Excluir Categoria?", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (isSub) {
                        "Ao excluir a subcategoria \"${cat.name}\", os produtos vinculados continuarão na categoria principal."
                    } else {
                        "Ao excluir a categoria \"${cat.name}\", suas subcategorias e produtos vinculados passarão para \"Sem categoria\"."
                    },
                    fontFamily = PlusJakartaSansFontFamily,
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingCat = null },
                    modifier = Modifier.testTag("cancel_delete_category_button"),
                ) {
                    Text("CANCELAR", fontFamily = PlusJakartaSansFontFamily)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCategory(cat.id)
                        deletingCat = null
                    },
                    modifier = Modifier.testTag("confirm_delete_category_button"),
                ) {
                    Text("EXCLUIR", fontFamily = PlusJakartaSansFontFamily, color = V19ErrorSoft)
                }
            },
            modifier = Modifier.testTag("confirm_delete_category_dialog"),
        )
    }
}

/**
 * Cartão Árvore de Categoria Principal + Subcategorias (referência: 03_gerenciamento_categorias.png)
 */
@Composable
private fun CategoryTreeCard(
    mainCategory: ProductCategory,
    subcategories: List<ProductCategory>,
    onAddSubcategory: () -> Unit,
    onEditCategory: (ProductCategory) -> Unit,
    onDeleteCategory: (ProductCategory) -> Unit,
) {
    ProductsGlassCard(
        modifier = Modifier.testTag("category_card_${mainCategory.id}"),
    ) {
        // Cabeçalho da Categoria Principal
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x66492075),
                border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.5f)),
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Mv3dIcon(iconRes = R.drawable.mv3d_produtos, contentDescription = null, size = 22.dp)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mainCategory.name,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = Color.White,
                )
                Text(
                    text = if (subcategories.isEmpty()) "Nenhuma subcategoria" else "${subcategories.size} subcategoria(s)",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    color = V19PurpleSoftText,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onEditCategory(mainCategory) },
                    modifier = Modifier.testTag("edit_category_${mainCategory.id}"),
                ) {
                    Text("✏️", fontSize = 16.sp)
                }
                TextButton(
                    onClick = { onDeleteCategory(mainCategory) },
                    modifier = Modifier.testTag("delete_category_${mainCategory.id}"),
                ) {
                    Text("🗑️", fontSize = 16.sp)
                }
            }
        }

        // Subcategorias Indentadas
        if (subcategories.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(10.dp))

            subcategories.forEach { subCat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "↳ ${subCat.name}",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = HomeCardLabelInk,
                        modifier = Modifier.weight(1f),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(
                            onClick = { onEditCategory(subCat) },
                            modifier = Modifier.testTag("edit_category_${subCat.id}"),
                        ) {
                            Text("✏️", fontSize = 14.sp)
                        }
                        TextButton(
                            onClick = { onDeleteCategory(subCat) },
                            modifier = Modifier.testTag("delete_category_${subCat.id}"),
                        ) {
                            Text("🗑️", fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ProductsSecondaryOutlinedButton(
            text = "+ Subcategoria",
            onClick = onAddSubcategory,
            testTag = "add_subcategory_button_${mainCategory.id}",
        )
    }
}

/**
 * TELA DEDICADA DE CRIAR / EDITAR CATEGORIA (referência: 04_criar_editar_categoria.png)
 */
@Composable
private fun CreateEditCategoryScreen(
    editor: CategoryEditorUiState,
    categories: List<ProductCategory>,
    onIsSubcategoryChanged: (Boolean) -> Unit,
    onParentChanged: (String?) -> Unit,
    onNameChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEditing = editor.editingCategoryId != null
    val isSubcategory = editor.isSubcategory
    val selectedParentId = editor.parentId
    val nameInput = editor.nameInput
    val errorMessage = editor.errorMessage
    val canChangeTypeAndParent = !isEditing && !editor.isSaving

    val mainCategories = remember(categories, editor.editingCategoryId) {
        categories
            .filter { it.parentId == null && (editor.editingCategoryId == null || it.id != editor.editingCategoryId) }
            .sortedBy { it.name }
    }
    val currentParentName = mainCategories.firstOrNull { it.id == selectedParentId }?.name

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("create_edit_category_dialog"),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                ) {
                    ProductsGlassCard {
                        // Tipo de categoria: Categoria Principal vs Subcategoria
                        ProductsFieldLabel(text = "Tipo de categoria")
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (canChangeTypeAndParent) 1f else 0.58f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Opção Categoria Principal
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = canChangeTypeAndParent) { onIsSubcategoryChanged(false) }
                                    .testTag("radio_category_main"),
                                shape = RoundedCornerShape(14.dp),
                                color = if (!isSubcategory) Color(0x66492075) else GlassInnerBoxBg,
                                border = BorderStroke(
                                    width = if (!isSubcategory) 1.5.dp else 1.dp,
                                    color = if (!isSubcategory) HomeCtaBorderGlow else GlassNeonBorder15,
                                ),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        RadioButton(
                                            selected = !isSubcategory,
                                            enabled = canChangeTypeAndParent,
                                            onClick = { onIsSubcategoryChanged(false) },
                                            colors = RadioButtonDefaults.colors(selectedColor = V19PurplePrimary),
                                        )
                                        Text(
                                            text = "Categoria principal",
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Grupo do nível principal, como 3D.",
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontSize = 11.sp,
                                        color = V19InkMuted,
                                    )
                                }
                            }

                            // Opção Subcategoria
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = canChangeTypeAndParent) { onIsSubcategoryChanged(true) }
                                    .testTag("radio_category_sub"),
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSubcategory) Color(0x66492075) else GlassInnerBoxBg,
                                border = BorderStroke(
                                    width = if (isSubcategory) 1.5.dp else 1.dp,
                                    color = if (isSubcategory) HomeCtaBorderGlow else GlassNeonBorder15,
                                ),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        RadioButton(
                                            selected = isSubcategory,
                                            enabled = canChangeTypeAndParent,
                                            onClick = { onIsSubcategoryChanged(true) },
                                            colors = RadioButtonDefaults.colors(selectedColor = V19PurplePrimary),
                                        )
                                        Text(
                                            text = "Subcategoria",
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Fica dentro de uma categoria principal.",
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontSize = 11.sp,
                                        color = V19InkMuted,
                                    )
                                }
                            }
                        }

                        if (isEditing) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Na edição, apenas o nome pode ser alterado. O tipo e a categoria principal são preservados.",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 12.sp,
                                color = V19GoldAccent,
                                modifier = Modifier.testTag("category_edit_hierarchy_notice"),
                            )
                        }

                        // Seletor de Categoria Principal quando for Subcategoria
                        if (isSubcategory) {
                            Spacer(modifier = Modifier.height(14.dp))
                            val options = mainCategories.map { it.id to it.name }
                            ProductsDropdownField(
                                label = "Categoria principal",
                                selectedText = currentParentName ?: "Selecione a categoria principal",
                                options = options,
                                onOptionSelected = onParentChanged,
                                testTag = "category_parent_dropdown",
                                enabled = canChangeTypeAndParent,
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Campo Nome
                        ProductsFieldLabel(
                            text = if (isSubcategory) "Nome da subcategoria" else "Nome da categoria principal",
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = onNameChanged,
                            enabled = !editor.isSaving,
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = Color.White,
                            ),
                            placeholder = {
                                Text(
                                    text = if (isSubcategory) "Ex.: Pokémon" else "Ex.: 3D",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 15.sp,
                                    color = V19InkMuted,
                                )
                            },
                            colors = namesDarkInputColors(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("category_name_input"),
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Texto explicativo do limite de 2 níveis
                        Text(
                            text = if (isSubcategory && currentParentName != null) {
                                "A subcategoria fica dentro de $currentParentName. Não existe terceiro nível de categorias."
                            } else {
                                "Não existe terceiro nível de categorias."
                            },
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.sp,
                            color = V19InkMuted,
                        )

                        if (!errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("category_form_error"),
                                shape = RoundedCornerShape(14.dp),
                                color = V19ErrorContainer,
                                border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.58f)),
                            ) {
                                Text(
                                    text = errorMessage,
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = V19ErrorSoft,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Botão Salvar Categoria
                        ProductsPrimaryCtaButton(
                            text = if (editor.isSaving) "Salvando..." else "Salvar categoria",
                            enabled = !editor.isSaving && nameInput.isNotBlank() && (!isSubcategory || selectedParentId != null),
                            onClick = onSave,
                            testTag = "save_category_button",
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Botão Cancelar
                        ProductsSecondaryOutlinedButton(
                            text = "Cancelar",
                            onClick = {
                                if (!editor.isSaving) onCancel()
                            },
                            testTag = "cancel_category_button",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductsFeedbackBanner(
    message: String,
    isError: Boolean,
    testTag: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = if (isError) V19ErrorContainer else V19SuccessContainer,
        border = BorderStroke(
            width = 1.dp,
            color = if (isError) V19ErrorSoft.copy(alpha = 0.58f) else V19MintSuccess.copy(alpha = 0.50f),
        ),
    ) {
        Text(
            text = message,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = if (isError) V19ErrorSoft else V19SuccessInk,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun MissingPeopleWarningCard(
    onGoToNames: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = V19GoldAccent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, V19GoldAccent.copy(alpha = 0.48f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Cadastre pessoas em Nomes antes de criar produtos.",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = V19GoldAccent,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onGoToNames,
                modifier = Modifier.testTag("products_go_to_names_button"),
            ) {
                Text(
                    text = "Abrir Nomes",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}