package com.mixvendas3d.app.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mixvendas3d.app.BuildConfig
import com.mixvendas3d.app.R
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19MintSuccess
import com.mixvendas3d.app.ui.theme.V19OutlineInput
import com.mixvendas3d.app.ui.theme.V19OutlineSubtle
import com.mixvendas3d.app.ui.theme.V19PurpleButtonStart
import com.mixvendas3d.app.ui.theme.V19PurpleGlow
import com.mixvendas3d.app.ui.theme.V19PurplePrimary
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SuccessContainer
import com.mixvendas3d.app.ui.theme.V19SuccessInk
import com.mixvendas3d.app.ui.theme.V19SurfaceCard
import com.mixvendas3d.app.ui.theme.V19SurfaceChip
import com.mixvendas3d.app.ui.theme.V19SurfaceInput
import com.mixvendas3d.app.util.CurrencyFormatter

@Composable
fun StartupDiagnosticRoute(
    viewModel: StartupDiagnosticViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StartupDiagnosticScreen(
        uiState = uiState,
        onDraftValueChanged = viewModel::onDraftValueChanged,
        onSaveClick = viewModel::saveDiagnosticPreference,
        onClearClick = viewModel::clearDiagnosticPreference,
        modifier = modifier,
    )
}

@Composable
fun StartupDiagnosticScreen(
    uiState: StartupDiagnosticUiState,
    onDraftValueChanged: (String) -> Unit,
    onSaveClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Papel de parede oficial do APK em resolução nítida, sem blur, espelhamento ou duplicação.
        Image(
            painter = painterResource(id = R.drawable.fundo_oficial_castelo_roxo),
            contentDescription = stringResource(R.string.stage1_wallpaper_desc),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .testTag("castle_wallpaper_image"),
        )

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DiagnosticHeaderCard()

                    PreferencePersistenceCard(
                        uiState = uiState,
                        onDraftValueChanged = onDraftValueChanged,
                        onSaveClick = onSaveClick,
                        onClearClick = onClearClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticHeaderCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stage1_ready_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = V19SurfaceCard.copy(alpha = 0.90f),
        ),
        border = BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.34f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground_art),
                    contentDescription = stringResource(R.string.stage1_icon_desc),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = V19SurfaceChip,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, V19OutlineInput),
                    ) {
                        Text(
                            text = stringResource(R.string.stage1_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = V19PurpleSoftText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }

                    Text(
                        text = stringResource(R.string.stage1_ready_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = V19InkPrimary,
                        modifier = Modifier.testTag("stage1_ready_title"),
                    )
                }
            }

            Text(
                text = stringResource(R.string.stage1_ready_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = V19InkMuted,
            )

            HorizontalDivider(color = V19OutlineSubtle)

            Text(
                text = stringResource(R.string.stage1_specs_title),
                style = MaterialTheme.typography.titleSmall,
                color = V19PurpleSoftText,
            )

            DiagnosticInfoRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = V19MintSuccess,
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = stringResource(R.string.stage1_spec_package_label),
                value = BuildConfig.APPLICATION_ID,
            )

            DiagnosticInfoRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = V19PurplePrimary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = stringResource(R.string.stage1_spec_sdk_label),
                value = "minSdk 26 · targetSdk 36 (Android ${Build.VERSION.RELEASE})",
            )

            DiagnosticInfoRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = V19MintSuccess,
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = stringResource(R.string.stage1_spec_offline_label),
                value = stringResource(R.string.stage1_spec_offline_value),
            )

            DiagnosticInfoRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = V19PurplePrimary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = stringResource(R.string.stage1_spec_db_label),
                value = stringResource(R.string.stage1_spec_db_empty),
            )
        }
    }
}

@Composable
private fun DiagnosticInfoRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = V19InkMuted,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = V19InkPrimary,
            )
        }
    }
}

@Composable
private fun PreferencePersistenceCard(
    uiState: StartupDiagnosticUiState,
    onDraftValueChanged: (String) -> Unit,
    onSaveClick: () -> Unit,
    onClearClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("room_persistence_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = V19SurfaceCard.copy(alpha = 0.92f),
        ),
        border = BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.34f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.stage1_pref_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = V19InkPrimary,
            )

            Text(
                text = stringResource(R.string.stage1_pref_section_desc),
                style = MaterialTheme.typography.bodySmall,
                color = V19InkMuted,
            )

            Text(
                text = stringResource(R.string.stage1_pref_key_label, uiState.diagnosticKey),
                style = MaterialTheme.typography.labelMedium,
                color = V19PurpleSoftText,
            )

            OutlinedTextField(
                value = uiState.draftValue,
                onValueChange = onDraftValueChanged,
                singleLine = true,
                label = {
                    Text(text = stringResource(R.string.stage1_pref_input_label))
                },
                placeholder = {
                    Text(text = stringResource(R.string.stage1_pref_input_placeholder))
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = V19SurfaceInput,
                    unfocusedContainerColor = V19SurfaceInput,
                    focusedBorderColor = V19PurplePrimary,
                    unfocusedBorderColor = V19OutlineInput,
                    focusedTextColor = V19InkPrimary,
                    unfocusedTextColor = V19InkPrimary,
                    focusedLabelColor = V19PurpleSoftText,
                    unfocusedLabelColor = V19InkMuted,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("preference_value_input"),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onSaveClick,
                    enabled = uiState.draftValue.isNotBlank() && !uiState.isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = V19PurpleButtonStart,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("save_preference_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = stringResource(R.string.stage1_pref_save))
                }

                OutlinedButton(
                    onClick = onClearClick,
                    enabled = (uiState.persistedPreference != null || uiState.draftValue.isNotEmpty()) && !uiState.isSaving,
                    border = BorderStroke(1.dp, V19OutlineInput),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = V19PurpleSoftText,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("clear_preference_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(text = stringResource(R.string.stage1_pref_clear))
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (uiState.persistedPreference != null) {
                    V19SuccessContainer
                } else {
                    V19SurfaceInput
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (uiState.persistedPreference != null) {
                        V19MintSuccess.copy(alpha = 0.45f)
                    } else {
                        V19OutlineSubtle
                    },
                ),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.stage1_pref_current_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.persistedPreference != null) {
                            V19SuccessInk
                        } else {
                            V19InkMuted
                        },
                    )
                    Text(
                        text = uiState.persistedPreference?.value
                            ?: stringResource(R.string.stage1_pref_none),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (uiState.persistedPreference != null) {
                            V19SuccessInk
                        } else {
                            V19InkPrimary
                        },
                        modifier = Modifier.testTag("persisted_preference_value"),
                    )
                    uiState.persistedPreference?.let { persisted ->
                        Text(
                            text = stringResource(
                                R.string.stage1_pref_updated_at,
                                CurrencyFormatter.formatEpochMillis(persisted.updatedAtEpochMs),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = V19SuccessInk.copy(alpha = 0.85f),
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.stage1_pref_count,
                            uiState.totalStoredPreferences,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = V19InkMuted,
                        modifier = Modifier.testTag("stored_preference_count"),
                    )
                }
            }
        }
    }
}
