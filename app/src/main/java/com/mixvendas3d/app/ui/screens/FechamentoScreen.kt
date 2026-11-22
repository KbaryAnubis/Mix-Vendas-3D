package com.mixvendas3d.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.models.PreviewPeriodFilter
import com.mixvendas3d.app.ui.components.FechamentoDailySalesCard
import com.mixvendas3d.app.ui.components.FechamentoDailySalesCard
import com.mixvendas3d.app.ui.components.FechamentoExportCsvButton
import com.mixvendas3d.app.ui.components.FechamentoHeroSummaryCard
import com.mixvendas3d.app.ui.components.FechamentoIndividualConferenceCard
import com.mixvendas3d.app.ui.components.FechamentoMachinesCard
import com.mixvendas3d.app.ui.components.FechamentoPaymentsCard
import com.mixvendas3d.app.ui.components.FechamentoPeopleGridSection
import com.mixvendas3d.app.ui.components.FechamentoPeriodSelector
import com.mixvendas3d.app.ui.theme.PlusJakartaSansFontFamily
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import com.mixvendas3d.app.ui.theme.V19GoldAccent
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19PurpleGlow
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import java.io.IOException

@Composable
fun FechamentoScreen(
    uiState: MixVendasShellUiState,
    onPeriodSelected: (PreviewPeriodFilter) -> Unit,
    onCustomDatesChanged: (String, String) -> Unit,
    onExportCsvClick: () -> Unit = {},
    onSelectCsvExportMode: (CsvExportMode) -> Unit = {},
    onDismissCsvExportDialog: () -> Unit = {},
    onConfirmCsvExport: () -> Unit = {},
    onCsvDocumentDestinationSelected: (Uri?, suspend (Uri, ByteArray) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var machinesExpanded by rememberSaveable { mutableStateOf(true) }
    val csvState = uiState.closingCsvExportState
    val appContext = LocalContext.current.applicationContext

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { selectedUri ->
        onCsvDocumentDestinationSelected(selectedUri) { uri, bytes ->
            val stream = appContext.contentResolver.openOutputStream(uri)
                ?: throw IOException("Não foi possível abrir o destino selecionado para escrita.")
            stream.use { out ->
                out.write(bytes)
                out.flush()
            }
        }
    }

    var launchedRequestToken by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingRequest = csvState.pendingDocumentRequest
    LaunchedEffect(pendingRequest?.requestToken) {
        if (pendingRequest != null && launchedRequestToken != pendingRequest.requestToken) {
            launchedRequestToken = pendingRequest.requestToken
            createDocumentLauncher.launch(pendingRequest.suggestedFileName)
        }
    }

    if (csvState.dialogVisible) {
        ClosingCsvExportDialog(
            selectedMode = csvState.selectedMode,
            isPreparing = csvState.isPreparing,
            onModeSelected = onSelectCsvExportMode,
            onDismiss = onDismissCsvExportDialog,
            onConfirm = onConfirmCsvExport,
        )
    }

    val emptyMessage = if (uiState.closingRealTotalSalesCount == 0) {
        "Nenhuma venda registrada ainda."
    } else {
        "Nenhuma venda registrada neste período."
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_fechamento"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "fechamento_content") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Cabeçalho limpo da aba Fechamento
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 18.dp, bottom = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.closing_title),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        letterSpacing = (-0.5).sp,
                        color = Color.White,
                        modifier = Modifier
                            .semantics { heading() }
                            .testTag("closing_header_title"),
                    )
                }

                // Seletor de período independente da aba Fechamento
                FechamentoPeriodSelector(
                    selectedPeriod = uiState.closingPeriodFilter,
                    fromDate = uiState.closingPeriodFromDate,
                    toDate = uiState.closingPeriodToDate,
                    onPeriodSelected = onPeriodSelected,
                    onCustomDatesChanged = onCustomDatesChanged,
                )

                // Resumo financeiro real do período (subtotal, descontos, bruto cobrado, tarifas e líquido)
                FechamentoHeroSummaryCard(
                    salesCount = uiState.closingFilteredRealSalesCount,
                    totalGross = uiState.closingRealTotalGross,
                    totalFee = uiState.closingRealTotalFee,
                    totalNet = uiState.closingRealTotalNet,
                    originalSubtotal = uiState.closingRealOriginalSubtotal,
                    discountTotal = uiState.closingRealTotalDiscount,
                )

                // Quanto cada pessoa recebe — grade responsiva sem limite de 2 participantes
                FechamentoPeopleGridSection(
                    personSummaries = uiState.closingRealPersonSummaries,
                    emptyMessage = emptyMessage,
                )

                // Conferência individual detalhada (sem barra colorida DynamicSplitProgressBar)
                FechamentoIndividualConferenceCard(
                    personSummaries = uiState.closingRealPersonSummaries,
                    emptyMessage = emptyMessage,
                )

                // Por forma de pagamento
                FechamentoPaymentsCard(
                    salesCount = uiState.closingFilteredRealSalesCount,
                    paymentBreakdowns = uiState.closingRealPaymentBreakdowns,
                    emptyMessage = emptyMessage,
                )

                // Maquininhas e tarifas expansível (estado salvo em rememberSaveable)
                FechamentoMachinesCard(
                    salesCount = uiState.closingFilteredRealSalesCount,
                    machineBreakdowns = uiState.closingRealMachineBreakdowns,
                    emptyMessage = emptyMessage,
                    expanded = machinesExpanded,
                    onToggleExpanded = { machinesExpanded = !machinesExpanded },
                )

                // Resumo por dia e agrupamento mensal pela data real da venda (sold_at_epoch_ms)
                FechamentoDailySalesCard(
                    report = uiState.closingDailyReport,
                    emptyMessage = emptyMessage,
                )

                // Resumo por dia e agrupamento mensal pela data real da venda (sold_at_epoch_ms)
                FechamentoDailySalesCard(
                    report = uiState.closingDailyReport,
                    emptyMessage = emptyMessage,
                )

                // Exportação CSV real via SAF (sem botão Imprimir ou rota fictícia)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("closing_csv_export_section"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (csvState.isPreparing || csvState.isWriting) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {}
                                .testTag("closing_csv_export_progress"),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF28174E).copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.65f)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = V19PurpleSoftText,
                                )
                                Text(
                                    text = stringResource(R.string.closing_export_csv_in_progress),
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = V19InkPrimary,
                                )
                            }
                        }
                    }

                    val csvFeedback = csvState.feedbackMessage
                    if (!csvFeedback.isNullOrBlank()) {
                        val containerColor = when {
                            csvState.feedbackIsError -> Color(0xFF4B1528).copy(alpha = 0.90f)
                            csvState.feedbackIsPartial -> Color(0xFF3E2D12).copy(alpha = 0.92f)
                            csvFeedback.startsWith("CSV exportado com sucesso.") -> Color(0xFF103E2E).copy(alpha = 0.90f)
                            else -> Color(0xFF28174E).copy(alpha = 0.92f)
                        }
                        val borderColor = when {
                            csvState.feedbackIsError -> V19ErrorSoft
                            csvState.feedbackIsPartial -> V19GoldAccent
                            csvFeedback.startsWith("CSV exportado com sucesso.") -> Color(0xFF66E2B3)
                            else -> V19PurpleGlow
                        }
                        val textColor = when {
                            csvState.feedbackIsError -> V19ErrorSoft
                            csvState.feedbackIsPartial -> V19GoldAccent
                            csvFeedback.startsWith("CSV exportado com sucesso.") -> Color(0xFFBAF7DE)
                            else -> V19InkPrimary
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {}
                                .testTag("closing_csv_feedback_banner"),
                            shape = RoundedCornerShape(14.dp),
                            color = containerColor,
                            border = BorderStroke(width = 1.dp, color = borderColor),
                        ) {
                            Text(
                                text = csvFeedback,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = textColor,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    FechamentoExportCsvButton(
                        enabled = !csvState.isBusy,
                        isBusy = csvState.isPreparing || csvState.isWriting,
                        onClick = onExportCsvClick,
                    )

                    Text(
                        text = stringResource(R.string.closing_export_csv_note),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.sp,
                        color = V19InkMuted,
                    )
                }
            }
        }
    }
}
