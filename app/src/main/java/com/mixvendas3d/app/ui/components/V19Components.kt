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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PreviewPeriodFilter
import com.mixvendas3d.app.domain.models.PreviewPersonSummary
import com.mixvendas3d.app.domain.models.SalePersonTotalSnapshot
import com.mixvendas3d.app.navigation.MainTab
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeCardGlassBottom
import com.mixvendas3d.app.ui.theme.HomeCardGlassTop
import com.mixvendas3d.app.ui.theme.V19BottomBarActiveBg
import com.mixvendas3d.app.ui.theme.V19BottomBarBg
import com.mixvendas3d.app.ui.theme.V19DemoBannerBg
import com.mixvendas3d.app.ui.theme.V19DemoBannerInk
import com.mixvendas3d.app.ui.theme.V19HeroEnd
import com.mixvendas3d.app.ui.theme.V19HeroStart
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19MintSuccess
import com.mixvendas3d.app.ui.theme.V19OutlineInput
import com.mixvendas3d.app.ui.theme.V19OutlineSubtle
import com.mixvendas3d.app.ui.theme.V19PersonPalette
import com.mixvendas3d.app.ui.theme.V19PurpleButtonEnd
import com.mixvendas3d.app.ui.theme.V19PurpleButtonStart
import com.mixvendas3d.app.ui.theme.V19PurpleGlow
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SuccessContainer
import com.mixvendas3d.app.ui.theme.V19SuccessInk
import com.mixvendas3d.app.ui.theme.V19SurfaceCard
import com.mixvendas3d.app.ui.theme.V19SurfaceInput
import com.mixvendas3d.app.ui.theme.V19TopBarEnd
import com.mixvendas3d.app.ui.theme.V19TopBarStart
import com.mixvendas3d.app.util.CurrencyFormatter

fun personAccentColor(index: Int): Color =
    V19PersonPalette[Math.floorMod(index, V19PersonPalette.size)]

@Composable
fun GlobalCastleWallpaper(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Única imagem global de fundo, nítida, sem blur e sem duplicação.
        Image(
            painter = painterResource(id = R.drawable.fundo_oficial_castelo_roxo),
            contentDescription = stringResource(R.string.stage1_wallpaper_desc),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .testTag("castle_wallpaper_image"),
        )
        content()
    }
}

@Composable
fun V19TopBar(
    isHome: Boolean,
    title: String,
    overline: String,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    onNewSaleClick: () -> Unit,
) {
    val barBrush = Brush.horizontalGradient(listOf(V19TopBarStart, V19TopBarEnd))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(barBrush)
            .statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isHome) {
                // V19 Início: botão superior centralizado "💵 NOVA VENDA" e SEM título "Visão geral"
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(V19PurpleButtonStart, V19PurpleButtonEnd),
                            ),
                        )
                        .border(
                            width = 1.dp,
                            color = V19PurpleGlow.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(14.dp),
                        )
                        .clickable(onClick = onNewSaleClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("home_new_sale_button"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.top_new_sale_cta),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showBackButton) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("topbar_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back_cd),
                                tint = V19InkPrimary,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = overline,
                            style = MaterialTheme.typography.labelMedium,
                            color = V19PurpleSoftText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = V19InkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("topbar_title"),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = V19PurpleGlow.copy(alpha = 0.30f))
    }
}

@Composable
fun V19BottomNavigationBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = V19BottomBarBg,
        border = BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.27f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomTabItem(
                tab = MainTab.INICIO,
                label = stringResource(R.string.nav_home),
                iconRes = R.drawable.mv3d_inicio,
                selected = currentTab == MainTab.INICIO,
                onClick = { onTabSelected(MainTab.INICIO) },
                modifier = Modifier.weight(1f),
            )
            BottomTabItem(
                tab = MainTab.VENDER,
                label = stringResource(R.string.nav_sell),
                iconRes = R.drawable.mv3d_nova_venda,
                selected = currentTab == MainTab.VENDER,
                onClick = { onTabSelected(MainTab.VENDER) },
                modifier = Modifier.weight(1f),
            )
            // VENDAS no centro, com aparência idêntica às demais quando não selecionada
            BottomTabItem(
                tab = MainTab.VENDAS,
                label = stringResource(R.string.nav_sales),
                iconRes = R.drawable.mv3d_vendas,
                selected = currentTab == MainTab.VENDAS,
                onClick = { onTabSelected(MainTab.VENDAS) },
                modifier = Modifier.weight(1f),
            )
            BottomTabItem(
                tab = MainTab.FECHAMENTO,
                label = stringResource(R.string.nav_closing),
                iconRes = R.drawable.mv3d_fechamento,
                selected = currentTab == MainTab.FECHAMENTO,
                onClick = { onTabSelected(MainTab.FECHAMENTO) },
                modifier = Modifier.weight(1f),
            )
            BottomTabItem(
                tab = MainTab.MAIS,
                label = stringResource(R.string.nav_more),
                iconRes = R.drawable.mv3d_mais,
                selected = currentTab == MainTab.MAIS,
                onClick = { onTabSelected(MainTab.MAIS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun mv3dIconGlobalPercentage(@DrawableRes iconRes: Int): Int = when (iconRes) {
    R.drawable.mv3d_editar,
    R.drawable.mv3d_excluir -> 140
    R.drawable.mv3d_buscar -> 150
    R.drawable.mv3d_cartao_bancario -> 155
    R.drawable.mv3d_produtos -> 170
    R.drawable.mv3d_dinheiro_moedas,
    R.drawable.mv3d_tarifas,
    R.drawable.mv3d_dinheiro_notas,
    R.drawable.mv3d_pessoas,
    R.drawable.mv3d_nomes,
    R.drawable.mv3d_pix -> 200
    R.drawable.mv3d_calendario -> 220
    R.drawable.mv3d_nova_venda,
    R.drawable.mv3d_inicio,
    R.drawable.mv3d_mais,
    R.drawable.mv3d_fechamento,
    R.drawable.mv3d_vendas,
    R.drawable.mv3d_ver_mais -> 230
    else -> 100
}

@Composable
fun Mv3dIcon(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    containerSize: Dp? = null,
    alpha: Float = 1f,
    scalePercent: Int? = null,
    maxSize: Dp = Dp.Infinity,
) {
    val scaledSize = (size * ((scalePercent ?: mv3dIconGlobalPercentage(iconRes)) / 100f))
        .coerceAtMost(maxSize)
    val image = @Composable {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            alpha = alpha,
            modifier = if (containerSize == null) modifier.size(scaledSize) else Modifier.size(scaledSize),
        )
    }
    if (containerSize != null) {
        Box(
            modifier = modifier.sizeIn(minWidth = containerSize, minHeight = containerSize),
            contentAlignment = Alignment.Center,
        ) {
            image()
        }
    } else {
        image()
    }
}

@Composable
private fun BottomTabItem(
    tab: MainTab,
    label: String,
    @DrawableRes iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (selected) V19BottomBarActiveBg else Color.Transparent
    val borderStroke = if (selected) {
        BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.50f))
    } else {
        null
    }
    val textColor = if (selected) Color.White else V19InkMuted

    Surface(
        modifier = modifier
            .heightIn(min = 66.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .testTag("nav_tab_${tab.name.lowercase()}"),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = borderStroke,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 1.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BoxWithConstraints {
                Mv3dIcon(
                    iconRes = iconRes,
                    contentDescription = label,
                    size = 36.dp,
                    maxSize = minOf(maxWidth, 64.dp),
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun V19TranslucentCard(
    modifier: Modifier = Modifier,
    contentPadding: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(HomeCardGlassTop, HomeCardGlassBottom)))
                .padding(contentPadding.dp),
            content = content,
        )
    }
}

@Composable
fun PreviewDemoBanner(
    previewEnabled: Boolean,
    demoCount: Int,
    onTogglePreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("preview_demo_banner"),
        shape = RoundedCornerShape(14.dp),
        color = V19DemoBannerBg,
        border = BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.34f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = V19DemoBannerInk,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (previewEnabled) {
                        stringResource(R.string.preview_banner_title, demoCount)
                    } else {
                        "Prévia demonstrativa ocultada"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = V19InkPrimary,
                )
                Text(
                    text = stringResource(R.string.preview_banner_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = V19DemoBannerInk,
                )
            }
            OutlinedButton(
                onClick = onTogglePreview,
                border = BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .testTag("toggle_preview_banner_button"),
            ) {
                Text(
                    text = stringResource(
                        if (previewEnabled) R.string.preview_banner_clear else R.string.preview_banner_restore,
                    ),
                    fontSize = 11.sp,
                    color = V19PurpleSoftText,
                )
            }
        }
    }
}

@Composable
fun PeriodFilterRow(
    overline: String,
    title: String,
    selectedPeriod: PreviewPeriodFilter,
    fromDate: String,
    toDate: String,
    onPeriodSelected: (PreviewPeriodFilter) -> Unit,
    onCustomDatesChanged: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = overline,
                    style = MaterialTheme.typography.labelMedium,
                    color = V19PurpleSoftText,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = V19InkPrimary,
                )
            }
            Box {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = V19SurfaceInput.copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, V19OutlineInput),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable { expanded = true }
                        .semantics(mergeDescendants = true) {}
                        .testTag("period_dropdown_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = selectedPeriod.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = V19InkPrimary,
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = V19PurpleSoftText,
                            modifier = Modifier.size(18.dp),
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
                                    color = V19InkPrimary,
                                    fontWeight = if (period == selectedPeriod) FontWeight.Bold else FontWeight.Normal,
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
        }

        if (selectedPeriod == PreviewPeriodFilter.CUSTOM) {
            CustomDateRangeFilterFields(
                fromDate = fromDate,
                toDate = toDate,
                onCustomDatesChanged = onCustomDatesChanged,
                useV19SurfaceColors = true,
            )
        }
    }
}

@Composable
fun V19HeroSummaryCard(
    salesCount: Int,
    totalGross: MoneyCents,
    totalFee: MoneyCents,
    totalNet: MoneyCents,
    originalSubtotal: MoneyCents? = null,
    discountTotal: MoneyCents? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("hero_summary_card"),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(V19HeroStart, V19HeroEnd)))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_hero_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = V19PurpleSoftText,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                ) {
                    Text(
                        text = "$salesCount ${if (salesCount == 1) "venda" else "vendas"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = V19InkPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                text = CurrencyFormatter.formatBrl(totalGross),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

            if (originalSubtotal != null && discountTotal != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Subtotal original",
                            style = MaterialTheme.typography.bodySmall,
                            color = V19PurpleSoftText,
                        )
                        Text(
                            text = CurrencyFormatter.formatBrl(originalSubtotal),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Descontos concedidos",
                            style = MaterialTheme.typography.bodySmall,
                            color = V19PurpleSoftText,
                        )
                        Text(
                            text = "− ${CurrencyFormatter.formatBrl(discountTotal)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tarifas",
                        style = MaterialTheme.typography.bodySmall,
                        color = V19PurpleSoftText,
                    )
                    Text(
                        text = "− ${CurrencyFormatter.formatBrl(totalFee)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Líquido",
                        style = MaterialTheme.typography.bodySmall,
                        color = V19PurpleSoftText,
                    )
                    Text(
                        text = CurrencyFormatter.formatBrl(totalNet),
                        style = MaterialTheme.typography.titleMedium,
                        color = V19MintSuccess,
                    )
                }
            }
        }
    }
}

@Composable
fun PersonSummaryGridCard(
    summary: PreviewPersonSummary,
    colorIndex: Int,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    PersonSummaryGridCardContent(
        personName = summary.person.name,
        netAmount = summary.net,
        colorIndex = colorIndex,
        subtitle = subtitle,
        modifier = modifier,
    )
}

@Composable
fun PersonSummaryGridCard(
    summary: SalePersonTotalSnapshot,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    PersonSummaryGridCardContent(
        personName = summary.personName,
        netAmount = summary.netAmount,
        colorIndex = summary.personColorIndex,
        subtitle = subtitle,
        modifier = modifier,
    )
}

@Composable
private fun PersonSummaryGridCardContent(
    personName: String,
    netAmount: MoneyCents,
    colorIndex: Int,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val accent = personAccentColor(colorIndex)
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(HomeCardGlassTop, HomeCardGlassBottom))),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(accent),
            )
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Text(
                        text = personName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = V19InkMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = CurrencyFormatter.formatBrl(netAmount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = V19InkPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = V19InkMuted,
                )
            }
        }
    }
}

@Composable
fun DynamicSplitProgressBar(
    personSummaries: List<PreviewPersonSummary>,
    totalNet: MoneyCents,
    modifier: Modifier = Modifier,
) {
    DynamicSplitProgressBarContent(
        segments = personSummaries.map { it.net.rawCents to it.person.colorIndex },
        individualSum = MoneyCents(personSummaries.sumOf { it.net.rawCents }),
        totalNet = totalNet,
        modifier = modifier,
    )
}

@Composable
fun DynamicSplitProgressBar(
    personSummaries: List<SalePersonTotalSnapshot>,
    totalNet: MoneyCents,
    modifier: Modifier = Modifier,
    isRealSnapshots: Boolean = true,
) {
    @Suppress("UNUSED_VARIABLE")
    val unused = isRealSnapshots
    DynamicSplitProgressBarContent(
        segments = personSummaries.map { it.netAmount.rawCents to it.personColorIndex },
        individualSum = MoneyCents(personSummaries.sumOf { it.netAmount.rawCents }),
        totalNet = totalNet,
        modifier = modifier,
    )
}

@Composable
private fun DynamicSplitProgressBarContent(
    segments: List<Pair<Long, Int>>,
    individualSum: MoneyCents,
    totalNet: MoneyCents,
    modifier: Modifier = Modifier,
) {
    val positiveSegments = segments.filter { it.first > 0L }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(V19SurfaceInput),
        ) {
            if (positiveSegments.isNotEmpty()) {
                positiveSegments.forEach { (amountCents, colorIndex) ->
                    Box(
                        modifier = Modifier
                            .weight(amountCents.toFloat())
                            .fillMaxHeight()
                            .background(personAccentColor(colorIndex)),
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .testTag("split_balance_banner"),
            shape = RoundedCornerShape(12.dp),
            color = V19SuccessContainer,
            border = BorderStroke(1.dp, V19MintSuccess.copy(alpha = 0.35f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = V19MintSuccess,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Soma individual: ${CurrencyFormatter.formatBrl(individualSum)} · Total líquido: ${CurrencyFormatter.formatBrl(totalNet)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = V19SuccessInk,
                )
            }
        }
    }
}

@Composable
fun v19TextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = V19SurfaceInput.copy(alpha = 0.90f),
    unfocusedContainerColor = V19SurfaceInput.copy(alpha = 0.90f),
    focusedBorderColor = V19PurpleGlow,
    unfocusedBorderColor = V19OutlineInput,
    focusedTextColor = V19InkPrimary,
    unfocusedTextColor = V19InkPrimary,
    focusedLabelColor = V19PurpleSoftText,
    unfocusedLabelColor = V19InkMuted,
    focusedPlaceholderColor = V19InkMuted,
    unfocusedPlaceholderColor = V19InkMuted,
)
