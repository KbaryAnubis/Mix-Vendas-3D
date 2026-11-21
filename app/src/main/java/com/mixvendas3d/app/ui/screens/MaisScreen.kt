package com.mixvendas3d.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.mixvendas3d.app.navigation.MaisSubDestination
import com.mixvendas3d.app.ui.components.MaisOptionCard
import com.mixvendas3d.app.ui.theme.PlusJakartaSansFontFamily

private data class MaisOptionItem(
    val destination: MaisSubDestination,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:DrawableRes val iconRes: Int,
)

@Composable
fun MaisScreen(
    onDestinationClick: (MaisSubDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val optionItems = listOf(
        MaisOptionItem(
            destination = MaisSubDestination.TYPES_PRODUCTS,
            titleRes = R.string.mais_card_products_title,
            descriptionRes = R.string.mais_card_products_desc,
            iconRes = R.drawable.mv3d_produtos,
        ),
        MaisOptionItem(
            destination = MaisSubDestination.NAMES,
            titleRes = R.string.mais_card_names_title,
            descriptionRes = R.string.mais_card_names_desc,
            iconRes = R.drawable.mv3d_nomes,
        ),
        MaisOptionItem(
            destination = MaisSubDestination.MACHINES,
            titleRes = R.string.mais_card_machines_title,
            descriptionRes = R.string.mais_card_machines_desc,
            iconRes = R.drawable.mv3d_maquininhas,
        ),
        MaisOptionItem(
            destination = MaisSubDestination.IMPORT_EXPORT,
            titleRes = R.string.mais_card_import_export_title,
            descriptionRes = R.string.mais_card_import_export_desc,
            iconRes = R.drawable.mv3d_importar_exportar,
        ),
        MaisOptionItem(
            destination = MaisSubDestination.UPDATE_SYSTEM,
            titleRes = R.string.mais_card_update_system_title,
            descriptionRes = R.string.mais_card_update_system_desc,
            iconRes = R.drawable.mv3d_atualizar_sistema,
        ),
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_mais"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "mais_cards_container") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 24.dp, bottom = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.more_title),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        letterSpacing = (-0.5).sp,
                        color = Color.White,
                        modifier = Modifier
                            .semantics { heading() }
                            .testTag("mais_header_title"),
                    )
                }

                optionItems.forEach { item ->
                    MaisOptionCard(
                        title = stringResource(item.titleRes),
                        description = stringResource(item.descriptionRes),
                        iconRes = item.iconRes,
                        testTag = "mais_item_${item.destination.name.lowercase()}",
                        onClick = { onDestinationClick(item.destination) },
                    )
                }
            }
        }
    }
}
