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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.mixvendas3d.app.domain.calculation.DiscountPreviewPersonLine
import com.mixvendas3d.app.domain.calculation.DiscountSelectablePersonItem
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
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19MintSuccess
import com.mixvendas3d.app.ui.theme.V19PurpleButtonStart
import com.mixvendas3d.app.ui.theme.V19PurplePrimary
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SuccessContainer
import com.mixvendas3d.app.ui.theme.V19SuccessInk
import com.mixvendas3d.app.ui.theme.V19SurfaceCard
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onSelectAll,
                    enabled = !allSelected && totalCount > 0,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (!allSelected) HomeCardNeonBorder.copy(alpha = 0.75f) else GlassNeonBorder15,
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
                        color = if (!allSelected) Color.White else V19InkMuted,
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

/**
 * Caixa 2 — Caixa recolhível independente para selecionar as pessoas que NÃO receberão desconto.
 * Exibe todas as pessoas únicas participantes dos produtos selecionados na Caixa 1 e protege
 * automaticamente a remuneração fixa por unidade (R$ 5,00/un), exigindo autorização explícita para reduzi-la.
 */
@Composable
fun DiscountProtectedPeopleBox(
    selectablePeople: List<DiscountSelectablePersonItem>,
    onToggleProtectedPerson: (String) -> Unit,
    onAuthorizeUnprotectFixedPerson: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var pendingFixedUnprotectPerson by remember { mutableStateOf<DiscountSelectablePersonItem?>(null) }

    val protectedCount = selectablePeople.count { it.isProtected }
    val protectedFixedCount = selectablePeople.count { it.isProtected && it.isFixedUnitProtectedByDefault }

    val subtitleText = when {
        selectablePeople.isEmpty() ->
            "Marque ao menos um produto acima para listar as pessoas participantes"
        protectedCount == 0 ->
            "Nenhuma pessoa protegida · Toque para ${if (expanded) "fechar" else "abrir"}"
        protectedFixedCount > 0 ->
            "$protectedCount pessoa(s) protegida(s) (inclui valor fixo R$ 5,00/un) · Toque para ${if (expanded) "fechar" else "abrir"}"
        else ->
            "$protectedCount pessoa(s) protegida(s) contra desconto · Toque para ${if (expanded) "fechar" else "abrir"}"
    }

    val boxShape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("discount_protected_people_box"),
        shape = boxShape,
        color = GlassInnerBoxBg,
        border = BorderStroke(
            width = if (protectedCount > 0) 1.3.dp else 1.1.dp,
            color = if (protectedCount > 0) V19GoldAccent.copy(alpha = 0.72f) else GlassNeonBorder15,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Cabeçalho clicável que abre/fecha a caixa
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
                    .testTag("discount_protected_people_header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = if (protectedCount > 0) V19GoldAccent else V19PurpleSoftText,
                    modifier = Modifier.size(22.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "PESSOAS QUE NÃO RECEBERÃO O DESCONTO",
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
                        color = if (protectedCount > 0) V19GoldAccent else HomeCardLabelInk.copy(alpha = 0.85f),
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) {
                        "Recolher pessoas que não receberão o desconto"
                    } else {
                        "Expandir pessoas que não receberão o desconto"
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.30f))

                    if (selectablePeople.isEmpty()) {
                        Text(
                            text = "Nenhum produto selecionado para desconto. Marque os produtos acima para exibir as pessoas participantes.",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = HomeCardLabelInk.copy(alpha = 0.82f),
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .testTag("discount_protected_people_empty"),
                        )
                    } else {
                        Text(
                            text = "Pessoas marcadas mantêm 100% da sua participação original em todos os produtos selecionados. O desconto será absorvido apenas pelas demais.",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = HomeCardLabelInk.copy(alpha = 0.85f),
                        )

                        selectablePeople.forEach { person ->
                            val rowShape = RoundedCornerShape(14.dp)
                            val handleToggle: () -> Unit = {
                                if (person.isProtected && person.isFixedUnitProtectedByDefault) {
                                    pendingFixedUnprotectPerson = person
                                } else {
                                    onToggleProtectedPerson(person.personId)
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(rowShape)
                                    .clickable(
                                        role = Role.Checkbox,
                                        onClick = handleToggle,
                                    )
                                    .testTag("discount_protected_person_row_${person.personId}"),
                                shape = rowShape,
                                color = Color.Transparent,
                                border = BorderStroke(
                                    width = if (person.isProtected) 1.3.dp else 1.dp,
                                    color = if (person.isProtected) {
                                        V19GoldAccent.copy(alpha = 0.78f)
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
                                            if (person.isProtected) {
                                                Brush.verticalGradient(listOf(Color(0xD93C2716), Color(0xCC24150E)))
                                            } else {
                                                Brush.verticalGradient(listOf(Color(0x99160A2B), Color(0x99120822)))
                                            },
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Checkbox(
                                        checked = person.isProtected,
                                        onCheckedChange = { handleToggle() },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = V19GoldAccent,
                                            uncheckedColor = HomeCardNeonBorder.copy(alpha = 0.75f),
                                            checkmarkColor = Color(0xFF1A0A2E),
                                        ),
                                        modifier = Modifier.testTag("discount_protected_person_checkbox_${person.personId}"),
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Text(
                                            text = person.personName,
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = person.participationSummary,
                                            fontFamily = PlusJakartaSansFontFamily,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            color = HomeCardLabelInk.copy(alpha = 0.90f),
                                        )
                                        when {
                                            person.isFixedUnitProtectedByDefault && person.isProtected -> {
                                                DiscountStatusPill(
                                                    text = "Valor fixo (R$ 5,00/un) · Protegida por padrão",
                                                    backgroundColor = Color(0xFF473114),
                                                    borderColor = V19GoldAccent.copy(alpha = 0.80f),
                                                    textColor = V19GoldAccent,
                                                )
                                            }
                                            person.isFixedUnitProtectedByDefault && person.isFixedReductionAuthorized -> {
                                                DiscountStatusPill(
                                                    text = "Valor fixo (R$ 5,00/un) · Redução autorizada",
                                                    backgroundColor = V19ErrorContainer,
                                                    borderColor = V19ErrorSoft.copy(alpha = 0.75f),
                                                    textColor = V19ErrorSoft,
                                                )
                                            }
                                            person.isProtected -> {
                                                DiscountStatusPill(
                                                    text = "Protegida — não recebe desconto",
                                                    backgroundColor = V19SuccessContainer,
                                                    borderColor = V19MintSuccess.copy(alpha = 0.70f),
                                                    textColor = V19SuccessInk,
                                                )
                                            }
                                            else -> {
                                                Text(
                                                    text = "Recebe desconto conforme regra do produto",
                                                    fontFamily = PlusJakartaSansFontFamily,
                                                    fontSize = 11.sp,
                                                    color = V19InkMuted,
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

    val pendingPerson = pendingFixedUnprotectPerson
    if (pendingPerson != null) {
        AlertDialog(
            onDismissRequest = { pendingFixedUnprotectPerson = null },
            containerColor = V19SurfaceCard,
            titleContentColor = Color.White,
            textContentColor = V19InkPrimary,
            modifier = Modifier.testTag("fixed_share_unprotect_dialog"),
            title = {
                Text(
                    text = "Autorizar redução de valor fixo?",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "${pendingPerson.personName} possui participação fixa de R$ 5,00 por unidade e é protegida por padrão contra descontos. Deseja autorizar que o desconto reduza a participação dessa pessoa?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = V19InkPrimary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val personId = pendingPerson.personId
                        pendingFixedUnprotectPerson = null
                        onAuthorizeUnprotectFixedPerson(personId)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = V19PurpleButtonStart,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("fixed_share_authorize_reduction_button"),
                ) {
                    Text(
                        text = "Autorizar redução",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingFixedUnprotectPerson = null },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("fixed_share_keep_protected_button"),
                ) {
                    Text(
                        text = "Manter protegida",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = V19GoldAccent,
                    )
                }
            },
        )
    }
}

/**
 * Prévia detalhada ao vivo do desconto antes de salvar a venda.
 * Exibe produtos selecionados/não selecionados, subtotais, pessoas protegidas/elegíveis
 * e a participação de cada pessoa ANTES e DEPOIS do desconto (além de tarifa e líquido).
 */
@OptIn(ExperimentalLayoutApi::class)
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

            // Produtos selecionados para desconto
            if (preview.selectedProducts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
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

            // Pessoas protegidas x Pessoas que absorvem o desconto
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val protectedNames = preview.protectedPeople.joinToString(", ") { it.personName }
                    .ifBlank { "Nenhuma" }
                val eligibleNames = preview.eligiblePeople.joinToString(", ") { it.personName }
                    .ifBlank { "Nenhuma" }
                DiscountStatusPill(
                    text = "Protegidas: $protectedNames",
                    backgroundColor = Color(0xFF3E2914),
                    borderColor = V19GoldAccent.copy(alpha = 0.72f),
                    textColor = V19GoldAccent,
                )
                DiscountStatusPill(
                    text = "Absorvem desconto: $eligibleNames",
                    backgroundColor = Color(0xFF2D1654),
                    borderColor = HomeCardNeonBorder.copy(alpha = 0.68f),
                    textColor = V19PurpleSoftText,
                )
            }

            // Detalhamento por pessoa: ANTES e DEPOIS do desconto
            if (preview.allPersonBreakdowns.isNotEmpty()) {
                HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.30f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Participação por pessoa (antes e depois)",
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
        person.isProtected && person.isFixedUnitShare -> {
            badgeLabel = "Protegida (fixo R$ 5,00/un)"
            badgeBg = Color(0xFF473114)
            badgeBorder = V19GoldAccent.copy(alpha = 0.80f)
            badgeText = V19GoldAccent
        }
        person.isProtected -> {
            badgeLabel = "Protegida"
            badgeBg = V19SuccessContainer
            badgeBorder = V19MintSuccess.copy(alpha = 0.70f)
            badgeText = V19SuccessInk
        }
        else -> {
            badgeLabel = "Absorve desconto"
            badgeBg = Color(0xFF33185E)
            badgeBorder = HomeCardNeonBorder.copy(alpha = 0.70f)
            badgeText = V19PurpleSoftText
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
            color = if (person.isProtected) V19GoldAccent.copy(alpha = 0.55f) else GlassNeonBorder15,
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
        modifier = modifier,
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
