package com.mixvendas3d.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.reporting.DailySalesAggregator
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeCardGlassBottom
import com.mixvendas3d.app.ui.theme.HomeCardGlassTop
import com.mixvendas3d.app.ui.theme.HomeCardLabelInk
import com.mixvendas3d.app.ui.theme.HomeCardNeonBorder
import com.mixvendas3d.app.ui.theme.PlusJakartaSansFontFamily
import com.mixvendas3d.app.ui.theme.V19ErrorContainer
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import com.mixvendas3d.app.ui.theme.V19GoldAccent
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19OutlineInput
import com.mixvendas3d.app.ui.theme.V19PurplePrimary
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SurfaceCard
import com.mixvendas3d.app.ui.theme.V19SurfaceInput
import com.mixvendas3d.app.util.CurrencyFormatter
import java.time.LocalDate
import java.time.ZoneId

enum class PeriodDateTarget {
    FROM,
    TO,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun mixVendasDatePickerColors(): DatePickerColors = DatePickerDefaults.colors(
    containerColor = V19SurfaceCard,
    titleContentColor = V19PurpleSoftText,
    headlineContentColor = Color.White,
    weekdayContentColor = V19InkMuted,
    subheadContentColor = V19PurpleSoftText,
    navigationContentColor = Color.White,
    yearContentColor = Color.White,
    currentYearContentColor = V19GoldAccent,
    selectedYearContentColor = Color.White,
    selectedYearContainerColor = V19PurplePrimary,
    dayContentColor = Color.White,
    selectedDayContentColor = Color.White,
    selectedDayContainerColor = V19PurplePrimary,
    todayContentColor = V19GoldAccent,
    todayDateBorderColor = V19GoldAccent,
)

/**
 * Componente compartilhado global para filtros de período personalizado (`Datas`) em `DD/MM/AAAA`,
 * com abertura de calendário interativo do Material 3 ao toque e limpeza rápida.
 */
@Composable
fun CustomDateRangeFilterFields(
    fromDate: String,
    toDate: String,
    onCustomDatesChanged: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    useV19SurfaceColors: Boolean = false,
) {
    var activePickerTarget by remember { mutableStateOf<PeriodDateTarget?>(null) }

    val fromParsed = remember(fromDate) { CurrencyFormatter.parseFlexibleFilterDate(fromDate) }
    val toParsed = remember(toDate) { CurrencyFormatter.parseFlexibleFilterDate(toDate) }
    val fromDisplay = remember(fromDate) { CurrencyFormatter.formatFilterDateDisplay(fromDate) }
    val toDisplay = remember(toDate) { CurrencyFormatter.formatFilterDateDisplay(toDate) }
    val invalidRange = fromParsed != null && toParsed != null && fromParsed.isAfter(toParsed)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PeriodDateFieldCard(
                label = "De",
                formattedValue = fromDisplay,
                placeholder = "DD/MM/AAAA",
                hasError = invalidRange,
                useV19SurfaceColors = useV19SurfaceColors,
                onOpenPicker = { activePickerTarget = PeriodDateTarget.FROM },
                onClear = { onCustomDatesChanged("", toDisplay) },
                fieldTestTag = "period_from_input",
                clearTestTag = "period_from_clear",
                clearContentDescription = "Limpar data inicial",
                modifier = Modifier.weight(1f),
            )
            PeriodDateFieldCard(
                label = "Até",
                formattedValue = toDisplay,
                placeholder = "DD/MM/AAAA",
                hasError = invalidRange,
                useV19SurfaceColors = useV19SurfaceColors,
                onOpenPicker = { activePickerTarget = PeriodDateTarget.TO },
                onClear = { onCustomDatesChanged(fromDisplay, "") },
                fieldTestTag = "period_to_input",
                clearTestTag = "period_to_clear",
                clearContentDescription = "Limpar data final",
                modifier = Modifier.weight(1f),
            )
        }

        if (invalidRange) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("period_invalid_range_warning"),
                shape = RoundedCornerShape(12.dp),
                color = V19ErrorContainer,
                border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.72f)),
            ) {
                Text(
                    text = "A data inicial não pode ser maior que a data final.",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = V19ErrorSoft,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }

    val target = activePickerTarget
    if (target != null) {
        val currentSelectedDate = when (target) {
            PeriodDateTarget.FROM -> fromParsed
            PeriodDateTarget.TO -> toParsed
        }
        PeriodDatePickerDialog(
            target = target,
            initialDate = currentSelectedDate,
            onDateConfirmed = { pickedDate ->
                val formatted = CurrencyFormatter.formatLocalDate(pickedDate)
                when (target) {
                    PeriodDateTarget.FROM -> onCustomDatesChanged(formatted, toDisplay)
                    PeriodDateTarget.TO -> onCustomDatesChanged(fromDisplay, formatted)
                }
                activePickerTarget = null
            },
            onClear = {
                when (target) {
                    PeriodDateTarget.FROM -> onCustomDatesChanged("", toDisplay)
                    PeriodDateTarget.TO -> onCustomDatesChanged(fromDisplay, "")
                }
                activePickerTarget = null
            },
            onDismiss = { activePickerTarget = null },
        )
    }
}

@Composable
private fun PeriodDateFieldCard(
    label: String,
    formattedValue: String,
    placeholder: String,
    hasError: Boolean,
    useV19SurfaceColors: Boolean,
    onOpenPicker: () -> Unit,
    onClear: () -> Unit,
    fieldTestTag: String,
    clearTestTag: String,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = when {
        hasError -> V19ErrorSoft
        formattedValue.isNotEmpty() -> HomeCardNeonBorder.copy(alpha = 0.75f)
        useV19SurfaceColors -> V19OutlineInput
        else -> GlassNeonBorder15
    }

    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape),
        shape = shape,
        color = if (useV19SurfaceColors) V19SurfaceInput.copy(alpha = 0.90f) else Color.Transparent,
        border = BorderStroke(if (hasError) 1.3.dp else 1.1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (useV19SurfaceColors) {
                        Modifier
                    } else {
                        Modifier.background(Brush.verticalGradient(listOf(HomeCardGlassTop, HomeCardGlassBottom)))
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClick = onOpenPicker)
                    .semantics(mergeDescendants = true) {}
                    .padding(
                        start = 12.dp,
                        end = if (formattedValue.isNotEmpty()) 4.dp else 12.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    )
                    .testTag(fieldTestTag),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = if (formattedValue.isNotEmpty()) V19GoldAccent else V19PurpleSoftText,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "$label (DD/MM/AAAA)",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = V19PurpleSoftText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formattedValue.ifEmpty { placeholder },
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = if (formattedValue.isNotEmpty()) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                        color = if (formattedValue.isNotEmpty()) Color.White else HomeCardLabelInk.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (formattedValue.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(clearTestTag),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = clearContentDescription,
                        tint = V19PurpleSoftText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodDatePickerDialog(
    target: PeriodDateTarget,
    initialDate: LocalDate?,
    onDateConfirmed: (LocalDate) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now(ZoneId.systemDefault()) }
    val seedDate = initialDate ?: today
    val initialUtcMillis = remember(seedDate) {
        DailySalesAggregator.localDateToUtcDatePickerMillis(seedDate)
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialUtcMillis,
        initialDisplayedMonthMillis = initialUtcMillis,
    )
    val titleText = when (target) {
        PeriodDateTarget.FROM -> "Selecionar data inicial (De)"
        PeriodDateTarget.TO -> "Selecionar data final (Até)"
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = V19SurfaceCard),
        modifier = Modifier.testTag("period_date_picker_dialog"),
        confirmButton = {
            Button(
                onClick = {
                    val millis = datePickerState.selectedDateMillis
                    val resolved = if (millis != null) {
                        DailySalesAggregator.utcDatePickerMillisToLocalDate(millis)
                    } else {
                        seedDate
                    }
                    onDateConfirmed(resolved)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = V19PurplePrimary,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("period_date_picker_confirm"),
            ) {
                Text(
                    text = stringResource(R.string.sell_date_picker_confirm),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (initialDate != null) {
                    OutlinedButton(
                        onClick = onClear,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, V19GoldAccent.copy(alpha = 0.65f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("period_date_picker_clear"),
                    ) {
                        Text(
                            text = "Limpar",
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = V19GoldAccent,
                        )
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("period_date_picker_cancel"),
                ) {
                    Text(
                        text = stringResource(R.string.sell_date_picker_cancel),
                        fontFamily = PlusJakartaSansFontFamily,
                        color = V19PurpleSoftText,
                    )
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = titleText,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = V19PurpleSoftText,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 8.dp),
                    )
                },
                colors = mixVendasDatePickerColors(),
            )
        }
    }
}
