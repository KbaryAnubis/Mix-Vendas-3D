package com.mixvendas3d.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SaleItemCalculationResult
import com.mixvendas3d.app.domain.models.SalePersonTotalSnapshot
import com.mixvendas3d.app.ui.theme.GlassInnerBoxBg
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeCardGlassBottom
import com.mixvendas3d.app.ui.theme.HomeCardGlassTop
import com.mixvendas3d.app.ui.theme.HomeCardLabelInk
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
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SurfaceCard
import com.mixvendas3d.app.util.CurrencyFormatter
import com.mixvendas3d.app.util.RateFormatter

@Composable
fun VendasFilterDropdownBar(
    selectedLabel: String,
    testTag: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingIconRes: Int? = null,
    dropdownContent: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .clickable(role = Role.Button) { expanded = true }
                .semantics(mergeDescendants = true) {}
                .testTag(testTag),
            shape = shape,
            color = GlassInnerBoxBg,
            border = BorderStroke(1.2.dp, GlassNeonBorder15),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                } else if (leadingIconRes != null) {
                    Mv3dIcon(
                        iconRes = leadingIconRes,
                        contentDescription = null,
                        size = 40.dp,
                    )
                }
                Text(
                    text = selectedLabel,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = V19PurpleSoftText,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = V19SurfaceCard,
        ) {
            dropdownContent { expanded = false }
        }
    }
}

@Composable
fun VendasFilterDropdownOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else V19InkPrimary,
            )
        },
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
fun VendasDiscreetSummaryCard(
    salesCount: Int,
    totalGross: MoneyCents,
    totalDiscount: MoneyCents,
    totalFee: MoneyCents,
    totalNet: MoneyCents,
    personSummaries: List<SalePersonTotalSnapshot>,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sales_top_summary_card"),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(HomeCardGlassTop, HomeCardGlassBottom)))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.sales_summary_title),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = HomeCardLabelInk,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = GlassInnerBoxBg,
                    border = BorderStroke(1.dp, GlassNeonBorder15),
                ) {
                    Text(
                        text = "$salesCount ${if (salesCount == 1) "venda" else "vendas"}",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = V19PurpleSoftText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DiscreetSummaryTile(
                    label = stringResource(R.string.sales_total_gross),
                    amount = totalGross,
                    subtext = if (totalDiscount.rawCents > 0L) {
                        "Descontos: ${CurrencyFormatter.formatBrl(totalDiscount)}"
                    } else {
                        "$salesCount lançamento(s)"
                    },
                    amountColor = Color.White,
                    modifier = Modifier.weight(1f),
                )
                DiscreetSummaryTile(
                    label = stringResource(R.string.sales_total_net),
                    amount = totalNet,
                    subtext = "Taxas: ${CurrencyFormatter.formatBrl(totalFee)}",
                    amountColor = V19MintSuccess,
                    modifier = Modifier.weight(1f),
                )
            }

            if (personSummaries.isNotEmpty()) {
                HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.25f))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.sales_people_net_title),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = V19PurpleSoftText,
                    )
                    personSummaries.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pair.forEach { summary ->
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics(mergeDescendants = true) {}
                                        .testTag("sales_person_net_${summary.personId}"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = GlassInnerBoxBg,
                                    border = BorderStroke(1.dp, GlassNeonBorder15),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(personAccentColor(summary.personColorIndex)),
                                            )
                                            Text(
                                                text = summary.personName,
                                                fontFamily = PlusJakartaSansFontFamily,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp,
                                                color = V19InkPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Text(
                                            text = CurrencyFormatter.formatBrl(summary.netAmount),
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscreetSummaryTile(
    label: String,
    amount: MoneyCents,
    subtext: String,
    amountColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = GlassInnerBoxBg,
        border = BorderStroke(1.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = V19PurpleSoftText,
            )
            Text(
                text = CurrencyFormatter.formatBrl(amount),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = amountColor,
                maxLines = 1,
            )
            Text(
                text = subtext,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                color = V19InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun VendasSaleHeroCard(
    sale: PersistedSale,
    deleteEnabled: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val calc = sale.calculation
    var detailsExpanded by rememberSaveable(sale.id) { mutableStateOf(false) }
    val productsTitle = remember(sale.id, calc.items) {
        calc.items.joinToString(" + ") { "${it.quantity}× ${it.productName}" }
    }
    val dateTimeText = remember(sale.soldAtEpochMs) {
        CurrencyFormatter.formatEpochMillis(sale.soldAtEpochMs)
    }
    val productCountLabel = remember(calc.items.size) {
        "${calc.items.size} ${if (calc.items.size == 1) "produto" else "produtos"}"
    }
    val cardShape = RoundedCornerShape(24.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sale_history_card_${sale.id}"),
        shape = cardShape,
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(HomeCardGlassTop, HomeCardGlassBottom),
                    ),
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Cabeçalho da venda: data, horário, quantidade de produtos e valor cobrado em destaque
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Mv3dIcon(
                        iconRes = R.drawable.mv3d_calendario,
                        contentDescription = null,
                        size = 38.dp,
                    )
                    Text(
                        text = "$dateTimeText · $productCountLabel",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sale_history_date_${sale.id}"),
                    )
                }

                Text(
                    text = CurrencyFormatter.formatBrl(calc.finalAmount),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    letterSpacing = (-0.6).sp,
                    color = Color.White,
                )

                Text(
                    text = productsTitle,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = HomeCardLabelInk.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Caixa interna de resumo financeiro
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = GlassInnerBoxBg,
                border = BorderStroke(1.dp, GlassNeonBorder15),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    VendasKeyValueRow(
                        label = stringResource(R.string.sales_subtotal_products),
                        value = CurrencyFormatter.formatBrl(calc.originalSubtotal),
                        labelColor = HomeCardLabelInk,
                        valueColor = Color.White,
                    )
                    if (calc.discountTotal.rawCents > 0L) {
                        val discountScopeLabel = when (val cfg = calc.discountConfig) {
                            is SaleDiscountConfigSnapshot.Proportional -> "Proporcional"
                            is SaleDiscountConfigSnapshot.SpecificProduct -> "Em ${cfg.productNameSnapshot}"
                        }
                        VendasKeyValueRow(
                            label = "Desconto ($discountScopeLabel)",
                            value = "− ${CurrencyFormatter.formatBrl(calc.discountTotal)}",
                            labelColor = HomeCardLabelInk,
                            valueColor = V19GoldAccent,
                        )
                    }
                    VendasKeyValueRow(
                        label = stringResource(R.string.sales_total_charged),
                        value = CurrencyFormatter.formatBrl(calc.finalAmount),
                        labelColor = Color.White,
                        valueColor = Color.White,
                        labelWeight = FontWeight.Bold,
                        valueWeight = FontWeight.ExtraBold,
                    )

                    if (calc.paymentMethod.usesMachine && calc.machineSnapshot != null) {
                        val snap = calc.machineSnapshot
                        val rateOrigin = if (snap.manualRateOverrideBps != null) " (manual)" else ""
                        HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.28f))
                        VendasKeyValueRow(
                            label = "Taxa da maquininha$rateOrigin",
                            value = RateFormatter.formatBpsAsPercent(calc.effectiveFeeRateBps),
                            labelColor = V19InkMuted,
                            valueColor = V19InkPrimary,
                        )
                        VendasKeyValueRow(
                            label = "Tarifa cobrada",
                            value = "− ${CurrencyFormatter.formatBrl(calc.feeTotal)}",
                            labelColor = V19InkMuted,
                            valueColor = V19ErrorSoft,
                        )
                        VendasKeyValueRow(
                            label = "Valor líquido",
                            value = CurrencyFormatter.formatBrl(calc.netTotal),
                            labelColor = HomeCardLabelInk,
                            valueColor = V19MintSuccess,
                            labelWeight = FontWeight.SemiBold,
                            valueWeight = FontWeight.Bold,
                        )
                    } else if (calc.discountTotal.rawCents > 0L) {
                        HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.28f))
                        VendasKeyValueRow(
                            label = "Valor líquido",
                            value = CurrencyFormatter.formatBrl(calc.netTotal),
                            labelColor = HomeCardLabelInk,
                            valueColor = V19MintSuccess,
                            labelWeight = FontWeight.SemiBold,
                            valueWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Forma de pagamento e dados de maquininha preservados do snapshot
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VendasKeyValueRow(
                    label = stringResource(R.string.sales_payment_method_label),
                    value = calc.paymentMethod.label,
                    leadingIconRes = when (calc.paymentMethod) {
                        SalePaymentMethod.PIX -> R.drawable.mv3d_pix
                        SalePaymentMethod.DEBIT, SalePaymentMethod.CREDIT -> R.drawable.mv3d_cartao_bancario
                        SalePaymentMethod.CASH -> null
                    },
                    labelColor = HomeCardLabelInk,
                    valueColor = Color.White,
                    valueWeight = FontWeight.Bold,
                )
                if (calc.paymentMethod.usesMachine && calc.machineSnapshot != null) {
                    val snap = calc.machineSnapshot
                    val machineLabel = buildString {
                        append(snap.machineName)
                        snap.appliedBrandDisplayLabel?.let { append(" · $it") }
                        snap.appliedInstallments?.let { inst ->
                            if (inst > 1 || snap.installmentsEnabled) append(" · ${inst}x")
                        }
                    }
                    VendasKeyValueRow(
                        label = "Maquininha",
                        value = machineLabel,
                        labelColor = V19InkMuted,
                        valueColor = V19PurpleSoftText,
                        valueWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Detalhamento expansível dos produtos e participantes
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val toggleShape = RoundedCornerShape(14.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(toggleShape)
                        .clickable(role = Role.Button) { detailsExpanded = !detailsExpanded }
                        .testTag("sale_details_toggle_${sale.id}"),
                    shape = toggleShape,
                    color = GlassInnerBoxBg,
                    border = BorderStroke(1.dp, GlassNeonBorder15),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Mv3dIcon(
                                iconRes = R.drawable.mv3d_ver_mais,
                                contentDescription = null,
                                size = 32.dp,
                            )
                            Text(
                                text = if (detailsExpanded) {
                                    "Ocultar detalhes dos itens (${calc.items.size})"
                                } else {
                                    "Ver detalhes dos itens (${calc.items.size})"
                                },
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = V19PurpleSoftText,
                            )
                        }
                        Icon(
                            imageVector = if (detailsExpanded) {
                                Icons.Filled.KeyboardArrowUp
                            } else {
                                Icons.Filled.KeyboardArrowDown
                            },
                            contentDescription = if (detailsExpanded) "Recolher detalhes" else "Expandir detalhes",
                            tint = V19PurpleSoftText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (detailsExpanded) {
                    val receiptDateText = remember(sale.soldAtEpochMs) {
                        CurrencyFormatter.formatDateOfEpochMillis(sale.soldAtEpochMs)
                    }
                    val receiptTimeText = remember(sale.soldAtEpochMs) {
                        CurrencyFormatter.formatTimeOfEpochMillis(sale.soldAtEpochMs)
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .testTag("sale_receipt_summary_${sale.id}"),
                        shape = RoundedCornerShape(14.dp),
                        color = GlassInnerBoxBg,
                        border = BorderStroke(1.dp, GlassNeonBorder15),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.sales_receipt_title),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = V19PurpleSoftText,
                            )
                            VendasKeyValueRow(
                                label = "Data da venda",
                                value = receiptDateText,
                                labelColor = HomeCardLabelInk,
                                valueColor = Color.White,
                                valueWeight = FontWeight.Bold,
                            )
                            VendasKeyValueRow(
                                label = "Horário",
                                value = receiptTimeText,
                                labelColor = HomeCardLabelInk,
                                valueColor = Color.White,
                                valueWeight = FontWeight.SemiBold,
                            )
                            VendasKeyValueRow(
                                label = "Valor final",
                                value = CurrencyFormatter.formatBrl(calc.finalAmount),
                                labelColor = HomeCardLabelInk,
                                valueColor = V19GoldAccent,
                                valueWeight = FontWeight.ExtraBold,
                            )
                        }
                    }

                    calc.items.forEach { item ->
                        VendasProductDetailCard(
                            saleId = sale.id,
                            item = item,
                            usesMachine = calc.paymentMethod.usesMachine,
                        )
                    }
                }
            }

            // Seção Líquido total de cada pessoa (sem barra proporcional e sem soma redundante verde)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.sales_people_section_title),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = Color.White,
                )
                calc.personTotals.forEachIndexed { index, alloc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HomePersonAvatarBadge(
                            badgeSize = 38.dp,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = alloc.personName,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.sales_person_individual_net),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                color = V19InkMuted,
                                maxLines = 1,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = CurrencyFormatter.formatBrl(alloc.netAmount),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                    if (index < calc.personTotals.lastIndex) {
                        HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.25f))
                    }
                }
            }

            if (calc.note.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = GlassInnerBoxBg,
                    border = BorderStroke(1.dp, GlassNeonBorder15),
                ) {
                    Text(
                        text = "Observação: ${calc.note}",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = V19PurpleSoftText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }

            // Botões Editar (roxo neon) e Excluir (vinho/rosa escuro)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val editShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = editShape,
                            ambientColor = HomeCtaShadowGlow.copy(alpha = 0.45f),
                            spotColor = HomeCtaShadowGlow.copy(alpha = 0.60f),
                        )
                        .clip(editShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    HomeCtaGradientStart.copy(alpha = 0.85f),
                                    HomeCtaGradientEnd.copy(alpha = 0.85f),
                                ),
                            ),
                        )
                        .border(1.2.dp, HomeCtaBorderGlow.copy(alpha = 0.78f), editShape)
                        .clickable(role = Role.Button, onClick = onEditClick)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("sale_edit_${sale.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Mv3dIcon(
                            iconRes = R.drawable.mv3d_editar,
                            contentDescription = null,
                            size = 34.dp,
                            maxSize = 42.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.sales_edit_action),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                        )
                    }
                }

                val deleteShape = RoundedCornerShape(14.dp)
                val deleteBorderColor = if (deleteEnabled) {
                    Color(0xFFE07A9A).copy(alpha = 0.78f)
                } else {
                    Color(0xFFE07A9A).copy(alpha = 0.32f)
                }
                val deleteGradient = if (deleteEnabled) {
                    Brush.horizontalGradient(listOf(Color(0xFF5B1835), Color(0xFF7B2146)))
                } else {
                    Brush.horizontalGradient(listOf(Color(0x805B1835), Color(0x807B2146)))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(deleteShape)
                        .background(deleteGradient)
                        .border(1.2.dp, deleteBorderColor, deleteShape)
                        .clickable(
                            enabled = deleteEnabled,
                            role = Role.Button,
                            onClick = onDeleteClick,
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("sale_delete_${sale.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Mv3dIcon(
                            iconRes = R.drawable.mv3d_excluir,
                            contentDescription = null,
                            size = 34.dp,
                            maxSize = 42.dp,
                            alpha = if (deleteEnabled) 1f else 0.55f,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.sales_delete_action),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (deleteEnabled) Color.White else Color.White.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VendasProductDetailCard(
    saleId: String,
    item: SaleItemCalculationResult,
    usesMachine: Boolean,
) {
    val itemShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sale_item_detail_${saleId}_${item.productId}"),
        shape = itemShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassInnerBoxBg)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Mv3dIcon(
                    iconRes = R.drawable.mv3d_produtos,
                    contentDescription = null,
                    size = 40.dp,
                )
                Text(
                    text = "${item.quantity}× ${item.productName}",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = CurrencyFormatter.formatBrl(item.finalAmount),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = Color.White,
                    maxLines = 1,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Valor original: ${CurrencyFormatter.formatBrl(item.originalSubtotal)}",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = V19InkMuted,
                    modifier = Modifier.weight(1f),
                )
                if (item.discountAmount.rawCents > 0L) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Desconto − ${CurrencyFormatter.formatBrl(item.discountAmount)}",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = V19GoldAccent,
                    )
                }
            }

            if (usesMachine) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Tarifa (${RateFormatter.formatBpsAsPercent(item.appliedFeeRateBps)}): − ${CurrencyFormatter.formatBrl(item.feeAmount)}",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = V19InkMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Líquido: ${CurrencyFormatter.formatBrl(item.netAmount)}",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = V19MintSuccess,
                    )
                }
            } else {
                VendasKeyValueRow(
                    label = "Líquido do produto",
                    value = CurrencyFormatter.formatBrl(item.netAmount),
                    labelColor = V19InkMuted,
                    valueColor = V19MintSuccess,
                )
            }

            if (item.participantSplits.isNotEmpty()) {
                HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.25f))
                item.participantSplits.forEachIndexed { splitIdx, split ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HomePersonAvatarBadge(
                            badgeSize = 34.dp,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = split.personName,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (usesMachine && split.feeAmount.rawCents > 0L) {
                                Text(
                                    text = "Bruto ${CurrencyFormatter.formatBrl(split.grossAmount)} · Taxa − ${CurrencyFormatter.formatBrl(split.feeAmount)}",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 11.sp,
                                    color = V19InkMuted,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = CurrencyFormatter.formatBrl(split.netAmount),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                    if (splitIdx < item.participantSplits.lastIndex) {
                        HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.16f))
                    }
                }
            }
        }
    }
}

@Composable
private fun VendasKeyValueRow(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
    labelWeight: FontWeight = FontWeight.Normal,
    valueWeight: FontWeight = FontWeight.SemiBold,
    leadingIconRes: Int? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIconRes != null) {
            val isPix = leadingIconRes == R.drawable.mv3d_pix
            Mv3dIcon(
                iconRes = leadingIconRes,
                contentDescription = null,
                size = if (isPix) 28.dp else 32.dp,
                scalePercent = if (isPix) null else 180,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = labelWeight,
            fontSize = 13.sp,
            color = labelColor,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = valueWeight,
            fontSize = 13.sp,
            color = valueColor,
        )
    }
}

@Composable
fun VendasEmptyStateCard(
    onGoToSell: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("vendas_empty_state"),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(HomeCardGlassTop, HomeCardGlassBottom),
                    ),
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Mv3dIcon(
                iconRes = R.drawable.mv3d_vendas,
                contentDescription = null,
                size = 64.dp,
                modifier = Modifier.testTag("icon_mv3d_vendas_empty"),
            )
            Text(
                text = stringResource(R.string.sales_empty_title),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.sales_empty_subtitle),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                color = V19InkMuted,
            )
            val ctaShape = RoundedCornerShape(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = ctaShape,
                        ambientColor = HomeCtaShadowGlow.copy(alpha = 0.55f),
                        spotColor = HomeCtaShadowGlow.copy(alpha = 0.70f),
                    )
                    .clip(ctaShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(HomeCtaGradientStart, HomeCtaGradientEnd),
                        ),
                    )
                    .border(1.2.dp, HomeCtaBorderGlow.copy(alpha = 0.88f), ctaShape)
                    .clickable(role = Role.Button, onClick = onGoToSell)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("vendas_empty_sell_cta"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.sales_empty_cta),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
fun VendasFilterEmptyStateCard(
    hasSelectedDay: Boolean,
    onClearSelectedDay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("vendas_filter_empty_state"),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(HomeCardGlassTop, HomeCardGlassBottom),
                    ),
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.sales_filter_empty_title),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = V19InkMuted,
            )
            if (hasSelectedDay) {
                OutlinedButton(
                    onClick = onClearSelectedDay,
                    border = BorderStroke(1.dp, V19GoldAccent.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = V19GoldAccent),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sales_day_filter_clear),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = V19GoldAccent,
                    )
                }
            }
        }
    }
}
