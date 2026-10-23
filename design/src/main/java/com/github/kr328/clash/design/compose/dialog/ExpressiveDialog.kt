@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.github.kr328.clash.design.compose.dialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.LocalSegment
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.Segment
import com.github.kr328.clash.design.compose.theme.ClashTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * A dialog in Material 3 Expressive, shown by the Compose tree of the activity
 * that asked for it ([ExpressiveDialogHost] sits in every screen's theme).
 *
 * [Builder] takes the calls the app made of MaterialAlertDialogBuilder — a
 * title, a message, up to three buttons, a list, a single choice — so the
 * places that ask for a dialog read as before; [ExpressiveDialog] is the
 * [DialogInterface] their listeners get.
 */
class ExpressiveDialog private constructor(
    internal val owner: Activity?,
    private val title: CharSequence?,
    private val message: CharSequence?,
    private val positive: Button?,
    private val negative: Button?,
    private val neutral: Button?,
    private val body: Body,
    private val cancelable: Boolean,
    private var onDismiss: ((DialogInterface) -> Unit)?,
    private var onCancel: ((DialogInterface) -> Unit)?,
) : DialogInterface {
    internal class Button(val label: String, val destructive: Boolean, val listener: ((DialogInterface, Int) -> Unit)?)

    internal sealed interface Body {
        object None : Body
        class Items(val items: List<String>, val listener: (DialogInterface, Int) -> Unit) : Body
        class SingleChoice(val items: List<String>, val checked: Int, val listener: (DialogInterface, Int) -> Unit) : Body
        class Custom(val content: @Composable ColumnScope.(ExpressiveDialog) -> Unit) : Body
    }

    val isShowing: Boolean get() = this in Dialogs.showing.value

    fun show(): ExpressiveDialog {
        Dialogs.showing.update { it + this }
        return this
    }

    override fun dismiss() {
        var removed = false
        Dialogs.showing.update {
            removed = this in it
            it - this
        }
        if (removed) onDismiss?.invoke(this)
    }

    override fun cancel() {
        if (!isShowing) return
        onCancel?.invoke(this)
        dismiss()
    }

    fun setOnDismissListener(listener: ((DialogInterface) -> Unit)?) {
        onDismiss = listener
    }

    fun setOnCancelListener(listener: ((DialogInterface) -> Unit)?) {
        onCancel = listener
    }

    private fun press(button: Button, which: Int) {
        button.listener?.invoke(this, which)
        dismiss()
    }

    @Composable
    internal fun Render() {
        when (body) {
            is Body.Items -> ItemsSheet(body)
            else -> Frame()
        }
    }

    @Composable
    private fun Frame() {
        DialogFrame(
            title = title?.toString(),
            cancelable = cancelable,
            onCancel = ::cancel,
            buttons = if (positive == null && negative == null && neutral == null) null else {
                {
                    neutral?.let { TextButton(onClick = { press(it, DialogInterface.BUTTON_NEUTRAL) }, shapes = ButtonDefaults.shapes()) { Text(it.label) } }
                    negative?.let { TextButton(onClick = { press(it, DialogInterface.BUTTON_NEGATIVE) }, shapes = ButtonDefaults.shapes()) { Text(it.label) } }
                    positive?.let { ConfirmButton(it.label, it.destructive) { press(it, DialogInterface.BUTTON_POSITIVE) } }
                }
            },
        ) {
            if (message != null) {
                Text(
                    message.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (body) {
                is Body.SingleChoice -> {
                    var checked by remember { mutableIntStateOf(body.checked) }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = if (message != null) 12.dp else 0.dp)) {
                        body.items.forEachIndexed { index, label ->
                            CompositionLocalProvider(LocalSegment provides Segment(index, body.items.size)) {
                                PreferenceRow(
                                    title = label,
                                    onClick = {
                                        checked = index
                                        body.listener(this@ExpressiveDialog, index)
                                    },
                                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    trailing = if (checked == index) {
                                        {
                                            Icon(
                                                painterResource(R.drawable.ic_baseline_check),
                                                contentDescription = null,
                                                tint = ClashTheme.extraColors.settingsGreen,
                                            )
                                        }
                                    } else null,
                                )
                            }
                        }
                    }
                }
                is Body.Custom -> body.content(this, this@ExpressiveDialog)
                else -> Unit
            }
        }
    }

    /** A plain list to pick from: a sheet of rows; a pick closes it. */
    @Composable
    private fun ItemsSheet(items: Body.Items) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = ::cancel,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            if (title != null) {
                Text(
                    title.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                )
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items.items.forEachIndexed { index, label ->
                    CompositionLocalProvider(LocalSegment provides Segment(index, items.items.size)) {
                        PreferenceRow(
                            title = label,
                            onClick = {
                                items.listener(this@ExpressiveDialog, index)
                                dismiss()
                            },
                        )
                    }
                }
                negative?.let {
                    TextButton(
                        onClick = { press(it, DialogInterface.BUTTON_NEGATIVE) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 8.dp),
                    ) { Text(it.label) }
                }
            }
        }
    }

    /** MaterialAlertDialogBuilder's calls, for a dialog in Material 3 Expressive. */
    class Builder(private val context: Context) {
        private var title: CharSequence? = null
        private var message: CharSequence? = null
        private var positive: Button? = null
        private var negative: Button? = null
        private var neutral: Button? = null
        private var body: Body = Body.None
        private var cancelable = true
        private var onDismiss: ((DialogInterface) -> Unit)? = null
        private var onCancel: ((DialogInterface) -> Unit)? = null

        fun setTitle(@StringRes title: Int) = apply { this.title = context.getString(title) }
        fun setTitle(title: CharSequence?) = apply { this.title = title }
        fun setMessage(@StringRes message: Int) = apply { this.message = context.getString(message) }
        fun setMessage(message: CharSequence?) = apply { this.message = message }

        fun setPositiveButton(@StringRes label: Int, listener: ((DialogInterface, Int) -> Unit)?) =
            setPositiveButton(context.getString(label), listener)

        fun setPositiveButton(label: CharSequence, listener: ((DialogInterface, Int) -> Unit)?) =
            apply { positive = Button(label.toString(), false, listener) }

        /** The positive button in the error colour: it deletes or discards. */
        fun setDestructiveButton(@StringRes label: Int, listener: ((DialogInterface, Int) -> Unit)?) =
            apply { positive = Button(context.getString(label), true, listener) }

        fun setNegativeButton(@StringRes label: Int, listener: ((DialogInterface, Int) -> Unit)?) =
            setNegativeButton(context.getString(label), listener)

        fun setNegativeButton(label: CharSequence, listener: ((DialogInterface, Int) -> Unit)?) =
            apply { negative = Button(label.toString(), false, listener) }

        fun setNeutralButton(@StringRes label: Int, listener: ((DialogInterface, Int) -> Unit)?) =
            setNeutralButton(context.getString(label), listener)

        fun setNeutralButton(label: CharSequence, listener: ((DialogInterface, Int) -> Unit)?) =
            apply { neutral = Button(label.toString(), false, listener) }

        fun setItems(items: Array<out CharSequence>, listener: (DialogInterface, Int) -> Unit) =
            apply { body = Body.Items(items.map { it.toString() }, listener) }

        fun setSingleChoiceItems(items: Array<out CharSequence>, checked: Int, listener: (DialogInterface, Int) -> Unit) =
            apply { body = Body.SingleChoice(items.map { it.toString() }, checked, listener) }

        /** Content of its own under the title and message: fields, keys, progress. */
        fun setContent(content: @Composable ColumnScope.(ExpressiveDialog) -> Unit) =
            apply { body = Body.Custom(content) }

        fun setCancelable(cancelable: Boolean) = apply { this.cancelable = cancelable }
        fun setOnDismissListener(listener: ((DialogInterface) -> Unit)?) = apply { onDismiss = listener }
        fun setOnCancelListener(listener: ((DialogInterface) -> Unit)?) = apply { onCancel = listener }

        fun create(): ExpressiveDialog = ExpressiveDialog(
            owner = context.findActivity(),
            title = title,
            message = message,
            positive = positive,
            negative = negative,
            neutral = neutral,
            body = body,
            cancelable = cancelable,
            onDismiss = onDismiss,
            onCancel = onCancel,
        )

        fun show(): ExpressiveDialog = create().show()
    }
}

/** The filled button of a dialog: the accent, or the error colour when it deletes. */
@Composable
internal fun ConfirmButton(label: String, destructive: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        colors = if (destructive) {
            ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError)
        } else {
            ButtonDefaults.buttonColors()
        },
    ) { Text(label) }
}

/**
 * A dialog of a Compose screen, as [ExpressiveDialog] draws its own: a title,
 * text or content of its own, and up to three buttons — the confirm one filled
 * (red when [destructive]).
 */
@Composable
fun ExpressiveAlert(
    onDismissRequest: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    title: String? = null,
    text: String? = null,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    neutralLabel: String? = null,
    onNeutral: () -> Unit = {},
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    DialogFrame(
        title = title,
        cancelable = true,
        onCancel = onDismissRequest,
        buttons = {
            if (neutralLabel != null) {
                TextButton(onClick = onNeutral, shapes = ButtonDefaults.shapes()) { Text(neutralLabel) }
            }
            if (dismissLabel != null) {
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(dismissLabel) }
            }
            ConfirmButton(confirmLabel, destructive = destructive, enabled = confirmEnabled, onClick = onConfirm)
        },
    ) {
        if (text != null) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content?.invoke(this)
    }
}

/** A dialog's frame: the title, content that scrolls, and the buttons at the end. */
@Composable
internal fun DialogFrame(
    title: String?,
    cancelable: Boolean,
    onCancel: () -> Unit,
    buttons: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = { if (cancelable) onCancel() },
        properties = DialogProperties(dismissOnBackPress = cancelable, dismissOnClickOutside = cancelable),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
        ) {
            Column(Modifier.padding(PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 18.dp))) {
                if (title != null) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    content = content,
                )
                if (buttons != null) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) { buttons() }
                }
            }
        }
    }
}

private object Dialogs {
    val showing = MutableStateFlow<List<ExpressiveDialog>>(emptyList())
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The dialogs this activity asked for (and any without an activity), over its screen. */
@Composable
fun ExpressiveDialogHost() {
    val activity = LocalContext.current.findActivity()
    val showing by Dialogs.showing.collectAsStateWithLifecycle()
    for (dialog in showing) {
        if (dialog.owner == null || dialog.owner === activity) {
            key(dialog) { Box { dialog.Render() } }
        }
    }
}
