package com.mixvendas3d.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.models.PersonWithUsage
import com.mixvendas3d.app.ui.components.NamesGlassCard
import com.mixvendas3d.app.ui.components.NamesPersonAvatarBadge
import com.mixvendas3d.app.ui.components.NamesPersonRow
import com.mixvendas3d.app.ui.components.NamesPrimaryCtaButton
import com.mixvendas3d.app.ui.components.UppercaseOutlinedTextField
import com.mixvendas3d.app.ui.components.namesDarkInputColors
import com.mixvendas3d.app.ui.theme.GlassInnerBoxBg
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeCardGlassBottom
import com.mixvendas3d.app.ui.theme.HomeCardLabelInk
import com.mixvendas3d.app.ui.theme.HomeCardNeonBorder
import com.mixvendas3d.app.ui.theme.HomeCardSubtleDivider
import com.mixvendas3d.app.ui.theme.PlusJakartaSansFontFamily
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19MintSuccess
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SuccessContainer
import com.mixvendas3d.app.ui.theme.V19SuccessInk
import com.mixvendas3d.app.ui.theme.V19SurfaceCard

@Composable
fun NamesScreen(
    uiState: NamesSectionUiState,
    onNameInputChanged: (String) -> Unit,
    onSavePerson: () -> Unit,
    onStartEdit: (PersonWithUsage) -> Unit,
    onCancelEdit: () -> Unit,
    onDeletePerson: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeleteTarget by remember { mutableStateOf<PersonWithUsage?>(null) }
    val isEditing = uiState.editingPersonId != null

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_names"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "names_content") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // 1. Cartão principal: Cadastrar / Editar participante
                NamesGlassCard {
                    Text(
                        text = if (isEditing) {
                            stringResource(R.string.names_edit_card_title)
                        } else {
                            stringResource(R.string.names_create_card_title)
                        },
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        color = Color.White,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NamesPersonAvatarBadge(
                            iconSize = 38.dp,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.names_input_label),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    UppercaseOutlinedTextField(
                        value = uiState.nameInput,
                        onValueChange = onNameInputChanged,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = Color.White,
                        ),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.names_input_placeholder),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 15.sp,
                                color = V19InkMuted,
                            )
                        },
                        colors = namesDarkInputColors(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .testTag("person_name_input"),
                    )

                    if (!uiState.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("names_error_banner"),
                            shape = RoundedCornerShape(14.dp),
                            color = V19ErrorSoft.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.55f)),
                        ) {
                            Text(
                                text = uiState.errorMessage,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 13.sp,
                                color = V19ErrorSoft,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    if (!uiState.feedbackMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("names_feedback_banner"),
                            shape = RoundedCornerShape(14.dp),
                            color = V19SuccessContainer,
                            border = BorderStroke(1.dp, V19MintSuccess.copy(alpha = 0.45f)),
                        ) {
                            Text(
                                text = uiState.feedbackMessage,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 13.sp,
                                color = V19SuccessInk,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    NamesPrimaryCtaButton(
                        text = if (isEditing) {
                            stringResource(R.string.names_save_changes_action)
                        } else {
                            stringResource(R.string.names_add_action)
                        },
                        isEditing = isEditing,
                        enabled = !uiState.isSaving,
                        onClick = onSavePerson,
                        testTag = "save_person_button",
                    )

                    if (isEditing) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onCancelEdit,
                            border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.65f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("cancel_person_edit_button"),
                        ) {
                            Text(
                                text = stringResource(R.string.names_cancel_edit_action),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = V19PurpleSoftText,
                            )
                        }
                    }
                }

                // 2. Cartão informativo: Sobre os participantes
                NamesGlassCard {
                    Text(
                        text = stringResource(R.string.names_about_card_title),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.names_about_card_body),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = HomeCardLabelInk.copy(alpha = 0.90f),
                    )
                }

                // 3. Seção de lista: Nomes cadastrados
                NamesGlassCard {
                    Text(
                        text = stringResource(R.string.names_list_card_title),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        color = Color.White,
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.35f))

                    if (uiState.people.isEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("names_empty_state"),
                            shape = RoundedCornerShape(16.dp),
                            color = GlassInnerBoxBg,
                            border = BorderStroke(1.dp, GlassNeonBorder15),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                NamesPersonAvatarBadge(
                                    iconSize = 64.dp,
                                )
                                Text(
                                    text = stringResource(R.string.names_empty_message),
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = V19InkMuted,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.people.isNotEmpty()) {
            itemsIndexed(
                items = uiState.people,
                key = { _, item -> item.person.id },
            ) { index, item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(top = 10.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = HomeCardGlassBottom,
                        border = BorderStroke(1.dp, GlassNeonBorder15),
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            NamesPersonRow(
                                item = item,
                                onEdit = { onStartEdit(item) },
                                actionIconScalePercent = if (index < 3) 160 else null,
                                onRequestDelete = {
                                    if (item.linkedProductCount > 0) {
                                        onDeletePerson(item.person.id)
                                    } else {
                                        pendingDeleteTarget = item
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDeleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTarget = null },
            containerColor = V19SurfaceCard,
            modifier = Modifier.testTag("delete_person_dialog"),
            title = {
                Text(
                    text = "Excluir pessoa?",
                    color = V19InkPrimary,
                )
            },
            text = {
                Text(
                    text = "Deseja excluir \"${target.person.name}\" do cadastro local? Esta ação não pode ser desfeita. O histórico de vendas já registradas permanece preservado.",
                    color = V19InkMuted,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteTarget = null
                        onDeletePerson(target.person.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = V19ErrorSoft.copy(alpha = 0.25f),
                        contentColor = V19ErrorSoft,
                    ),
                    modifier = Modifier.testTag("confirm_delete_person_button"),
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteTarget = null },
                    modifier = Modifier.testTag("cancel_delete_person_button"),
                ) {
                    Text("Cancelar", color = V19PurpleSoftText)
                }
            },
        )
    }
}
