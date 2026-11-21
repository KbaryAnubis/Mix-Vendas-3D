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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
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
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19PurplePrimary
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText

@Composable
fun MachinesGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
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
                .padding(horizontal = 20.dp, vertical = 20.dp),
            content = content,
        )
    }
}

@Composable
fun MachinesIconBadge(
    @DrawableRes iconRes: Int = R.drawable.mv3d_maquininhas,
    modifier: Modifier = Modifier,
    iconSize: Dp = 50.dp,
    scalePercent: Int? = null,
) {
    Mv3dIcon(
        iconRes = iconRes,
        contentDescription = null,
        size = iconSize,
        modifier = modifier,
        scalePercent = scalePercent,
    )
}

@Composable
fun MachinesPrimaryCtaButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    leadingText: String? = null,
    @DrawableRes iconRes: Int? = R.drawable.ic_products_save,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
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
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leadingText != null) {
                Text(
                    text = leadingText,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
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

@Composable
fun MachinesToggleCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = V19PurplePrimary,
                uncheckedColor = HomeCardNeonBorder,
                checkmarkColor = Color.White,
            ),
            modifier = Modifier.testTag(switchTestTag),
        )
        Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun MachinesCollapsibleSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    sectionTestTag: String,
    toggleTestTag: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    iconScalePercent: Int? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    MachinesGlassCard(modifier = modifier.testTag(sectionTestTag)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (iconRes != null) {
                Mv3dIcon(
                    iconRes = iconRes,
                    contentDescription = null,
                    size = 48.dp,
                    scalePercent = iconScalePercent,
                    maxSize = 86.4.dp,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x805C2B99))
                        .border(1.dp, HomeCardNeonBorder.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = title,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier
                    .size(48.dp)
                    .testTag(toggleTestTag),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Recolher $title" else "Expandir $title",
                    tint = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subtitle,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = V19InkMuted,
        )

        if (expanded) {
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun MachinesRateOptionRow(
    title: String,
    subtitle: String? = null,
    badgeIcon: ImageVector,
    rateValue: String,
    onRateChanged: (String) -> Unit,
    inputTestTag: String,
    placeholder: String = "0,00",
    modifier: Modifier = Modifier,
    @DrawableRes badgeIconRes: Int? = null,
    badgeIconScalePercent: Int? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (badgeIconRes != null) {
            Mv3dIcon(
                iconRes = badgeIconRes,
                contentDescription = null,
                size = 38.dp,
                scalePercent = badgeIconScalePercent,
                maxSize = 66.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x555E2CA0))
                    .border(1.dp, HomeCardNeonBorder.copy(alpha = 0.40f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = null,
                    tint = V19PurpleSoftText,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = HomeCardLabelInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    color = V19InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        OutlinedTextField(
            value = rateValue,
            onValueChange = onRateChanged,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color.White,
                textAlign = TextAlign.End,
            ),
            placeholder = {
                Text(
                    text = placeholder,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 14.sp,
                    color = V19InkMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            suffix = {
                Text(
                    text = "%",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = V19PurpleSoftText,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = namesDarkInputColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .width(124.dp)
                .testTag(inputTestTag),
        )
    }
}
