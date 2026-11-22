package com.mixvendas3d.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.InstallmentTiers
import com.mixvendas3d.app.domain.models.Machine
import com.mixvendas3d.app.ui.components.MachinesCollapsibleSectionCard
import com.mixvendas3d.app.ui.components.MachinesGlassCard
import com.mixvendas3d.app.ui.components.MachinesIconBadge
import com.mixvendas3d.app.ui.components.MachinesPrimaryCtaButton
import com.mixvendas3d.app.ui.components.MachinesRateOptionRow
import com.mixvendas3d.app.ui.components.MachinesToggleCheckRow
import com.mixvendas3d.app.ui.components.Mv3dIcon
import com.mixvendas3d.app.ui.components.ProductsSecondaryOutlinedButton
import com.mixvendas3d.app.ui.components.namesDarkInputColors
import com.mixvendas3d.app.ui.theme.GlassInnerBoxBg
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeCardLabelInk
import com.mixvendas3d.app.ui.theme.HomeCardNeonBorder
import com.mixvendas3d.app.ui.theme.HomeCardSubtleDivider
import com.mixvendas3d.app.ui.theme.HomeCtaBorderGlow
import com.mixvendas3d.app.ui.theme.HomeCtaGradientEnd
import com.mixvendas3d.app.ui.theme.HomeCtaGradientStart
import com.mixvendas3d.app.ui.theme.PlusJakartaSansFontFamily
import com.mixvendas3d.app.ui.theme.V19ErrorContainer
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import com.mixvendas3d.app.ui.theme.V19GoldAccent
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19MintSuccess
import com.mixvendas3d.app.ui.theme.V19PurplePrimary
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SuccessContainer
import com.mixvendas3d.app.ui.theme.V19SuccessInk
import com.mixvendas3d.app.ui.theme.V19SurfaceCard
import com.mixvendas3d.app.util.RateFormatter

@Composable
fun MachinesScreen(
    uiState: MachinesUiState,
    onSearchChanged: (String) -> Unit,
    onOpenNewMachine: () -> Unit,
    onOpenEditMachine: (Machine) -> Unit,
    onToggleCreditEnabled: (Machine) -> Unit,
    onDeleteMachine: (String) -> Unit,
    onCloseEditor: () -> Unit,
    onRequestBackFromEditor: () -> Unit,
    onDismissDiscardConfirmation: () -> Unit,
    onConfirmDiscardChanges: () -> Unit,
    onEditorNameChanged: (String) -> Unit,
    onEditorDebitRateChanged: (String) -> Unit,
    onEditorCreditEnabledChanged: (Boolean) -> Unit,
    onEditorCreditDefaultRateChanged: (String) -> Unit,
    onEditorBrandRatesEnabledChanged: (Boolean) -> Unit,
    onEditorBrandRateChanged: (CardBrand, String) -> Unit,
    onEditorInstallmentsEnabledChanged: (Boolean) -> Unit,
    onEditorInstallmentAddonChanged: (Int, String) -> Unit,
    onEditorMaxInstallmentsChanged: (Int?) -> Unit = {},
    onToggleBrandSectionExpanded: () -> Unit,
    onToggleInstallmentsSectionExpanded: () -> Unit,
    onSaveMachine: () -> Unit,
    modifier: Modifier = Modifier,
    onEditorBrandDebitRateChanged: (CardBrand, String) -> Unit = { _, _ -> },
    onNewCustomBrandNameChanged: (String) -> Unit = {},
    onNewCustomBrandRateChanged: (String) -> Unit = {},
    onNewCustomBrandCreditRateChanged: (String) -> Unit = {},
    onNewCustomBrandDebitRateChanged: (String) -> Unit = {},
    onAddCustomBrand: () -> Unit = {},
    onCustomBrandRateChanged: (String, String) -> Unit = { _, _ -> },
    onCustomBrandCreditRateChanged: (String, String) -> Unit = { _, _ -> },
    onCustomBrandDebitRateChanged: (String, String) -> Unit = { _, _ -> },
    onRemoveCustomBrand: (String) -> Unit = {},
) {
    val filteredMachines = uiState.filteredMachines
    var pendingDeleteTarget by remember { mutableStateOf<Machine?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_machines"),
    ) {
        if (!uiState.editor.isOpen) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(key = "machines_list_content") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        MachinesGlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                MachinesIconBadge(scalePercent = 200)
                                Text(
                                    text = stringResource(R.string.machines_list_card_title),
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = Color.White,
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics { heading() },
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = stringResource(R.string.machines_list_card_subtitle),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = HomeCardLabelInk.copy(alpha = 0.88f),
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            MachinesPrimaryCtaButton(
                                text = stringResource(R.string.machines_new_button),
                                enabled = true,
                                onClick = onOpenNewMachine,
                                testTag = "new_machine_button",
                                leadingText = "+",
                                iconRes = null,
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = uiState.searchQuery,
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
                                        text = "Buscar maquininha por nome...",
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontSize = 14.sp,
                                        color = V19InkMuted,
                                    )
                                },
                                colors = namesDarkInputColors(),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("machines_search_input"),
                            )

                            if (!uiState.errorMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("machines_error_banner"),
                                    shape = RoundedCornerShape(14.dp),
                                    color = V19ErrorContainer,
                                    border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.58f)),
                                ) {
                                    Text(
                                        text = uiState.errorMessage,
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = V19ErrorSoft,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    )
                                }
                            }

                            if (!uiState.feedbackMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("machines_feedback_banner"),
                                    shape = RoundedCornerShape(14.dp),
                                    color = V19SuccessContainer,
                                    border = BorderStroke(1.dp, V19MintSuccess.copy(alpha = 0.50f)),
                                ) {
                                    Text(
                                        text = uiState.feedbackMessage,
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = V19SuccessInk,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }

                        if (filteredMachines.isEmpty()) {
                            MachinesGlassCard(
                                modifier = Modifier.testTag("machines_empty_state"),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    MachinesIconBadge(iconSize = 64.dp)
                                    Text(
                                        text = if (uiState.machines.isEmpty()) {
                                            "Nenhuma maquininha cadastrada ainda. Toque em \"Cadastrar nova maquininha\" para configurar débito e crédito."
                                        } else {
                                            "Nenhuma maquininha encontrada com essa busca."
                                        },
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = V19InkMuted,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        } else {
                            filteredMachines.forEachIndexed { index, machine ->
                                androidx.compose.runtime.key(machine.id) {
                                    MachineV2Card(
                                        machine = machine,
                                        showListIcons = index < 2,
                                        onEdit = { onOpenEditMachine(machine) },
                                        onToggleCredit = { onToggleCreditEnabled(machine) },
                                        onRequestDelete = { pendingDeleteTarget = machine },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            MachineEditorContent(
                editor = uiState.editor,
                onCancel = onCloseEditor,
                onRequestBack = onRequestBackFromEditor,
                onNameChanged = onEditorNameChanged,
                onDebitRateChanged = onEditorDebitRateChanged,
                onCreditEnabledChanged = onEditorCreditEnabledChanged,
                onCreditDefaultRateChanged = onEditorCreditDefaultRateChanged,
                onBrandRatesEnabledChanged = onEditorBrandRatesEnabledChanged,
                onBrandRateChanged = onEditorBrandRateChanged,
                onBrandDebitRateChanged = onEditorBrandDebitRateChanged,
                onInstallmentsEnabledChanged = onEditorInstallmentsEnabledChanged,
                onInstallmentAddonChanged = onEditorInstallmentAddonChanged,
                onMaxInstallmentsChanged = onEditorMaxInstallmentsChanged,
                onToggleBrandSectionExpanded = onToggleBrandSectionExpanded,
                onToggleInstallmentsSectionExpanded = onToggleInstallmentsSectionExpanded,
                onNewCustomBrandNameChanged = onNewCustomBrandNameChanged,
                onNewCustomBrandRateChanged = onNewCustomBrandRateChanged,
                onNewCustomBrandCreditRateChanged = onNewCustomBrandCreditRateChanged,
                onNewCustomBrandDebitRateChanged = onNewCustomBrandDebitRateChanged,
                onAddCustomBrand = onAddCustomBrand,
                onCustomBrandRateChanged = onCustomBrandRateChanged,
                onCustomBrandCreditRateChanged = onCustomBrandCreditRateChanged,
                onCustomBrandDebitRateChanged = onCustomBrandDebitRateChanged,
                onRemoveCustomBrand = onRemoveCustomBrand,
                onSave = onSaveMachine,
            )
        }
    }

    pendingDeleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTarget = null },
            containerColor = V19SurfaceCard,
            title = {
                Text(
                    text = "Excluir maquininha?",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = V19InkPrimary,
                )
            },
            text = {
                Text(
                    text = "Deseja excluir \"${target.name}\" e todas as suas tarifas do banco local? Esta ação não pode ser desfeita.",
                    fontFamily = PlusJakartaSansFontFamily,
                    color = V19InkMuted,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteTarget = null
                        onDeleteMachine(target.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = V19ErrorSoft.copy(alpha = 0.25f),
                        contentColor = V19ErrorSoft,
                    ),
                    modifier = Modifier.testTag("confirm_delete_machine_button"),
                ) {
                    Text("Excluir", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteTarget = null },
                    modifier = Modifier.testTag("cancel_delete_machine_button"),
                ) {
                    Text("Cancelar", fontFamily = PlusJakartaSansFontFamily, color = V19PurpleSoftText)
                }
            },
        )
    }

    if (uiState.editor.isOpen && uiState.editor.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDiscardConfirmation,
            containerColor = V19SurfaceCard,
            modifier = Modifier.testTag("machine_discard_dialog"),
            title = {
                Text(
                    text = stringResource(R.string.machines_discard_title),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = V19InkPrimary,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.machines_discard_message),
                    fontFamily = PlusJakartaSansFontFamily,
                    color = V19InkMuted,
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDiscardChanges,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = V19ErrorSoft.copy(alpha = 0.25f),
                        contentColor = V19ErrorSoft,
                    ),
                    modifier = Modifier.testTag("confirm_discard_machine_button"),
                ) {
                    Text(
                        text = stringResource(R.string.machines_discard_confirm),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissDiscardConfirmation,
                    modifier = Modifier.testTag("cancel_discard_machine_button"),
                ) {
                    Text(
                        text = stringResource(R.string.machines_discard_continue),
                        fontFamily = PlusJakartaSansFontFamily,
                        color = V19PurpleSoftText,
                    )
                }
            },
        )
    }
}

@Composable
private fun MachineV2Card(
    machine: Machine,
    showListIcons: Boolean,
    onEdit: () -> Unit,
    onToggleCredit: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    var detailsExpanded by remember(machine.id) { mutableStateOf(false) }

    MachinesGlassCard(
        modifier = Modifier.testTag("machine_card_${machine.id}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showListIcons) {
                MachinesIconBadge(iconSize = 40.dp, scalePercent = 160)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = machine.name,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (machine.creditEnabled) {
                        Color(0x554E1E7E)
                    } else {
                        V19GoldAccent.copy(alpha = 0.16f)
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (machine.creditEnabled) {
                            HomeCardNeonBorder.copy(alpha = 0.65f)
                        } else {
                            V19GoldAccent.copy(alpha = 0.55f)
                        },
                    ),
                ) {
                    Text(
                        text = if (machine.creditEnabled) "Crédito ativo" else "Somente Débito",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = if (machine.creditEnabled) HomeCardLabelInk else V19GoldAccent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Débito",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = V19InkMuted,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = RateFormatter.formatBpsAsPercent(machine.debitRateBps),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    color = Color.White,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Crédito",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = V19InkMuted,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (machine.creditEnabled) {
                        RateFormatter.formatBpsAsPercent(machine.creditDefaultRateBps)
                    } else {
                        "Desativado"
                    },
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    color = if (machine.creditEnabled) Color.White else V19GoldAccent,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val configuredStandardCount = (machine.brandRatesBps.keys + machine.brandDebitRatesBps.keys).size
        val customBrandCount = machine.customBrandRates.size
        val summaryText = if (!machine.creditEnabled) {
            if (machine.brandRatesEnabled) {
                val debitSpecificCount = machine.brandDebitRatesBps.size +
                    machine.customBrandRates.count { it.debitRateBps != null }
                val customNote = if (customBrandCount > 0) " ($customBrandCount personalizada(s))" else ""
                if (debitSpecificCount > 0 || customBrandCount > 0) {
                    "Crédito desativado · $debitSpecificCount taxa(s) específica(s) no débito$customNote"
                } else {
                    "Crédito desativado · bandeiras no débito usam taxa padrão"
                }
            } else {
                "Crédito desativado no momento · taxas salvas preservadas"
            }
        } else {
            val totalBrandCount = configuredStandardCount + customBrandCount
            val brandSummary = if (machine.brandRatesEnabled) {
                if (customBrandCount > 0) {
                    "$totalBrandCount bandeira(s) com taxa específica ($customBrandCount personalizada(s))"
                } else {
                    "$configuredStandardCount bandeira(s) com taxa específica"
                }
            } else {
                "Bandeiras usam taxa padrão"
            }
            val installmentSummary = if (machine.installmentsEnabled) {
                val customMax = machine.maxInstallments
                if (customMax != null) {
                    "Parcelamento ativo (até ${customMax}x)"
                } else {
                    "Parcelamento padrão (1, 2, 3, 4, 6 e 12x)"
                }
            } else {
                "Sem parcelamento"
            }
            "$brandSummary · $installmentSummary"
        }

        Text(
            text = summaryText,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            color = V19InkMuted,
        )

        if (detailsExpanded) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.28f))
            Spacer(modifier = Modifier.height(10.dp))

            if (machine.brandRatesEnabled) {
                val debitStandardText = CardBrand.entries.joinToString(" · ") { brand ->
                    val rate = machine.brandDebitRatesBps[brand]
                    if (rate != null) {
                        "${brand.label} ${RateFormatter.formatBpsAsPercent(rate)}"
                    } else {
                        "${brand.label} (padrão)"
                    }
                }
                val debitBrandsDetails = if (machine.customBrandRates.isEmpty()) {
                    debitStandardText
                } else {
                    val debitCustomText = machine.customBrandRates.joinToString(" · ") { custom ->
                        val rate = custom.debitRateBps
                        if (rate != null) {
                            "${custom.name} (personalizada) ${RateFormatter.formatBpsAsPercent(rate)}"
                        } else {
                            "${custom.name} (personalizada) (padrão)"
                        }
                    }
                    "$debitStandardText · $debitCustomText"
                }
                Text(
                    text = "Bandeiras (Débito): $debitBrandsDetails",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    color = HomeCardLabelInk,
                    modifier = Modifier.testTag("machine_card_debit_brands_${machine.id}"),
                )
                Spacer(modifier = Modifier.height(6.dp))
            } else {
                Text(
                    text = "Taxas por bandeira desativadas (usa taxa padrão).",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    color = HomeCardLabelInk,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (machine.creditEnabled) {
                if (machine.brandRatesEnabled) {
                    val creditStandardText = CardBrand.entries.joinToString(" · ") { brand ->
                        val rate = machine.brandRatesBps[brand]
                        if (rate != null) {
                            "${brand.label} ${RateFormatter.formatBpsAsPercent(rate)}"
                        } else {
                            "${brand.label} (padrão)"
                        }
                    }
                    val creditBrandsDetails = if (machine.customBrandRates.isEmpty()) {
                        creditStandardText
                    } else {
                        val creditCustomText = machine.customBrandRates.joinToString(" · ") { custom ->
                            val rate = custom.creditRateBps
                            if (rate != null) {
                                "${custom.name} (personalizada) ${RateFormatter.formatBpsAsPercent(rate)}"
                            } else {
                                "${custom.name} (personalizada) (padrão)"
                            }
                        }
                        "$creditStandardText · $creditCustomText"
                    }
                    Text(
                        text = "Bandeiras (Crédito): $creditBrandsDetails",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.sp,
                        color = HomeCardLabelInk,
                        modifier = Modifier.testTag("machine_card_credit_brands_${machine.id}"),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                val installmentDetails = if (machine.installmentsEnabled) {
                    machine.allowedInstallmentTiers().joinToString(" · ") { tier ->
                        val addon = machine.installmentAddonsBps[tier] ?: 0
                        "${tier}x +${RateFormatter.formatBpsAsPercent(addon)}"
                    }
                } else {
                    "Parcelamento desativado (1x sem adicional)."
                }
                Text(
                    text = "Parcelamento: $installmentDetails",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    color = V19PurpleSoftText,
                )
            } else {
                Text(
                    text = "Crédito desativado. Taxa padrão salva: ${RateFormatter.formatBpsAsPercent(machine.creditDefaultRateBps)}.",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    color = V19InkMuted,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ATIVAR CRÉDITO",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = HomeCardLabelInk,
            )
            Switch(
                checked = machine.creditEnabled,
                onCheckedChange = { onToggleCredit() },
                colors = SwitchDefaults.colors(checkedTrackColor = V19PurplePrimary),
                modifier = Modifier.testTag("machine_credit_switch_${machine.id}"),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { detailsExpanded = !detailsExpanded },
                border = BorderStroke(1.1.dp, HomeCardNeonBorder.copy(alpha = 0.58f)),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .fillMaxHeight()
                    .testTag("toggle_machine_rates_${machine.id}"),
            ) {
                Text(
                    text = if (detailsExpanded) "Ocultar" else "Ver taxas",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(HomeCtaGradientStart, HomeCtaGradientEnd),
                        ),
                    )
                    .clickable(onClick = onEdit)
                    .testTag("edit_machine_${machine.id}"),
                contentAlignment = Alignment.Center,
            ) {
                MachineListActionLabel("Editar", R.drawable.mv3d_editar, showListIcons)
            }

            Surface(
                onClick = onRequestDelete,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .fillMaxHeight()
                    .testTag("delete_machine_${machine.id}"),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xAA6A2541),
                border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.55f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MachineListActionLabel("Excluir", R.drawable.mv3d_excluir, showListIcons)
                }
            }
        }
    }
}

@Composable
private fun MachineListActionLabel(
    label: String,
    @DrawableRes iconRes: Int,
    showIcon: Boolean,
) {
    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showIcon) {
            Mv3dIcon(
                iconRes = iconRes,
                contentDescription = null,
                size = 36.dp,
                scalePercent = 160,
                maxSize = 48.dp,
            )
        }
        Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun MachineEditorContent(
    editor: MachineEditorUiState,
    onCancel: () -> Unit,
    onRequestBack: () -> Unit,
    onNameChanged: (String) -> Unit,
    onDebitRateChanged: (String) -> Unit,
    onCreditEnabledChanged: (Boolean) -> Unit,
    onCreditDefaultRateChanged: (String) -> Unit,
    onBrandRatesEnabledChanged: (Boolean) -> Unit,
    onBrandRateChanged: (CardBrand, String) -> Unit,
    onBrandDebitRateChanged: (CardBrand, String) -> Unit,
    onInstallmentsEnabledChanged: (Boolean) -> Unit,
    onInstallmentAddonChanged: (Int, String) -> Unit,
    onMaxInstallmentsChanged: (Int?) -> Unit,
    onToggleBrandSectionExpanded: () -> Unit,
    onToggleInstallmentsSectionExpanded: () -> Unit,
    onNewCustomBrandNameChanged: (String) -> Unit,
    onNewCustomBrandRateChanged: (String) -> Unit,
    onNewCustomBrandCreditRateChanged: (String) -> Unit,
    onNewCustomBrandDebitRateChanged: (String) -> Unit,
    onAddCustomBrand: () -> Unit,
    onCustomBrandRateChanged: (String, String) -> Unit,
    onCustomBrandCreditRateChanged: (String, String) -> Unit,
    onCustomBrandDebitRateChanged: (String, String) -> Unit,
    onRemoveCustomBrand: (String) -> Unit,
    onSave: () -> Unit,
) {
    val isEditing = editor.editingMachineId != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("machine_editor_dialog"),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("machine_editor_scroll"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "editor_primary_fields") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MachinesGlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MachinesIconBadge()
                            Text(
                                text = if (isEditing) {
                                    stringResource(R.string.machines_edit_title)
                                } else {
                                    stringResource(R.string.machines_create_title)
                                },
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { heading() },
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (isEditing) {
                                stringResource(R.string.machines_edit_subtitle)
                            } else {
                                stringResource(R.string.machines_create_subtitle)
                            },
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = HomeCardLabelInk.copy(alpha = 0.88f),
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        ProductsSecondaryOutlinedButton(
                            text = "← ${stringResource(R.string.machines_back_to_list)}",
                            onClick = onRequestBack,
                            testTag = "back_to_machines_list_button",
                        )
                    }

                    MachinesGlassCard {
                        Text(
                            text = stringResource(R.string.machines_name_label),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
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
                                    text = stringResource(R.string.machines_name_placeholder),
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 15.sp,
                                    color = V19InkMuted,
                                )
                            },
                            colors = namesDarkInputColors(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("machine_name_input"),
                        )
                    }

                    MachinesGlassCard {
                        Text(
                            text = stringResource(R.string.machines_debit_rate_label),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = editor.debitRateInput,
                            onValueChange = onDebitRateChanged,
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = Color.White,
                            ),
                            placeholder = {
                                Text(
                                    text = "0,00",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 15.sp,
                                    color = V19InkMuted,
                                )
                            },
                            suffix = {
                                Text(
                                    text = "%",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = V19PurpleSoftText,
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = namesDarkInputColors(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("machine_debit_rate_input"),
                        )
                    }
                }
            }

            if (editor.creditEnabled) {
                item(key = "editor_credit_default_card") {
                    MachinesGlassCard {
                        Text(
                            text = stringResource(R.string.machines_credit_default_rate_label),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = editor.creditDefaultRateInput,
                            onValueChange = onCreditDefaultRateChanged,
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = Color.White,
                            ),
                            placeholder = {
                                Text(
                                    text = "0,00",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 15.sp,
                                    color = V19InkMuted,
                                )
                            },
                            suffix = {
                                Text(
                                    text = "%",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = V19PurpleSoftText,
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = namesDarkInputColors(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("machine_credit_default_rate_input"),
                        )
                    }
                }
            }

            item(key = "editor_flags_card") {
                MachinesGlassCard {
                    MachinesToggleCheckRow(
                        label = stringResource(R.string.machines_enable_credit),
                        checked = editor.creditEnabled,
                        onCheckedChange = onCreditEnabledChanged,
                        switchTestTag = "editor_credit_enabled_switch",
                    )

                    MachinesToggleCheckRow(
                        label = stringResource(R.string.machines_enable_brands),
                        checked = editor.brandRatesEnabled,
                        onCheckedChange = onBrandRatesEnabledChanged,
                        switchTestTag = "editor_brand_rates_switch",
                    )

                    if (editor.creditEnabled) {
                        MachinesToggleCheckRow(
                            label = stringResource(R.string.machines_enable_installments),
                            checked = editor.installmentsEnabled,
                            onCheckedChange = onInstallmentsEnabledChanged,
                            switchTestTag = "editor_installments_switch",
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Crédito desativado: configurações de parcelamento no crédito ficam ocultas.",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            color = V19GoldAccent,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.machines_flags_footer),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = V19InkMuted,
                    )
                }
            }

            if (editor.brandRatesEnabled) {
                item(key = "editor_brand_rates_section") {
                    MachinesCollapsibleSectionCard(
                        title = stringResource(R.string.machines_brands_section_title),
                        subtitle = stringResource(R.string.machines_brands_section_desc),
                        icon = Icons.Filled.CreditCard,
                        iconRes = R.drawable.mv3d_cartao_bancario,
                        iconScalePercent = 180,
                        expanded = editor.isBrandSectionExpanded,
                        onToggleExpand = onToggleBrandSectionExpanded,
                        sectionTestTag = "editor_brand_rates_section",
                        toggleTestTag = "toggle_brand_section_collapse",
                    ) {
                        Text(
                            text = "Bandeiras padrão (${CardBrand.entries.size})",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            modifier = Modifier.testTag("editor_standard_brands_header"),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (editor.creditEnabled) {
                                "Preencha apenas as bandeiras com taxa diferente da padrão no débito e/ou no crédito."
                            } else {
                                "Crédito desativado: configure taxas específicas no débito (taxas de crédito salvas permanecem preservadas)."
                            },
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = V19InkMuted,
                        )
                    }
                }

                if (editor.isBrandSectionExpanded) {
                    items(
                        items = CardBrand.entries,
                        key = { "standard_brand_${it.storageKey}" },
                    ) { brand ->
                        StandardBrandRateRowCard(
                            brand = brand,
                            creditEnabled = editor.creditEnabled,
                            debitRateInput = editor.brandDebitRateInputs[brand].orEmpty(),
                            creditRateInput = editor.brandRateInputs[brand].orEmpty(),
                            onDebitRateChanged = { onBrandDebitRateChanged(brand, it) },
                            onCreditRateChanged = { onBrandRateChanged(brand, it) },
                        )
                    }

                    item(key = "editor_custom_brands_header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("editor_custom_brands_block"),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Bandeiras personalizadas",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White,
                            )
                            Text(
                                text = stringResource(R.string.machines_custom_brands_helper),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = V19InkMuted,
                            )
                        }
                    }

                    item(key = "editor_custom_brands_add") {
                        CustomBrandAddCard(
                            creditEnabled = editor.creditEnabled,
                            newNameInput = editor.newCustomBrandNameInput,
                            newCreditRateInput = editor.newCustomBrandCreditRateInput,
                            newDebitRateInput = editor.newCustomBrandDebitRateInput,
                            onNewNameChanged = onNewCustomBrandNameChanged,
                            onNewCreditRateChanged = onNewCustomBrandCreditRateChanged,
                            onNewDebitRateChanged = onNewCustomBrandDebitRateChanged,
                            onAddCustomBrand = onAddCustomBrand,
                        )
                    }

                    if (!editor.customBrandErrorMessage.isNullOrBlank()) {
                        item(key = "editor_custom_brands_error") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_brand_error_banner"),
                                shape = RoundedCornerShape(14.dp),
                                color = V19ErrorContainer,
                                border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.58f)),
                            ) {
                                Text(
                                    text = editor.customBrandErrorMessage,
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = V19ErrorSoft,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }

                    items(
                        items = editor.customBrandDrafts,
                        key = { "custom_brand_${it.localId}" },
                    ) { customItem ->
                        CustomBrandItemCard(
                            item = customItem,
                            creditEnabled = editor.creditEnabled,
                            onCustomDebitRateChanged = onCustomBrandDebitRateChanged,
                            onCustomCreditRateChanged = onCustomBrandCreditRateChanged,
                            onRemoveCustomBrand = onRemoveCustomBrand,
                        )
                    }
                }
            }

            if (editor.creditEnabled && editor.installmentsEnabled) {
                item(key = "editor_installments_section") {
                    val basePreviewBps = RateFormatter.parsePercentageToBps(editor.creditDefaultRateInput)
                    val activeTiers = InstallmentTiers.allowedFor(editor.maxInstallments)
                    MachinesCollapsibleSectionCard(
                        title = stringResource(R.string.machines_installments_section_title),
                        subtitle = stringResource(R.string.machines_installments_section_desc),
                        icon = Icons.AutoMirrored.Filled.ViewList,
                        iconRes = R.drawable.mv3d_tarifas,
                        expanded = editor.isInstallmentsSectionExpanded,
                        onToggleExpand = onToggleInstallmentsSectionExpanded,
                        sectionTestTag = "editor_installments_section",
                        toggleTestTag = "toggle_installments_section_collapse",
                    ) {
                        MaxInstallmentsSelectorBlock(
                            selectedMax = editor.maxInstallments,
                            onSelectMax = onMaxInstallmentsChanged,
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(
                            color = HomeCardSubtleDivider.copy(alpha = 0.35f),
                            thickness = 1.dp,
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        InstallmentTiers.SUPPORTED.forEachIndexed { index, tier ->
                            val rawAddon = editor.installmentAddonInputs[tier].orEmpty()
                            val addonBps = RateFormatter.parsePercentageToBps(rawAddon) ?: 0
                            val isTierActive = tier in activeTiers
                            val previewText = when {
                                !isTierActive -> "Preservada (fora do limite atual)"
                                basePreviewBps != null && basePreviewBps >= 0 && addonBps >= 0 ->
                                    "Padrão: ${RateFormatter.formatBpsAsPercent(basePreviewBps + addonBps)}"
                                else -> null
                            }

                            MachinesRateOptionRow(
                                title = "${tier}x",
                                subtitle = previewText,
                                badgeIcon = Icons.AutoMirrored.Filled.ViewList,
                                badgeIconRes = R.drawable.mv3d_tarifas,
                                rateValue = rawAddon,
                                onRateChanged = { onInstallmentAddonChanged(tier, it) },
                                inputTestTag = "machine_installment_input_$tier",
                                placeholder = "0,00",
                            )
                            if (index < InstallmentTiers.SUPPORTED.lastIndex) {
                                HorizontalDivider(
                                    color = HomeCardSubtleDivider.copy(alpha = 0.25f),
                                    thickness = 1.dp,
                                )
                            }
                        }
                    }
                }
            }

            if (!editor.errorMessage.isNullOrBlank()) {
                item(key = "editor_error_banner") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("machine_editor_error_banner"),
                        shape = RoundedCornerShape(16.dp),
                        color = V19ErrorContainer,
                        border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.58f)),
                    ) {
                        Text(
                            text = editor.errorMessage,
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = V19ErrorSoft,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            item(key = "editor_action_buttons") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MachinesPrimaryCtaButton(
                        text = if (isEditing) {
                            stringResource(R.string.machines_save_edit)
                        } else {
                            stringResource(R.string.machines_save_create)
                        },
                        enabled = !editor.isSaving,
                        onClick = onSave,
                        testTag = "save_machine_button",
                    )

                    ProductsSecondaryOutlinedButton(
                        text = stringResource(R.string.machines_cancel_action),
                        onClick = onCancel,
                        testTag = "cancel_machine_editor_button",
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaxInstallmentsSelectorBlock(
    selectedMax: Int?,
    onSelectMax: (Int?) -> Unit,
) {
    val currentDescription = if (selectedMax == null) {
        "Atual: Padrão anterior (1x, 2x, 3x, 4x, 6x e 12x)"
    } else {
        "Atual: Até ${selectedMax}x (todas de 1x até ${selectedMax}x)"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("machine_max_installments_selector"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.machines_max_installments_label),
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color.White,
        )
        Text(
            text = currentDescription,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = HomeCtaBorderGlow,
            modifier = Modifier.testTag("machine_max_installments_summary"),
        )

        MaxInstallmentChoicePill(
            label = stringResource(R.string.machines_max_installments_legacy_option),
            selected = selectedMax == null,
            onClick = { onSelectMax(null) },
            testTag = "machine_max_installments_option_legacy",
            modifier = Modifier.fillMaxWidth(),
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InstallmentTiers.SUPPORTED.forEach { tier ->
                MaxInstallmentChoicePill(
                    label = "${tier}x",
                    selected = selectedMax == tier,
                    onClick = { onSelectMax(tier) },
                    testTag = "machine_max_installments_option_$tier",
                    modifier = Modifier.widthIn(min = 54.dp),
                )
            }
        }

        Text(
            text = stringResource(R.string.machines_max_installments_helper),
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = V19InkMuted,
        )
    }
}

@Composable
private fun MaxInstallmentChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(
                if (selected) {
                    Brush.horizontalGradient(listOf(HomeCtaGradientStart, HomeCtaGradientEnd))
                } else {
                    Brush.verticalGradient(listOf(GlassInnerBoxBg, GlassInnerBoxBg))
                },
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) HomeCtaBorderGlow.copy(alpha = 0.90f) else GlassNeonBorder15,
                shape = shape,
            )
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            color = if (selected) Color.White else HomeCardLabelInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StandardBrandRateRowCard(
    brand: CardBrand,
    creditEnabled: Boolean,
    debitRateInput: String,
    creditRateInput: String,
    onDebitRateChanged: (String) -> Unit,
    onCreditRateChanged: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("standard_brand_row_${brand.storageKey}"),
        shape = RoundedCornerShape(18.dp),
        color = GlassInnerBoxBg,
        border = BorderStroke(1.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = brand.label,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (debitRateInput.isBlank() && (!creditEnabled || creditRateInput.isBlank())) {
                    Text(
                        text = "Usa padrão",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = V19InkMuted,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BrandCompactRateField(
                    label = "Débito (%)",
                    value = debitRateInput,
                    placeholder = "Padrão",
                    onValueChange = onDebitRateChanged,
                    testTag = "standard_brand_debit_rate_${brand.storageKey}",
                    modifier = Modifier.weight(1f),
                )
                if (creditEnabled) {
                    BrandCompactRateField(
                        label = "Crédito (%)",
                        value = creditRateInput,
                        placeholder = "Padrão",
                        onValueChange = onCreditRateChanged,
                        testTag = "standard_brand_credit_rate_${brand.storageKey}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandCompactRateField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = V19PurpleSoftText,
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White,
                textAlign = TextAlign.End,
            ),
            placeholder = {
                Text(
                    text = placeholder,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    color = V19InkMuted,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            suffix = {
                Text(
                    text = "%",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = V19PurpleSoftText,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = namesDarkInputColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
        )
    }
}

@Composable
private fun CustomBrandAddCard(
    creditEnabled: Boolean,
    newNameInput: String,
    newCreditRateInput: String,
    newDebitRateInput: String,
    onNewNameChanged: (String) -> Unit,
    onNewCreditRateChanged: (String) -> Unit,
    onNewDebitRateChanged: (String) -> Unit,
    onAddCustomBrand: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = GlassInnerBoxBg,
        border = BorderStroke(1.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newNameInput,
                onValueChange = onNewNameChanged,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = Color.White,
                ),
                placeholder = {
                    Text(
                        text = "Ex.: Crédz, Alelo, Banese Card",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 14.sp,
                        color = V19InkMuted,
                    )
                },
                colors = namesDarkInputColors(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_brand_name_input"),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BrandCompactRateField(
                    label = "Débito (%)",
                    value = newDebitRateInput,
                    placeholder = "0,00",
                    onValueChange = onNewDebitRateChanged,
                    testTag = "new_custom_brand_debit_rate_input",
                    modifier = Modifier.weight(1f),
                )
                if (creditEnabled) {
                    BrandCompactRateField(
                        label = "Crédito (%)",
                        value = newCreditRateInput,
                        placeholder = "0,00",
                        onValueChange = onNewCreditRateChanged,
                        testTag = "new_custom_brand_credit_rate_input",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Button(
                onClick = onAddCustomBrand,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = V19PurplePrimary,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("add_custom_brand_button"),
            ) {
                Text(
                    text = "+ Adicionar",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun CustomBrandItemCard(
    item: CustomBrandDraftItem,
    creditEnabled: Boolean,
    onCustomDebitRateChanged: (String, String) -> Unit,
    onCustomCreditRateChanged: (String, String) -> Unit,
    onRemoveCustomBrand: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("custom_brand_row_${item.localId}"),
        shape = RoundedCornerShape(14.dp),
        color = GlassInnerBoxBg,
        border = BorderStroke(1.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.name,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onRemoveCustomBrand(item.localId) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("remove_custom_brand_${item.localId}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remover bandeira ${item.name}",
                        tint = V19ErrorSoft,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BrandCompactRateField(
                    label = "Débito (%)",
                    value = item.debitRateInput,
                    placeholder = "Padrão",
                    onValueChange = { onCustomDebitRateChanged(item.localId, it) },
                    testTag = "custom_brand_debit_rate_${item.localId}",
                    modifier = Modifier.weight(1f),
                )
                if (creditEnabled) {
                    BrandCompactRateField(
                        label = "Crédito (%)",
                        value = item.creditRateInput,
                        placeholder = "Padrão",
                        onValueChange = { onCustomCreditRateChanged(item.localId, it) },
                        testTag = "custom_brand_credit_rate_${item.localId}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
