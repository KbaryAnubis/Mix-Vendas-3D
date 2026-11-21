package com.mixvendas3d.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.reporting.RealDailySalesReport
import com.mixvendas3d.app.domain.reporting.RealDaySalesSummary
import com.mixvendas3d.app.domain.reporting.RealMonthSalesSummary
import com.mixvendas3d.app.ui.components.V19TranslucentCard
import com.mixvendas3d.app.ui.theme.V19GoldAccent
import com.mixvendas3d.app.ui.theme.V19HeroEnd
import com.mixvendas3d.app.ui.theme.V19HeroStart
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19MintSuccess
import com.mixvendas3d.app.ui.theme.V19OutlineSubtle
import com.mixvendas3d.app.ui.theme.V19PurpleGlow
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SurfaceChip
import com.mixvendas3d.app.ui.theme.V19SurfaceInput
import com.mixvendas3d.app.util.CurrencyFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DailySalesScreen(
    report: RealDailySalesReport,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayMonthFmt = remember { DateTimeFormatter.ofPattern("dd/MM") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_mais_detail_daily_sales"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "daily_sales_header_and_summary") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.daily_sales_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = V19InkMuted,
                    modifier = Modifier.testTag("daily_sales_description"),
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag("daily_sales_summary_card"),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.45f)),
                ) {
                    Column(
                        modifier = Modifier
                            .background(Brush.linearGradient(listOf(V19HeroStart, V19HeroEnd)))
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.daily_sales_total_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = V19PurpleSoftText,
                        )
                        Text(
                            text = CurrencyFormatter.formatBrl(report.totalSold),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.testTag("daily_sales_total_sold"),
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.16f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("daily_sales_counts"),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SummaryMetricPill(
                                label = "Vendas no histórico",
                                value = "${report.totalSalesCount} ${if (report.totalSalesCount == 1) "venda" else "vendas"}",
                                modifier = Modifier.weight(1f),
                            )
                            SummaryMetricPill(
                                label = "Dias com movimento",
                                value = "${report.totalDaysCount} ${if (report.totalDaysCount == 1) "dia" else "dias"}",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        if (report.months.isEmpty()) {
            item(key = "daily_sales_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                ) {
                    V19TranslucentCard(
                        modifier = Modifier.testTag("daily_sales_empty_state"),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = V19PurpleSoftText,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = stringResource(R.string.daily_sales_empty_message),
                                style = MaterialTheme.typography.titleMedium,
                                color = V19InkPrimary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        } else {
            items(
                items = report.months,
                key = { it.yearMonth.toString() },
            ) { month ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                ) {
                    MonthSalesCard(
                        month = month,
                        dayMonthFmt = dayMonthFmt,
                        onDayClick = onDayClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = V19PurpleSoftText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MonthSalesCard(
    month: RealMonthSalesSummary,
    dayMonthFmt: DateTimeFormatter,
    onDayClick: (LocalDate) -> Unit,
) {
    V19TranslucentCard(
        modifier = Modifier.testTag("daily_month_card_${month.yearMonth}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(V19SurfaceChip.copy(alpha = 0.80f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = month.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = V19GoldAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${month.salesCount} ${if (month.salesCount == 1) "venda" else "vendas"} · ${month.days.size} ${if (month.days.size == 1) "dia" else "dias"}",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = V19InkMuted,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.testTag("daily_month_total_${month.yearMonth}"),
            ) {
                Text(
                    text = stringResource(R.string.daily_sales_month_total_prefix),
                    style = MaterialTheme.typography.labelSmall,
                    color = V19PurpleSoftText,
                )
                Text(
                    text = CurrencyFormatter.formatBrl(month.totalSold),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = V19MintSuccess,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        month.days.forEachIndexed { idx, day ->
            DaySalesRow(
                day = day,
                dayMonthFmt = dayMonthFmt,
                onClick = { onDayClick(day.date) },
            )
            if (idx < month.days.lastIndex) {
                HorizontalDivider(color = V19OutlineSubtle)
            }
        }
    }
}

@Composable
private fun DaySalesRow(
    day: RealDaySalesSummary,
    dayMonthFmt: DateTimeFormatter,
    onClick: () -> Unit,
) {
    val dateLabel = day.date.format(dayMonthFmt)
    val countLabel = "${day.salesCount} ${if (day.salesCount == 1) "venda" else "vendas"}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("daily_day_row_${day.date}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(V19SurfaceInput.copy(alpha = 0.92f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = V19InkPrimary,
                )
            }
            Text(
                text = countLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = V19InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = CurrencyFormatter.formatBrl(day.totalSold),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Abrir vendas de $dateLabel",
                tint = V19PurpleSoftText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
