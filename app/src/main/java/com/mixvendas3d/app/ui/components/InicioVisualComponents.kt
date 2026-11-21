package com.mixvendas3d.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
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
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.util.CurrencyFormatter

@Composable
fun HomeMiniIconBadge(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 52.dp,
) {
    Mv3dIcon(
        iconRes = iconRes,
        contentDescription = contentDescription,
        size = badgeSize,
        modifier = modifier,
    )
}

@Composable
fun HomePersonAvatarBadge(
    modifier: Modifier = Modifier,
    badgeSize: Dp = 40.dp,
) {
    Mv3dIcon(
        iconRes = R.drawable.mv3d_nomes,
        contentDescription = null,
        size = badgeSize,
        modifier = modifier,
    )
}

@Composable
fun InicioNovaVendaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = HomeCtaShadowGlow.copy(alpha = 0.70f),
                spotColor = HomeCtaShadowGlow.copy(alpha = 0.85f),
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
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("home_new_sale_button"),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Mv3dIcon(
                iconRes = R.drawable.mv3d_nova_venda,
                contentDescription = stringResource(R.string.top_new_sale_cd),
                size = 40.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.home_new_sale_label),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun InicioMetricCard(
    @DrawableRes iconRes: Int,
    label: String,
    value: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(HomeCardGlassTop, HomeCardGlassBottom),
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HomeMiniIconBadge(
                iconRes = iconRes,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.weight(1f).height(2.dp))
            Text(
                text = label,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = HomeCardLabelInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 21.sp,
                letterSpacing = (-0.4).sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun InicioPeopleSalesCard(
    visiblePeople: List<SalePersonTotalSnapshot>,
    totalPeopleCount: Int,
    personSalesCounts: Map<String, Int>,
    showAllPeople: Boolean,
    onToggleShowAllPeople: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_people_sales_card"),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(HomeCardGlassTop, HomeCardGlassBottom),
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeMiniIconBadge(
                    iconRes = R.drawable.mv3d_pessoas,
                    contentDescription = null,
                    badgeSize = 48.dp,
                )
                Text(
                    text = stringResource(R.string.home_people_card_title),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.32f))

            if (visiblePeople.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .semantics(mergeDescendants = true) {}
                        .testTag("home_people_empty_state"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_people_empty_title),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = V19InkPrimary,
                    )
                    Text(
                        text = stringResource(R.string.home_people_empty_subtitle),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        color = V19InkMuted,
                    )
                }
            } else {
                visiblePeople.forEachIndexed { index, summary ->
                    val salesCount = personSalesCounts[summary.personId] ?: 0
                    val countLabel = if (salesCount == 1) {
                        "1 venda"
                    } else {
                        "$salesCount vendas"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 13.dp)
                            .semantics(mergeDescendants = true) {}
                            .testTag("home_person_card_${summary.personId}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HomePersonAvatarBadge()
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = summary.personName,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = countLabel,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.sp,
                                color = V19InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = CurrencyFormatter.formatBrl(summary.netAmount),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            letterSpacing = (-0.3).sp,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                    if (index < visiblePeople.lastIndex) {
                        HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.25f))
                    }
                }
            }

            if (totalPeopleCount > 2) {
                Spacer(modifier = Modifier.height(8.dp))
                val btnShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(btnShape)
                        .background(GlassInnerBoxBg)
                        .border(
                            width = 1.dp,
                            color = GlassNeonBorder15,
                            shape = btnShape,
                        )
                        .clickable(
                            role = Role.Button,
                            onClick = onToggleShowAllPeople,
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("toggle_more_people_button"),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Mv3dIcon(
                            iconRes = R.drawable.mv3d_ver_mais,
                            contentDescription = null,
                            size = 32.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                if (showAllPeople) {
                                    R.string.home_people_less
                                } else {
                                    R.string.home_people_more_action
                                },
                            ),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
