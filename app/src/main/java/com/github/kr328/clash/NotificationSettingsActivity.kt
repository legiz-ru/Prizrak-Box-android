package com.github.kr328.clash

import com.github.kr328.clash.BaseActivity.Event
import com.github.kr328.clash.remote.Remote
import kotlinx.coroutines.flow.SharedFlow
import androidx.compose.runtime.Composable
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.design.compose.screen.NotificationPermissionStatus
import com.github.kr328.clash.design.compose.screen.NotificationSettingsScreen
import com.github.kr328.clash.service.ProfileWorker
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.subscription.SUBSCRIPTION_ALERT_CHANNEL
import com.github.kr328.clash.util.notificationPermissionStatus
import com.github.kr328.clash.util.openSystemNotificationSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Everything the app can notify the user about, in one place: the permission
 * itself, then one switch per notification kind — including two the panel
 * drives (expiry/traffic thresholds), see SubscriptionAlertReporter.
 */
class NotificationSettingsActivity : SectionActivity() {
    override fun createSection() = NotificationSection(this, ::finish)
}

class NotificationSection(activity: BaseActivity, close: () -> Unit) : SettingsSection(activity, close) {
    private val srvStore = ServiceStore(activity)

    private val permissionStatusFlow = MutableStateFlow(activity.notificationPermissionStatus())
    private val dynamicNotificationFlow = MutableStateFlow(srvStore.dynamicNotification)
    private val notifySubscriptionProgressFlow = MutableStateFlow(srvStore.notifySubscriptionProgress)
    private val notifySubscriptionErrorsFlow = MutableStateFlow(srvStore.notifySubscriptionErrors)
    private val notifySubscriptionAlertsFlow = MutableStateFlow(srvStore.notifySubscriptionAlerts)

    override suspend fun run(events: SharedFlow<Event>) {
        refreshPermissionStatus()
        events.collect {
            // Covers coming back from the system permission/channel settings screens.
            if (it == Event.ActivityStart) refreshPermissionStatus()
        }
    }

    @Composable
    override fun Content(onBack: (() -> Unit)?) {
        val permissionStatus by permissionStatusFlow.collectAsStateWithLifecycle()
        val dynamicNotification by dynamicNotificationFlow.collectAsStateWithLifecycle()
        val notifySubscriptionProgress by notifySubscriptionProgressFlow.collectAsStateWithLifecycle()
        val notifySubscriptionErrors by notifySubscriptionErrorsFlow.collectAsStateWithLifecycle()
        val notifySubscriptionAlerts by notifySubscriptionAlertsFlow.collectAsStateWithLifecycle()

        NotificationSettingsScreen(
            onBack = onBack,
            permissionStatus = permissionStatus,
            onRequestPermission = { activity.launch { requestPermission() } },
            onOpenSystemNotificationSettings = { activity.openSystemNotificationSettings() },
            dynamicNotification = dynamicNotification,
            dynamicNotificationEnabled = !Remote.broadcasts.clashRunning,
            onDynamicNotification = {
                srvStore.dynamicNotification = it
                dynamicNotificationFlow.value = it
            },
            notifySubscriptionProgress = notifySubscriptionProgress,
            onNotifySubscriptionProgress = {
                srvStore.notifySubscriptionProgress = it
                notifySubscriptionProgressFlow.value = it
            },
            onOpenProgressChannelSettings = {
                activity.openSystemNotificationSettings(ProfileWorker.STATUS_CHANNEL)
            },
            notifySubscriptionErrors = notifySubscriptionErrors,
            onNotifySubscriptionErrors = {
                srvStore.notifySubscriptionErrors = it
                notifySubscriptionErrorsFlow.value = it
            },
            onOpenErrorsChannelSettings = {
                activity.openSystemNotificationSettings(ProfileWorker.RESULT_CHANNEL)
            },
            onOpenUpdaterServiceChannelSettings = {
                activity.openSystemNotificationSettings(ProfileWorker.SERVICE_CHANNEL)
            },
            notifySubscriptionAlerts = notifySubscriptionAlerts,
            onNotifySubscriptionAlerts = {
                srvStore.notifySubscriptionAlerts = it
                notifySubscriptionAlertsFlow.value = it
            },
            onOpenSubscriptionAlertsChannelSettings = {
                activity.openSystemNotificationSettings(SUBSCRIPTION_ALERT_CHANNEL)
            },
        )
    }

    private suspend fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                activity.startActivityForResult(
                    RequestPermission(),
                    android.Manifest.permission.POST_NOTIFICATIONS,
                )
            } catch (e: Exception) {
                com.github.kr328.clash.common.log.Log.w("Request notifications: $e", e)
            }
        }

        activity.uiStore.notificationsAsked = true

        refreshPermissionStatus()
    }

    private fun refreshPermissionStatus() {
        permissionStatusFlow.value = activity.notificationPermissionStatus()
    }
}
