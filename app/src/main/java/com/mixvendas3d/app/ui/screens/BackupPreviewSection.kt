package com.mixvendas3d.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.export.PreviewChangeOperation
import com.mixvendas3d.app.ui.components.V19TranslucentCard
import com.mixvendas3d.app.ui.theme.GlassInnerBoxBg
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import com.mixvendas3d.app.ui.theme.V19GoldAccent
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19OutlineSubtle
import com.mixvendas3d.app.ui.theme.V19PurpleButtonStart
import com.mixvendas3d.app.ui.theme.V19PurpleGlow
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SurfaceChip

@Composable
fun RevisarRestauracaoSection(
    preview: BackupPreviewUi,
    isBusy: Boolean,
    canTriggerRestore: Boolean = false,
    onRequestRestoreClick: () -> Unit = {},
    onBackToConflicts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preview_restore_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Banner de status compatível com a confirmação da revisão (sem executar restauração)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .testTag("review_ready_banner"),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF103E2E).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, Color(0xFF66E2B3)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFFBAF7DE),
                    modifier = Modifier.size(20.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.backup_conflicts_review_ready_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFBAF7DE),
                    )
                    Text(
                        text = stringResource(R.string.backup_preview_not_executed),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFBAF7DE).copy(alpha = 0.9f),
                    )
                }
            }
        }

        // Card principal: REVISAR RESTAURAÇÃO + Contadores Reais
        V19TranslucentCard(
            modifier = Modifier.testTag("preview_overview_card"),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = V19SurfaceChip,
                    border = BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.45f)),
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FactCheck,
                        contentDescription = null,
                        tint = V19PurpleSoftText,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.backup_preview_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = V19InkPrimary,
                        modifier = Modifier.testTag("preview_restore_title"),
                    )
                    Text(
                        text = stringResource(R.string.backup_preview_section_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = V19InkMuted,
                        modifier = Modifier.testTag("preview_restore_subtitle"),
                    )
                }
            }

            Spacer(modifier = Modifier.size(12.dp))
            HorizontalDivider(color = V19OutlineSubtle)
            Spacer(modifier = Modifier.size(8.dp))

            PreviewKeyValueRow(
                label = "Data do backup",
                value = preview.formattedBackupDate,
                testTag = "preview_backup_date",
            )
            HorizontalDivider(color = V19OutlineSubtle)
            PreviewKeyValueRow(
                label = "Pessoas que serão adicionadas",
                value = preview.peopleToAddCount.toString(),
                valueColor = if (preview.peopleToAddCount > 0) Color(0xFFBAF7DE) else V19InkPrimary,
                testTag = "preview_add_people_count",
            )
            HorizontalDivider(color = V19OutlineSubtle)
            PreviewKeyValueRow(
                label = "Produtos que serão adicionados",
                value = preview.productsToAddCount.toString(),
                valueColor = if (preview.productsToAddCount > 0) Color(0xFFBAF7DE) else V19InkPrimary,
                testTag = "preview_add_products_count",
            )
            HorizontalDivider(color = V19OutlineSubtle)
            PreviewKeyValueRow(
                label = "Maquininhas que serão adicionadas",
                value = preview.machinesToAddCount.toString(),
                valueColor = if (preview.machinesToAddCount > 0) Color(0xFFBAF7DE) else V19InkPrimary,
                testTag = "preview_add_machines_count",
            )
            HorizontalDivider(color = V19OutlineSubtle)
            PreviewKeyValueRow(
                label = "Vendas que serão adicionadas",
                value = preview.salesToAddCount.toString(),
                valueColor = if (preview.salesToAddCount > 0) Color(0xFFBAF7DE) else V19InkPrimary,
                testTag = "preview_add_sales_count",
            )
            HorizontalDivider(color = V19OutlineSubtle)
            PreviewKeyValueRow(
                label = "Registros mantidos no celular",
                value = preview.keptRecordsCount.toString(),
                testTag = "preview_kept_count",
            )
            HorizontalDivider(color = V19OutlineSubtle)
            PreviewKeyValueRow(
                label = "Registros ignorados",
                value = preview.ignoredRecordsCount.toString(),
                testTag = "preview_ignored_count",
            )
            HorizontalDivider(color = V19OutlineSubtle)
            PreviewKeyValueRow(
                label = "Conjuntos que serão substituídos",
                value = preview.unitsToReplaceCount.toString(),
                valueColor = if (preview.unitsToReplaceCount > 0) V19GoldAccent else V19InkPrimary,
                testTag = "preview_replace_count",
            )
            HorizontalDivider(color = V19OutlineSubtle)
            PreviewKeyValueRow(
                label = "Registros somente locais preservados",
                value = preview.localOnlyPreservedCount.toString(),
                testTag = "preview_local_only_preserved_count",
            )
            HorizontalDivider(color = V19OutlineSubtle)
            PreviewKeyValueRow(
                label = "Quantidade final de registros",
                value = preview.finalTotalRecordsCount.toString(),
                valueColor = V19PurpleSoftText,
                testTag = "preview_final_total_count",
            )

            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = "Após mesclagem: ${preview.finalPeopleCount} pessoa(s) · " +
                    "${preview.finalProductsCount} produto(s) · " +
                    "${preview.finalMachinesCount} maquininha(s) · " +
                    "${preview.finalSalesCount} venda(s) · " +
                    "${preview.finalSaleItemsCount} item(ns) · " +
                    "${preview.finalPreferencesCount} preferência(s).",
                style = MaterialTheme.typography.labelSmall,
                color = V19InkMuted,
                modifier = Modifier.testTag("preview_final_tables_breakdown"),
            )
        }

        // Card de detalhamento das alterações (PreviewChangedEntry)
        V19TranslucentCard(
            modifier = Modifier.testTag("preview_changes_card"),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = V19SurfaceChip,
                    border = BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.40f)),
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        tint = V19PurpleSoftText,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.backup_preview_changes_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = V19InkPrimary,
                    modifier = Modifier.testTag("preview_changes_title"),
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            if (preview.changeGroups.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag("preview_no_changes_banner"),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF103E2E).copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color(0xFF66E2B3)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = Color(0xFFBAF7DE),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.backup_preview_no_changes),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFBAF7DE),
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    preview.changeGroups.forEach { group ->
                        PreviewChangeGroupBlock(group = group)
                    }
                }
            }
        }

        // Card de Resumo Financeiro Previsto (exclusivamente PreviewFinancialSummary)
        PreviewFinancialSummaryCard(financial = preview.financialSummary)

        // Card de Proteção das Vendas e Histórico
        PreviewSalesProtectionCard()

        // Rodapé explícito de segurança + navegação para Voltar aos conflitos
        V19TranslucentCard(
            modifier = Modifier.testTag("preview_footer_card"),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF3E2D12).copy(alpha = 0.90f),
                border = BorderStroke(1.dp, V19GoldAccent),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = V19GoldAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.backup_preview_not_executed),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = V19GoldAccent,
                            modifier = Modifier.testTag("preview_not_executed_badge"),
                        )
                        Text(
                            text = stringResource(R.string.backup_preview_review_before_confirm),
                            style = MaterialTheme.typography.bodySmall,
                            color = V19InkPrimary,
                            modifier = Modifier.testTag("preview_review_before_confirm_note"),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(12.dp))

            Button(
                onClick = onRequestRestoreClick,
                enabled = canTriggerRestore && !isBusy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = V19PurpleButtonStart,
                    contentColor = Color.White,
                    disabledContainerColor = V19SurfaceChip,
                    disabledContentColor = V19InkMuted,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("restore_backup_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.backup_restore_button),
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.size(10.dp))

            OutlinedButton(
                onClick = onBackToConflicts,
                enabled = !isBusy,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.65f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = V19SurfaceChip.copy(alpha = 0.85f),
                    contentColor = V19InkPrimary,
                    disabledContainerColor = V19SurfaceChip,
                    disabledContentColor = V19InkMuted,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("back_to_conflicts_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.backup_preview_back_to_conflicts),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.backup_preview_back_to_conflicts),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PreviewChangeGroupBlock(
    group: BackupPreviewChangeGroupUi,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preview_group_${group.groupType.key}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${group.groupType.title} (${group.entries.size})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = V19PurpleSoftText,
        )

        group.entries.forEach { item ->
            val isAdd = item.operation == PreviewChangeOperation.ADD
            val badgeContainer = if (isAdd) {
                Color(0xFF103E2E).copy(alpha = 0.90f)
            } else {
                Color(0xFF3E2D12).copy(alpha = 0.92f)
            }
            val badgeBorder = if (isAdd) Color(0xFF66E2B3) else V19GoldAccent
            val badgeText = if (isAdd) Color(0xFFBAF7DE) else V19GoldAccent
            val opLabel = if (isAdd) {
                stringResource(R.string.backup_preview_op_add)
            } else {
                stringResource(R.string.backup_preview_op_replace)
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .testTag("preview_entry_${item.entryKey}"),
                shape = RoundedCornerShape(12.dp),
                color = GlassInnerBoxBg,
                border = BorderStroke(1.dp, badgeBorder.copy(alpha = 0.55f)),
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
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = badgeContainer,
                            border = BorderStroke(1.dp, badgeBorder),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = if (isAdd) Icons.Filled.AddCircleOutline else Icons.Filled.SwapHoriz,
                                    contentDescription = null,
                                    tint = badgeText,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = opLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeText,
                                )
                            }
                        }

                        if (item.isAtomicUnit) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = GlassInnerBoxBg,
                                border = BorderStroke(1.dp, GlassNeonBorder15),
                            ) {
                                Text(
                                    text = "Conjunto único",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = V19InkMuted,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = V19InkPrimary,
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = V19PurpleSoftText,
                    )

                    item.detailLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = V19InkPrimary,
                        )
                    }

                    if (!item.atomicReplacementNote.isNullOrBlank()) {
                        Text(
                            text = item.atomicReplacementNote,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = V19GoldAccent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewFinancialSummaryCard(
    financial: BackupPreviewFinancialUi,
) {
    V19TranslucentCard(
        modifier = Modifier.testTag("preview_financial_card"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = V19SurfaceChip,
                border = BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.40f)),
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AttachMoney,
                    contentDescription = null,
                    tint = V19PurpleSoftText,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Text(
                text = stringResource(R.string.backup_preview_financial_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = V19InkPrimary,
                modifier = Modifier.testTag("preview_financial_title"),
            )
        }

        Spacer(modifier = Modifier.size(12.dp))
        HorizontalDivider(color = V19OutlineSubtle)

        PreviewKeyValueRow(
            label = "Vendas atualmente no celular (${financial.localSalesCount})",
            value = financial.localTotalFinalBrl,
            testTag = "preview_fin_local_total",
        )
        HorizontalDivider(color = V19OutlineSubtle)
        PreviewKeyValueRow(
            label = "Total previsto após restauração (${financial.finalSalesCount})",
            value = financial.finalTotalFinalBrl,
            valueColor = V19PurpleSoftText,
            testTag = "preview_fin_final_total",
        )
        HorizontalDivider(color = V19OutlineSubtle)
        PreviewKeyValueRow(
            label = "Total de tarifas atual",
            value = financial.localTotalFeeBrl,
            testTag = "preview_fin_local_fee",
        )
        HorizontalDivider(color = V19OutlineSubtle)
        PreviewKeyValueRow(
            label = "Total de tarifas previsto",
            value = financial.finalTotalFeeBrl,
            testTag = "preview_fin_final_fee",
        )
        HorizontalDivider(color = V19OutlineSubtle)
        PreviewKeyValueRow(
            label = "Total líquido atual",
            value = financial.localTotalNetBrl,
            testTag = "preview_fin_local_net",
        )
        HorizontalDivider(color = V19OutlineSubtle)
        PreviewKeyValueRow(
            label = "Total líquido previsto",
            value = financial.finalTotalNetBrl,
            valueColor = Color(0xFFBAF7DE),
            testTag = "preview_fin_final_net",
        )
        HorizontalDivider(color = V19OutlineSubtle)
        PreviewKeyValueRow(
            label = "Impacto das novas vendas (${financial.addedSalesCount})",
            value = "${financial.addedSalesFinalBrl} (líq. ${financial.addedSalesNetBrl} · tarifa ${financial.addedSalesFeeBrl})",
            valueColor = if (financial.addedSalesCount > 0) Color(0xFFBAF7DE) else V19InkPrimary,
            testTag = "preview_fin_added_impact",
        )
        HorizontalDivider(color = V19OutlineSubtle)
        PreviewKeyValueRow(
            label = "Impacto das vendas substituídas (${financial.replacedSalesCount})",
            value = "${financial.replacedSalesFinalDeltaBrl} (líq. ${financial.replacedSalesNetDeltaBrl} · tarifa ${financial.replacedSalesFeeDeltaBrl})",
            valueColor = deltaColor(financial.replacedSalesFinalDeltaCents),
            testTag = "preview_fin_replaced_impact",
        )
        HorizontalDivider(color = V19OutlineSubtle)
        PreviewKeyValueRow(
            label = "Diferença financeira total (valor vendido)",
            value = financial.netFinalAmountDeltaBrl,
            valueColor = deltaColor(financial.netFinalAmountDeltaCents),
            testTag = "preview_fin_total_delta_final",
        )
        HorizontalDivider(color = V19OutlineSubtle)
        PreviewKeyValueRow(
            label = "Diferença financeira total (tarifas)",
            value = financial.netFeeAmountDeltaBrl,
            valueColor = deltaColor(financial.netFeeAmountDeltaCents),
            testTag = "preview_fin_total_delta_fee",
        )
        HorizontalDivider(color = V19OutlineSubtle)
        PreviewKeyValueRow(
            label = "Diferença financeira total (líquido)",
            value = financial.netNetAmountDeltaBrl,
            valueColor = deltaColor(financial.netNetAmountDeltaCents),
            testTag = "preview_fin_total_delta_net",
        )
    }
}

@Composable
private fun PreviewSalesProtectionCard() {
    val protectionItems = listOf(
        stringResource(R.string.backup_preview_protection_local_only),
        stringResource(R.string.backup_preview_protection_no_dup),
        stringResource(R.string.backup_preview_protection_add_new),
        stringResource(R.string.backup_preview_protection_explicit_replace),
        stringResource(R.string.backup_preview_protection_snapshots),
        stringResource(R.string.backup_preview_protection_no_mixing),
    )

    V19TranslucentCard(
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .testTag("preview_sales_protection_card"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = V19SurfaceChip,
                border = BorderStroke(1.dp, Color(0xFF66E2B3).copy(alpha = 0.55f)),
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = Color(0xFFBAF7DE),
                    modifier = Modifier.padding(8.dp),
                )
            }
            Text(
                text = stringResource(R.string.backup_preview_sales_protection_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = V19InkPrimary,
            )
        }

        Spacer(modifier = Modifier.size(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            protectionItems.forEach { bullet ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF66E2B3),
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(14.dp),
                    )
                    Text(
                        text = bullet,
                        style = MaterialTheme.typography.bodySmall,
                        color = V19InkPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewKeyValueRow(
    label: String,
    value: String,
    testTag: String,
    valueColor: Color = V19InkPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = V19InkMuted,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}

private fun deltaColor(deltaCents: Long): Color {
    return when {
        deltaCents > 0L -> Color(0xFFBAF7DE)
        deltaCents < 0L -> V19ErrorSoft
        else -> V19InkPrimary
    }
}
