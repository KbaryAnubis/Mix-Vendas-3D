package com.mixvendas3d.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.selected
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.Machine
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PreviewPaymentMethod
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.ui.theme.GlassInnerBoxBg
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeCardGlassBottom
import com.mixvendas3d.app.ui.theme.HomeCardGlassTop
import com.mixvendas3d.app.ui.theme.HomeCardLabelInk
import com.mixvendas3d.app.ui.theme.HomeCardNeonBorder
import com.mixvendas3d.app.ui.theme.HomeCtaBorderGlow
import com.mixvendas3d.app.ui.theme.HomeCtaGradientEnd
import com.mixvendas3d.app.ui.theme.HomeCtaGradientStart
import com.mixvendas3d.app.ui.theme.HomeCtaShadowGlow
import com.mixvendas3d.app.ui.theme.PlusJakartaSansFontFamily
import com.mixvendas3d.app.ui.theme.V19GoldAccent
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19PurplePrimary
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.util.CurrencyFormatter
import com.mixvendas3d.app.util.RateFormatter

@Composable
fun VenderGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
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
                .padding(horizontal = 18.dp, vertical = 18.dp),
            content = content,
        )
    }
}

@Composable
fun VenderSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = Color.White,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun VenderChooseProductsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .shadow(
                elevation = 12.dp,
                shape = shape,
                ambientColor = HomeCtaShadowGlow.copy(alpha = 0.65f),
                spotColor = HomeCtaShadowGlow.copy(alpha = 0.80f),
            )
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(HomeCtaGradientStart, HomeCtaGradientEnd),
                ),
            )
            .border(
                width = 1.5.dp,
                color = HomeCtaBorderGlow.copy(alpha = 0.88f),
                shape = shape,
            )
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 14.dp)
            .testTag("open_product_picker_button"),
        contentAlignment = Alignment.Center,
    ) {
        val compactLayout = maxWidth < 360.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compactLayout) 12.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mv3dIcon(
                iconRes = R.drawable.mv3d_produtos,
                contentDescription = null,
                size = 36.dp,
            )
            Spacer(modifier = Modifier.width(if (compactLayout) 8.dp else 12.dp))
            Text(
                text = stringResource(R.string.sell_choose_products),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = if (compactLayout) 16.sp else 18.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(if (compactLayout) 6.dp else 10.dp))
            Mv3dIcon(
                iconRes = R.drawable.mv3d_buscar,
                contentDescription = null,
                size = 32.dp,
            )
        }
    }
}

@Composable
fun VenderDashedEmptyCard(
    hasActiveCatalogProducts: Boolean,
    modifier: Modifier = Modifier,
) {
    val cornerRadius = 18.dp
    val message: AnnotatedString = if (hasActiveCatalogProducts) {
        buildAnnotatedString {
            append("Nenhum produto selecionado. Toque em ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                append("Escolher produtos")
            }
            append(" para começar.")
        }
    } else {
        AnnotatedString(
            "Nenhum produto ativo cadastrado no Room. Cadastre ou ative produtos em Mais → Produtos.",
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(GlassInnerBoxBg)
            .testTag("vender_empty_products_card"),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = 1.3.dp.toPx()
            val radiusPx = cornerRadius.toPx()
            drawRoundRect(
                color = GlassNeonBorder15,
                style = Stroke(
                    width = strokePx,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f),
                ),
                cornerRadius = CornerRadius(radiusPx, radiusPx),
            )
        }
        Text(
            text = message,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = HomeCardLabelInk,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
fun VenderSelectedSummaryBar(
    totalItemsCount: Int,
    totalAmount: MoneyCents,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("vender_selected_summary_bar"),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, GlassNeonBorder15),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(HomeCardGlassTop, HomeCardGlassBottom),
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "$totalItemsCount item(ns) selecionado(s)",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = HomeCardLabelInk.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.sell_summary_total_label),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = CurrencyFormatter.formatBrl(totalAmount),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                letterSpacing = (-0.4).sp,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun VenderPaymentMethodTile(
    method: PreviewPaymentMethod,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val tileModifier = modifier
        .heightIn(min = 54.dp)
        .then(
            if (selected) {
                Modifier.shadow(
                    elevation = 12.dp,
                    shape = shape,
                    ambientColor = HomeCtaShadowGlow.copy(alpha = 0.65f),
                    spotColor = HomeCtaShadowGlow.copy(alpha = 0.80f),
                )
            } else {
                Modifier
            },
        )
        .clip(shape)
        .background(
            if (selected) {
                Brush.horizontalGradient(listOf(HomeCtaGradientStart, HomeCtaGradientEnd))
            } else {
                Brush.verticalGradient(listOf(HomeCardGlassTop, HomeCardGlassBottom))
            },
        )
        .border(
            width = if (selected) 1.5.dp else 1.1.dp,
            color = if (selected) HomeCtaBorderGlow.copy(alpha = 0.90f) else GlassNeonBorder15,
            shape = shape,
        )
        .clickable(role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 10.dp)
        .testTag("pay_method_${method.name.lowercase()}")

    BoxWithConstraints(modifier = tileModifier, contentAlignment = Alignment.Center) {
        val iconLimit = maxWidth * 0.42f
        val rowIconHeight = when (method) {
            PreviewPaymentMethod.CASH, PreviewPaymentMethod.PIX -> 64.dp
            PreviewPaymentMethod.DEBIT, PreviewPaymentMethod.CREDIT -> 62.dp
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = minOf(rowIconHeight, iconLimit)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (method) {
                PreviewPaymentMethod.CASH -> Mv3dIcon(
                    iconRes = R.drawable.mv3d_dinheiro_notas,
                    contentDescription = null,
                    size = 40.dp,
                    scalePercent = 160,
                    maxSize = iconLimit,
                )
                PreviewPaymentMethod.PIX -> Mv3dIcon(
                    iconRes = R.drawable.mv3d_pix,
                    contentDescription = null,
                    size = 28.dp,
                    maxSize = iconLimit,
                )
                PreviewPaymentMethod.DEBIT,
                PreviewPaymentMethod.CREDIT,
                -> Mv3dIcon(
                    iconRes = R.drawable.mv3d_cartao_bancario,
                    contentDescription = null,
                    size = 40.dp,
                    maxSize = iconLimit,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = method.label,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun VenderAutoSplitSectionCard(
    selectedEntries: List<Pair<Product, Int>>,
    discountCents: Long,
    discountTargetLabel: String?,
    modifier: Modifier = Modifier,
) {
    if (selectedEntries.isEmpty()) {
        val shape = RoundedCornerShape(16.dp)
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .testTag("vender_auto_split_empty"),
            shape = shape,
            color = GlassInnerBoxBg,
            border = BorderStroke(1.1.dp, GlassNeonBorder15),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Mv3dIcon(
                    iconRes = R.drawable.mv3d_produtos,
                    contentDescription = null,
                    size = 64.dp,
                )
                Text(
                    text = stringResource(R.string.sell_split_empty),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = HomeCardLabelInk,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        VenderGlassCard(
            modifier = modifier.testTag("vender_auto_split_card"),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                selectedEntries.forEachIndexed { index, (product, qty) ->
                    val lineSubtotal = MoneyCents(product.price.rawCents * qty)
                    val participantsLine = product.participants.joinToString(" · ") { split ->
                        val totalShare = MoneyCents(split.amount.rawCents * qty)
                        "${split.personName}: ${CurrencyFormatter.formatBrl(totalShare)}"
                    }
                    val feeOwnerName = product.feeOwnerPersonId?.let { ownerId ->
                        product.participants.firstOrNull { it.personId == ownerId }?.personName
                    }
                    val feeRuleLabel = when (product.feeResponsibilityMode) {
                        FeeResponsibilityMode.PROPORTIONAL -> "Tarifa: Divisão proporcional"
                        FeeResponsibilityMode.EQUAL -> "Tarifa: Divisão igualitária"
                        FeeResponsibilityMode.INDIVIDUAL -> "Tarifa: ${feeOwnerName ?: "Pessoa específica"}"
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${qty}× ${product.name}",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = CurrencyFormatter.formatBrl(lineSubtotal),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = V19PurpleSoftText,
                            )
                        }
                        if (participantsLine.isNotEmpty()) {
                            Text(
                                text = participantsLine,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = HomeCardLabelInk,
                            )
                        }
                        Text(
                            text = feeRuleLabel,
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            color = V19InkMuted,
                        )
                    }
                    if (index < selectedEntries.lastIndex) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
                if (discountCents > 0L && !discountTargetLabel.isNullOrBlank()) {
                    Text(
                        text = "Desconto de ${CurrencyFormatter.formatBrl(MoneyCents(discountCents))}: $discountTargetLabel.",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = V19GoldAccent,
                    )
                }
            }
        }
    }
}

@Composable
fun VenderPrimarySaveButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = HomeCtaShadowGlow.copy(alpha = if (enabled) 0.70f else 0.25f),
                spotColor = HomeCtaShadowGlow.copy(alpha = if (enabled) 0.85f else 0.30f),
            )
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(HomeCtaGradientStart, HomeCtaGradientEnd))
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFF422768), Color(0xFF331B54)))
                },
            )
            .border(
                width = 1.5.dp,
                color = if (enabled) {
                    HomeCtaBorderGlow.copy(alpha = 0.88f)
                } else {
                    HomeCardNeonBorder.copy(alpha = 0.40f)
                },
                shape = shape,
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("save_sale_button"),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun VenderProductPickerRow(
    product: Product,
    quantity: Int,
    maxQuantity: Int,
    showHistoricalBadge: Boolean,
    onToggle: () -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = quantity > 0
    val shape = RoundedCornerShape(18.dp)
    val participantsSummary = product.participants.joinToString(" · ") { split ->
        "${split.personName} ${CurrencyFormatter.formatBrl(split.amount)}"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = HomeCtaShadowGlow.copy(alpha = 0.55f),
                        spotColor = HomeCtaShadowGlow.copy(alpha = 0.70f),
                    )
                } else {
                    Modifier
                },
            )
            .testTag("picker_row_${product.id}"),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.1.dp,
            color = if (selected) {
                HomeCtaBorderGlow.copy(alpha = 0.90f)
            } else {
                GlassNeonBorder15
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (selected) {
                        Brush.verticalGradient(listOf(Color(0xE63D1B6A), Color(0xDF260E47)))
                    } else {
                        Brush.verticalGradient(listOf(GlassInnerBoxBg, GlassInnerBoxBg))
                    },
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onToggle)
                    .padding(vertical = 2.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = V19PurplePrimary,
                        uncheckedColor = HomeCardNeonBorder.copy(alpha = 0.78f),
                        checkmarkColor = Color.White,
                    ),
                    modifier = Modifier.testTag("picker_checkbox_${product.id}"),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = product.name,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = CurrencyFormatter.formatBrl(product.price),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = if (selected) HomeCtaBorderGlow else V19PurpleSoftText,
                    )
                    if (participantsSummary.isNotEmpty()) {
                        Text(
                            text = participantsSummary,
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = HomeCardLabelInk.copy(alpha = 0.88f),
                        )
                    }
                    if (showHistoricalBadge) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFF3E291A).copy(alpha = 0.88f),
                            border = BorderStroke(1.dp, V19GoldAccent.copy(alpha = 0.75f)),
                            modifier = Modifier
                                .semantics(mergeDescendants = true) {}
                                .testTag("picker_historical_badge_${product.id}"),
                        ) {
                            Text(
                                text = stringResource(R.string.product_picker_historical_badge),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                color = V19GoldAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) Color(0xE01A0932) else Color(0xBF140725),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) HomeCardNeonBorder.copy(alpha = 0.72f) else HomeCardNeonBorder.copy(alpha = 0.34f),
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    IconButton(
                        onClick = onDecrement,
                        enabled = quantity > 0,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("picker_dec_${product.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Diminuir ${product.name}",
                            tint = if (quantity > 0) Color.White else V19InkMuted.copy(alpha = 0.45f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = quantity.toString(),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = if (selected) Color.White else V19InkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .widthIn(min = 24.dp)
                            .testTag("picker_qty_${product.id}"),
                    )
                    IconButton(
                        onClick = onIncrement,
                        enabled = quantity < maxQuantity,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("picker_inc_${product.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Aumentar ${product.name}",
                            tint = if (quantity < maxQuantity) Color.White else V19InkMuted.copy(alpha = 0.45f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VenderProductPickerFooter(
    distinctProductsCount: Int,
    totalUnitsCount: Int,
    subtotalCents: Long,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val footerShape = RoundedCornerShape(20.dp)
    val ctaShape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("product_picker_footer"),
        shape = footerShape,
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, HomeCardNeonBorder.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xEB2C1250), Color(0xF017082C)),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = "$distinctProductsCount produto(s) · $totalUnitsCount unidade(s)",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = HomeCardLabelInk.copy(alpha = 0.90f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("product_picker_counts"),
                    )
                    Text(
                        text = stringResource(R.string.product_picker_subtotal_label),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = CurrencyFormatter.formatBrl(MoneyCents(subtotalCents)),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.4).sp,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.testTag("product_picker_subtotal"),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = ctaShape,
                        ambientColor = HomeCtaShadowGlow.copy(alpha = 0.65f),
                        spotColor = HomeCtaShadowGlow.copy(alpha = 0.80f),
                    )
                    .clip(ctaShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(HomeCtaGradientStart, HomeCtaGradientEnd),
                        ),
                    )
                    .border(
                        width = 1.4.dp,
                        color = HomeCtaBorderGlow.copy(alpha = 0.88f),
                        shape = ctaShape,
                    )
                    .clickable(
                        role = Role.Button,
                        onClick = onDoneClick,
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("close_product_picker_button"),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.product_picker_done_action),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VenderMachinePickerRow(
    machine: Machine,
    method: PreviewPaymentMethod,
    selected: Boolean,
    showHistoricalBadge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val rateLabel = if (method == PreviewPaymentMethod.CREDIT) {
        "Crédito padrão: ${RateFormatter.formatBpsAsPercent(machine.creditDefaultRateBps)}"
    } else {
        "Débito: ${RateFormatter.formatBpsAsPercent(machine.debitRateBps)}"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(
                if (selected) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = HomeCtaShadowGlow.copy(alpha = 0.55f),
                        spotColor = HomeCtaShadowGlow.copy(alpha = 0.70f),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("machine_picker_row_${machine.id}"),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.1.dp,
            color = if (selected) {
                HomeCtaBorderGlow.copy(alpha = 0.90f)
            } else {
                GlassNeonBorder15
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (selected) {
                        Brush.verticalGradient(listOf(Color(0xE63D1B6A), Color(0xDF260E47)))
                    } else {
                        Brush.verticalGradient(listOf(GlassInnerBoxBg, GlassInnerBoxBg))
                    },
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Mv3dIcon(
                iconRes = R.drawable.mv3d_maquininhas,
                contentDescription = null,
                size = 46.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = machine.name,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = rateLabel,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (selected) HomeCtaBorderGlow else V19PurpleSoftText,
                )
                if (method == PreviewPaymentMethod.CREDIT &&
                    (machine.brandRatesEnabled || machine.installmentsEnabled)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (machine.brandRatesEnabled) {
                            MachinePickerFeaturePill(text = "Taxas por bandeira")
                        }
                        if (machine.installmentsEnabled) {
                            val pillText = machine.maxInstallments?.let { "Parcelamento até ${it}x" }
                                ?: "Parcelamento (12x)"
                            MachinePickerFeaturePill(text = pillText)
                        }
                    }
                }
                if (showHistoricalBadge) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF3E291A).copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, V19GoldAccent.copy(alpha = 0.75f)),
                        modifier = Modifier
                            .semantics(mergeDescendants = true) {}
                            .testTag("machine_picker_historical_badge_${machine.id}"),
                    ) {
                        Text(
                            text = stringResource(R.string.machine_picker_historical_badge),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            color = V19GoldAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            if (selected) {
                val badgeShape = RoundedCornerShape(999.dp)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(badgeShape)
                        .background(
                            Brush.horizontalGradient(listOf(HomeCtaGradientStart, HomeCtaGradientEnd)),
                        )
                        .border(1.dp, HomeCtaBorderGlow.copy(alpha = 0.90f), badgeShape)
                        .testTag("machine_picker_selected_icon_${machine.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.machine_picker_selected_cd),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MachinePickerFeaturePill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = GlassInnerBoxBg,
        border = BorderStroke(1.dp, GlassNeonBorder15),
    ) {
        Text(
            text = text,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = HomeCardLabelInk,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun PickerCategoryHeader(
    categoryName: String,
    productCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF2C154C))
            .border(1.2.dp, HomeCardNeonBorder.copy(alpha = 0.65f), shape)
            .clickable(onClick = onToggleExpand)
            .testTag(testTag)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (isExpanded) "▼" else "▶",
                    fontSize = 12.sp,
                    color = HomeCtaBorderGlow,
                )
                Text(
                    text = categoryName,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x66492075))
                    .border(1.dp, HomeCardNeonBorder.copy(alpha = 0.40f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "$productCount item(ns)",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = V19PurpleSoftText,
                )
            }
        }
    }
}

@Composable
fun PickerSubcategoryHeader(
    subcategoryName: String,
    productCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .clip(shape)
            .background(Color(0xFF210E3A))
            .border(1.dp, GlassNeonBorder15, shape)
            .clickable(onClick = onToggleExpand)
            .testTag(testTag)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (isExpanded) "▼" else "▶",
                    fontSize = 10.sp,
                    color = V19PurpleSoftText,
                )
                Text(
                    text = "↳ $subcategoryName",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = V19PurpleSoftText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "$productCount item(ns)",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                color = HomeCardLabelInk,
            )
        }
    }
}

