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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.models.PersonWithUsage
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeCardGlassBottom
import com.mixvendas3d.app.ui.theme.HomeCardGlassTop
import com.mixvendas3d.app.ui.theme.HomeCardNeonBorder
import com.mixvendas3d.app.ui.theme.HomeCtaBorderGlow
import com.mixvendas3d.app.ui.theme.HomeCtaGradientEnd
import com.mixvendas3d.app.ui.theme.HomeCtaGradientStart
import com.mixvendas3d.app.ui.theme.HomeCtaShadowGlow
import com.mixvendas3d.app.ui.theme.PlusJakartaSansFontFamily
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText

@Composable
fun NamesV2TopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backButtonShape = RoundedCornerShape(15.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBackClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("topbar_back_button"),
            shape = backButtonShape,
            color = HomeCardGlassTop,
            border = BorderStroke(1.2.dp, GlassNeonBorder15),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_names_back_chevron),
                    contentDescription = stringResource(R.string.back_cd),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 27.sp,
            lineHeight = 33.sp,
            letterSpacing = (-0.4).sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
                .testTag("topbar_title"),
        )
    }
}

@Composable
fun NamesGlassCard(
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
fun NamesPersonAvatarBadge(
    modifier: Modifier = Modifier,
    iconSize: Dp = 40.dp,
    @DrawableRes iconRes: Int = R.drawable.mv3d_nomes,
) {
    Mv3dIcon(
        iconRes = iconRes,
        contentDescription = null,
        size = iconSize,
        modifier = modifier,
    )
}

@Composable
fun NamesPrimaryCtaButton(
    text: String,
    isEditing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
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
            .padding(horizontal = 20.dp, vertical = 15.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (isEditing) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Mv3dIcon(
                    iconRes = R.drawable.mv3d_nomes,
                    contentDescription = null,
                    size = 36.dp,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun NamesPersonRow(
    item: PersonWithUsage,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
    actionIconScalePercent: Int? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .testTag("person_row_${item.person.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mv3dIcon(
            iconRes = R.drawable.mv3d_nomes,
            contentDescription = null,
            size = 40.dp,
            maxSize = 56.dp,
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.person.name,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (item.linkedProductCount > 0) {
                    if (item.linkedProductCount == 1) {
                        stringResource(R.string.names_row_linked_single)
                    } else {
                        stringResource(R.string.names_row_linked_plural, item.linkedProductCount)
                    }
                } else {
                    stringResource(R.string.names_row_default_subtitle)
                },
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                color = if (item.linkedProductCount > 0) V19PurpleSoftText else V19InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(
            onClick = onEdit,
            modifier = Modifier
                .size(if (actionIconScalePercent != null) 52.dp else 48.dp)
                .testTag("edit_person_${item.person.id}"),
        ) {
            Mv3dIcon(
                iconRes = R.drawable.mv3d_editar,
                contentDescription = "Editar ${item.person.name}",
                size = 36.dp,
                scalePercent = actionIconScalePercent,
                maxSize = if (actionIconScalePercent != null) 52.dp else 48.dp,
            )
        }

        IconButton(
            onClick = onRequestDelete,
            modifier = Modifier
                .size(if (actionIconScalePercent != null) 52.dp else 48.dp)
                .testTag("delete_person_${item.person.id}"),
        ) {
            Mv3dIcon(
                iconRes = R.drawable.mv3d_excluir,
                contentDescription = "Excluir ${item.person.name}",
                size = 36.dp,
                scalePercent = actionIconScalePercent,
                maxSize = if (actionIconScalePercent != null) 52.dp else 48.dp,
                alpha = if (item.linkedProductCount > 0) 0.45f else 1f,
            )
        }
    }
}

@Composable
fun namesDarkInputColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color(0xCC180929),
    unfocusedContainerColor = Color(0xCC180929),
    focusedBorderColor = HomeCtaBorderGlow.copy(alpha = 0.85f),
    unfocusedBorderColor = HomeCardNeonBorder.copy(alpha = 0.50f),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = HomeCtaBorderGlow,
    focusedPlaceholderColor = V19InkMuted,
    unfocusedPlaceholderColor = V19InkMuted,
)
