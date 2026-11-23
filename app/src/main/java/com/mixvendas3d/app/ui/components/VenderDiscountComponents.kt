package com.mixvendas3d.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.domain.calculation.DiscountPeopleSelectionMode
import com.mixvendas3d.app.domain.calculation.DiscountPreviewPersonLine
import com.mixvendas3d.app.domain.calculation.DiscountProductPeopleGroup
import com.mixvendas3d.app.domain.calculation.DiscountUnifiedPersonOption
import com.mixvendas3d.app.domain.calculation.MonetaryRounding
import com.mixvendas3d.app.domain.calculation.VenderDiscountPreview
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.ui.theme.GlassInnerBoxBg
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeCardLabelInk
import com.mixvendas3d.app.ui.theme.HomeCardNeonBorder
import com.mixvendas3d.app.ui.theme.HomeCardSubtleDivider
import com.mixvendas3d.app.ui.theme.HomeCtaBorderGlow
import com.mixvendas3d.app.ui.theme.PlusJakartaSansFontFamily
import com.mixvendas3d.app.ui.theme.V19ErrorContainer
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import com.mixvendas3d.app.ui.theme.V19GoldAccent
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19MintSuccess
import com.mixvendas3d.app.ui.theme.V19PurplePrimary
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SuccessContainer
import com.mixvendas3d.app.ui.theme.V19SuccessInk
import com.mixvendas3d.app.util.CurrencyFormatter

/**
 * Caixa 1 — Seleção múltipla dos produtos que receberão o desconto.
 * Produtos desmarcados permanecem com seus valores e divisões originais intactos.
 */
@Composable
fun DiscountProductsSelectionBox(
    selectedEntries: List<Pair<Product, Int>>,
    selectedDiscountProductIds: Set<String>,
    discountCents: Long,
    targetMissing: Boolean,
    onToggleProduct: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeSelectedCount = selectedEntries.count { it.first.id in selectedDiscountProductIds }
    val totalCount = selectedEntries.size
    val allSelected = totalCount > 0 && activeSelectedCount == totalCount
    val noneSelected = activeSelectedCount == 0

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discount_products_box"),
        shape = RoundedCornerShape(16.dp),
        color = GlassInnerBoxBg,
        border = BorderStroke(
            width = if (targetMissing || (discountCents > 0L && noneSelected)) 1.5.dp else 1.1.dp,
            color = if (targetMissing || (discountCents > 0L && noneSelected)) {
                V19ErrorSoft
            } else {
                GlassNeonBorder15
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Produtos que receberão desconto",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                    )
                    Text(
                        text = "Marque um ou mais produtos. Itens desmarcados ficam inalterados.",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = HomeCardLabelInk.copy(alpha = 0.85f),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                DiscountStatusPill(
                    text = "$activeSelectedCount de $totalCount",
                    backgroundColor = if (noneSelected && discountCents > 0L) {
                        V19ErrorContainer
                    } else {
                        Color(0xFF33185E)
                    },
                    borderColor = if (noneSelected && discountCents > 0L) {
                        V19ErrorSoft.copy(alpha = 0.75f)
                    } else {
                        HomeCardNeonBorder.copy(alpha = 0.70f)
                    },
                    textColor = if (noneSelected && discountCents > 0L) V19ErrorSoft else V19PurpleSoftText,
                )
            }

            // Ações rápidas: Selecionar todos / Limpar seleção
            val canSelectAll = !allSelected && totalCount > 0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onSelectAll,
                    enabled = canSelectAll,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (canSelectAll) HomeCardNeonBorder.copy(alpha = 0.75f) else GlassNeonBorder15,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("discount_select_all_button"),
                ) {
                    Text(
                        text = "Selecionar todos",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (canSelectAll) Color.White else V19InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onClearAll,
                    enabled = !noneSelected,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (!noneSelected) V19GoldAccent.copy(alpha = 0.65f) else GlassNeonBorder15,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("discount_clear_selection_button"),
                ) {
                    Text(
                        text = "Limpar seleção",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (!noneSelected) V19GoldAccent else V19InkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (targetMissing) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discount_target_missing_warning"),
                    shape = RoundedCornerShape(12.dp),
                    color = V19ErrorContainer,
                    border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.72f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = V19ErrorSoft,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Um produto que recebia o desconto foi removido da venda. Selecione os produtos desejados ou ajuste o valor final.",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = V19ErrorSoft,
                        )
                    }
                }
            } else if (discountCents > 0L && noneSelected) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discount_no_products_warning"),
                    shape = RoundedCornerShape(12.dp),
                    color = V19ErrorContainer,
                    border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.72f)),
                ) {
                    Text(
                        text = "Selecione pelo menos um produto para aplicar o desconto.",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = V19ErrorSoft,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }

            if (selectedEntries.isEmpty()) {
                Text(
                    text = "Adicione produtos à venda acima para selecionar quais receberão o desconto.",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = HomeCardLabelInk.copy(alpha = 0.82f),
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .testTag("discount_products_empty"),
                )
            } else {
                if (discountCents == 0L && !targetMissing) {
                    Text(
                        text = "Informe acima um valor final menor que o subtotal para aplicar o desconto nos produtos marcados.",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = V19PurpleSoftText.copy(alpha = 0.90f),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedEntries.forEach { (product, qty) ->
                    val checked = product.id in selectedDiscountProductIds
                    val lineSubtotal = MonetaryRounding.safeMultiplyOrNull(product.price.rawCents, qty) ?: 0L
                    val rowShape = RoundedCornerShape(14.dp)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .clickable(
                                role = Role.Checkbox,
                                onClick = { onToggleProduct(product.id) },
                            )
                            .testTag("discount_product_row_${product.id}"),
                        shape = rowShape,
                        color = Color.Transparent,
                        border = BorderStroke(
                            width = if (checked) 1.3.dp else 1.dp,
                            color = if (checked) HomeCtaBorderGlow.copy(alpha = 0.78f) else GlassNeonBorder15,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .background(
                                    if (checked) {
                                        Brush.verticalGradient(listOf(Color(0xD9391A66), Color(0xCC240D43)))
                                    } else {
                                        Brush.verticalGradient(listOf(Color(0x99160A2B), Color(0x99120822)))
                                    },
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggleProduct(product.id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = V19PurplePrimary,
                                    uncheckedColor = HomeCardNeonBorder.copy(alpha = 0.75f),
                                    checkmarkColor = Color.White,
                                ),
                                modifier = Modifier.testTag("discount_product_checkbox_${product.id}"),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = product.name,
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (checked) Color.White else HomeCardLabelInk,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${qty}× · ${CurrencyFormatter.formatBrl(product.price)}/un · Subtotal: ${CurrencyFormatter.formatBrl(MoneyCents(lineSubtotal))}",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 12.sp,
                                    color = HomeCardLabelInk.copy(alpha = 0.88f),
                                )
                                Text(
                                    text = if (checked) "Recebe desconto" else "Sem desconto (inalterado)",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = if (checked) V19GoldAccent else V19InkMuted,
                                )
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Seletor do modo de escolha das pessoas que receberão o desconto:
 * - UNIFICADA: lista única sem repetições aplicada apenas aos produtos selecionados em que cada pessoa participa.
 * - INDIVIDUAL POR PRODUTO: seleção independente em cada produto marcado para desconto.
 */
@Composable
fun DiscountPeopleModeSelector(
    selectedMode: DiscountPeopleSelectionMode,
    onModeSelected: (DiscountPeopleSelectionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discount_mode_selector"),
        shape = RoundedCornerShape(16.dp),
        color = GlassInnerBoxBg,
        border = BorderStroke(1.1.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Modo de seleção de pessoas",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                )
                Text(
                    text = if (selectedMode == DiscountPeopleSelectionMode.UNIFIED) {
                        "Lista única sem repetições para todos os produtos selecionados."
                    } else {
                        "Escolha independente das pessoas em cada produto selecionado."
                    },
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = HomeCardLabelInk.copy(alpha = 0.85f),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DiscountModeOptionRow(
                    title = "UNIFICADA",
                    subtitle = "Marca ou desmarca cada pessoa uma única vez",
                    selected = selectedMode == DiscountPeopleSelectionMode.UNIFIED,
                    onClick = { onModeSelected(DiscountPeopleSelectionMode.UNIFIED) },
                    rowTag = "discount_mode_unified",
                )
                DiscountModeOptionRow(
                    title = "INDIVIDUAL POR PRODUTO",
                    subtitle = "Define as pessoas separadamente em cada produto",
                    selected = selectedMode == DiscountPeopleSelectionMode.INDIVIDUAL_BY_PRODUCT,
                    onClick = { onModeSelected(DiscountPeopleSelectionMode.INDIVIDUAL_BY_PRODUCT) },
                    rowTag = "discount_mode_individual",
                )
            }
        }
    }
}

@Composable
private fun DiscountModeOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    rowTag: String,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .testTag(rowTag),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            width = if (selected) 1.3.dp else 1.dp,
            color = if (selected) HomeCtaBorderGlow.copy(alpha = 0.82f) else GlassNeonBorder15,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(
                    if (selected) {
                        Brush.verticalGradient(listOf(Color(0xD9391A66), Color(0xCC240D43)))
                    } else {
                        Brush.verticalGradient(listOf(Color(0x99160A2B), Color(0x99120822)))
                    },
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = V19GoldAccent,
                    unselectedColor = HomeCardNeonBorder.copy(alpha = 0.75f),
                ),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = if (selected) Color.White else HomeCardLabelInk,
                )
                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = HomeCardLabelInk.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/**
 * Caixa 2 (Modo UNIFICADA) — Lista única sem repetições reunindo todos os participantes dos produtos
 * selecionados para desconto. Ao marcar/desmarcar uma pessoa, aplica apenas nos produtos
 * selecionados em que ela participa.
 */
@Composable
fun DiscountUnifiedPeopleBox(
    unifiedOptions: List<DiscountUnifiedPersonOption>,
    missingPeopleProductNames: List<String>,
    discountCents: Long,
    hasProductsInSale: Boolean,
    hasSelectedDiscountProducts: Boolean,
    onTogglePerson: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedCount = unifiedOptions.count { it.isSelected }
    val hasMissingWarning = missingPeopleProductNames.isNotEmpty() && hasSelectedDiscountProducts
    val highlightError = discountCents > 0L && hasMissingWarning

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discount_unified_people_box"),
        shape = RoundedCornerShape(16.dp),
        color = GlassInnerBoxBg,
        border = BorderStroke(
            width = if (highlightError) 1.5.dp else 1.1.dp,
            color = when {
                highlightError -> V19ErrorSoft
                unifiedOptions.isNotEmpty() -> HomeCardNeonBorder.copy(alpha = 0.65f)
                else -> GlassNeonBorder15
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = if (highlightError) V19ErrorSoft else V19PurpleSoftText,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "PESSOAS QUE RECEBERÃO O DESCONTO",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            color = Color.White,
                        )
                        Text(
                            text = "Modo unificado · Aplica-se somente aos produtos marcados em que cada pessoa participa.",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = HomeCardLabelInk.copy(alpha = 0.85f),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                DiscountStatusPill(
                    text = "$selectedCount de ${unifiedOptions.size}",
                    backgroundColor = if (highlightError) V19ErrorContainer else Color(0xFF33185E),
                    borderColor = if (highlightError) V19ErrorSoft.copy(alpha = 0.78f) else HomeCardNeonBorder.copy(alpha = 0.70f),
                    textColor = if (highlightError) V19ErrorSoft else V19PurpleSoftText,
                    modifier = Modifier.testTag("discount_unified_count"),
                )
            }

            if (hasMissingWarning) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discount_unified_missing_people_warning"),
                    shape = RoundedCornerShape(12.dp),
                    color = V19ErrorContainer,
                    border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.72f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = V19ErrorSoft,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Selecione pelo menos uma pessoa para: ${missingPeopleProductNames.joinToString(", ")}.",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = V19ErrorSoft,
                        )
                    }
                }
            }

            if (unifiedOptions.isEmpty()) {
                Text(
                    text = if (!hasProductsInSale) {
                        "Adicione produtos à venda e marque ao menos um item acima para listar as pessoas participantes."
                    } else {
                        "Nenhum produto selecionado para desconto. Marque os produtos acima para exibir as pessoas."
                    },
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = HomeCardLabelInk.copy(alpha = 0.82f),
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .testTag("discount_unified_people_empty"),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    unifiedOptions.forEach { person ->
                        val rowShape = RoundedCornerShape(12.dp)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .clickable(
                                    role = Role.Checkbox,
                                    onClick = { onTogglePerson(person.personId) },
                                )
                                .testTag("discount_unified_person_row_${person.personId}"),
                            shape = rowShape,
                            color = Color.Transparent,
                            border = BorderStroke(
                                width = if (person.isSelected) 1.2.dp else 1.dp,
                                color = if (person.isSelected) {
                                    HomeCtaBorderGlow.copy(alpha = 0.76f)
                                } else {
                                    GlassNeonBorder15
                                },
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .background(
                                        if (person.isSelected) {
                                            Brush.verticalGradient(listOf(Color(0xD9391A66), Color(0xCC240D43)))
                                        } else {
                                            Brush.verticalGradient(listOf(Color(0x99160A2B), Color(0x99120822)))
                                        },
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Checkbox(
                                    checked = person.isSelected,
                                    onCheckedChange = { onTogglePerson(person.personId) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = V19PurplePrimary,
                                        uncheckedColor = HomeCardNeonBorder.copy(alpha = 0.75f),
                                        checkmarkColor = Color.White,
                                    ),
                                    modifier = Modifier.testTag(
                                        "discount_unified_person_checkbox_${person.personId}",
                                    ),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = person.personName,
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (person.isSelected) Color.White else HomeCardLabelInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "Produtos: ${person.participatingProductNames.joinToString(", ")} · Original: ${CurrencyFormatter.formatBrl(person.totalOriginalSubtotal)}",
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = HomeCardLabelInk.copy(alpha = 0.88f),
                                    )
                                    if (person.isSelected) {
                                        Text(
                                            text = "Recebe desconto nos produtos participantes",
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            color = V19GoldAccent,
                                        )
                                    } else {
                                        DiscountStatusPill(
                                            text = "Sem desconto — mantém 100%",
                                            backgroundColor = V19SuccessContainer,
                                            borderColor = V19MintSuccess.copy(alpha = 0.70f),
                                            textColor = V19SuccessInk,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Caixa 2 (Modo INDIVIDUAL POR PRODUTO) — Caixa recolhível independente para selecionar, por produto,
 * as pessoas que RECEBERÃO o desconto. O desconto de cada produto é dividido igualmente apenas entre
 * as pessoas marcadas naquele produto. Pessoas desmarcadas mantêm 100% do valor original.
 */
@Composable
fun DiscountRecipientPeopleBox(
    productGroups: List<DiscountProductPeopleGroup>,
    discountCents: Long,
    onTogglePersonForProduct: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    hasProductsInSale: Boolean = true,
    initialExpanded: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf(initialExpanded) }

    val hasAnyEmptyGroup = discountCents > 0L && productGroups.any { it.selectedCount == 0 }
    val totalSelectedAcrossGroups = productGroups.sumOf { it.selectedCount }

    val subtitleText = when {
        !hasProductsInSale ->
            "Adicione produtos à venda para listar as pessoas por produto"
        productGroups.isEmpty() ->
            "Marque ao menos um produto acima para escolher quem receberá o desconto"
        hasAnyEmptyGroup ->
            "Atenção: há produto sem pessoa selecionada · Toque para ${if (expanded) "fechar" else "abrir"}"
        else ->
            "$totalSelectedAcrossGroups seleção(ões) em ${productGroups.size} produto(s) · Divisão igual · Toque para ${if (expanded) "fechar" else "abrir"}"
    }

    val boxShape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discount_recipient_people_box"),
        shape = boxShape,
        color = GlassInnerBoxBg,
        border = BorderStroke(
            width = if (hasAnyEmptyGroup) 1.5.dp else 1.1.dp,
            color = when {
                hasAnyEmptyGroup -> V19ErrorSoft
                productGroups.isNotEmpty() -> HomeCardNeonBorder.copy(alpha = 0.65f)
                else -> GlassNeonBorder15
            },
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Cabeçalho clicável que abre/fecha a caixa recolhível
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(boxShape)
                    .clickable(
                        role = Role.Button,
                        onClick = { expanded = !expanded },
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("discount_recipient_people_header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Group,
                    contentDescription = null,
                    tint = if (hasAnyEmptyGroup) V19ErrorSoft else V19PurpleSoftText,
                    modifier = Modifier.size(22.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "PESSOAS QUE RECEBERÃO O DESCONTO",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = Color.White,
                    )
                    Text(
                        text = subtitleText,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = if (hasAnyEmptyGroup) V19ErrorSoft else HomeCardLabelInk.copy(alpha = 0.85f),
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) {
                        "Recolher pessoas que receberão o desconto"
                    } else {
                        "Expandir pessoas que receberão o desconto"
                    },
                    tint = V19PurpleSoftText,
                    modifier = Modifier.size(24.dp),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.30f))

                    if (productGroups.isEmpty()) {
                        Text(
                            text = if (!hasProductsInSale) {
                                "Adicione produtos à venda e marque ao menos um item acima para escolher as pessoas que receberão o desconto."
                            } else {
                                "Nenhum produto selecionado para desconto. Marque os produtos acima para exibir as pessoas de cada produto."
                            },
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = HomeCardLabelInk.copy(alpha = 0.82f),
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .testTag("discount_recipient_people_empty"),
                        )
                    } else {
                        Text(
                            text = "Marque as pessoas que receberão o desconto em cada produto. O desconto do produto é dividido igualmente apenas entre as selecionadas.",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = HomeCardLabelInk.copy(alpha = 0.85f),
                        )

                        productGroups.forEach { group ->
                            val groupMissingRecipients = discountCents > 0L && group.selectedCount == 0
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("discount_recipient_product_group_${group.productId}"),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0x991C0C36),
                                border = BorderStroke(
                                    width = if (groupMissingRecipients) 1.4.dp else 1.dp,
                                    color = if (groupMissingRecipients) {
                                        V19ErrorSoft.copy(alpha = 0.85f)
                                    } else {
                                        GlassNeonBorder15
                                    },
                                ),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = "${group.quantity}× ${group.productName}",
                                                fontFamily = PlusJakartaSansFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color.White,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "Subtotal do item: ${CurrencyFormatter.formatBrl(group.originalSubtotal)}",
                                                fontFamily = PlusJakartaSansFontFamily,
                                                fontSize = 12.sp,
                                                color = HomeCardLabelInk.copy(alpha = 0.88f),
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        DiscountStatusPill(
                                            text = "${group.selectedCount} de ${group.totalCount} selecionada(s)",
                                            backgroundColor = if (group.selectedCount == 0) {
                                                V19ErrorContainer
                                            } else {
                                                Color(0xFF33185E)
                                            },
                                            borderColor = if (group.selectedCount == 0) {
                                                V19ErrorSoft.copy(alpha = 0.78f)
                                            } else {
                                                HomeCardNeonBorder.copy(alpha = 0.70f)
                                            },
                                            textColor = if (group.selectedCount == 0) V19ErrorSoft else V19PurpleSoftText,
                                            modifier = Modifier.testTag("discount_recipient_group_count_${group.productId}"),
                                        )
                                    }

                                    if (group.selectedCount == 0) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("discount_recipient_no_people_warning_${group.productId}"),
                                            shape = RoundedCornerShape(10.dp),
                                            color = V19ErrorContainer,
                                            border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.72f)),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Warning,
                                                    contentDescription = null,
                                                    tint = V19ErrorSoft,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Text(
                                                    text = "Selecione pelo menos uma pessoa para receber o desconto em \"${group.productName}\".",
                                                    fontFamily = PlusJakartaSansFontFamily,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp,
                                                    color = V19ErrorSoft,
                                                )
                                            }
                                        }
                                    }

                                    group.people.forEach { person ->
                                        val rowShape = RoundedCornerShape(12.dp)
                                        val toggleAction = {
                                            onTogglePersonForProduct(group.productId, person.personId)
                                        }
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(rowShape)
                                                .clickable(
                                                    role = Role.Checkbox,
                                                    onClick = toggleAction,
                                                )
                                                .testTag("discount_recipient_person_row_${group.productId}_${person.personId}"),
                                            shape = rowShape,
                                            color = Color.Transparent,
                                            border = BorderStroke(
                                                width = if (person.isSelected) 1.2.dp else 1.dp,
                                                color = if (person.isSelected) {
                                                    HomeCtaBorderGlow.copy(alpha = 0.76f)
                                                } else {
                                                    GlassNeonBorder15
                                                },
                                            ),
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 52.dp)
                                                    .background(
                                                        if (person.isSelected) {
                                                            Brush.verticalGradient(listOf(Color(0xD9391A66), Color(0xCC240D43)))
                                                        } else {
                                                            Brush.verticalGradient(listOf(Color(0x99160A2B), Color(0x99120822)))
                                                        },
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Checkbox(
                                                    checked = person.isSelected,
                                                    onCheckedChange = { toggleAction() },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = V19PurplePrimary,
                                                        uncheckedColor = HomeCardNeonBorder.copy(alpha = 0.75f),
                                                        checkmarkColor = Color.White,
                                                    ),
                                                    modifier = Modifier.testTag(
                                                        "discount_recipient_person_checkbox_${group.productId}_${person.personId}",
                                                    ),
                                                )
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                                ) {
                                                    Text(
                                                        text = person.personName,
                                                        fontFamily = PlusJakartaSansFontFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = if (person.isSelected) Color.White else HomeCardLabelInk,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    val qtyHint = if (group.quantity > 1) {
                                                        " (${group.quantity}× ${CurrencyFormatter.formatBrl(person.unitAmount)})"
                                                    } else {
                                                        ""
                                                    }
                                                    Text(
                                                        text = "Original neste produto: ${CurrencyFormatter.formatBrl(person.lineOriginalSubtotal)}$qtyHint",
                                                        fontFamily = PlusJakartaSansFontFamily,
                                                        fontSize = 12.sp,
                                                        color = HomeCardLabelInk.copy(alpha = 0.90f),
                                                    )
                                                    if (person.isSelected) {
                                                        Text(
                                                            text = "Recebe desconto igualitário",
                                                            fontFamily = PlusJakartaSansFontFamily,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 11.sp,
                                                            color = V19GoldAccent,
                                                        )
                                                    } else {
                                                        DiscountStatusPill(
                                                            text = "Sem desconto — mantém 100%",
                                                            backgroundColor = V19SuccessContainer,
                                                            borderColor = V19MintSuccess.copy(alpha = 0.70f),
                                                            textColor = V19SuccessInk,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Prévia detalhada ao vivo do desconto antes de salvar a venda.
 * Exibe produtos selecionados/não selecionados, subtotais, pessoas protegidas/elegíveis
 * e a participação de cada pessoa ANTES e DEPOIS do desconto (além de tarifa e líquido).
 */
@Composable
fun VenderDiscountPreviewCard(
    preview: VenderDiscountPreview,
    modifier: Modifier = Modifier,
) {
    VenderGlassCard(
        modifier = modifier.testTag("vender_discount_preview_card"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Prévia detalhada do desconto",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                DiscountStatusPill(
                    text = if (preview.isValid) "Conferido" else "Bloqueado",
                    backgroundColor = if (preview.isValid) V19SuccessContainer else V19ErrorContainer,
                    borderColor = if (preview.isValid) V19MintSuccess.copy(alpha = 0.72f) else V19ErrorSoft.copy(alpha = 0.78f),
                    textColor = if (preview.isValid) V19SuccessInk else V19ErrorSoft,
                )
            }

            if (!preview.validationError.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag("vender_discount_preview_error"),
                    shape = RoundedCornerShape(14.dp),
                    color = V19ErrorContainer,
                    border = BorderStroke(1.2.dp, V19ErrorSoft.copy(alpha = 0.80f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = V19ErrorSoft,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = preview.validationError,
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = V19ErrorSoft,
                        )
                    }
                }
            }

            // Resumo numérico dos produtos selecionados
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = GlassInnerBoxBg,
                border = BorderStroke(1.dp, GlassNeonBorder15),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PreviewMetricRow(
                        label = "Valor original dos selecionados",
                        value = CurrencyFormatter.formatBrl(preview.selectedOriginalSubtotal),
                        valueColor = Color.White,
                    )
                    PreviewMetricRow(
                        label = "Valor total do desconto",
                        value = if (preview.discountTotal.rawCents > 0L) {
                            "- ${CurrencyFormatter.formatBrl(preview.discountTotal)}"
                        } else {
                            CurrencyFormatter.formatBrl(MoneyCents.ZERO)
                        },
                        valueColor = V19GoldAccent,
                    )
                    HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.28f))
                    PreviewMetricRow(
                        label = "Valor final dos selecionados",
                        value = CurrencyFormatter.formatBrl(preview.selectedFinalAmount),
                        valueColor = V19MintSuccess,
                        bold = true,
                    )
                }
            }

            // Produtos selecionados para desconto com detalhamento por pessoa no produto
            if (preview.selectedProducts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Produtos com desconto (${preview.selectedProducts.size})",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = V19PurpleSoftText,
                    )
                    preview.selectedProducts.forEach { line ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("preview_selected_product_${line.productId}"),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x99261047),
                            border = BorderStroke(1.dp, GlassNeonBorder15),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${line.quantity}× ${line.productName}",
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = CurrencyFormatter.formatBrl(line.finalAmount),
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = V19MintSuccess,
                                    )
                                }
                                Text(
                                    text = "Original: ${CurrencyFormatter.formatBrl(line.originalSubtotal)} · Desconto: -${CurrencyFormatter.formatBrl(line.discountAmount)}",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 12.sp,
                                    color = HomeCardLabelInk.copy(alpha = 0.88f),
                                )
                                if (line.selectedRecipients.isNotEmpty()) {
                                    Text(
                                        text = "Recebem desconto igualitário (${line.selectedRecipients.size}):",
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = V19GoldAccent,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                    line.selectedRecipients.forEach { pLine ->
                                        Text(
                                            text = "• ${pLine.personName}: ${CurrencyFormatter.formatBrl(pLine.originalShare)} → ${CurrencyFormatter.formatBrl(pLine.finalShare)} (-${CurrencyFormatter.formatBrl(pLine.discountAbsorbed)})",
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontSize = 11.5.sp,
                                            color = Color.White,
                                            modifier = Modifier.testTag(
                                                "preview_product_recipient_${line.productId}_${pLine.personId}",
                                            ),
                                        )
                                    }
                                }
                                if (line.unselectedParticipants.isNotEmpty()) {
                                    Text(
                                        text = "Sem desconto neste produto (100% mantido):",
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = V19SuccessInk,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                    line.unselectedParticipants.forEach { pLine ->
                                        Text(
                                            text = "• ${pLine.personName}: ${CurrencyFormatter.formatBrl(pLine.originalShare)} (sem desconto)",
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontSize = 11.5.sp,
                                            color = V19SuccessInk,
                                            modifier = Modifier.testTag(
                                                "preview_product_unselected_${line.productId}_${pLine.personId}",
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Produtos NÃO selecionados (inalterados)
            if (preview.unselectedProducts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Produtos sem desconto (${preview.unselectedProducts.size} · inalterados)",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = HomeCardLabelInk,
                    )
                    preview.unselectedProducts.forEach { line ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("preview_unselected_product_${line.productId}"),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x80130924),
                            border = BorderStroke(1.dp, GlassNeonBorder15.copy(alpha = 0.65f)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${line.quantity}× ${line.productName}",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = HomeCardLabelInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${CurrencyFormatter.formatBrl(line.originalSubtotal)} (sem desconto)",
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = V19InkMuted,
                                )
                            }
                        }
                    }
                }
            }

            // Detalhamento consolidado por pessoa: ANTES e DEPOIS do desconto
            if (preview.allPersonBreakdowns.isNotEmpty()) {
                HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.30f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Total consolidado por pessoa (antes e depois)",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                    )
                    preview.allPersonBreakdowns.forEach { person ->
                        DiscountPreviewPersonRow(person = person)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscountPreviewPersonRow(
    person: DiscountPreviewPersonLine,
) {
    val badgeLabel: String
    val badgeBg: Color
    val badgeBorder: Color
    val badgeText: Color

    when {
        !person.participatesInSelectedProducts -> {
            badgeLabel = "Fora do desconto"
            badgeBg = Color(0xFF1D1033)
            badgeBorder = GlassNeonBorder15
            badgeText = HomeCardLabelInk
        }
        person.receivesDiscountInAnyProduct -> {
            badgeLabel = "Recebe desconto"
            badgeBg = Color(0xFF33185E)
            badgeBorder = HomeCardNeonBorder.copy(alpha = 0.70f)
            badgeText = V19PurpleSoftText
        }
        else -> {
            badgeLabel = "Sem desconto (100%)"
            badgeBg = V19SuccessContainer
            badgeBorder = V19MintSuccess.copy(alpha = 0.70f)
            badgeText = V19SuccessInk
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("preview_person_${person.personId}"),
        shape = RoundedCornerShape(12.dp),
        color = GlassInnerBoxBg,
        border = BorderStroke(
            width = 1.dp,
            color = if (!person.receivesDiscountInAnyProduct) {
                V19MintSuccess.copy(alpha = 0.45f)
            } else {
                GlassNeonBorder15
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = person.personName,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                DiscountStatusPill(
                    text = badgeLabel,
                    backgroundColor = badgeBg,
                    borderColor = badgeBorder,
                    textColor = badgeText,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Antes: ${CurrencyFormatter.formatBrl(person.beforeDiscount)}",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    color = HomeCardLabelInk,
                )
                Text(
                    text = "Depois: ${CurrencyFormatter.formatBrl(person.afterDiscountGross)}",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = if (person.discountAbsorbed.rawCents > 0L) V19GoldAccent else V19MintSuccess,
                )
            }

            if (person.discountAbsorbed.rawCents > 0L) {
                Text(
                    text = "Desconto absorvido: -${CurrencyFormatter.formatBrl(person.discountAbsorbed)}",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = V19GoldAccent,
                )
            }

            if (person.feeAmount.rawCents > 0L) {
                Text(
                    text = "Tarifa maquininha: -${CurrencyFormatter.formatBrl(person.feeAmount)} · Líquido: ${CurrencyFormatter.formatBrl(person.netAmount)}",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = V19PurpleSoftText,
                )
            }
        }
    }
}

@Composable
private fun PreviewMetricRow(
    label: String,
    value: String,
    valueColor: Color,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (bold) 13.sp else 12.sp,
            color = if (bold) Color.White else HomeCardLabelInk,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold,
            fontSize = if (bold) 15.sp else 13.sp,
            color = valueColor,
        )
    }
}

@Composable
private fun DiscountStatusPill(
    text: String,
    backgroundColor: Color,
    borderColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(999.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = text,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}
