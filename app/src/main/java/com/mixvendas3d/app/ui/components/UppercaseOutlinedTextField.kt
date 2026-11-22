package com.mixvendas3d.app.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import com.mixvendas3d.app.util.TextNormalization

/**
 * Campo [OutlinedTextField] que converte automaticamente a digitação para MAIÚSCULAS (pt-BR)
 * em tempo real, preservando acentos, cedilha, números, espaços, símbolos, posição do cursor
 * e composição do IME Android sem pular o cursor ao final.
 */
@Composable
fun UppercaseOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = TextStyle.Default,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val normalizedExternal = TextNormalization.toRegistrationUppercase(value)

    var textFieldValueState by remember {
        mutableStateOf(
            TextFieldValue(
                text = normalizedExternal,
                selection = TextRange(normalizedExternal.length),
            ),
        )
    }

    // Sincroniza alterações externas (ex.: reset de formulário ou abertura para edição)
    val currentTextFieldValue = if (textFieldValueState.text != normalizedExternal) {
        val clampedStart = textFieldValueState.selection.start.coerceIn(0, normalizedExternal.length)
        val clampedEnd = textFieldValueState.selection.end.coerceIn(0, normalizedExternal.length)
        val syncedSelection = if (normalizedExternal.isEmpty()) {
            TextRange.Zero
        } else if (textFieldValueState.text.isEmpty()) {
            TextRange(normalizedExternal.length)
        } else {
            TextRange(clampedStart, clampedEnd)
        }
        TextFieldValue(
            text = normalizedExternal,
            selection = syncedSelection,
            composition = null,
        )
    } else {
        textFieldValueState
    }

    SideEffect {
        if (currentTextFieldValue != textFieldValueState) {
            textFieldValueState = currentTextFieldValue
        }
    }

    OutlinedTextField(
        value = currentTextFieldValue,
        onValueChange = { incoming ->
            val upper = TextNormalization.toRegistrationUppercase(incoming.text)
            val mappedSelection = mapSelectionToUppercase(
                originalText = incoming.text,
                upperText = upper,
                selection = incoming.selection,
            )
            val mappedComposition = incoming.composition?.let { comp ->
                if (upper.length == incoming.text.length) {
                    TextRange(
                        start = comp.start.coerceIn(0, upper.length),
                        end = comp.end.coerceIn(0, upper.length),
                    )
                } else {
                    null
                }
            }
            val nextValue = TextFieldValue(
                text = upper,
                selection = mappedSelection,
                composition = mappedComposition,
            )
            textFieldValueState = nextValue
            if (upper != normalizedExternal || incoming.text != upper) {
                onValueChange(upper)
            }
        },
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions.copy(
            capitalization = KeyboardCapitalization.Characters,
        ),
        keyboardActions = keyboardActions,
        shape = shape,
        colors = colors,
    )
}

internal fun mapSelectionToUppercase(
    originalText: String,
    upperText: String,
    selection: TextRange,
): TextRange {
    if (upperText.length == originalText.length) {
        return TextRange(
            start = selection.start.coerceIn(0, upperText.length),
            end = selection.end.coerceIn(0, upperText.length),
        )
    }
    val safeStart = selection.start.coerceIn(0, originalText.length)
    val safeEnd = selection.end.coerceIn(0, originalText.length)
    val mappedStart = TextNormalization.toRegistrationUppercase(originalText.substring(0, safeStart))
        .length
        .coerceIn(0, upperText.length)
    val mappedEnd = TextNormalization.toRegistrationUppercase(originalText.substring(0, safeEnd))
        .length
        .coerceIn(0, upperText.length)
    return TextRange(mappedStart, mappedEnd)
}
