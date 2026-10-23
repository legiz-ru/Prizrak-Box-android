package com.github.kr328.clash.design.dialog

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.dialog.ConfirmButton
import com.github.kr328.clash.design.compose.dialog.ExpressiveDialog
import com.github.kr328.clash.design.util.Validator
import com.github.kr328.clash.design.util.ValidatorAcceptAll
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun Context.requestModelTextInput(
    initial: String,
    title: CharSequence,
    hint: CharSequence? = null,
    error: CharSequence? = null,
    validator: Validator = ValidatorAcceptAll,
): String {
    return this.requestModelTextInput(initial, title, null, hint, error, validator)!!
}

/**
 * A line of text, in a Material 3 Expressive dialog: OK only while [validator]
 * accepts it; [reset] (when given) answers null. Dismissed, the answer is [initial].
 */
suspend fun Context.requestModelTextInput(
    initial: String?,
    title: CharSequence,
    reset: CharSequence?,
    hint: CharSequence? = null,
    error: CharSequence? = null,
    validator: Validator = ValidatorAcceptAll,
): String? {
    return suspendCancellableCoroutine { cont ->
        fun answer(value: String?) {
            if (cont.isActive) cont.resume(value)
        }

        val dialog = ExpressiveDialog.Builder(this)
            .setTitle(title)
            .setOnDismissListener { answer(initial) }
            .setContent { dialog ->
                TextInput(
                    initial = initial.orEmpty(),
                    hint = hint?.toString(),
                    error = error?.toString(),
                    validator = validator,
                    multiline = false,
                    reset = reset?.toString(),
                    onReset = { answer(null); dialog.dismiss() },
                    onCancel = { dialog.dismiss() },
                    onOk = { text -> answer(if (validator(text)) text else initial); dialog.dismiss() },
                )
            }
            .show()

        cont.invokeOnCancellation { dialog.dismiss() }
    }
}

/** Several lines of text; dismissed, the answer is [initial]. */
suspend fun Context.requestMultilineTextInput(
    initial: String,
    title: CharSequence,
    hint: CharSequence? = null,
): String {
    return suspendCancellableCoroutine { cont ->
        fun answer(value: String) {
            if (cont.isActive) cont.resume(value)
        }

        val dialog = ExpressiveDialog.Builder(this)
            .setTitle(title)
            .setOnDismissListener { answer(initial) }
            .setContent { dialog ->
                TextInput(
                    initial = initial,
                    hint = hint?.toString(),
                    error = null,
                    validator = ValidatorAcceptAll,
                    multiline = true,
                    reset = null,
                    onReset = {},
                    onCancel = { dialog.dismiss() },
                    onOk = { text -> answer(text); dialog.dismiss() },
                )
            }
            .show()

        cont.invokeOnCancellation { dialog.dismiss() }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TextInput(
    initial: String,
    hint: String?,
    error: String?,
    validator: Validator,
    multiline: Boolean,
    reset: String?,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onOk: (String) -> Unit,
) {
    // Selected whole, as the old field had it: typing replaces it.
    var value by remember {
        mutableStateOf(TextFieldValue(initial, if (multiline) TextRange(initial.length) else TextRange(0, initial.length)))
    }
    val valid = validator(value.text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = hint?.let { { Text(it) } },
        isError = !valid && error != null,
        supportingText = if (!valid && error != null) {
            { Text(error) }
        } else null,
        singleLine = !multiline,
        minLines = if (multiline) 4 else 1,
        maxLines = if (multiline) 10 else 1,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        if (reset != null) {
            TextButton(onClick = onReset, shapes = ButtonDefaults.shapes()) { Text(reset) }
        }
        TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) }
        ConfirmButton(stringResource(R.string.ok), enabled = valid) { onOk(value.text) }
    }
}
