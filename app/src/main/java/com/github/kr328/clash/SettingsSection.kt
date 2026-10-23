package com.github.kr328.clash

import com.github.kr328.clash.BaseActivity.Event
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.SharedFlow

/**
 * One section of Settings (Visual, Network, Rules…), apart from the window it
 * is shown in: its own window on a phone ([SectionActivity]), the right
 * column of Settings in a wide window, as iOS's split view — the tabs then
 * stay where they are on Home.
 *
 * @param activity the screen it is shown on, for its stores, dialogs and launches.
 * @param close what "leave this section" means there: finish its window, or
 *   empty the right column.
 */
abstract class SettingsSection(
    protected val activity: BaseActivity,
    protected val close: () -> Unit,
) {
    /** Loads what the section shows and follows [events] until it is left (the coroutine is cancelled). */
    abstract suspend fun run(events: SharedFlow<Event>)

    /** The section's screen; [onBack] null in the right column, where there is nothing to go back to. */
    @Composable
    abstract fun Content(onBack: (() -> Unit)?)
}

/** A section's own window (a phone, a narrow window). */
abstract class SectionActivity : BaseActivity() {
    protected abstract fun createSection(): SettingsSection

    override suspend fun main() {
        val section = createSection()
        setContent {
            AppTheme {
                section.Content(onBack = ::finish)
            }
        }
        section.run(eventFlow)
    }
}
