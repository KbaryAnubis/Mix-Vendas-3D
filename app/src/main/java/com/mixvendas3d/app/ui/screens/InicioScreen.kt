package com.mixvendas3d.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.ui.components.InicioMetricCard
import com.mixvendas3d.app.ui.components.InicioNovaVendaButton
import com.mixvendas3d.app.ui.components.InicioPeopleSalesCard
import com.mixvendas3d.app.ui.theme.PlusJakartaSansFontFamily
import com.mixvendas3d.app.util.CurrencyFormatter

@Composable
fun InicioScreen(
    uiState: MixVendasShellUiState,
    onNewSaleClick: () -> Unit,
    onToggleShowAllPeople: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allPeople = uiState.homeRealPersonSummaries
    val visiblePeople = if (uiState.showAllPeopleOnHome) {
        allPeople
    } else {
        allPeople.take(2)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_inicio"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "home_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .statusBarsPadding()
                    .padding(top = 24.dp, bottom = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.nav_home),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    letterSpacing = (-0.5).sp,
                    color = Color.White,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag("home_header_title"),
                )
            }
        }

        item(key = "home_cta_new_sale") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
            ) {
                InicioNovaVendaButton(onClick = onNewSaleClick)
            }
        }

        item(key = "home_kpi_row_1") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InicioMetricCard(
                    iconRes = R.drawable.mv3d_dinheiro_moedas,
                    label = stringResource(R.string.home_metric_total_sold),
                    value = CurrencyFormatter.formatBrl(uiState.homeRealTotalGross),
                    testTag = "home_metric_total_sold",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                InicioMetricCard(
                    iconRes = R.drawable.mv3d_tarifas,
                    label = stringResource(R.string.home_metric_fees),
                    value = CurrencyFormatter.formatBrl(uiState.homeRealTotalFee),
                    testTag = "home_metric_fees",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        item(key = "home_kpi_row_2") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InicioMetricCard(
                    iconRes = R.drawable.mv3d_dinheiro_notas,
                    label = stringResource(R.string.home_metric_total_net),
                    value = CurrencyFormatter.formatBrl(uiState.homeRealTotalNet),
                    testTag = "home_metric_total_net",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                InicioMetricCard(
                    iconRes = R.drawable.mv3d_vendas,
                    label = stringResource(R.string.home_metric_sales_count),
                    value = uiState.homeFilteredRealSalesCount.toString(),
                    testTag = "home_metric_sales_count",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        item(key = "home_people_sales") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
            ) {
                InicioPeopleSalesCard(
                    visiblePeople = visiblePeople,
                    totalPeopleCount = allPeople.size,
                    personSalesCounts = uiState.homeRealPersonSalesCounts,
                    showAllPeople = uiState.showAllPeopleOnHome,
                    onToggleShowAllPeople = onToggleShowAllPeople,
                )
            }
        }
    }
}
