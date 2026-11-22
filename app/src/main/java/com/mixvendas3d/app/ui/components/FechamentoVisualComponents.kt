package com.mixvendas3d.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PreviewPeriodFilter
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.models.SalePersonTotalSnapshot
import com.mixvendas3d.app.domain.reporting.RealDailySalesReport
import com.mixvendas3d.app.ui.screens.RealMachineBreakdown
import com.mixvendas3d.app.ui.screens.RealPaymentMethodBreakdown
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

@Composable
fun FechamentoPeriodSelector(
    selectedPeriod: PreviewPeriodFilter,
    fromDate: String,
    toDate: String,
    onPeriodSelected: (PreviewPeriodFilter) -> Unit,
    onCustomDatesChanged: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clip(shape)
                    .clickable(role = Role.Button) { expanded = true }
                    .semantics(mergeDescendants = true) {}
                    .testTag("period_dropdown_button"),
                shape = shape,
                color = Color.Transparent,
                border = BorderStroke(1.2.dp, GlassNeonBorder15),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(HomeCardGlassTop, HomeCardGlassBottom)))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Mv3dIcon(
                        iconRes = R.drawable.mv3d_calendario,
                        contentDescription = stringResource(R.string.closing_period_label),
                        size = 40.dp,
                    )

                    Text(
                        text = selectedPeriod.label,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
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
                PreviewPeriodFilter.entries.forEach { period ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = period.label,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = if (period == selectedPeriod) FontWeight.Bold else FontWeight.Normal,
                                color = if (period == selectedPeriod) Color.White else V19InkPrimary,
                            )
                        },
                        onClick = {
                            expanded = false
                            onPeriodSelected(period)
                        },
                        modifier = Modifier.testTag("period_option_${period.name.lowercase()}"),
                    )
                }
            }
        }

        if (selectedPeriod == PreviewPeriodFilter.CUSTOM) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = fromDate,
                    onValueChange = { onCustomDatesChanged(it, toDate) },
                    label = { Text("De (AAAA-MM-DD)") },
                    placeholder = { Text("2026-11-01") },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color.White,
                    ),
                    colors = namesDarkInputColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("period_from_input"),
                )
                OutlinedTextField(
                    value = toDate,
                    onValueChange = { onCustomDatesChanged(fromDate, it) },
                    label = { Text("Até (AAAA-MM-DD)") },
                    placeholder = { Text("2026-11-30") },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = Color.White,
                    ),
                    colors = namesDarkInputColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("period_to_input"),
                )
            }
        }
    }
}

@Composable
fun FechamentoHeroSummaryCard(
    salesCount: Int,
    totalGross: MoneyCents,
    totalFee: MoneyCents,
    totalNet: MoneyCents,
    originalSubtotal: MoneyCents,
    discountTotal: MoneyCents,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("hero_summary_card"),
        shape = cardShape,
        color = Color.Transparent,
        border = BorderStroke(1.3.dp, GlassNeonBorder15),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeMiniIconBadge(
                    iconRes = R.drawable.mv3d_dinheiro_moedas,
                    contentDescription = null,
                    badgeSize = 50.dp,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.closing_total_sold_label),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = HomeCardLabelInk,
                    )
                    Text(
                        text = CurrencyFormatter.formatBrl(totalGross),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        letterSpacing = (-0.6).sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

            }

            Surface(
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(999.dp),
                color = GlassInnerBoxBg,
                border = BorderStroke(1.dp, GlassNeonBorder15),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Mv3dIcon(
                        iconRes = R.drawable.mv3d_vendas,
                        contentDescription = null,
                        size = 24.dp,
                    )
                    Text(
                        text = "$salesCount ${if (salesCount == 1) "venda" else "vendas"}",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }

            // Linhas secundárias preservando Subtotal original e Descontos concedidos
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = GlassInnerBoxBg,
                border = BorderStroke(1.dp, GlassNeonBorder15),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.closing_original_subtotal_label),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = V19InkMuted,
                        )
                        Text(
                            text = CurrencyFormatter.formatBrl(originalSubtotal),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.closing_discounts_label),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = V19InkMuted,
                        )
                        Text(
                            text = "− ${CurrencyFormatter.formatBrl(discountTotal)}",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (discountTotal.rawCents > 0L) V19GoldAccent else V19InkPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }

            HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.34f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroMiniStat(
                    iconRes = R.drawable.mv3d_tarifas,
                    label = stringResource(R.string.closing_fees_label),
                    value = "− ${CurrencyFormatter.formatBrl(totalFee)}",
                    valueColor = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .width(1.dp)
                        .height(38.dp)
                        .background(HomeCardSubtleDivider.copy(alpha = 0.35f)),
                )
                HeroMiniStat(
                    iconRes = R.drawable.mv3d_dinheiro_notas,
                    label = stringResource(R.string.closing_net_label),
                    value = CurrencyFormatter.formatBrl(totalNet),
                    valueColor = V19MintSuccess,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroMiniStat(
    @DrawableRes iconRes: Int,
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Mv3dIcon(
            iconRes = iconRes,
            contentDescription = null,
            size = 40.dp,
            maxSize = 40.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = V19InkMuted,
                maxLines = 1,
            )
            Text(
                text = value,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun FechamentoPeopleGridSection(
    personSummaries: List<SalePersonTotalSnapshot>,
    emptyMessage: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Mv3dIcon(
                iconRes = R.drawable.mv3d_pessoas,
                contentDescription = null,
                size = 48.dp,
            )
            Text(
                text = stringResource(R.string.closing_people_title),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
        }

        if (personSummaries.isEmpty()) {
            val shape = RoundedCornerShape(18.dp)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                color = GlassInnerBoxBg,
                border = BorderStroke(1.dp, GlassNeonBorder15),
            ) {
                Text(
                    text = emptyMessage,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 13.sp,
                    color = V19InkMuted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val twoColumns = maxWidth >= 320.dp
                if (twoColumns) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        personSummaries.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                pair.forEach { summary ->
                                    FechamentoPersonNetCard(
                                        summary = summary,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        personSummaries.forEach { summary ->
                            FechamentoPersonNetCard(
                                summary = summary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FechamentoPersonNetCard(
    summary: SalePersonTotalSnapshot,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .testTag("closing_person_card_${summary.personId}"),
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
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomePersonAvatarBadge(
                    badgeSize = 38.dp,
                )
                Text(
                    text = summary.personName,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = HomeCardLabelInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = CurrencyFormatter.formatBrl(summary.netAmount),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                letterSpacing = (-0.4).sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun FechamentoIndividualConferenceCard(
    personSummaries: List<SalePersonTotalSnapshot>,
    emptyMessage: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("closing_individual_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.closing_individual_title),
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            color = Color.White,
            modifier = Modifier.semantics { heading() },
        )

        if (personSummaries.isEmpty()) {
            val emptyShape = RoundedCornerShape(18.dp)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = emptyShape,
                color = GlassInnerBoxBg,
                border = BorderStroke(1.dp, GlassNeonBorder15),
            ) {
                Text(
                    text = emptyMessage,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 13.sp,
                    color = V19InkMuted,
                    modifier = Modifier
                        .padding(16.dp)
                        .testTag("closing_individual_empty_message"),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                personSummaries.forEach { summary ->
                    FechamentoIndividualPersonRow(summary = summary)
                }
            }
        }
    }
}

@Composable
private fun FechamentoIndividualPersonRow(
    summary: SalePersonTotalSnapshot,
) {
    val rowShape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("closing_individual_row_${summary.personId}"),
        shape = rowShape,
        color = Color.Transparent,
        border = BorderStroke(1.1.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(HomeCardGlassTop, HomeCardGlassBottom),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HomePersonAvatarBadge(
                    badgeSize = 38.dp,
                )
                Text(
                    text = summary.personName,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = CurrencyFormatter.formatBrl(summary.netAmount),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = V19MintSuccess,
                    maxLines = 1,
                )
            }

            HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.25f))

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 300.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IndividualMetricCell(
                            label = stringResource(R.string.closing_individual_gross_label),
                            value = CurrencyFormatter.formatBrl(summary.grossAmount),
                            valueColor = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .width(1.dp)
                                .height(28.dp)
                                .background(HomeCardSubtleDivider.copy(alpha = 0.30f)),
                        )
                        IndividualMetricCell(
                            label = stringResource(R.string.closing_individual_fees_label),
                            value = "− ${CurrencyFormatter.formatBrl(summary.feeAmount)}",
                            valueColor = if (summary.feeAmount.rawCents > 0L) V19ErrorSoft else V19InkPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .width(1.dp)
                                .height(28.dp)
                                .background(HomeCardSubtleDivider.copy(alpha = 0.30f)),
                        )
                        IndividualMetricCell(
                            label = stringResource(R.string.closing_individual_net_label),
                            value = CurrencyFormatter.formatBrl(summary.netAmount),
                            valueColor = V19MintSuccess,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IndividualMetricCompactLine(
                            label = stringResource(R.string.closing_individual_gross_label),
                            value = CurrencyFormatter.formatBrl(summary.grossAmount),
                            valueColor = Color.White,
                        )
                        IndividualMetricCompactLine(
                            label = stringResource(R.string.closing_individual_fees_label),
                            value = "− ${CurrencyFormatter.formatBrl(summary.feeAmount)}",
                            valueColor = if (summary.feeAmount.rawCents > 0L) V19ErrorSoft else V19InkPrimary,
                        )
                        IndividualMetricCompactLine(
                            label = stringResource(R.string.closing_individual_net_label),
                            value = CurrencyFormatter.formatBrl(summary.netAmount),
                            valueColor = V19MintSuccess,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IndividualMetricCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            color = V19InkMuted,
            maxLines = 1,
        )
        Text(
            text = value,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IndividualMetricCompactLine(
    label: String,
    value: String,
    valueColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 12.sp,
            color = V19InkMuted,
        )
        Text(
            text = value,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = valueColor,
        )
    }
}

@Composable
fun FechamentoPaymentsCard(
    salesCount: Int,
    paymentBreakdowns: List<RealPaymentMethodBreakdown>,
    emptyMessage: String,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("closing_payments_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.closing_payments_title),
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            color = Color.White,
            modifier = Modifier.semantics { heading() },
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (salesCount == 0) {
                    Text(
                        text = emptyMessage,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 13.sp,
                        color = V19InkMuted,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .testTag("closing_payments_empty_message"),
                    )
                } else {
                    paymentBreakdowns.forEachIndexed { index, row ->
                        val feeSubtitle = if (row.method.usesMachine) {
                            "${row.count} ${if (row.count == 1) "venda" else "vendas"} · Taxas ${CurrencyFormatter.formatBrl(row.fee)}"
                        } else {
                            "${row.count} ${if (row.count == 1) "venda" else "vendas"} · Sem tarifa de maquininha"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                                .semantics(mergeDescendants = true) {}
                                .testTag("closing_payment_row_${row.method.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            when (row.method) {
                                SalePaymentMethod.CASH -> Mv3dIcon(
                                    iconRes = R.drawable.mv3d_dinheiro_notas,
                                    contentDescription = null,
                                    size = 44.dp,
                                    scalePercent = 220,
                                    maxSize = 72.dp,
                                )
                                SalePaymentMethod.PIX -> Mv3dIcon(
                                    iconRes = R.drawable.mv3d_pix,
                                    contentDescription = null,
                                    size = 28.dp,
                                    containerSize = 72.dp,
                                    scalePercent = 220,
                                )
                                SalePaymentMethod.DEBIT,
                                SalePaymentMethod.CREDIT,
                                -> Mv3dIcon(
                                    iconRes = R.drawable.mv3d_cartao_bancario,
                                    contentDescription = null,
                                    size = 44.dp,
                                    scalePercent = 220,
                                    maxSize = 72.dp,
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = row.method.label,
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                )
                                Text(
                                    text = feeSubtitle,
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 12.sp,
                                    color = V19InkMuted,
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = CurrencyFormatter.formatBrl(row.gross),
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = Color.White,
                                )
                                Text(
                                    text = "Líq. ${CurrencyFormatter.formatBrl(row.net)}",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = V19MintSuccess,
                                )
                            }
                        }
                        if (index < paymentBreakdowns.lastIndex) {
                            HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.28f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FechamentoMachinesCard(
    salesCount: Int,
    machineBreakdowns: List<RealMachineBreakdown>,
    emptyMessage: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("closing_machines_card"),
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
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onToggleExpanded)
                    .padding(vertical = 4.dp)
                    .testTag("closing_machines_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Mv3dIcon(
                    iconRes = R.drawable.mv3d_maquininhas,
                    contentDescription = null,
                    size = 50.dp,
                    scalePercent = 220,
                    maxSize = 96.dp,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.closing_machines_title),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White,
                    )
                    Text(
                        text = stringResource(R.string.closing_machines_subtitle),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.sp,
                        color = V19InkMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.closing_collapse_machines_cd else R.string.closing_expand_machines_cd,
                    ),
                    tint = V19PurpleSoftText,
                    modifier = Modifier.size(24.dp),
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.28f))
                Spacer(modifier = Modifier.height(8.dp))

                if (machineBreakdowns.isEmpty()) {
                    Text(
                        text = if (salesCount == 0) {
                            emptyMessage
                        } else {
                            "Nenhuma venda no cartão neste filtro."
                        },
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 13.sp,
                        color = V19InkMuted,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .testTag("closing_machines_empty_message"),
                    )
                } else {
                    machineBreakdowns.forEachIndexed { idx, machine ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .semantics(mergeDescendants = true) {}
                                .testTag("closing_machine_row_${machine.machineKey}"),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = machine.machineName,
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = GlassInnerBoxBg,
                                    border = BorderStroke(1.dp, GlassNeonBorder15),
                                ) {
                                    Text(
                                        text = machine.paymentMethod.label,
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = V19PurpleSoftText,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            Text(
                                text = "${machine.count} ${if (machine.count == 1) "venda" else "vendas"} · Movimentado ${CurrencyFormatter.formatBrl(machine.gross)}",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 13.sp,
                                color = V19InkMuted,
                            )
                            Text(
                                text = "Tarifas − ${CurrencyFormatter.formatBrl(machine.fee)} · Líquido ${CurrencyFormatter.formatBrl(machine.net)}",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = V19MintSuccess,
                            )
                            Text(
                                text = if (machine.singleRateSummaryLabel != null) {
                                    "Taxa aplicada: ${machine.singleRateSummaryLabel}"
                                } else {
                                    buildString {
                                        append("Múltiplas taxas efetivas aplicadas — valores somados")
                                        if (machine.hasManualOverride) {
                                            append(" (inclui tarifa manual)")
                                        }
                                    }
                                },
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 12.sp,
                                color = V19PurpleSoftText,
                            )
                        }
                        if (idx < machineBreakdowns.lastIndex) {
                            HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.24f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FechamentoExportCsvButton(
    enabled: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val bgBrush = if (enabled) {
        Brush.horizontalGradient(listOf(HomeCtaGradientStart, HomeCtaGradientEnd))
    } else {
        Brush.horizontalGradient(
            listOf(
                HomeCtaGradientStart.copy(alpha = 0.45f),
                HomeCtaGradientEnd.copy(alpha = 0.45f),
            ),
        )
    }
    val borderColor = if (enabled) {
        HomeCtaBorderGlow.copy(alpha = 0.88f)
    } else {
        HomeCtaBorderGlow.copy(alpha = 0.35f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .shadow(
                elevation = if (enabled) 14.dp else 0.dp,
                shape = shape,
                ambientColor = HomeCtaShadowGlow.copy(alpha = 0.65f),
                spotColor = HomeCtaShadowGlow.copy(alpha = 0.80f),
            )
            .clip(shape)
            .background(bgBrush)
            .border(1.4.dp, borderColor, shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 15.dp)
            .testTag("export_csv_button"),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.60f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isBusy) {
                    stringResource(R.string.closing_export_csv_in_progress)
                } else {
                    stringResource(R.string.closing_export_csv_action)
                },
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.60f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun FechamentoDailySalesCard(
    report: RealDailySalesReport,
    emptyMessage: String,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("closing_daily_sales_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.closing_daily_sales_title),
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            color = Color.White,
            modifier = Modifier.semantics { heading() },
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (report.months.isEmpty()) {
                    Text(
                        text = emptyMessage,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 13.sp,
                        color = V19InkMuted,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .testTag("closing_daily_empty_message"),
                    )
                } else {
                    report.months.forEachIndexed { monthIndex, month ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("closing_month_${month.yearMonth}"),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("closing_month_header_${month.yearMonth}"),
                                shape = RoundedCornerShape(12.dp),
                                color = GlassInnerBoxBg,
                                border = BorderStroke(1.dp, GlassNeonBorder15),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(1.dp),
                                    ) {
                                        Text(
                                            text = month.title,
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = V19PurpleSoftText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${month.salesCount} ${if (month.salesCount == 1) "venda" else "vendas"}",
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontSize = 11.sp,
                                            color = V19InkMuted,
                                        )
                                    }
                                    Text(
                                        text = CurrencyFormatter.formatBrl(month.totalSold),
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                    )
                                }
                            }

                            month.days.forEachIndexed { dayIndex, day ->
                                val dayDateFormatted = CurrencyFormatter.formatLocalDate(day.date)
                                val dayCountLabel = "${day.salesCount} ${if (day.salesCount == 1) "venda" else "vendas"}"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                        .semantics(mergeDescendants = true) {}
                                        .testTag("closing_day_${day.date}"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(1.dp),
                                    ) {
                                        Text(
                                            text = dayDateFormatted,
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White,
                                        )
                                        Text(
                                            text = dayCountLabel,
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontSize = 12.sp,
                                            color = V19InkMuted,
                                        )
                                    }
                                    Text(
                                        text = CurrencyFormatter.formatBrl(day.totalSold),
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = V19MintSuccess,
                                    )
                                }
                                if (dayIndex < month.days.lastIndex) {
                                    HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.24f))
                                }
                            }
                        }

                        if (monthIndex < report.months.lastIndex) {
                            HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.36f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FechamentoDailySalesCard(
    report: RealDailySalesReport,
    emptyMessage: String,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("closing_daily_sales_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.closing_daily_sales_title),
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            color = Color.White,
            modifier = Modifier.semantics { heading() },
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (report.months.isEmpty()) {
                    Text(
                        text = emptyMessage,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 13.sp,
                        color = V19InkMuted,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .testTag("closing_daily_empty_message"),
                    )
                } else {
                    report.months.forEachIndexed { monthIndex, month ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("closing_month_${month.yearMonth}"),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("closing_month_header_${month.yearMonth}"),
                                shape = RoundedCornerShape(12.dp),
                                color = GlassInnerBoxBg,
                                border = BorderStroke(1.dp, GlassNeonBorder15),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(1.dp),
                                    ) {
                                        Text(
                                            text = month.title,
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = V19PurpleSoftText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${month.salesCount} ${if (month.salesCount == 1) "venda" else "vendas"}",
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontSize = 11.sp,
                                            color = V19InkMuted,
                                        )
                                    }
                                    Text(
                                        text = CurrencyFormatter.formatBrl(month.totalSold),
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                    )
                                }
                            }

                            month.days.forEachIndexed { dayIndex, day ->
                                val dayDateFormatted = CurrencyFormatter.formatLocalDate(day.date)
                                val dayCountLabel = "${day.salesCount} ${if (day.salesCount == 1) "venda" else "vendas"}"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                        .semantics(mergeDescendants = true) {}
                                        .testTag("closing_day_${day.date}"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(1.dp),
                                    ) {
                                        Text(
                                            text = dayDateFormatted,
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White,
                                        )
                                        Text(
                                            text = dayCountLabel,
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontSize = 12.sp,
                                            color = V19InkMuted,
                                        )
                                    }
                                    Text(
                                        text = CurrencyFormatter.formatBrl(day.totalSold),
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = V19MintSuccess,
                                    )
                                }
                                if (dayIndex < month.days.lastIndex) {
                                    HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.24f))
                                }
                            }
                        }

                        if (monthIndex < report.months.lastIndex) {
                            HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.36f))
                        }
                    }
                }
            }
        }
    }
}
