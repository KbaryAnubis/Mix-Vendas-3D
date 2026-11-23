package com.mixvendas3d.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.calculation.MonetaryRounding
import com.mixvendas3d.app.domain.calculation.SaleDiscountPolicy
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.Machine
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PreviewPaymentMethod
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductCategory
import com.mixvendas3d.app.domain.reporting.DailySalesAggregator
import com.mixvendas3d.app.ui.components.DiscountProductsSelectionBox
import com.mixvendas3d.app.ui.components.DiscountProtectedPeopleBox
import com.mixvendas3d.app.ui.components.PickerCategoryHeader
import com.mixvendas3d.app.ui.components.PickerSubcategoryHeader
import com.mixvendas3d.app.ui.components.Mv3dIcon
import com.mixvendas3d.app.ui.components.VenderAutoSplitSectionCard
import com.mixvendas3d.app.ui.components.VenderChooseProductsButton
import com.mixvendas3d.app.ui.components.VenderDashedEmptyCard
import com.mixvendas3d.app.ui.components.VenderDiscountPreviewCard
import com.mixvendas3d.app.ui.components.VenderGlassCard
import com.mixvendas3d.app.ui.components.VenderMachinePickerRow
import com.mixvendas3d.app.ui.components.VenderPaymentMethodTile
import com.mixvendas3d.app.ui.components.VenderPrimarySaveButton
import com.mixvendas3d.app.ui.components.VenderProductPickerFooter
import com.mixvendas3d.app.ui.components.VenderProductPickerRow
import com.mixvendas3d.app.ui.components.VenderSectionHeading
import com.mixvendas3d.app.ui.components.VenderSelectedSummaryBar
import com.mixvendas3d.app.ui.components.namesDarkInputColors
import com.mixvendas3d.app.ui.theme.GlassInnerBoxBg
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeCardLabelInk
import com.mixvendas3d.app.ui.theme.HomeCardNeonBorder
import com.mixvendas3d.app.ui.theme.HomeCardSubtleDivider
import com.mixvendas3d.app.ui.theme.HomeCtaShadowGlow
import com.mixvendas3d.app.ui.theme.PlusJakartaSansFontFamily
import com.mixvendas3d.app.ui.theme.V19ErrorContainer
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import com.mixvendas3d.app.ui.theme.V19GoldAccent
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19MintSuccess
import com.mixvendas3d.app.ui.theme.V19PurpleButtonStart
import com.mixvendas3d.app.ui.theme.V19PurplePrimary
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SuccessContainer
import com.mixvendas3d.app.ui.theme.V19SuccessInk
import com.mixvendas3d.app.ui.theme.V19SurfaceCard
import com.mixvendas3d.app.util.CurrencyFormatter
import com.mixvendas3d.app.util.RateFormatter
import com.mixvendas3d.app.util.TextNormalization
import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val MACHINE_DIACRITICS_REGEX = "\\p{M}+".toRegex()

private fun normalizeMachineSearchText(text: String, locale: Locale): String {
    val lowered = text.trim().lowercase(locale)
    return Normalizer.normalize(lowered, Normalizer.Form.NFD)
        .replace(MACHINE_DIACRITICS_REGEX, "")
}

@Composable
fun VenderScreen(
    uiState: MixVendasShellUiState,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onPickerSearchChanged: (String) -> Unit,
    onToggleProductSelection: (String) -> Unit,
    onIncrementProductQty: (String) -> Unit,
    onDecrementProductQty: (String) -> Unit,
    onRemoveProduct: (String) -> Unit,
    onFinalAmountChanged: (String) -> Unit,
    onDiscountTargetSelected: (String) -> Unit,
    onPaymentMethodSelected: (PreviewPaymentMethod) -> Unit,
    onOpenMachinePicker: () -> Unit,
    onCloseMachinePicker: () -> Unit,
    onMachinePickerSearchChanged: (String) -> Unit,
    onMachineSelected: (String) -> Unit,
    onBrandSelected: (CardBrand) -> Unit,
    onInstallmentsSelected: (Int) -> Unit,
    onFeeOverrideToggled: (Boolean) -> Unit,
    onManualRateChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSaveSale: () -> Unit,
    onCancelEditing: () -> Unit = {},
    onDismissDiscardEdit: () -> Unit = {},
    onConfirmDiscardEdit: () -> Unit = {},
    modifier: Modifier = Modifier,
    onBrandKeySelected: (String) -> Unit = { key ->
        CardBrand.fromStorageKey(key)?.let(onBrandSelected)
    },
    onCustomSaleDateToggled: (Boolean) -> Unit = {},
    onSaleDateSelected: (LocalDate) -> Unit = {},
    onToggleDiscountProduct: (String) -> Unit = {},
    onSelectAllDiscountProducts: () -> Unit = {},
    onClearDiscountProducts: () -> Unit = {},
    onToggleProtectedDiscountPerson: (String) -> Unit = {},
    onAuthorizeUnprotectFixedPerson: (String) -> Unit = {},
) {
    val form = uiState.venderForm
    var saleDatePickerOpen by rememberSaveable { mutableStateOf(false) }
    val canOpenSaleDateCalendar = form.isEditing || form.saleDateCustomized
    LaunchedEffect(canOpenSaleDateCalendar) {
        if (!canOpenSaleDateCalendar) {
            saleDatePickerOpen = false
        }
    }
    val activeCatalogMap = remember(uiState.activeCatalogProducts) {
        uiState.activeCatalogProducts.associateBy { it.id }
    }
    val productMap = remember(uiState.activeCatalogProducts, form.historicalProductsById) {
        MixVendasShellViewModel.resolvedProductsMap(form, uiState.activeCatalogProducts)
    }
    val pickerProducts = remember(uiState.activeCatalogProducts, form.historicalProductsById) {
        MixVendasShellViewModel.pickerCatalogProducts(form, uiState.activeCatalogProducts)
    }
    val selectedEntries = form.selectedQuantities.entries
        .filter { it.value > 0 }
        .mapNotNull { (id, qty) -> productMap[id]?.let { it to qty } }

    val subtotalCents = MixVendasShellViewModel.computeSelectedSubtotalCents(
        form.selectedQuantities,
        productMap,
    )
    val typedFinalCents = MixVendasShellViewModel.parseBrlInputToCents(form.finalAmountInput)
    val effectiveFinalCents = when {
        form.finalAmountInput.isBlank() -> subtotalCents
        typedFinalCents != null -> typedFinalCents
        else -> subtotalCents
    }
    val discountCents = (subtotalCents - effectiveFinalCents).coerceAtLeast(0L)
    val finalAmountError = when {
        selectedEntries.isEmpty() -> null
        form.finalAmountInput.isBlank() -> if (form.finalAmountTouched) "Informe o valor final da venda." else null
        typedFinalCents == null -> "Valor final inválido (ex.: 95,00)."
        typedFinalCents <= 0L -> "O valor final deve ser maior que zero."
        typedFinalCents > subtotalCents -> "Não pode ultrapassar o subtotal original."
        else -> null
    }
    val showDiscountOptions = selectedEntries.isNotEmpty() &&
        form.finalAmountInput.isNotBlank() &&
        typedFinalCents != null &&
        typedFinalCents in 1L until subtotalCents

    val totalSelectedQty = selectedEntries.sumOf { it.second }
    val summaryTotalCents = if (finalAmountError == null) effectiveFinalCents else subtotalCents

    val effectiveDiscountSelectedProductIds = form.effectiveDiscountSelectedProductIds()
    val selectedDiscountEntries = remember(selectedEntries, effectiveDiscountSelectedProductIds) {
        selectedEntries.filter { it.first.id in effectiveDiscountSelectedProductIds }
    }
    val selectableDiscountPeople = remember(
        selectedDiscountEntries,
        form.discountProtectedPersonIds,
        form.discountUnprotectedFixedPersonIds,
    ) {
        SaleDiscountPolicy.buildSelectablePeopleForDiscount(
            selectedDiscountEntries = selectedDiscountEntries,
            protectedPersonIds = form.discountProtectedPersonIds,
            unprotectedFixedPersonIds = form.discountUnprotectedFixedPersonIds,
        )
    }
    val discountPreview = remember(
        form,
        uiState.activeCatalogProducts,
        uiState.machines,
    ) {
        if (selectedEntries.isNotEmpty() && (showDiscountOptions || form.isDiscountTargetMissing)) {
            MixVendasShellViewModel.buildDiscountPreview(
                form = form,
                activeProducts = uiState.activeCatalogProducts,
                machines = uiState.machines,
            )
        } else {
            null
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_vender"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "vender_main_content") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!form.isEditing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = 18.dp, bottom = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.sell_title),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 34.sp,
                            lineHeight = 40.sp,
                            letterSpacing = (-0.5).sp,
                            color = Color.White,
                            modifier = Modifier
                                .semantics { heading() }
                                .testTag("vender_header_title"),
                        )
                    }
                } else {
                    val originalSale = form.originalEditingSale
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .testTag("vender_editing_banner"),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xD92B1152),
                        border = BorderStroke(1.4.dp, V19GoldAccent.copy(alpha = 0.80f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Mv3dIcon(
                                        iconRes = R.drawable.mv3d_editar,
                                        contentDescription = null,
                                        size = 36.dp,
                                    )
                                    Text(
                                        text = stringResource(R.string.sell_edit_banner_title),
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = V19GoldAccent,
                                    )
                                }
                                OutlinedButton(
                                    onClick = onCancelEditing,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.72f)),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("cancel_edit_button"),
                                ) {
                                    Text(
                                        text = stringResource(R.string.sell_cancel_edit_action),
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                    )
                                }
                            }
                            if (originalSale != null) {
                                val originalDate = DailySalesAggregator.toLocalDate(
                                    originalSale.soldAtEpochMs,
                                    ZoneId.systemDefault(),
                                )
                                val editDateLine = if (form.selectedSaleDate == originalDate) {
                                    "Data original preservada: ${CurrencyFormatter.formatEpochMillis(originalSale.soldAtEpochMs)}"
                                } else {
                                    "Nova data: ${CurrencyFormatter.formatLocalDate(form.selectedSaleDate)} · " +
                                        "horário original preservado (${CurrencyFormatter.formatTimeOfEpochMillis(originalSale.soldAtEpochMs)})"
                                }
                                Text(
                                    text = editDateLine,
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 12.sp,
                                    color = V19PurpleSoftText,
                                )
                            }
                        }
                    }
                }

                // Seção 1 — Produtos vendidos
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    VenderSectionHeading(text = stringResource(R.string.sell_step_products))

                    VenderChooseProductsButton(onClick = onOpenPicker)

                    if (selectedEntries.isEmpty()) {
                        VenderDashedEmptyCard(
                            hasActiveCatalogProducts = uiState.activeCatalogProducts.isNotEmpty(),
                        )
                    } else {
                        VenderGlassCard {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                selectedEntries.forEachIndexed { index, (product, qty) ->
                                    val isHistoricalItem = form.historicalProductsById.containsKey(product.id)
                                    val catalogCurrent = activeCatalogMap[product.id]
                                    val historicalNote = when {
                                        isHistoricalItem && catalogCurrent == null ->
                                            "Item histórico preservado (removido ou inativo no cadastro)"
                                        isHistoricalItem && catalogCurrent != null && (catalogCurrent.name != product.name || catalogCurrent.price != product.price) ->
                                            "Preço/nome originais da venda preservados (${CurrencyFormatter.formatBrl(product.price)})"
                                        else -> null
                                    }
                                    SelectedSaleItemRow(
                                        product = product,
                                        quantity = qty,
                                        historicalHint = historicalNote,
                                        onIncrement = { onIncrementProductQty(product.id) },
                                        onDecrement = { onDecrementProductQty(product.id) },
                                        onRemove = { onRemoveProduct(product.id) },
                                    )
                                    if (index < selectedEntries.lastIndex) {
                                        HorizontalDivider(
                                            color = HomeCardSubtleDivider.copy(alpha = 0.28f),
                                            thickness = 1.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    VenderSelectedSummaryBar(
                        totalItemsCount = totalSelectedQty,
                        totalAmount = MoneyCents(summaryTotalCents),
                    )
                }

                // Card Valor final e desconto
                VenderGlassCard {
                    VenderSectionHeading(text = stringResource(R.string.sell_final_section))

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.sell_subtotal_label),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp,
                            color = HomeCardLabelInk,
                        )
                        Text(
                            text = CurrencyFormatter.formatBrl(MoneyCents(subtotalCents)),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.sell_discount_label),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp,
                            color = HomeCardLabelInk,
                        )
                        Text(
                            text = CurrencyFormatter.formatBrl(MoneyCents(discountCents)),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (discountCents > 0L) V19GoldAccent else Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.30f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.sell_final_input_label),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = form.finalAmountInput,
                        onValueChange = onFinalAmountChanged,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = Color.White,
                        ),
                        placeholder = {
                            Text(
                                text = "0,00",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 17.sp,
                                color = HomeCardLabelInk.copy(alpha = 0.65f),
                            )
                        },
                        isError = finalAmountError != null,
                        supportingText = if (finalAmountError != null) {
                            {
                                Text(
                                    text = finalAmountError,
                                    color = V19ErrorSoft,
                                    modifier = Modifier.testTag("vender_final_amount_error"),
                                )
                            }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = namesDarkInputColors(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sale_final_value_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.sell_final_helper),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = HomeCardLabelInk.copy(alpha = 0.85f),
                    )

                    if (!form.recalculatedNotice.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {}
                                .testTag("vender_recalculated_banner"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (form.recalculatedNoticeIsError) {
                                V19ErrorContainer
                            } else {
                                Color(0xFF281947).copy(alpha = 0.90f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (form.recalculatedNoticeIsError) V19ErrorSoft else V19GoldAccent.copy(alpha = 0.72f),
                            ),
                        ) {
                            Text(
                                text = form.recalculatedNotice,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 12.sp,
                                color = if (form.recalculatedNoticeIsError) V19ErrorSoft else V19GoldAccent,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    DiscountProductsSelectionBox(
                        selectedEntries = selectedEntries,
                        selectedDiscountProductIds = effectiveDiscountSelectedProductIds,
                        discountCents = discountCents,
                        targetMissing = form.isDiscountTargetMissing,
                        onToggleProduct = onToggleDiscountProduct,
                        onSelectAll = onSelectAllDiscountProducts,
                        onClearAll = onClearDiscountProducts,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DiscountProtectedPeopleBox(
                        selectablePeople = selectableDiscountPeople,
                        onToggleProtectedPerson = onToggleProtectedDiscountPerson,
                        onAuthorizeUnprotectFixedPerson = onAuthorizeUnprotectFixedPerson,
                        hasProductsInSale = selectedEntries.isNotEmpty(),
                    )
                }

                // Card Observação (opcional)
                VenderGlassCard(
                    modifier = Modifier.testTag("sale_note_card"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Mv3dIcon(
                            iconRes = R.drawable.mv3d_editar,
                            contentDescription = null,
                            size = 36.dp,
                        )
                        Text(
                            text = stringResource(R.string.sell_note_label),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = form.note,
                        onValueChange = onNoteChanged,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp,
                            color = Color.White,
                        ),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.sell_note_placeholder),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 15.sp,
                                color = HomeCardLabelInk.copy(alpha = 0.72f),
                            )
                        },
                        colors = namesDarkInputColors(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .testTag("sale_note_input"),
                    )
                }

                // Seletor de data real da venda — posicionado imediatamente abaixo de Observação
                VenderSaleDateSelectorCard(
                    selectedDate = form.selectedSaleDate,
                    customSaleDateEnabled = form.saleDateCustomized,
                    isEditing = form.isEditing,
                    originalSaleEpochMs = form.originalEditingSale?.soldAtEpochMs,
                    onCustomSaleDateToggled = { enabled ->
                        if (!enabled) {
                            saleDatePickerOpen = false
                        }
                        onCustomSaleDateToggled(enabled)
                    },
                    onOpenPicker = {
                        if (canOpenSaleDateCalendar) {
                            saleDatePickerOpen = true
                        }
                    },
                )

                // Seção 2 — Forma de pagamento
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    VenderSectionHeading(text = stringResource(R.string.sell_step_payment))

                    PaymentMethodGrid(
                        selectedMethod = form.selectedMethod,
                        onSelectMethod = onPaymentMethodSelected,
                    )

                    if (form.selectedMethod == PreviewPaymentMethod.CASH ||
                        form.selectedMethod == PreviewPaymentMethod.PIX
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = GlassInnerBoxBg,
                            border = BorderStroke(1.1.dp, GlassNeonBorder15),
                        ) {
                            Text(
                                text = "${form.selectedMethod.label}: nenhum desconto de maquininha aplicável.",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp,
                                color = HomeCardLabelInk,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        }
                    } else {
                        VenderGlassCard {
                            MachineCardExtrasSection(
                                form = form,
                                machines = uiState.machines,
                                onOpenMachinePicker = onOpenMachinePicker,
                                onBrandKeySelected = onBrandKeySelected,
                                onInstallmentsSelected = onInstallmentsSelected,
                                onFeeOverrideToggled = onFeeOverrideToggled,
                                onManualRateChanged = onManualRateChanged,
                            )
                        }
                    }
                }

                // Seção 3 — Divisão automática por produto
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    VenderSectionHeading(text = stringResource(R.string.sell_step_split))

                    val discountTargetLabel = if (discountCents > 0L) {
                        val protectedSuffix = if (form.discountProtectedPersonIds.isNotEmpty()) {
                            " (${form.discountProtectedPersonIds.size} pessoa(s) protegida(s))"
                        } else {
                            ""
                        }
                        val matchedProducts = selectedEntries.filter { it.first.id in effectiveDiscountSelectedProductIds }
                        when {
                            matchedProducts.isEmpty() -> "nenhum produto selecionado"
                            matchedProducts.size == selectedEntries.size ->
                                "distribuído proporcionalmente entre todos os produtos$protectedSuffix"
                            matchedProducts.size == 1 ->
                                "aplicado somente em ${matchedProducts.first().first.name}$protectedSuffix"
                            else ->
                                "aplicado em ${matchedProducts.size} produtos (${matchedProducts.joinToString(", ") { it.first.name }})$protectedSuffix"
                        }
                    } else {
                        null
                    }
                    VenderAutoSplitSectionCard(
                        selectedEntries = selectedEntries,
                        discountCents = discountCents,
                        discountTargetLabel = discountTargetLabel,
                    )

                    if (discountPreview != null) {
                        VenderDiscountPreviewCard(
                            preview = discountPreview,
                        )
                    }
                }

                // Feedback e botão principal
                val feedbackMsg = form.feedbackMessage
                if (!feedbackMsg.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .testTag("vender_feedback_banner"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (form.feedbackIsError) V19ErrorContainer else V19SuccessContainer,
                        border = BorderStroke(
                            width = 1.2.dp,
                            color = if (form.feedbackIsError) {
                                V19ErrorSoft.copy(alpha = 0.65f)
                            } else {
                                V19MintSuccess.copy(alpha = 0.65f)
                            },
                        ),
                    ) {
                        Text(
                            text = feedbackMsg,
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (form.feedbackIsError) V19ErrorSoft else V19SuccessInk,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }

                val buttonText = when {
                    form.isEditing && form.isSaving -> stringResource(R.string.sell_saving_changes_action)
                    form.isEditing -> stringResource(R.string.sell_save_changes_action)
                    form.isSaving -> stringResource(R.string.sell_saving_action)
                    else -> stringResource(R.string.sell_save_action)
                }
                VenderPrimarySaveButton(
                    text = buttonText,
                    enabled = !form.isSaving,
                    onClick = onSaveSale,
                )
            }
        }
    }

    if (form.pickerOpen) {
        SearchableProductPickerDialog(
            products = pickerProducts,
            categories = uiState.catalogProductCategories,
            historicalIds = form.historicalProductsById.keys,
            activeCatalogMap = activeCatalogMap,
            selectedQuantities = form.selectedQuantities,
            searchQuery = form.pickerSearchQuery,
            onSearchChanged = onPickerSearchChanged,
            onToggleProduct = onToggleProductSelection,
            onIncrementQty = onIncrementProductQty,
            onDecrementQty = onDecrementProductQty,
            onDismiss = onClosePicker,
        )
    }

    if (form.machinePickerOpen &&
        (form.selectedMethod == PreviewPaymentMethod.DEBIT || form.selectedMethod == PreviewPaymentMethod.CREDIT)
    ) {
        val eligibleMachines = when (form.selectedMethod) {
            PreviewPaymentMethod.DEBIT -> uiState.machines
            PreviewPaymentMethod.CREDIT -> uiState.machines.filter { it.creditEnabled }
            else -> emptyList()
        }
        val histSelected = MixVendasShellViewModel.resolvedSelectedMachine(form, uiState.machines)
        val pickerMachines = if (
            histSelected != null &&
            MixVendasShellViewModel.canUseHistoricalMachineSnapshot(form) &&
            eligibleMachines.none { it.id == histSelected.id }
        ) {
            listOf(histSelected) + eligibleMachines
        } else {
            eligibleMachines
        }
        SearchableMachinePickerDialog(
            method = form.selectedMethod,
            machines = pickerMachines,
            hasAnyRegisteredMachines = uiState.machines.isNotEmpty(),
            historicalOnlyMachineId = if (
                MixVendasShellViewModel.canUseHistoricalMachineSnapshot(form) &&
                eligibleMachines.none { it.id == histSelected?.id }
            ) {
                histSelected?.id
            } else {
                null
            },
            selectedMachineId = form.selectedMachineId,
            searchQuery = form.machinePickerSearchQuery,
            onSearchChanged = onMachinePickerSearchChanged,
            onSelectMachine = onMachineSelected,
            onDismiss = onCloseMachinePicker,
        )
    }

    if (form.showDiscardEditDialog) {
        AlertDialog(
            onDismissRequest = onDismissDiscardEdit,
            containerColor = V19SurfaceCard,
            titleContentColor = Color.White,
            textContentColor = V19InkPrimary,
            modifier = Modifier.testTag("discard_edit_dialog"),
            title = {
                Text(
                    text = "Descartar alterações?",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Você fez alterações nesta venda que ainda não foram salvas. Deseja descartar e sair da edição?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDiscardEdit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = V19PurpleButtonStart,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.testTag("discard_edit_confirm_button"),
                ) {
                    Text("Descartar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissDiscardEdit,
                    modifier = Modifier.testTag("discard_edit_keep_button"),
                ) {
                    Text("Continuar editando", color = V19PurpleSoftText)
                }
            },
        )
    }

    if (saleDatePickerOpen && canOpenSaleDateCalendar) {
        val originalSaleDate = form.originalEditingSale?.let {
            DailySalesAggregator.toLocalDate(it.soldAtEpochMs, ZoneId.systemDefault())
        }
        SaleDatePickerModal(
            selectedDate = form.selectedSaleDate,
            originalDate = originalSaleDate,
            onDateSelected = { pickedDate ->
                saleDatePickerOpen = false
                onSaleDateSelected(pickedDate)
            },
            onDismiss = { saleDatePickerOpen = false },
        )
    }
}

@Composable
private fun VenderSaleDateSelectorCard(
    selectedDate: LocalDate,
    customSaleDateEnabled: Boolean,
    isEditing: Boolean,
    originalSaleEpochMs: Long?,
    onCustomSaleDateToggled: (Boolean) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)
    val effectiveDate = if (!isEditing && !customSaleDateEnabled) today else selectedDate
    val originalDate = originalSaleEpochMs?.let { DailySalesAggregator.toLocalDate(it, zoneId) }
    val formattedDate = CurrencyFormatter.formatLocalDate(effectiveDate)
    val calendarAvailable = isEditing || customSaleDateEnabled

    val helperText = when {
        isEditing && originalDate != null && effectiveDate != originalDate ->
            "Data alterada · horário original (${CurrencyFormatter.formatTimeOfEpochMillis(originalSaleEpochMs)}) preservado"
        isEditing && originalSaleEpochMs != null ->
            "Data original da venda · horário ${CurrencyFormatter.formatTimeOfEpochMillis(originalSaleEpochMs)}"
        !customSaleDateEnabled ->
            "Data atual do aparelho · salva com o horário atual"
        effectiveDate == today ->
            "Data personalizada ativa (hoje) · escolha no calendário"
        effectiveDate.isBefore(today) ->
            "Data anterior selecionada · salva com o horário atual"
        else ->
            "Data personalizada selecionada · salva com o horário atual"
    }

    VenderGlassCard(
        modifier = Modifier.testTag("sale_date_card"),
    ) {
        if (!isEditing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onCustomSaleDateToggled(!customSaleDateEnabled) }
                    .padding(vertical = 2.dp)
                    .testTag("custom_sale_date_toggle_row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sell_use_custom_date_toggle),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                    )
                    Text(
                        text = if (customSaleDateEnabled) {
                            "Ativado · toque em Alterar para escolher outra data"
                        } else {
                            "Desativado · utiliza automaticamente a data de hoje"
                        },
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.sp,
                        color = if (customSaleDateEnabled) V19PurpleSoftText else HomeCardLabelInk.copy(alpha = 0.85f),
                    )
                }
                Switch(
                    checked = customSaleDateEnabled,
                    onCheckedChange = onCustomSaleDateToggled,
                    colors = SwitchDefaults.colors(checkedTrackColor = V19PurplePrimary),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("custom_sale_date_switch"),
                )
            }

            HorizontalDivider(
                color = HomeCardSubtleDivider.copy(alpha = 0.25f),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Mv3dIcon(
                iconRes = R.drawable.mv3d_calendario,
                contentDescription = null,
                size = 38.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.sell_date_label),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = V19PurpleSoftText,
                )
                Text(
                    text = formattedDate,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = Color.White,
                    modifier = Modifier.testTag("sale_date_value_text"),
                )
                Text(
                    text = helperText,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = if (isEditing && originalDate != null && effectiveDate != originalDate) {
                        V19GoldAccent
                    } else {
                        HomeCardLabelInk.copy(alpha = 0.85f)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (calendarAvailable) {
                OutlinedButton(
                    onClick = onOpenPicker,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.1.dp, HomeCardNeonBorder.copy(alpha = 0.72f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("sale_date_selector_button"),
                ) {
                    Text(
                        text = stringResource(R.string.sell_date_change_action),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaleDatePickerModal(
    selectedDate: LocalDate,
    originalDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = LocalDate.now(ZoneId.systemDefault())
    val yesterday = today.minusDays(1)
    val initialUtcMillis = remember(selectedDate) {
        DailySalesAggregator.localDateToUtcDatePickerMillis(selectedDate)
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialUtcMillis,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(
            containerColor = V19SurfaceCard,
        ),
        modifier = Modifier.testTag("sale_date_picker_dialog"),
        confirmButton = {
            Button(
                onClick = {
                    val millis = datePickerState.selectedDateMillis
                    val resolved = if (millis != null) {
                        DailySalesAggregator.utcDatePickerMillisToLocalDate(millis)
                    } else {
                        selectedDate
                    }
                    onDateSelected(resolved)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = V19PurplePrimary,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("sale_date_picker_confirm"),
            ) {
                Text(
                    text = stringResource(R.string.sell_date_picker_confirm),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("sale_date_picker_cancel"),
            ) {
                Text(
                    text = stringResource(R.string.sell_date_picker_cancel),
                    fontFamily = PlusJakartaSansFontFamily,
                    color = V19PurpleSoftText,
                )
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onDateSelected(today) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.65f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("sale_date_quick_today"),
                ) {
                    Text(
                        text = "Hoje",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color.White,
                    )
                }
                OutlinedButton(
                    onClick = { onDateSelected(yesterday) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.65f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("sale_date_quick_yesterday"),
                ) {
                    Text(
                        text = "Ontem",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color.White,
                    )
                }
                if (originalDate != null && originalDate != selectedDate) {
                    OutlinedButton(
                        onClick = { onDateSelected(originalDate) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, V19GoldAccent.copy(alpha = 0.70f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("sale_date_quick_original"),
                    ) {
                        Text(
                            text = "Original",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = V19GoldAccent,
                        )
                    }
                }
            }

            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = stringResource(R.string.sell_date_picker_title),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = V19PurpleSoftText,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 8.dp),
                    )
                },
                colors = DatePickerDefaults.colors(
                    containerColor = V19SurfaceCard,
                    titleContentColor = V19PurpleSoftText,
                    headlineContentColor = Color.White,
                    weekdayContentColor = V19InkMuted,
                    subheadContentColor = V19PurpleSoftText,
                    navigationContentColor = Color.White,
                    yearContentColor = Color.White,
                    currentYearContentColor = V19GoldAccent,
                    selectedYearContentColor = Color.White,
                    selectedYearContainerColor = V19PurplePrimary,
                    dayContentColor = Color.White,
                    selectedDayContentColor = Color.White,
                    selectedDayContainerColor = V19PurplePrimary,
                    todayContentColor = V19GoldAccent,
                    todayDateBorderColor = V19GoldAccent,
                ),
            )
        }
    }
}

@Composable
private fun SelectedSaleItemRow(
    product: Product,
    quantity: Int,
    historicalHint: String? = null,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = product.name,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val lineTotalText = MonetaryRounding.safeMultiplyOrNull(product.price.rawCents, quantity)
                ?.let { CurrencyFormatter.formatBrl(MoneyCents(it)) }
                ?: "Limite excedido"
            Text(
                text = "Unit.: ${CurrencyFormatter.formatBrl(product.price)} · Total: $lineTotalText",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = V19PurpleSoftText,
            )
            if (!historicalHint.isNullOrBlank()) {
                Text(
                    text = historicalHint,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 11.sp,
                    color = V19GoldAccent,
                    modifier = Modifier.testTag("selected_historical_hint_${product.id}"),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = onDecrement,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("selected_dec_${product.id}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Diminuir ${product.name}",
                    tint = V19PurpleSoftText,
                )
            }
            Text(
                text = quantity.toString(),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White,
            )
            IconButton(
                onClick = onIncrement,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("selected_inc_${product.id}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Aumentar ${product.name}",
                    tint = V19PurpleSoftText,
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("selected_remove_${product.id}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remover ${product.name}",
                    tint = V19InkMuted,
                )
            }
        }
    }
}


@Composable
private fun PaymentMethodGrid(
    selectedMethod: PreviewPaymentMethod,
    onSelectMethod: (PreviewPaymentMethod) -> Unit,
) {
    val rows = listOf(
        listOf(PreviewPaymentMethod.CASH, PreviewPaymentMethod.PIX),
        listOf(PreviewPaymentMethod.DEBIT, PreviewPaymentMethod.CREDIT),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { method ->
                    VenderPaymentMethodTile(
                        method = method,
                        selected = method == selectedMethod,
                        onClick = { onSelectMethod(method) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MachineCardExtrasSection(
    form: VenderFormUiState,
    machines: List<Machine>,
    onOpenMachinePicker: () -> Unit,
    onBrandKeySelected: (String) -> Unit,
    onInstallmentsSelected: (Int) -> Unit,
    onFeeOverrideToggled: (Boolean) -> Unit,
    onManualRateChanged: (String) -> Unit,
) {
    val eligibleMachines = when (form.selectedMethod) {
        PreviewPaymentMethod.DEBIT -> machines
        PreviewPaymentMethod.CREDIT -> machines.filter { it.creditEnabled }
        else -> emptyList()
    }
    val canUseHistorical = MixVendasShellViewModel.canUseHistoricalMachineSnapshot(form)
    val selectedMachine = MixVendasShellViewModel.resolvedSelectedMachine(form, machines)
    val catalogMachine = eligibleMachines.firstOrNull { it.id == form.selectedMachineId }
    val optionsMachine = catalogMachine ?: selectedMachine
    val selectedBrandKey = form.resolvedBrandKey()
    val selectedBrandLabel = form.resolvedBrandDisplayLabel()
    val showHistoricalMachineNotice = canUseHistorical && selectedMachine != null && (
        catalogMachine == null ||
            catalogMachine.name != selectedMachine.name ||
            ((form.selectedMethod == PreviewPaymentMethod.CREDIT || form.selectedMethod == PreviewPaymentMethod.DEBIT) &&
                catalogMachine.brandRatesEnabled &&
                CardBrand.fromStorageKey(selectedBrandKey) == null &&
                catalogMachine.findCustomBrand(selectedBrandKey) == null) ||
            (form.selectedMethod == PreviewPaymentMethod.CREDIT &&
                (!catalogMachine.installmentsEnabled && selectedMachine.installmentsEnabled ||
                    !catalogMachine.isInstallmentAllowed(form.selectedInstallments))) ||
            catalogMachine.effectiveAutomaticRateBpsForKey(
                method = form.selectedMethod,
                brandKey = selectedBrandKey,
                installments = form.selectedInstallments,
            ) != form.historicalMachineSnapshot?.automaticRateBps
        )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("machine_extras_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.sell_machine_label),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White,
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onOpenMachinePicker)
                    .testTag("machine_selector_dropdown"),
                shape = RoundedCornerShape(14.dp),
                color = GlassInnerBoxBg,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selectedMachine != null) {
                        HomeCardNeonBorder.copy(alpha = 0.85f)
                    } else {
                        GlassNeonBorder15
                    },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Mv3dIcon(
                        iconRes = R.drawable.mv3d_maquininhas,
                        contentDescription = null,
                        size = 42.dp,
                    )
                    Text(
                        text = when {
                            selectedMachine != null -> selectedMachine.name
                            eligibleMachines.isEmpty() -> if (form.selectedMethod == PreviewPaymentMethod.CREDIT) {
                                "Nenhuma maquininha com crédito ativo"
                            } else {
                                "Nenhuma maquininha cadastrada"
                            }
                            else -> "Pesquisar e selecionar maquininha..."
                        },
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = if (selectedMachine != null) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 15.sp,
                        color = if (selectedMachine != null) Color.White else V19InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Mv3dIcon(
                        iconRes = R.drawable.mv3d_buscar,
                        contentDescription = "Selecionar maquininha",
                        size = 32.dp,
                    )
                }
            }
        }

        if (showHistoricalMachineNotice) {
            Text(
                text = "Maquininha e tarifa originais da venda preservadas. Troque a maquininha se desejar aplicar regras atuais.",
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 11.sp,
                color = V19GoldAccent,
                modifier = Modifier.testTag("vender_historical_machine_notice"),
            )
        }

        if (eligibleMachines.isEmpty() && selectedMachine == null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vender_no_eligible_machines"),
                shape = RoundedCornerShape(14.dp),
                color = GlassInnerBoxBg,
                border = BorderStroke(1.dp, GlassNeonBorder15),
            ) {
                Text(
                    text = if (form.selectedMethod == PreviewPaymentMethod.CREDIT) {
                        "Nenhuma maquininha com crédito ativado no Room. Cadastre ou ative o crédito em Mais → Maquininhas."
                    } else {
                        "Nenhuma maquininha cadastrada no Room. Cadastre uma em Mais → Maquininhas."
                    },
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 13.sp,
                    color = HomeCardLabelInk,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (selectedMachine != null && optionsMachine != null) {
            if (form.selectedMethod == PreviewPaymentMethod.DEBIT && optionsMachine.brandRatesEnabled) {
                val debitBrandOptions: List<Pair<String, String>> = if (catalogMachine != null) {
                    val standardOptions = CardBrand.entries.map { brand ->
                        val rateSource = if (canUseHistorical && brand.storageKey == selectedBrandKey) {
                            selectedMachine
                        } else {
                            catalogMachine
                        }
                        val rateBps = rateSource.baseDebitRateBps(brand)
                        val hasSpecificDebit = rateSource.brandRatesEnabled && rateSource.brandDebitRatesBps.containsKey(brand)
                        val suffix = if (hasSpecificDebit) {
                            RateFormatter.formatBpsAsPercent(rateBps)
                        } else {
                            "${RateFormatter.formatBpsAsPercent(rateBps)} padrão"
                        }
                        brand.storageKey to "${brand.label} ($suffix)"
                    }
                    val customOptions = catalogMachine.customBrandRates.map { custom ->
                        val rateSource = if (canUseHistorical && custom.key == selectedBrandKey) {
                            selectedMachine
                        } else {
                            catalogMachine
                        }
                        val rateBps = rateSource.baseDebitRateBpsForKey(custom.key)
                        val hasSpecificDebit = rateSource.brandRatesEnabled &&
                            rateSource.findCustomBrand(custom.key)?.debitRateBps != null
                        val suffix = if (hasSpecificDebit) {
                            RateFormatter.formatBpsAsPercent(rateBps)
                        } else {
                            "${RateFormatter.formatBpsAsPercent(rateBps)} padrão"
                        }
                        custom.key to "${custom.name} ($suffix)"
                    }
                    standardOptions + customOptions
                } else {
                    val rateBps = selectedMachine.baseDebitRateBpsForKey(selectedBrandKey)
                    val standard = CardBrand.fromStorageKey(selectedBrandKey)
                    val hasSpecificDebit = if (standard != null) {
                        selectedMachine.brandRatesEnabled && selectedMachine.brandDebitRatesBps.containsKey(standard)
                    } else {
                        selectedMachine.brandRatesEnabled && selectedMachine.findCustomBrand(selectedBrandKey)?.debitRateBps != null
                    }
                    val suffix = if (hasSpecificDebit) {
                        RateFormatter.formatBpsAsPercent(rateBps)
                    } else {
                        "${RateFormatter.formatBpsAsPercent(rateBps)} padrão"
                    }
                    listOf(selectedBrandKey to "$selectedBrandLabel ($suffix)")
                }
                SimpleDropdownField(
                    label = stringResource(R.string.sell_brand_label),
                    selectedText = selectedBrandLabel,
                    options = debitBrandOptions,
                    onOptionSelected = onBrandKeySelected,
                    testTag = "brand_selector_dropdown",
                )
            } else if (form.selectedMethod == PreviewPaymentMethod.CREDIT) {
                val showInstallmentDropdown = optionsMachine.installmentsEnabled ||
                    (canUseHistorical && selectedMachine.installmentsEnabled)
                if (optionsMachine.brandRatesEnabled || showInstallmentDropdown) {
                    val brandOptions: List<Pair<String, String>> = if (catalogMachine != null) {
                        val standardOptions = CardBrand.entries.map { brand ->
                            val rateSource = if (canUseHistorical && brand.storageKey == selectedBrandKey) {
                                selectedMachine
                            } else {
                                catalogMachine
                            }
                            val rateBps = rateSource.baseCreditRateBps(brand)
                            val suffix = if (rateSource.usesBrandSpecificRate(brand)) {
                                RateFormatter.formatBpsAsPercent(rateBps)
                            } else {
                                "${RateFormatter.formatBpsAsPercent(rateBps)} padrão"
                            }
                            brand.storageKey to "${brand.label} ($suffix)"
                        }
                        val customOptions = catalogMachine.customBrandRates.map { custom ->
                            val rateSource = if (canUseHistorical && custom.key == selectedBrandKey) {
                                selectedMachine
                            } else {
                                catalogMachine
                            }
                            val rateBps = rateSource.baseCreditRateBpsForKey(custom.key)
                            custom.key to "${custom.name} (${RateFormatter.formatBpsAsPercent(rateBps)})"
                        }
                        standardOptions + customOptions
                    } else {
                        val rateBps = selectedMachine.baseCreditRateBpsForKey(selectedBrandKey)
                        val suffix = if (selectedMachine.usesBrandSpecificRateForKey(selectedBrandKey)) {
                            RateFormatter.formatBpsAsPercent(rateBps)
                        } else {
                            "${RateFormatter.formatBpsAsPercent(rateBps)} padrão"
                        }
                        listOf(selectedBrandKey to "$selectedBrandLabel ($suffix)")
                    }
                    val baseInstallmentChoices = if (catalogMachine != null && catalogMachine.installmentsEnabled) {
                        catalogMachine.allowedInstallmentTiers()
                    } else {
                        listOf(form.selectedInstallments)
                    }
                    val installmentChoices = if (canUseHistorical && form.selectedInstallments !in baseInstallmentChoices) {
                        (listOf(form.selectedInstallments) + baseInstallmentChoices).distinct()
                    } else {
                        baseInstallmentChoices
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (optionsMachine.brandRatesEnabled) {
                            Box(modifier = Modifier.weight(1f)) {
                                SimpleDropdownField(
                                    label = stringResource(R.string.sell_brand_label),
                                    selectedText = selectedBrandLabel,
                                    options = brandOptions,
                                    onOptionSelected = onBrandKeySelected,
                                    testTag = "brand_selector_dropdown",
                                )
                            }
                        }
                        if (showInstallmentDropdown) {
                            Box(modifier = Modifier.weight(1f)) {
                                SimpleDropdownField(
                                    label = stringResource(R.string.sell_installments_label),
                                    selectedText = "${form.selectedInstallments}x",
                                    options = installmentChoices.map { tier ->
                                        val rateSource = if (canUseHistorical && tier == form.selectedInstallments) {
                                            selectedMachine
                                        } else {
                                            optionsMachine
                                        }
                                        val addon = rateSource.installmentAddonBps(tier)
                                        tier.toString() to "${tier}x (+${RateFormatter.formatBpsAsPercent(addon)})"
                                    },
                                    onOptionSelected = { onInstallmentsSelected(it.toIntOrNull() ?: 1) },
                                    testTag = "installments_selector_dropdown",
                                )
                            }
                        }
                    }
                }
            }

            val autoRateBps = selectedMachine.effectiveAutomaticRateBpsForKey(
                method = form.selectedMethod,
                brandKey = selectedBrandKey,
                installments = form.selectedInstallments,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onFeeOverrideToggled(!form.feeOverrideEnabled) }
                    .testTag("manual_fee_override_row"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = form.feeOverrideEnabled,
                    onCheckedChange = onFeeOverrideToggled,
                    colors = CheckboxDefaults.colors(
                        checkedColor = V19PurplePrimary,
                        uncheckedColor = HomeCardNeonBorder,
                        checkmarkColor = Color.White,
                    ),
                    modifier = Modifier.testTag("manual_fee_override_checkbox"),
                )
                Text(
                    text = "Substituir tarifa automática nesta venda",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 14.sp,
                    color = Color.White,
                )
            }

            if (form.feeOverrideEnabled) {
                val parsedManual = RateFormatter.parsePercentageToBps(form.manualRateInput)
                val manualError = when {
                    form.manualRateInput.isBlank() -> null
                    parsedManual == null -> "Tarifa manual inválida. Use até 2 casas decimais (ex.: 3,50)."
                    parsedManual < RateFormatter.MIN_RATE_BPS -> "Tarifa manual não pode ser negativa."
                    parsedManual > RateFormatter.MAX_RATE_BPS -> "Tarifa manual não pode ultrapassar 100,00%."
                    else -> null
                }

                OutlinedTextField(
                    value = form.manualRateInput,
                    onValueChange = onManualRateChanged,
                    label = { Text("Tarifa manual (%) — substitui a automática") },
                    placeholder = { Text("Ex.: 3,50") },
                    singleLine = true,
                    isError = manualError != null,
                    supportingText = {
                        if (manualError != null) {
                            Text(
                                text = manualError,
                                color = V19ErrorSoft,
                                modifier = Modifier.testTag("vender_manual_rate_error"),
                            )
                        } else {
                            Text(
                                text = "A tarifa manual substitui integralmente a tarifa da maquininha (nunca soma).",
                                color = V19InkMuted,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = namesDarkInputColors(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_rate_input"),
                )
            }

            if (autoRateBps != null) {
                val manualValidBps = if (form.feeOverrideEnabled) {
                    RateFormatter.parsePercentageToBps(form.manualRateInput)
                        ?.takeIf { it in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS }
                } else {
                    null
                }
                val summaryText = when {
                    form.feeOverrideEnabled && manualValidBps != null ->
                        "Tarifa aplicada nesta venda (manual): ${RateFormatter.formatBpsAsPercent(manualValidBps)} (substitui ${RateFormatter.formatBpsAsPercent(autoRateBps)})"
                    form.selectedMethod == PreviewPaymentMethod.DEBIT ->
                        "Tarifa automática no débito: ${RateFormatter.formatBpsAsPercent(autoRateBps)}"
                    else -> {
                        val baseBps = selectedMachine.baseCreditRateBpsForKey(selectedBrandKey)
                        val addonBps = selectedMachine.installmentAddonBps(form.selectedInstallments)
                        val brandOrigin = if (selectedMachine.usesBrandSpecificRateForKey(selectedBrandKey)) {
                            selectedBrandLabel
                        } else {
                            "padrão"
                        }
                        if (selectedMachine.installmentsEnabled) {
                            "Tarifa automática no crédito: ${RateFormatter.formatBpsAsPercent(autoRateBps)} (base $brandOrigin ${RateFormatter.formatBpsAsPercent(baseBps)} + ${form.selectedInstallments}x ${RateFormatter.formatBpsAsPercent(addonBps)})"
                        } else {
                            "Tarifa automática no crédito: ${RateFormatter.formatBpsAsPercent(autoRateBps)} (base $brandOrigin)"
                        }
                    }
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag("vender_rate_summary"),
                    shape = RoundedCornerShape(14.dp),
                    color = GlassInnerBoxBg,
                    border = BorderStroke(1.dp, GlassNeonBorder15),
                ) {
                    Text(
                        text = summaryText,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.sp,
                        color = HomeCardLabelInk,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleDropdownField(
    label: String,
    selectedText: String,
    options: List<Pair<String, String>>,
    onOptionSelected: (String) -> Unit,
    testTag: String,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = HomeCardLabelInk,
        )
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { expanded = true }
                    .testTag(testTag),
                shape = RoundedCornerShape(14.dp),
                color = GlassInnerBoxBg,
                border = BorderStroke(1.dp, GlassNeonBorder15),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedText,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = V19PurpleSoftText,
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = V19SurfaceCard,
            ) {
                options.forEach { (id, text) ->
                    DropdownMenuItem(
                        text = { Text(text = text, color = V19InkPrimary) },
                        onClick = {
                            expanded = false
                            onOptionSelected(id)
                        },
                    )
                }
            }
        }
    }
}

private data class PickerCategoryGroup(
    val mainCategory: ProductCategory,
    val subcategories: List<ProductCategory>,
    val directProducts: List<Product>,
    val subProductsBySubId: Map<String, List<Product>>,
    val totalCount: Int,
)

private data class PickerCatalogStructure(
    val uncategorizedProducts: List<Product>,
    val categoryGroups: List<PickerCategoryGroup>,
)

@Composable
private fun SearchableProductPickerDialog(
    products: List<Product>,
    categories: List<ProductCategory> = emptyList(),
    historicalIds: Set<String> = emptySet(),
    activeCatalogMap: Map<String, Product> = emptyMap(),
    selectedQuantities: Map<String, Int>,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    onToggleProduct: (String) -> Unit,
    onIncrementQty: (String) -> Unit,
    onDecrementQty: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val normalizedQuery = remember(searchQuery) { TextNormalization.normalizeForSearch(searchQuery) }
    val isSearching = normalizedQuery.isNotEmpty()
    val filteredProducts = remember(products, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            products
        } else {
            products.filter { TextNormalization.normalizeForSearch(it.name).contains(normalizedQuery) }
        }
    }
    val pickerStructure = remember(filteredProducts, categories, isSearching) {
        if (categories.isEmpty()) {
            null
        } else {
            val mainCategories = categories.filter { it.parentId == null }.sortedBy { it.name }
            val mainCategoryIds = mainCategories.mapTo(HashSet(mainCategories.size)) { it.id }
            val subcategoriesByParent = categories
                .filter { it.parentId != null }
                .groupBy { it.parentId!! }
                .mapValues { (_, list) -> list.sortedBy { it.name } }

            val productsByMainCategory = HashMap<String, MutableList<Product>>(mainCategories.size)
            val uncategorizedProducts = ArrayList<Product>()
            filteredProducts.forEach { product ->
                val catId = product.categoryId
                if (catId == null || !mainCategoryIds.contains(catId)) {
                    uncategorizedProducts.add(product)
                } else {
                    productsByMainCategory.getOrPut(catId) { ArrayList() }.add(product)
                }
            }

            val groups = mainCategories.mapNotNull { mainCat ->
                val productsInMain = productsByMainCategory[mainCat.id].orEmpty()
                if (productsInMain.isEmpty()) {
                    return@mapNotNull null
                }
                val allSubcategories = subcategoriesByParent[mainCat.id].orEmpty()
                val validSubIds = allSubcategories.mapTo(HashSet(allSubcategories.size)) { it.id }
                val directProducts = ArrayList<Product>()
                val subProductsMap = HashMap<String, MutableList<Product>>(allSubcategories.size)
                productsInMain.forEach { product ->
                    val subId = product.subcategoryId
                    if (subId == null || !validSubIds.contains(subId)) {
                        directProducts.add(product)
                    } else {
                        subProductsMap.getOrPut(subId) { ArrayList() }.add(product)
                    }
                }
                val visibleSubcategories = if (isSearching) {
                    allSubcategories.filter { sub -> !subProductsMap[sub.id].isNullOrEmpty() }
                } else {
                    allSubcategories
                }
                val subProductsBySubId = visibleSubcategories.associate { sub ->
                    sub.id to (subProductsMap[sub.id]?.toList() ?: emptyList())
                }
                val totalCount = productsInMain.size
                PickerCategoryGroup(
                    mainCategory = mainCat,
                    subcategories = visibleSubcategories,
                    directProducts = directProducts,
                    subProductsBySubId = subProductsBySubId,
                    totalCount = totalCount,
                )
            }
            PickerCatalogStructure(
                uncategorizedProducts = uncategorizedProducts,
                categoryGroups = groups,
            )
        }
    }

    val distinctSelectedCount = selectedQuantities.count { it.value > 0 }
    val totalUnitsCount = selectedQuantities.values.sumOf { it.coerceAtLeast(0) }
    val subtotalCents = MixVendasShellViewModel.computeSelectedSubtotalCents(selectedQuantities, products)
    val dialogShape = RoundedCornerShape(26.dp)

    var expandedCategoryIds by remember { mutableStateOf(setOf<String>()) }
    var expandedSubcategoryIds by remember { mutableStateOf(setOf<String>()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 580.dp)
                    .fillMaxHeight(0.94f)
                    .shadow(
                        elevation = 18.dp,
                        shape = dialogShape,
                        ambientColor = HomeCtaShadowGlow.copy(alpha = 0.50f),
                        spotColor = HomeCtaShadowGlow.copy(alpha = 0.65f),
                    )
                    .testTag("product_picker_dialog"),
                shape = dialogShape,
                color = Color.Transparent,
                border = BorderStroke(1.4.dp, HomeCardNeonBorder.copy(alpha = 0.78f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xF5271044), Color(0xF7140724)),
                            ),
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.product_picker_title),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                lineHeight = 25.sp,
                                letterSpacing = (-0.3).sp,
                                color = Color.White,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = stringResource(R.string.product_picker_subtitle),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = HomeCardLabelInk.copy(alpha = 0.88f),
                            )
                        }
                        Surface(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("product_picker_close_x_button"),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xD92B124D),
                            border = BorderStroke(1.1.dp, HomeCardNeonBorder.copy(alpha = 0.65f)),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.product_picker_close_cd),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
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
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchChanged("") },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("product_picker_clear_search"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.product_picker_clear_search_cd),
                                        tint = V19PurpleSoftText,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.product_picker_search_placeholder),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 14.sp,
                                color = HomeCardLabelInk.copy(alpha = 0.70f),
                            )
                        },
                        colors = namesDarkInputColors(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("product_picker_search"),
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("product_picker_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        if (filteredProducts.isEmpty()) {
                            item(key = "picker_empty_state_item") {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    color = GlassInnerBoxBg,
                                    border = BorderStroke(1.dp, GlassNeonBorder15),
                                ) {
                                    Text(
                                        text = if (products.isEmpty()) {
                                            stringResource(R.string.product_picker_empty_catalog)
                                        } else {
                                            stringResource(R.string.product_picker_empty_search)
                                        },
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = V19PurpleSoftText,
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp, vertical = 20.dp)
                                            .testTag("picker_empty_state"),
                                    )
                                }
                            }
                        } else if (pickerStructure == null) {
                            items(filteredProducts, key = { it.id }) { product ->
                                val qty = selectedQuantities[product.id] ?: 0
                                val isHist = historicalIds.contains(product.id)
                                val catalogNow = activeCatalogMap[product.id]
                                val showHistoricalBadge = isHist && (
                                    catalogNow == null ||
                                        catalogNow.name != product.name ||
                                        catalogNow.price != product.price ||
                                        catalogNow.participants != product.participants
                                    )
                                VenderProductPickerRow(
                                    product = product,
                                    quantity = qty,
                                    maxQuantity = SaleFinancialEngine.MAX_ITEM_QUANTITY,
                                    showHistoricalBadge = showHistoricalBadge,
                                    onToggle = { onToggleProduct(product.id) },
                                    onDecrement = { onDecrementQty(product.id) },
                                    onIncrement = { onIncrementQty(product.id) },
                                )
                            }
                        } else {
                            // Section 1: Sem Categoria
                            if (pickerStructure.uncategorizedProducts.isNotEmpty()) {
                                val isUncatExpanded = "uncategorized" in expandedCategoryIds || isSearching
                                item(key = "picker_cat_header_uncategorized") {
                                    PickerCategoryHeader(
                                        categoryName = "Sem categoria",
                                        productCount = pickerStructure.uncategorizedProducts.size,
                                        isExpanded = isUncatExpanded,
                                        onToggleExpand = {
                                            expandedCategoryIds = if (isUncatExpanded && !isSearching) {
                                                expandedCategoryIds - "uncategorized"
                                            } else {
                                                expandedCategoryIds + "uncategorized"
                                            }
                                        },
                                        testTag = "picker_category_header_uncategorized",
                                    )
                                }
                                if (isUncatExpanded) {
                                    items(pickerStructure.uncategorizedProducts, key = { it.id }) { product ->
                                        val qty = selectedQuantities[product.id] ?: 0
                                        val isHist = historicalIds.contains(product.id)
                                        val catalogNow = activeCatalogMap[product.id]
                                        val showHistoricalBadge = isHist && (
                                            catalogNow == null ||
                                                catalogNow.name != product.name ||
                                                catalogNow.price != product.price ||
                                                catalogNow.participants != product.participants
                                            )
                                        VenderProductPickerRow(
                                            product = product,
                                            quantity = qty,
                                            maxQuantity = SaleFinancialEngine.MAX_ITEM_QUANTITY,
                                            showHistoricalBadge = showHistoricalBadge,
                                            onToggle = { onToggleProduct(product.id) },
                                            onDecrement = { onDecrementQty(product.id) },
                                            onIncrement = { onIncrementQty(product.id) },
                                        )
                                    }
                                }
                            }

                            // Section 2: Main Categories & Subcategories (with safe fallback for orphaned subcategoryId)
                            pickerStructure.categoryGroups.forEach { group ->
                                val mainCat = group.mainCategory
                                val isCatExpanded = mainCat.id in expandedCategoryIds || isSearching
                                item(key = "picker_cat_header_${mainCat.id}") {
                                    PickerCategoryHeader(
                                        categoryName = mainCat.name,
                                        productCount = group.totalCount,
                                        isExpanded = isCatExpanded,
                                        onToggleExpand = {
                                            expandedCategoryIds = if (isCatExpanded && !isSearching) {
                                                expandedCategoryIds - mainCat.id
                                            } else {
                                                expandedCategoryIds + mainCat.id
                                            }
                                        },
                                        testTag = "picker_category_header_${mainCat.id}",
                                    )
                                }

                                if (isCatExpanded) {
                                    if (group.directProducts.isNotEmpty()) {
                                        items(group.directProducts, key = { "direct_${it.id}" }) { product ->
                                            val qty = selectedQuantities[product.id] ?: 0
                                            val isHist = historicalIds.contains(product.id)
                                            val catalogNow = activeCatalogMap[product.id]
                                            val showHistoricalBadge = isHist && (
                                                catalogNow == null ||
                                                    catalogNow.name != product.name ||
                                                    catalogNow.price != product.price ||
                                                    catalogNow.participants != product.participants
                                                )
                                            VenderProductPickerRow(
                                                product = product,
                                                quantity = qty,
                                                maxQuantity = SaleFinancialEngine.MAX_ITEM_QUANTITY,
                                                showHistoricalBadge = showHistoricalBadge,
                                                onToggle = { onToggleProduct(product.id) },
                                                onDecrement = { onDecrementQty(product.id) },
                                                onIncrement = { onIncrementQty(product.id) },
                                            )
                                        }
                                    }

                                    group.subcategories.forEach { subCat ->
                                        val subProds = group.subProductsBySubId[subCat.id].orEmpty()
                                        val isSubExpanded = subCat.id in expandedSubcategoryIds || isSearching
                                        item(key = "picker_subcat_header_${subCat.id}") {
                                            PickerSubcategoryHeader(
                                                subcategoryName = subCat.name,
                                                productCount = subProds.size,
                                                isExpanded = isSubExpanded,
                                                onToggleExpand = {
                                                    expandedSubcategoryIds = if (isSubExpanded && !isSearching) {
                                                        expandedSubcategoryIds - subCat.id
                                                    } else {
                                                        expandedSubcategoryIds + subCat.id
                                                    }
                                                },
                                                testTag = "picker_subcategory_header_${subCat.id}",
                                            )
                                        }
                                        if (isSubExpanded && subProds.isNotEmpty()) {
                                            items(subProds, key = { "sub_${it.id}" }) { product ->
                                                val qty = selectedQuantities[product.id] ?: 0
                                                val isHist = historicalIds.contains(product.id)
                                                val catalogNow = activeCatalogMap[product.id]
                                                val showHistoricalBadge = isHist && (
                                                    catalogNow == null ||
                                                        catalogNow.name != product.name ||
                                                        catalogNow.price != product.price ||
                                                        catalogNow.participants != product.participants
                                                    )
                                                VenderProductPickerRow(
                                                    product = product,
                                                    quantity = qty,
                                                    maxQuantity = SaleFinancialEngine.MAX_ITEM_QUANTITY,
                                                    showHistoricalBadge = showHistoricalBadge,
                                                    onToggle = { onToggleProduct(product.id) },
                                                    onDecrement = { onDecrementQty(product.id) },
                                                    onIncrement = { onIncrementQty(product.id) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    VenderProductPickerFooter(
                        distinctProductsCount = distinctSelectedCount,
                        totalUnitsCount = totalUnitsCount,
                        subtotalCents = subtotalCents,
                        onDoneClick = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchableMachinePickerDialog(
    method: PreviewPaymentMethod,
    machines: List<Machine>,
    hasAnyRegisteredMachines: Boolean = machines.isNotEmpty(),
    historicalOnlyMachineId: String? = null,
    selectedMachineId: String?,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    onSelectMachine: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val normalizedQuery = remember(searchQuery, ptBr) {
        normalizeMachineSearchText(searchQuery, ptBr)
    }
    val filteredMachines = remember(machines, normalizedQuery, ptBr) {
        machines.filter { machine ->
            normalizedQuery.isEmpty() ||
                normalizeMachineSearchText(machine.name, ptBr).contains(normalizedQuery)
        }
    }
    val dialogShape = RoundedCornerShape(26.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 580.dp)
                .fillMaxHeight(0.86f)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .shadow(
                    elevation = 18.dp,
                    shape = dialogShape,
                    ambientColor = HomeCtaShadowGlow.copy(alpha = 0.50f),
                    spotColor = HomeCtaShadowGlow.copy(alpha = 0.65f),
                )
                .testTag("machine_picker_dialog"),
            shape = dialogShape,
            color = Color.Transparent,
            border = BorderStroke(1.4.dp, HomeCardNeonBorder.copy(alpha = 0.78f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xF5271044), Color(0xF7140724)),
                        ),
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.machine_picker_title),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            lineHeight = 25.sp,
                            letterSpacing = (-0.3).sp,
                            color = Color.White,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = if (method == PreviewPaymentMethod.CREDIT) {
                                stringResource(R.string.machine_picker_subtitle_credit)
                            } else {
                                stringResource(R.string.machine_picker_subtitle_debit)
                            },
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = HomeCardLabelInk.copy(alpha = 0.88f),
                        )
                    }
                    Surface(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_machine_picker_button"),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xD92B124D),
                        border = BorderStroke(1.1.dp, HomeCardNeonBorder.copy(alpha = 0.65f)),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.machine_picker_close_cd),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
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
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchChanged("") },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("machine_picker_clear_search"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.machine_picker_clear_search_cd),
                                    tint = V19PurpleSoftText,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.machine_picker_search_placeholder),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 14.sp,
                            color = HomeCardLabelInk.copy(alpha = 0.70f),
                        )
                    },
                    colors = namesDarkInputColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("machine_picker_search"),
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("machine_picker_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    if (filteredMachines.isEmpty()) {
                        item {
                            val emptyMessage = when {
                                machines.isEmpty() && !hasAnyRegisteredMachines ->
                                    stringResource(R.string.machine_picker_empty_catalog)
                                machines.isEmpty() && method == PreviewPaymentMethod.CREDIT ->
                                    stringResource(R.string.machine_picker_empty_credit)
                                machines.isEmpty() ->
                                    stringResource(R.string.machine_picker_empty_catalog)
                                else ->
                                    stringResource(R.string.machine_picker_empty_search)
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = GlassInnerBoxBg,
                                border = BorderStroke(1.dp, GlassNeonBorder15),
                            ) {
                                Text(
                                    text = emptyMessage,
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = V19PurpleSoftText,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 20.dp)
                                        .testTag("machine_picker_empty_state"),
                                )
                            }
                        }
                    } else {
                        items(filteredMachines, key = { it.id }) { machine ->
                            VenderMachinePickerRow(
                                machine = machine,
                                method = method,
                                selected = machine.id == selectedMachineId,
                                showHistoricalBadge = historicalOnlyMachineId == machine.id,
                                onClick = { onSelectMachine(machine.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
