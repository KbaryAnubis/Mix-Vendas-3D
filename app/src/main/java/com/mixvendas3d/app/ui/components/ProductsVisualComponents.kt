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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
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
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.util.CurrencyFormatter

@Composable
fun ProductsGlassCard(
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
fun ProductsSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        color = Color.White,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun ProductsFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = Color.White,
        modifier = modifier,
    )
}

@Composable
fun ProductsMonetaryTextField(
    maskedValue: String,
    onMaskedInputChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
) {
    val fieldValue = TextFieldValue(
        text = maskedValue,
        selection = TextRange(maskedValue.length),
    )

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { updated -> onMaskedInputChanged(updated.text) },
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            color = if (enabled) Color.White else V19InkMuted,
            textAlign = textAlign,
        ),
        placeholder = {
            Text(
                text = "0,00",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                color = if (enabled) Color.White.copy(alpha = 0.72f) else V19InkMuted.copy(alpha = 0.62f),
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = namesDarkInputColors(),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    )
}

@Composable
fun ProductsPrimaryCtaButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    @DrawableRes iconRes: Int = R.drawable.ic_products_save,
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
            if (iconRes == R.drawable.mv3d_produtos) {
                Mv3dIcon(
                    iconRes = iconRes,
                    contentDescription = null,
                    size = 36.dp,
                )
            } else {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
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
fun ProductsSecondaryOutlinedButton(
    text: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag(testTag),
        shape = shape,
        color = GlassInnerBoxBg,
        border = BorderStroke(1.2.dp, GlassNeonBorder15),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ProductsRegisteredRow(
    product: Product,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    editIconScalePercent: Int? = null,
    categoryName: String? = null,
    subcategoryName: String? = null,
) {
    val statusText = if (product.active) "Ativo" else "Inativo"
    val participantCountText = if (product.participants.size == 1) {
        "1 participante"
    } else {
        "${product.participants.size} participante(s)"
    }
    val feeOwnerName = product.feeOwnerPersonId?.let { ownerId ->
        product.participants.firstOrNull { it.personId == ownerId }?.personName
    }
    val feeSummary = when (product.feeResponsibilityMode) {
        FeeResponsibilityMode.INDIVIDUAL -> "Tarifa: ${feeOwnerName ?: "Pessoa específica"}"
        FeeResponsibilityMode.EQUAL -> "Tarifa: Divisão igualitária"
        FeeResponsibilityMode.PROPORTIONAL -> "Tarifa: Divisão proporcional"
    }
    val splitLine = product.participants.joinToString(" · ") { split ->
        "${split.personName}: ${CurrencyFormatter.formatBrl(split.amount)}"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .testTag("product_card_${product.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = product.name,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("product_name_${product.id}"),
            )
            if (!categoryName.isNullOrBlank()) {
                val categoryText = if (!subcategoryName.isNullOrBlank()) {
                    "$categoryName › $subcategoryName"
                } else {
                    categoryName
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x448B5CF6),
                    border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .testTag("product_category_badge_${product.id}"),
                ) {
                    Text(
                        text = categoryText,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = V19PurpleSoftText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = "${CurrencyFormatter.formatBrl(product.price)} · $statusText · $participantCountText",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = if (product.active) HomeCardLabelInk else V19InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("product_status_${product.id}"),
            )
            if (splitLine.isNotEmpty()) {
                Text(
                    text = splitLine,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = V19PurpleSoftText.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = feeSummary,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                color = V19InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("product_fee_rule_${product.id}"),
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onEdit,
                modifier = Modifier
                    .size(if (editIconScalePercent != null) 58.dp else 52.dp)
                    .testTag("edit_product_${product.id}"),
                shape = RoundedCornerShape(15.dp),
                color = Color(0xCC5B2AA6),
                border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.72f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Mv3dIcon(
                        iconRes = R.drawable.mv3d_editar,
                        contentDescription = "Editar ${product.name}",
                        size = 36.dp,
                        scalePercent = editIconScalePercent,
                    )
                }
            }

            Surface(
                onClick = onDelete,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("delete_product_${product.id}"),
                shape = RoundedCornerShape(15.dp),
                color = Color(0xCC56213B),
                border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.58f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Mv3dIcon(
                        iconRes = R.drawable.mv3d_excluir,
                        contentDescription = "Excluir ${product.name}",
                        size = 36.dp,
                        scalePercent = editIconScalePercent,
                    )
                }
            }
        }
    }
}

@Composable
fun ProductsDropdownField(
    label: String,
    selectedText: String,
    options: List<Pair<String?, String>>,
    onOptionSelected: (String?) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ProductsFieldLabel(text = label)
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .clickable(enabled = enabled) { expanded = true }
                    .testTag(testTag),
                shape = RoundedCornerShape(16.dp),
                color = GlassInnerBoxBg,
                border = BorderStroke(1.dp, if (enabled) GlassNeonBorder15 else GlassNeonBorder15.copy(alpha = 0.3f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedText,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = if (enabled) Color.White else V19InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = if (enabled) V19PurpleSoftText else V19InkMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF23103C)),
            ) {
                options.forEach { (id, text) ->
                    val itemTag = when {
                        id == null -> "${testTag}_option_none"
                        testTag == "product_fee_owner_dropdown" -> "fee_owner_$id"
                        else -> "${testTag}_option_$id"
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = text,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = Color.White,
                            )
                        },
                        onClick = {
                            expanded = false
                            onOptionSelected(id)
                        },
                        modifier = Modifier.testTag(itemTag),
                    )
                }
            }
        }
    }
}

