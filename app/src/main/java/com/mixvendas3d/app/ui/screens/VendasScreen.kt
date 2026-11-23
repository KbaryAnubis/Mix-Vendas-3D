package com.mixvendas3d.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.PreviewPaymentMethod
import com.mixvendas3d.app.domain.models.PreviewPeriodFilter
import com.mixvendas3d.app.ui.components.Mv3dIcon
import com.mixvendas3d.app.ui.components.VendasDiscreetSummaryCard
import com.mixvendas3d.app.ui.components.VendasEmptyStateCard
import com.mixvendas3d.app.ui.components.VendasFilterDropdownBar
import com.mixvendas3d.app.ui.components.VendasFilterDropdownOption
import com.mixvendas3d.app.ui.components.VendasFilterEmptyStateCard
import com.mixvendas3d.app.ui.components.VendasSaleHeroCard
import com.mixvendas3d.app.ui.components.namesDarkInputColors
import com.mixvendas3d.app.ui.theme.PlusJakartaSansFontFamily
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import com.mixvendas3d.app.ui.theme.V19GoldAccent
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19OutlineInput
import com.mixvendas3d.app.ui.theme.V19OutlineSubtle
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SurfaceCard
import com.mixvendas3d.app.ui.theme.V19SurfaceChip
import com.mixvendas3d.app.ui.theme.V19SurfaceInput
import com.mixvendas3d.app.util.CurrencyFormatter
import java.time.format.DateTimeFormatter

@Composable
fun VendasScreen(
    uiState: MixVendasShellUiState,
    onPeriodSelected: (PreviewPeriodFilter) -> Unit,
    onCustomDatesChanged: (String, String) -> Unit,
    onSearchChanged: (String) -> Unit,
    onMethodFilterSelected: (PreviewPaymentMethod?) -> Unit,
    onClearSelectedDay: () -> Unit = {},
    onGoToSell: () -> Unit = {},
    onEditSale: (String) -> Unit = {},
    onRequestDeleteSale: (String) -> Unit = {},
    onDismissDeleteDialog: () -> Unit = {},
    onConfirmDeleteSale: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val deleteState = uiState.saleDeleteState

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_vendas"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "vendas_top_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 18.dp, bottom = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sales_title),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        letterSpacing = (-0.5).sp,
                        color = Color.White,
                        modifier = Modifier
                            .semantics { heading() }
                            .testTag("vendas_header_title"),
                    )
                }

                val salesFeedback = uiState.salesFeedbackMessage
                if (!salesFeedback.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .testTag("vendas_feedback_banner"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (uiState.salesFeedbackIsError) {
                            Color(0xE64B1528)
                        } else {
                            Color(0xE6103E2E)
                        },
                        border = BorderStroke(
                            width = 1.2.dp,
                            color = if (uiState.salesFeedbackIsError) V19ErrorSoft else Color(0xFF66E2B3),
                        ),
                    ) {
                        Text(
                            text = salesFeedback,
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (uiState.salesFeedbackIsError) V19ErrorSoft else Color(0xFFBAF7DE),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }

                val selectedDate = uiState.selectedSalesDate
                if (selectedDate != null) {
                    val dayFormatted = remember(selectedDate) {
                        selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vendas_selected_day_banner"),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xE828174E),
                        border = BorderStroke(1.2.dp, V19GoldAccent.copy(alpha = 0.70f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                    text = stringResource(R.string.sales_day_filter_prefix, dayFormatted),
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                )
                                Text(
                                    text = stringResource(R.string.sales_day_filter_subtitle),
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 11.sp,
                                    color = V19InkMuted,
                                )
                            }
                            OutlinedButton(
                                onClick = onClearSelectedDay,
                                border = BorderStroke(1.dp, V19GoldAccent.copy(alpha = 0.65f)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("vendas_clear_day_filter_button"),
                            ) {
                                Text(
                                    text = stringResource(R.string.sales_day_filter_clear),
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = V19GoldAccent,
                                )
                            }
                        }
                    }
                }

                // Três controles verticais amplos no topo do conteúdo
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sales_filters_card"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Filtro 1 — Período
                    VendasFilterDropdownBar(
                        selectedLabel = uiState.periodFilter.label,
                        testTag = "period_dropdown_button",
                        leadingIconRes = R.drawable.mv3d_calendario,
                    ) { dismiss ->
                        PreviewPeriodFilter.entries.forEach { period ->
                            VendasFilterDropdownOption(
                                label = period.label,
                                selected = period == uiState.periodFilter,
                                onClick = {
                                    dismiss()
                                    onPeriodSelected(period)
                                },
                                modifier = Modifier.testTag("period_option_${period.name.lowercase()}"),
                            )
                        }
                    }

                    if (uiState.periodFilter == PreviewPeriodFilter.CUSTOM) {
                        com.mixvendas3d.app.ui.components.CustomDateRangeFilterFields(
                            fromDate = uiState.periodFromDate,
                            toDate = uiState.periodToDate,
                            onCustomDatesChanged = onCustomDatesChanged,
                        )
                    }

                    // Filtro 2 — Pesquisa
                    OutlinedTextField(
                        value = uiState.salesSearchQuery,
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
                        trailingIcon = if (uiState.salesSearchQuery.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { onSearchChanged("") },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.sales_search_clear_cd),
                                        tint = V19PurpleSoftText,
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.sales_search_placeholder),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 15.sp,
                                color = V19InkMuted,
                            )
                        },
                        colors = namesDarkInputColors(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sales_search_input"),
                    )

                    // Filtro 3 — Forma de pagamento
                    VendasFilterDropdownBar(
                        selectedLabel = uiState.salesMethodFilter?.label
                            ?: stringResource(R.string.sales_filter_all_methods),
                        testTag = "sales_method_filter_dropdown",
                        leadingIconRes = R.drawable.mv3d_dinheiro_moedas,
                    ) { dismiss ->
                        VendasFilterDropdownOption(
                            label = stringResource(R.string.sales_filter_all_methods),
                            selected = uiState.salesMethodFilter == null,
                            onClick = {
                                dismiss()
                                onMethodFilterSelected(null)
                            },
                        )
                        PreviewPaymentMethod.entries.forEach { method ->
                            VendasFilterDropdownOption(
                                label = method.label,
                                selected = uiState.salesMethodFilter == method,
                                onClick = {
                                    dismiss()
                                    onMethodFilterSelected(method)
                                },
                            )
                        }
                    }
                }

                // Resumo discreto do período filtrado (sem barra proporcional e sem soma verde redundante)
                VendasDiscreetSummaryCard(
                    salesCount = uiState.filteredRealSales.size,
                    totalGross = uiState.realTotalGross,
                    totalDiscount = uiState.realTotalDiscount,
                    totalFee = uiState.realTotalFee,
                    totalNet = uiState.realTotalNet,
                    personSummaries = uiState.realPersonSummaries,
                )
            }
        }

        if (uiState.realSalesCount == 0) {
            item(key = "vendas_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                ) {
                    VendasEmptyStateCard(onGoToSell = onGoToSell)
                }
            }
        } else if (uiState.filteredRealSales.isEmpty()) {
            item(key = "vendas_filter_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                ) {
                    VendasFilterEmptyStateCard(
                        hasSelectedDay = uiState.selectedSalesDate != null,
                        onClearSelectedDay = onClearSelectedDay,
                    )
                }
            }
        } else {
            items(uiState.filteredRealSales, key = { it.id }) { sale ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                ) {
                    VendasSaleHeroCard(
                        sale = sale,
                        deleteEnabled = !deleteState.isDeleting,
                        onEditClick = { onEditSale(sale.id) },
                        onDeleteClick = { onRequestDeleteSale(sale.id) },
                    )
                }
            }
        }
    }

    val targetSale = deleteState.targetSale
    if (targetSale != null) {
        DeleteSaleConfirmationDialog(
            sale = targetSale,
            isDeleting = deleteState.isDeleting,
            errorMessage = deleteState.errorMessage,
            onDismiss = onDismissDeleteDialog,
            onConfirm = onConfirmDeleteSale,
        )
    }
}

@Composable
private fun DeleteSaleConfirmationDialog(
    sale: PersistedSale,
    isDeleting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val calc = sale.calculation
    val dateTimeText = remember(sale.soldAtEpochMs) {
        CurrencyFormatter.formatEpochMillis(sale.soldAtEpochMs)
    }
    val productCount = calc.items.size
    val totalUnits = calc.items.sumOf { it.quantity }
    val itemsSummaryText = remember(productCount, totalUnits) {
        "$productCount ${if (productCount == 1) "produto" else "produtos"} · " +
            "$totalUnits ${if (totalUnits == 1) "item" else "itens"}"
    }

    AlertDialog(
        onDismissRequest = {
            if (!isDeleting) {
                onDismiss()
            }
        },
        containerColor = V19SurfaceCard,
        titleContentColor = Color.White,
        textContentColor = V19InkPrimary,
        modifier = Modifier.testTag("delete_sale_dialog"),
        icon = {
            Mv3dIcon(
                iconRes = R.drawable.mv3d_excluir,
                contentDescription = null,
                size = 48.dp,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.sales_delete_dialog_title),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.testTag("delete_sale_dialog_title"),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag("delete_sale_dialog_info"),
                    shape = RoundedCornerShape(12.dp),
                    color = V19SurfaceInput.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, V19OutlineSubtle),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RowKeyValue("Data e hora", dateTimeText)
                        RowKeyValue(
                            "Valor final cobrado",
                            CurrencyFormatter.formatBrl(calc.finalAmount),
                            V19GoldAccent,
                        )
                        RowKeyValue("Quantidade", itemsSummaryText)
                    }
                }

                Text(
                    text = stringResource(R.string.sales_delete_dialog_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = V19InkPrimary,
                    modifier = Modifier.testTag("delete_sale_dialog_message"),
                )

                if (isDeleting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_sale_progress"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = V19PurpleSoftText,
                        )
                        Text(
                            text = "Excluindo venda...",
                            style = MaterialTheme.typography.bodySmall,
                            color = V19PurpleSoftText,
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .testTag("delete_sale_error_banner"),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF4B1528).copy(alpha = 0.90f),
                        border = BorderStroke(1.dp, V19ErrorSoft),
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = V19ErrorSoft,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isDeleting,
                border = BorderStroke(1.dp, V19OutlineInput),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("delete_sale_cancel_button"),
            ) {
                Text(
                    text = stringResource(R.string.sales_delete_cancel_action),
                    color = V19PurpleSoftText,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9E1F3A),
                    contentColor = Color.White,
                    disabledContainerColor = V19SurfaceChip,
                    disabledContentColor = V19InkMuted,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("delete_sale_confirm_button"),
            ) {
                Text(
                    text = if (isDeleting) {
                        stringResource(R.string.sales_delete_in_progress)
                    } else {
                        stringResource(R.string.sales_delete_confirm_action)
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    )
}

@Composable
private fun RowKeyValue(
    label: String,
    value: String,
    valueColor: Color = Color.White,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = V19InkMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}
