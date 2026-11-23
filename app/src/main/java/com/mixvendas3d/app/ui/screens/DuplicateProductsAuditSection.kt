package com.mixvendas3d.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mixvendas3d.app.domain.calculation.AuditedProductEntry
import com.mixvendas3d.app.domain.calculation.ExactDuplicateGroup
import com.mixvendas3d.app.domain.calculation.ProductDuplicateAuditReport
import com.mixvendas3d.app.domain.calculation.SimilarProductGroup
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.ui.theme.GlassInnerBoxBg
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeCardGlassBottom
import com.mixvendas3d.app.ui.theme.HomeCardGlassTop
import com.mixvendas3d.app.ui.theme.HomeCardLabelInk
import com.mixvendas3d.app.ui.theme.HomeCardNeonBorder
import com.mixvendas3d.app.ui.theme.HomeCardSubtleDivider
import com.mixvendas3d.app.ui.theme.PlusJakartaSansFontFamily
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import com.mixvendas3d.app.ui.theme.V19GoldAccent
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19MintSuccess
import com.mixvendas3d.app.ui.theme.V19OutlineInput
import com.mixvendas3d.app.ui.theme.V19PurplePrimary
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SurfaceChip
import com.mixvendas3d.app.util.CurrencyFormatter

@Composable
internal fun DuplicateProductsAuditSectionCard(
    auditState: DuplicateProductsAuditUiState,
    onVerifyClick: () -> Unit,
    onOpenResultsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UpdateSystemGlassContainer(
        modifier = modifier.testTag("duplicate_products_audit_section"),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = V19SurfaceChip,
                    border = BorderStroke(1.2.dp, HomeCardNeonBorder.copy(alpha = 0.60f)),
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.FindInPage,
                            contentDescription = "Auditoria de produtos duplicados",
                            tint = V19PurpleSoftText,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = V19SurfaceChip,
                        border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.45f)),
                    ) {
                        Text(
                            text = "AUDITORIA DO CATÁLOGO",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            color = V19PurpleSoftText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        text = "Produtos duplicados e semelhantes",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        color = V19InkPrimary,
                    )
                }
            }

            Text(
                text = "Analisa todos os produtos cadastrados (ativos e inativos) para localizar duplicados exatos e produtos com mesmo nome e valor que merecem revisão. Nenhum produto é excluído automaticamente.",
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = HomeCardLabelInk.copy(alpha = 0.90f),
            )

            // Botão principal: VERIFICAR PRODUTOS DUPLICADOS
            val buttonEnabled = !auditState.isBusy
            val buttonShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(buttonShape)
                    .background(
                        Brush.horizontalGradient(
                            if (buttonEnabled) {
                                listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
                            } else {
                                listOf(Color(0x664F46E5), Color(0x667C3AED))
                            },
                        ),
                    )
                    .border(
                        1.2.dp,
                        if (buttonEnabled) Color(0xFFA5B4FC).copy(alpha = 0.85f) else GlassNeonBorder15,
                        buttonShape,
                    )
                    .clickable(
                        enabled = buttonEnabled,
                        role = Role.Button,
                        onClick = onVerifyClick,
                    )
                    .testTag("verify_duplicate_products_button")
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (auditState.isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("duplicate_audit_progress_indicator"),
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.FindInPage,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = if (auditState.isAnalyzing) {
                            "ANALISANDO CATÁLOGO..."
                        } else {
                            "VERIFICAR PRODUTOS DUPLICADOS"
                        },
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        color = if (buttonEnabled) Color.White else Color.White.copy(alpha = 0.72f),
                    )
                }
            }

            auditState.errorMessage?.let { err ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("duplicate_audit_error_banner"),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xD94B1528),
                    border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.80f)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = V19ErrorSoft,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = err,
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.5.sp,
                            color = V19ErrorSoft,
                        )
                    }
                }
            }

            auditState.report?.let { report ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("duplicate_audit_inline_summary"),
                    shape = RoundedCornerShape(14.dp),
                    color = GlassInnerBoxBg,
                    border = BorderStroke(
                        1.dp,
                        if (report.hasFindings) V19GoldAccent.copy(alpha = 0.55f) else V19MintSuccess.copy(alpha = 0.55f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (report.hasFindings) {
                                "Última verificação: ${report.totalProductsAnalyzed} produto(s) · ${report.exactDuplicateGroups.size} grupo(s) exatos · ${report.similarProductGroups.size} grupo(s) semelhantes"
                            } else {
                                "Última verificação: ${report.totalProductsAnalyzed} produto(s) analisados · nenhuma duplicidade encontrada."
                            },
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = if (report.hasFindings) V19GoldAccent else V19MintSuccess,
                        )
                        if (auditState.catalogChangedSinceAnalysis) {
                            Text(
                                text = "O catálogo foi alterado desde a última análise. Clique em \"VERIFICAR PRODUTOS DUPLICADOS\" para atualizar.",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 11.5.sp,
                                color = V19InkMuted,
                            )
                        }
                        if (!auditState.isResultsOpen) {
                            OutlinedButton(
                                onClick = onOpenResultsClick,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.65f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("reopen_duplicate_audit_results_button"),
                            ) {
                                Text(
                                    text = "VER RESULTADO DA ANÁLISE",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = V19PurpleSoftText,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DuplicateProductsAuditResultsDialog(
    auditState: DuplicateProductsAuditUiState,
    onReanalyze: () -> Unit,
    onRequestDelete: (AuditedProductEntry) -> Unit,
    onClose: () -> Unit,
) {
    val report = auditState.report ?: return

    Dialog(
        onDismissRequest = {
            if (!auditState.isDeleting) onClose()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 640.dp)
                .fillMaxHeight(0.90f)
                .testTag("duplicate_audit_results_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFA190C30),
            border = BorderStroke(1.25.dp, HomeCardNeonBorder.copy(alpha = 0.65f)),
            tonalElevation = 12.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Cabeçalho fixo da janela de resultados
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF28144E), Color(0xFF1E103D))),
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auditoria de produtos",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color.White,
                        )
                        Text(
                            text = "Atualizado em ${formatUpdateEpoch(report.analyzedAtEpochMs)}",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 11.5.sp,
                            color = V19InkMuted,
                        )
                    }
                    IconButton(
                        onClick = onReanalyze,
                        enabled = !auditState.isBusy,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("duplicate_audit_reanalyze_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reanalisar catálogo",
                            tint = V19PurpleSoftText,
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        enabled = !auditState.isDeleting,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("duplicate_audit_close_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fechar janela de resultados",
                            tint = Color.White,
                        )
                    }
                }

                HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.35f))

                // Conteúdo rolável de alta performance com LazyColumn
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("duplicate_audit_results_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "audit_summary_metrics") {
                        AuditSummaryMetricsBlock(report = report)
                    }

                    if (auditState.catalogChangedSinceAnalysis) {
                        item(key = "audit_stale_banner") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("duplicate_audit_stale_banner"),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xD93E280D),
                                border = BorderStroke(1.dp, V19GoldAccent.copy(alpha = 0.78f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.WarningAmber,
                                        contentDescription = null,
                                        tint = V19GoldAccent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = "O catálogo mudou após esta análise. Toque no ícone de atualizar para recalcular.",
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontSize = 12.sp,
                                        color = V19GoldAccent,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    auditState.feedbackMessage?.let { feedback ->
                        item(key = "audit_feedback_banner") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("duplicate_audit_feedback_banner"),
                                shape = RoundedCornerShape(14.dp),
                                color = if (auditState.feedbackIsError) Color(0xD94B1528) else Color(0xD90E3B2B),
                                border = BorderStroke(
                                    1.dp,
                                    if (auditState.feedbackIsError) V19ErrorSoft else Color(0xFF66E2B3),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = if (auditState.feedbackIsError) {
                                            Icons.Filled.ErrorOutline
                                        } else {
                                            Icons.Filled.CheckCircle
                                        },
                                        contentDescription = null,
                                        tint = if (auditState.feedbackIsError) V19ErrorSoft else Color(0xFFBAF7DE),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = feedback,
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontSize = 12.5.sp,
                                        color = if (auditState.feedbackIsError) V19ErrorSoft else Color(0xFFBAF7DE),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    if (!report.hasFindings) {
                        item(key = "audit_empty_state") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("duplicate_audit_empty_state"),
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xD90E3B2B),
                                border = BorderStroke(1.2.dp, Color(0xFF66E2B3).copy(alpha = 0.72f)),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Catálogo sem duplicidades",
                                        tint = V19MintSuccess,
                                        modifier = Modifier.size(34.dp),
                                    )
                                    Text(
                                        text = "Nenhum produto duplicado encontrado!",
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = Color.White,
                                    )
                                    Text(
                                        text = "Todos os ${report.totalProductsAnalyzed} produto(s) analisados estão livres de duplicidades exatas ou registros semelhantes com mesmo nome e valor.",
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.sp,
                                        color = Color(0xFFBAF7DE),
                                    )
                                }
                            }
                        }
                    }

                    if (report.exactDuplicateGroups.isNotEmpty()) {
                        item(key = "exact_duplicates_header") {
                            AuditSectionHeader(
                                title = "Duplicados exatos (${report.exactDuplicateGroups.size})",
                                subtitle = "Produtos idênticos em nome, preço total, categoria, subcategoria e divisão de participantes.",
                                accent = V19ErrorSoft,
                                testTag = "duplicate_audit_exact_section_header",
                            )
                        }
                        report.exactDuplicateGroups.forEach { group ->
                            item(key = "${group.groupId}_header") {
                                ExactDuplicateGroupHeaderCard(group = group)
                            }
                            items(
                                items = group.entries.mapIndexed { idx, entry -> idx to entry },
                                key = { (_, entry) -> "${group.groupId}_entry_${entry.product.id}" },
                            ) { (idx, entry) ->
                                AuditedProductEntryCard(
                                    entry = entry,
                                    copyLabel = "Registro ${idx + 1} de ${group.copyCount}",
                                    accentBorderColor = V19ErrorSoft.copy(alpha = 0.55f),
                                    isActionBusy = auditState.isBusy,
                                    onRequestDelete = { onRequestDelete(entry) },
                                )
                            }
                        }
                    }

                    if (report.similarProductGroups.isNotEmpty()) {
                        item(key = "similar_products_header") {
                            AuditSectionHeader(
                                title = "Produtos semelhantes que precisam de revisão (${report.similarProductGroups.size})",
                                subtitle = "Produtos com mesmo nome e preço total, mas com diferenças em categoria, subcategoria ou divisão.",
                                accent = V19GoldAccent,
                                testTag = "duplicate_audit_similar_section_header",
                            )
                        }
                        report.similarProductGroups.forEach { group ->
                            item(key = "${group.groupId}_header") {
                                SimilarProductGroupHeaderCard(group = group)
                            }
                            items(
                                items = group.entries.mapIndexed { idx, entry -> idx to entry },
                                key = { (_, entry) -> "${group.groupId}_entry_${entry.product.id}" },
                            ) { (idx, entry) ->
                                AuditedProductEntryCard(
                                    entry = entry,
                                    copyLabel = "Registro ${idx + 1} de ${group.entries.size}",
                                    accentBorderColor = V19GoldAccent.copy(alpha = 0.55f),
                                    isActionBusy = auditState.isBusy,
                                    onRequestDelete = { onRequestDelete(entry) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditSummaryMetricsBlock(report: ProductDuplicateAuditReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("duplicate_audit_metrics_block"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UpdateMetricTile(
                label = "Produtos analisados",
                count = report.totalProductsAnalyzed,
                accent = V19PurpleSoftText,
                testTag = "audit_metric_total_products",
                modifier = Modifier.weight(1f),
            )
            UpdateMetricTile(
                label = "Grupos exatos",
                count = report.exactDuplicateGroups.size,
                accent = if (report.exactDuplicateGroups.isNotEmpty()) V19ErrorSoft else V19MintSuccess,
                testTag = "audit_metric_exact_groups",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UpdateMetricTile(
                label = "Registros em duplicidade exata",
                count = report.exactDuplicateRecordsCount,
                accent = if (report.exactDuplicateRecordsCount > 0) V19ErrorSoft else V19MintSuccess,
                testTag = "audit_metric_exact_records",
                modifier = Modifier.weight(1f),
            )
            UpdateMetricTile(
                label = "Grupos semelhantes",
                count = report.similarProductGroups.size,
                accent = if (report.similarProductGroups.isNotEmpty()) V19GoldAccent else V19MintSuccess,
                testTag = "audit_metric_similar_groups",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AuditSectionHeader(
    title: String,
    subtitle: String,
    accent: Color,
    testTag: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = title,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.5.sp,
            color = accent,
        )
        Text(
            text = subtitle,
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = V19InkMuted,
        )
    }
}

@Composable
private fun ExactDuplicateGroupHeaderCard(
    group: ExactDuplicateGroup,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exact_duplicate_group_${group.groupId}"),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, V19ErrorSoft.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xE036132B), Color(0xDC200B1E))))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.displayName,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = Color.White,
                )
                Text(
                    text = CurrencyFormatter.formatBrl(MoneyCents(group.priceCents)),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = V19PurpleSoftText,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = V19ErrorSoft.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.65f)),
            ) {
                Text(
                    text = "${group.copyCount} cópias idênticas",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = V19ErrorSoft,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SimilarProductGroupHeaderCard(
    group: SimilarProductGroup,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("similar_product_group_${group.groupId}"),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, V19GoldAccent.copy(alpha = 0.65f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(HomeCardGlassTop, HomeCardGlassBottom)))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.displayName,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color.White,
                    )
                    Text(
                        text = CurrencyFormatter.formatBrl(MoneyCents(group.priceCents)),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = V19PurpleSoftText,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = V19GoldAccent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, V19GoldAccent.copy(alpha = 0.65f)),
                ) {
                    Text(
                        text = "${group.entries.size} registros semelhantes",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = V19GoldAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            if (group.differenceSummaries.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x9935210A),
                    border = BorderStroke(1.dp, V19GoldAccent.copy(alpha = 0.45f)),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "Diferenças encontradas neste grupo:",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = V19GoldAccent,
                        )
                        group.differenceSummaries.forEach { summary ->
                            Text(
                                text = "• $summary",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = V19InkPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditedProductEntryCard(
    entry: AuditedProductEntry,
    copyLabel: String,
    accentBorderColor: Color,
    isActionBusy: Boolean,
    onRequestDelete: () -> Unit,
) {
    val product = entry.product
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audited_product_card_${product.id}"),
        shape = RoundedCornerShape(14.dp),
        color = GlassInnerBoxBg,
        border = BorderStroke(1.dp, accentBorderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$copyLabel · ID #${entry.shortIdLabel}",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = V19PurpleSoftText,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (product.active) Color(0x3310B981) else V19SurfaceChip,
                    border = BorderStroke(
                        1.dp,
                        if (product.active) V19MintSuccess.copy(alpha = 0.6f) else GlassNeonBorder15,
                    ),
                ) {
                    Text(
                        text = if (product.active) "Ativo" else "Inativo",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp,
                        color = if (product.active) V19MintSuccess else V19InkMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            Text(
                text = "${product.name} · ${CurrencyFormatter.formatBrl(product.price)}",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = Color.White,
            )

            Text(
                text = "Categoria: ${entry.categoryName ?: "Sem categoria"} · Subcategoria: ${entry.subcategoryName ?: "Sem subcategoria"}",
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 12.sp,
                color = V19InkPrimary,
            )

            val splitSummary = product.participants.joinToString(" • ") { split ->
                "${split.personName}: ${CurrencyFormatter.formatBrl(split.amount)}"
            }
            Text(
                text = "Participantes: $splitSummary",
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = HomeCardLabelInk,
            )

            Text(
                text = "Criado em ${formatUpdateEpoch(product.createdAtEpochMs)}",
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 11.sp,
                color = V19InkMuted,
            )

            if (entry.linkedSalesCount > 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("audit_product_sales_lock_${product.id}"),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x993B1726),
                    border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.55f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Exclusão bloqueada por vendas vinculadas",
                            tint = V19ErrorSoft,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Vinculado a ${entry.linkedSalesCount} venda(s). Exclusão bloqueada para preservar o histórico financeiro.",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            color = V19ErrorSoft,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            val canDelete = entry.canDelete && !isActionBusy
            OutlinedButton(
                onClick = onRequestDelete,
                enabled = canDelete,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (canDelete) V19ErrorSoft.copy(alpha = 0.80f) else GlassNeonBorder15,
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = V19ErrorSoft,
                    disabledContentColor = V19InkMuted.copy(alpha = 0.55f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("audit_delete_product_button_${product.id}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "EXCLUIR ESTE PRODUTO",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                )
            }
        }
    }
}

@Composable
internal fun ConfirmDeleteAuditedProductDialog(
    entry: AuditedProductEntry,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val product = entry.product
    AlertDialog(
        onDismissRequest = {
            if (!isDeleting) onDismiss()
        },
        modifier = Modifier.testTag("confirm_delete_audited_product_dialog"),
        containerColor = Color(0xFA21123D),
        titleContentColor = Color.White,
        textContentColor = V19InkPrimary,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 12.dp,
        icon = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xD94B1528),
                border = BorderStroke(1.2.dp, V19ErrorSoft),
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Confirmar exclusão de produto",
                    tint = V19ErrorSoft,
                    modifier = Modifier.padding(10.dp),
                )
            }
        },
        title = {
            Text(
                text = "Confirmar exclusão do produto",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = Color.White,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x881A0932),
                    border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = product.name,
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.5.sp,
                            color = Color.White,
                        )
                        Text(
                            text = "Preço total: ${CurrencyFormatter.formatBrl(product.price)}",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = V19PurpleSoftText,
                        )
                        Text(
                            text = "Categoria: ${entry.categoryName ?: "Sem categoria"}",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.5.sp,
                            color = V19InkPrimary,
                        )
                        Text(
                            text = "Subcategoria: ${entry.subcategoryName ?: "Sem subcategoria"}",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.5.sp,
                            color = V19InkPrimary,
                        )
                        val splitText = product.participants.joinToString(" • ") { split ->
                            "${split.personName}: ${CurrencyFormatter.formatBrl(split.amount)}"
                        }
                        Text(
                            text = "Participantes: $splitText",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.sp,
                            color = HomeCardLabelInk,
                        )
                        Text(
                            text = "ID #${entry.shortIdLabel} · ${if (product.active) "Ativo" else "Inativo"}",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 11.5.sp,
                            color = V19InkMuted,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xC03B1726),
                    border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Somente este registro será removido permanentemente do catálogo. Os demais produtos e todo o histórico de vendas permanecerão preservados.",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = V19ErrorSoft,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                enabled = !isDeleting,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB91C1C),
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_delete_audited_product_button"),
            ) {
                Text(
                    text = if (isDeleting) "Excluindo..." else "Confirmar exclusão",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isDeleting,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, V19OutlineInput),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("cancel_delete_audited_product_button"),
            ) {
                Text(
                    text = "Cancelar",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = V19PurpleSoftText,
                )
            }
        },
    )
}
