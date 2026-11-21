package com.mixvendas3d.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.domain.models.SalePersonTotalSnapshot
import com.mixvendas3d.app.domain.update.SaleUpdateCandidate
import com.mixvendas3d.app.domain.update.SaleUpdateConflict
import com.mixvendas3d.app.domain.update.SystemUpdateAnalysisResult
import com.mixvendas3d.app.domain.update.SystemUpdateBatchSummary
import com.mixvendas3d.app.ui.theme.GlassInnerBoxBg
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeBadgeBgEnd
import com.mixvendas3d.app.ui.theme.HomeBadgeBgStart
import com.mixvendas3d.app.ui.theme.HomeBadgeBorder
import com.mixvendas3d.app.ui.theme.HomeCardGlassBottom
import com.mixvendas3d.app.ui.theme.HomeCardGlassTop
import com.mixvendas3d.app.ui.theme.HomeCardLabelInk
import com.mixvendas3d.app.ui.theme.HomeCardNeonBorder
import com.mixvendas3d.app.ui.theme.HomeCardSubtleDivider
import com.mixvendas3d.app.ui.theme.HomeCtaBorderGlow
import com.mixvendas3d.app.ui.theme.HomeCtaGradientEnd
import com.mixvendas3d.app.ui.theme.HomeCtaGradientStart
import com.mixvendas3d.app.ui.theme.HomeCtaShadowGlow
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
import com.mixvendas3d.app.ui.theme.V19SurfaceInput
import com.mixvendas3d.app.util.CurrencyFormatter
import com.mixvendas3d.app.util.RateFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val updateDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "dd/MM/yyyy HH:mm",
    Locale.forLanguageTag("pt-BR"),
)

internal fun formatUpdateEpoch(epochMs: Long): String {
    return Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(updateDateFormatter)
}

@Composable
internal fun UpdateMetricTile(
    label: String,
    count: Int,
    accent: Color,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = GlassInnerBoxBg,
        border = BorderStroke(1.dp, if (count > 0) accent.copy(alpha = 0.45f) else GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = V19InkPrimary,
            )
        }
    }
}

@Composable
internal fun SaleUpdatePreviewCard(
    candidate: SaleUpdateCandidate,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("update_system_preview_card_${candidate.saleId}"),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.25.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(HomeCardGlassTop, HomeCardGlassBottom)),
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val badgeShape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(badgeShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    HomeBadgeBgStart.copy(alpha = 0.72f),
                                    HomeBadgeBgEnd.copy(alpha = 0.85f),
                                ),
                            ),
                        )
                        .border(1.dp, HomeBadgeBorder.copy(alpha = 0.50f), badgeShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = candidate.productNames.joinToString(" + "),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        color = Color.White,
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = V19SurfaceChip,
                        border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.45f)),
                    ) {
                        Text(
                            text = "Antes / Depois",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = V19PurpleSoftText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = GlassInnerBoxBg,
                border = BorderStroke(1.dp, GlassNeonBorder15),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${formatUpdateEpoch(candidate.soldAtEpochMs)} · ${candidate.paymentMethodLabel}",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = HomeCardLabelInk.copy(alpha = 0.88f),
                    )
                    Text(
                        text = "Cobrado: ${CurrencyFormatter.formatBrl(candidate.originalChargedAmount)}",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                    )
                }
            }

            if (candidate.changeReasons.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = GlassInnerBoxBg,
                    border = BorderStroke(1.dp, GlassNeonBorder15),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Motivos da atualização",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = V19PurpleSoftText,
                        )
                        candidate.changeReasons.forEach { reason ->
                            Text(
                                text = "• $reason",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                                color = V19InkPrimary,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.30f))

            PreviewFinancialBlock(
                badgeText = "ANTES — Situação atual do histórico",
                accentColor = V19PurpleSoftText,
                containerColor = GlassInnerBoxBg,
                borderColor = GlassNeonBorder15,
                feeRateBps = candidate.historicalFeeRateBps,
                feeAmount = candidate.historicalFeeAmount,
                netAmount = candidate.historicalNetAmount,
                netHighlightColor = Color.White,
                personTotals = candidate.historicalPersonTotals,
            )

            PreviewFinancialBlock(
                badgeText = "DEPOIS — Valores previstos após atualização",
                accentColor = Color(0xFFBAF7DE),
                containerColor = Color(0xBF0E3B2B),
                borderColor = Color(0xFF66E2B3).copy(alpha = 0.72f),
                feeRateBps = candidate.proposedFeeRateBps,
                feeAmount = candidate.proposedFeeAmount,
                netAmount = candidate.proposedNetAmount,
                netHighlightColor = V19MintSuccess,
                personTotals = candidate.proposedPersonTotals,
            )
        }
    }
}

@Composable
private fun PreviewFinancialBlock(
    badgeText: String,
    accentColor: Color,
    containerColor: Color,
    borderColor: Color,
    feeRateBps: Int,
    feeAmount: com.mixvendas3d.app.domain.models.MoneyCents,
    netAmount: com.mixvendas3d.app.domain.models.MoneyCents,
    netHighlightColor: Color,
    personTotals: List<SalePersonTotalSnapshot>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.15.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = badgeText,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = accentColor,
            )

            HorizontalDivider(color = borderColor.copy(alpha = 0.32f))

            PreviewKeyValueLine(
                label = "Taxa",
                value = RateFormatter.formatBpsAsPercent(feeRateBps),
                valueColor = V19InkPrimary,
            )
            PreviewKeyValueLine(
                label = "Tarifa",
                value = "-${CurrencyFormatter.formatBrl(feeAmount)}",
                valueColor = V19InkPrimary,
            )
            PreviewKeyValueLine(
                label = "Líquido",
                value = CurrencyFormatter.formatBrl(netAmount),
                valueColor = netHighlightColor,
                isBold = true,
            )

            if (personTotals.isNotEmpty()) {
                HorizontalDivider(color = borderColor.copy(alpha = 0.25f))
                Text(
                    text = "Participantes",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = accentColor.copy(alpha = 0.90f),
                )
                personTotals.forEach { person ->
                    PreviewKeyValueLine(
                        label = person.personName,
                        value = CurrencyFormatter.formatBrl(person.netAmount),
                        valueColor = V19InkPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewKeyValueLine(
    label: String,
    value: String,
    valueColor: Color,
    isBold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            color = HomeCardLabelInk.copy(alpha = 0.85f),
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        Text(
            text = value,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = if (isBold) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = if (isBold) 14.sp else 13.sp,
            color = valueColor,
        )
    }
}

@Composable
internal fun SaleUpdateConflictCard(
    conflict: SaleUpdateConflict,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("update_system_conflict_card_${conflict.saleId}"),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.25.dp, V19ErrorSoft.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xE63D132B), Color(0xDC240A1E)),
                    ),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = V19ErrorSoft.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.55f)),
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = V19ErrorSoft,
                        modifier = Modifier.padding(8.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = conflict.productNames.joinToString(" + "),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.5.sp,
                        lineHeight = 21.sp,
                        color = Color.White,
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = V19ErrorSoft.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.55f)),
                    ) {
                        Text(
                            text = "Exige revisão",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = V19ErrorSoft,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0x78250919),
                border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.28f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "${formatUpdateEpoch(conflict.soldAtEpochMs)} · ${conflict.paymentMethodLabel}",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = V19InkMuted,
                    )
                    Text(
                        text = "Cobrado: ${CurrencyFormatter.formatBrl(conflict.originalChargedAmount)}",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = V19InkPrimary,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                conflict.reasons.forEach { reason ->
                    Text(
                        text = "• $reason",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = V19ErrorSoft,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ConfirmSystemUpdateDialog(
    analysis: SystemUpdateAnalysisResult,
    isPartialWithConflicts: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFA21123D),
        titleContentColor = Color.White,
        textContentColor = V19InkPrimary,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 12.dp,
        icon = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isPartialWithConflicts) Color(0xD93E280D) else V19SurfaceChip,
                border = BorderStroke(
                    1.2.dp,
                    if (isPartialWithConflicts) V19GoldAccent else HomeCardNeonBorder.copy(alpha = 0.72f),
                ),
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = if (isPartialWithConflicts) Icons.Filled.WarningAmber else Icons.Filled.Info,
                    contentDescription = null,
                    tint = if (isPartialWithConflicts) V19GoldAccent else V19PurpleSoftText,
                    modifier = Modifier.padding(10.dp),
                )
            }
        },
        title = {
            Text(
                text = if (isPartialWithConflicts) {
                    "Confirmar atualização parcial"
                } else {
                    "Atualizar os valores do histórico"
                },
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x881A0932),
                    border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (isPartialWithConflicts) {
                            "Somente as ${analysis.salesToUpdateCount} venda(s) válidas serão recalculadas e atualizadas no Room. As ${analysis.requiresReviewCount} venda(s) com conflito permanecerão intactas."
                        } else {
                            "Serão atualizadas ${analysis.salesToUpdateCount} venda(s) no banco local Room conforme os cadastros atuais, preservando datas originais, quantidades e valores cobrados ao cliente."
                        },
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = V19InkPrimary,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xC038240E),
                    border = BorderStroke(1.dp, V19GoldAccent.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Um registro reversível será salvo para permitir desfazer esta operação caso necessário. Recomendamos também manter um backup JSON salvo.",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = V19GoldAccent,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        },
        confirmButton = {
            val btnShape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .testTag("update_system_confirm_apply_button")
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .shadow(
                        elevation = 8.dp,
                        shape = btnShape,
                        ambientColor = HomeCtaShadowGlow.copy(alpha = 0.55f),
                        spotColor = HomeCtaShadowGlow.copy(alpha = 0.70f),
                    )
                    .clip(btnShape)
                    .background(Brush.horizontalGradient(listOf(HomeCtaGradientStart, HomeCtaGradientEnd)))
                    .border(1.2.dp, HomeCtaBorderGlow.copy(alpha = 0.85f), btnShape)
                    .clickable(role = Role.Button, onClick = onConfirm)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Confirmar atualização",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, V19OutlineInput),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("update_system_cancel_apply_button"),
            ) {
                Text(
                    text = "Cancelar",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = V19PurpleSoftText,
                )
            }
        },
        modifier = Modifier.testTag("update_system_confirm_dialog"),
    )
}

@Composable
internal fun ConfirmUndoSystemUpdateDialog(
    summary: SystemUpdateBatchSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFA21123D),
        titleContentColor = Color.White,
        textContentColor = V19InkPrimary,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 12.dp,
        icon = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xD93E280D),
                border = BorderStroke(1.2.dp, V19GoldAccent),
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = null,
                    tint = V19GoldAccent,
                    modifier = Modifier.padding(10.dp),
                )
            }
        },
        title = {
            Text(
                text = "Desfazer última atualização",
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x881A0932),
                    border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "As ${summary.updatedSalesCount} venda(s) alteradas em ${formatUpdateEpoch(summary.appliedAtEpochMs)} voltarão exatamente ao estado financeiro anterior à atualização.",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = V19InkPrimary,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xD90E3B2B),
                    border = BorderStroke(1.dp, Color(0xFF66E2B3).copy(alpha = 0.72f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFFBAF7DE),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Vendas novas registradas após essa atualização não serão afetadas nem removidas.",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFFBAF7DE),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            val btnShape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .testTag("update_system_confirm_undo_button")
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .shadow(
                        elevation = 8.dp,
                        shape = btnShape,
                        ambientColor = V19GoldAccent.copy(alpha = 0.45f),
                        spotColor = V19GoldAccent.copy(alpha = 0.60f),
                    )
                    .clip(btnShape)
                    .background(Brush.horizontalGradient(listOf(V19GoldAccent, Color(0xFFD99B38))))
                    .border(1.2.dp, Color(0xFFFFE0A6), btnShape)
                    .clickable(role = Role.Button, onClick = onConfirm)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Confirmar reversão",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = Color(0xFF1A0932),
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, V19OutlineInput),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("update_system_cancel_undo_button"),
            ) {
                Text(
                    text = "Cancelar",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = V19PurpleSoftText,
                )
            }
        },
        modifier = Modifier.testTag("update_system_undo_dialog"),
    )
}

