package com.mixvendas3d.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.mixvendas3d.app.domain.calculation.ParticipantDiffType
import com.mixvendas3d.app.domain.calculation.ParticipantSplitDiff
import com.mixvendas3d.app.domain.calculation.ProductDuplicateComparison
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.ui.screens.PendingProductDuplicateConfirmation
import com.mixvendas3d.app.ui.theme.GlassInnerBoxBg
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeCardLabelInk
import com.mixvendas3d.app.ui.theme.HomeCardNeonBorder
import com.mixvendas3d.app.ui.theme.HomeCtaBorderGlow
import com.mixvendas3d.app.ui.theme.HomeCtaGradientEnd
import com.mixvendas3d.app.ui.theme.HomeCtaGradientStart
import com.mixvendas3d.app.ui.theme.PlusJakartaSansFontFamily
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import com.mixvendas3d.app.ui.theme.V19GoldAccent
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19MintSuccess
import com.mixvendas3d.app.ui.theme.V19OutlineInput
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SurfaceChip
import com.mixvendas3d.app.util.CurrencyFormatter

@Composable
fun ProductSimilarDuplicateDialog(
    pending: PendingProductDuplicateConfirmation,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onConfirmSaveAnyway: () -> Unit,
) {
    val result = pending.result
    val matchCount = result.matches.size
    val confirmLabel = if (pending.isEditing) "Salvar mesmo assim" else "Cadastrar mesmo assim"

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) onCancel()
        },
        modifier = Modifier.testTag("product_duplicate_confirmation_dialog"),
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
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = "Alerta de produto semelhante",
                    tint = V19GoldAccent,
                    modifier = Modifier.padding(10.dp),
                )
            }
        },
        title = {
            Text(
                text = "Produto com mesmo nome e valor encontrado.",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                color = Color.White,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (matchCount == 1) {
                        "Encontramos 1 produto já cadastrado com o mesmo nome e preço total, mas com diferenças importantes. Confira abaixo antes de continuar:"
                    } else {
                        "Encontramos $matchCount produtos já cadastrados com o mesmo nome e preço total, mas com diferenças importantes. Confira todos abaixo antes de continuar:"
                    },
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = HomeCardLabelInk,
                )

                // Bloco do produto sendo cadastrado/editado
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_duplicate_candidate_summary"),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x99281652),
                    border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.55f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (pending.isEditing) {
                                "PRODUTO EM EDIÇÃO"
                            } else {
                                "PRODUTO EM CADASTRO"
                            },
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = V19PurpleSoftText,
                        )
                        Text(
                            text = "${result.candidateDisplayName} · ${CurrencyFormatter.formatBrl(MoneyCents(result.candidatePriceCents))}",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White,
                        )
                        Text(
                            text = "Categoria: ${result.candidateCategoryName ?: "Sem categoria"} · Subcategoria: ${result.candidateSubcategoryName ?: "Sem subcategoria"}",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.sp,
                            color = V19InkPrimary,
                        )
                        val splitSummary = result.candidateParticipants.joinToString(" • ") { (name, cents) ->
                            "$name: ${CurrencyFormatter.formatBrl(MoneyCents(cents))}"
                        }
                        Text(
                            text = "Divisão: $splitSummary",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.sp,
                            color = V19InkMuted,
                        )
                    }
                }

                // Lista completa de todos os produtos semelhantes encontrados
                result.matches.forEachIndexed { index, comparison ->
                    SimilarProductComparisonCard(
                        index = index,
                        total = matchCount,
                        comparison = comparison,
                    )
                }
            }
        },
        confirmButton = {
            val btnShape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .testTag("product_duplicate_confirm_button")
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .clip(btnShape)
                    .background(
                        Brush.horizontalGradient(
                            if (isSaving) {
                                listOf(
                                    HomeCtaGradientStart.copy(alpha = 0.45f),
                                    HomeCtaGradientEnd.copy(alpha = 0.45f),
                                )
                            } else {
                                listOf(HomeCtaGradientStart, HomeCtaGradientEnd)
                            },
                        ),
                    )
                    .border(1.2.dp, HomeCtaBorderGlow.copy(alpha = 0.85f), btnShape)
                    .clickable(
                        enabled = !isSaving,
                        role = Role.Button,
                        onClick = onConfirmSaveAnyway,
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isSaving) "Salvando..." else confirmLabel,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                enabled = !isSaving,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, V19OutlineInput),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("product_duplicate_cancel_button"),
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

@Composable
private fun SimilarProductComparisonCard(
    index: Int,
    total: Int,
    comparison: ProductDuplicateComparison,
) {
    val existing = comparison.existingProduct
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_duplicate_match_card_${existing.id}"),
        shape = RoundedCornerShape(14.dp),
        color = GlassInnerBoxBg,
        border = BorderStroke(1.dp, V19GoldAccent.copy(alpha = 0.50f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (total > 1) {
                        "REGISTRO EXISTENTE ${index + 1} DE $total"
                    } else {
                        "PRODUTO JÁ CADASTRADO"
                    },
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    color = V19GoldAccent,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (existing.active) Color(0x3310B981) else V19SurfaceChip,
                    border = BorderStroke(
                        1.dp,
                        if (existing.active) V19MintSuccess.copy(alpha = 0.6f) else GlassNeonBorder15,
                    ),
                ) {
                    Text(
                        text = if (existing.active) "Ativo" else "Inativo",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        color = if (existing.active) V19MintSuccess else V19InkMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            // O que está igual
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Igual",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = V19MintSuccess,
                )
                comparison.equalItems.forEach { item ->
                    Text(
                        text = "• $item",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = V19InkPrimary,
                    )
                }
            }

            HorizontalDivider(color = GlassNeonBorder15)

            // O que está diferente
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Diferente",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = V19GoldAccent,
                )

                comparison.fieldDifferences.forEach { diff ->
                    Text(
                        text = "• ${diff.fieldLabel}: atual \"${diff.existingValue}\" → novo \"${diff.candidateValue}\"",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = Color.White,
                    )
                }

                if (comparison.hasParticipantDifferences) {
                    Text(
                        text = "• Divisão entre participantes:",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color.White,
                    )
                    comparison.participantDiffs.forEach { pDiff ->
                        ParticipantDiffRow(pDiff)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantDiffRow(diff: ParticipantSplitDiff) {
    val existingText = diff.existingAmountCents?.let { CurrencyFormatter.formatBrl(MoneyCents(it)) }
    val candidateText = diff.candidateAmountCents?.let { CurrencyFormatter.formatBrl(MoneyCents(it)) }

    val (line, color) = when (diff.diffType) {
        ParticipantDiffType.ADDED ->
            "  + ${diff.personName}: adicionado ($candidateText)" to V19MintSuccess
        ParticipantDiffType.REMOVED ->
            "  − ${diff.personName}: removido (era $existingText)" to V19ErrorSoft
        ParticipantDiffType.AMOUNT_CHANGED ->
            "  ≠ ${diff.personName}: $existingText → $candidateText" to V19GoldAccent
        ParticipantDiffType.UNCHANGED ->
            "  = ${diff.personName}: $candidateText (igual)" to V19InkMuted
    }

    Text(
        text = line,
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = if (diff.diffType == ParticipantDiffType.UNCHANGED) FontWeight.Normal else FontWeight.SemiBold,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        color = color,
    )
}
