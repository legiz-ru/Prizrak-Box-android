package com.github.kr328.clash

import com.github.kr328.clash.design.compose.dialog.ExpressiveAlert
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import com.github.kr328.clash.design.store.ProfileRecency
import com.github.kr328.clash.design.compose.deck.DeckUpdates
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.github.kr328.clash.common.util.TvUtils
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.AddProfileAction
import com.github.kr328.clash.design.compose.component.UrlQrDialog
import com.github.kr328.clash.design.compose.screen.MainScreen
import com.github.kr328.clash.design.compose.screen.SettingsNavTarget
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.update.UpdateChecker
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.subscription.EXTRA_SUBSCRIPTION_ALERT_KIND
import com.github.kr328.clash.service.subscription.EXTRA_SUBSCRIPTION_ALERT_UUID
import com.github.kr328.clash.service.subscription.SubscriptionAlert
import com.github.kr328.clash.service.subscription.describeSubscriptionAlert
import com.github.kr328.clash.service.subscription.reportSubscriptionAlerts
import com.github.kr328.clash.util.UrlOpener
import com.github.kr328.clash.util.applyDynamicShortcuts
import com.github.kr328.clash.util.importProfileFromUrl
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.util.testGroupLatency
import com.github.kr328.clash.util.withProfile
import com.github.kr328.clash.design.compose.dialog.ExpressiveDialog
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import com.github.kr328.clash.util.qrScannerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.github.kr328.clash.design.compose.theme.ProfileCardColours
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.selects.select
import java.util.UUID
import java.util.concurrent.TimeUnit

@Composable
private fun NotificationPermissionDialog(
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    ExpressiveAlert(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.notification_permission_ask_title),
        text = stringResource(R.string.notification_permission_ask_text),
        confirmLabel = stringResource(R.string.notification_permission_ask_allow),
        onConfirm = onAllow,
        dismissLabel = stringResource(R.string.notification_permission_ask_later),
        onDismiss = onSkip,
    )

}

class MainActivity : BaseActivity() {
    private val isLoadingFlow = MutableStateFlow(true)
    private val runningFlow = MutableStateFlow(false)
    /** From the tap on the ghost until the tunnel runs, stops or is refused: the button breathes. */
    private val connectingFlow = MutableStateFlow(false)
    private val hasProfilesFlow = MutableStateFlow(false)
    private val activeProfileFlow = MutableStateFlow<Profile?>(null)
    /** The active profile's ↻ turns until its update completes or fails. */
    private val updatingProfileFlow = MutableStateFlow(false)
    private val profilesFlow = MutableStateFlow<List<Profile>>(emptyList())
    private val deckUpdatesFlow = MutableStateFlow(DeckUpdates())
    private val latencyTestingFlow = MutableStateFlow(false)
    private val proxyGroupsFlow = MutableStateFlow<List<Pair<String, ProxyGroup>>>(emptyList())
    private val useDotsFlow = MutableStateFlow(true)
    /** A tab asked for by the intent (Settings' Profiles tab on a tablet). */
    private val requestedTabFlow = MutableStateFlow<SettingsNavTarget?>(null)

    /**
     * Proxies whose delay is being measured right now, by proxy name.
     *
     * The core keeps one object per name, so a name is enough to key this: the
     * same node shown in two groups is the same measurement.
     */
    private val testingProxiesFlow = MutableStateFlow<Set<String>>(emptySet())

    /** Shows the in-app "why we need this" dialog ahead of the system permission prompt. */
    private val notificationPromptFlow = MutableStateFlow(false)

    private val urlOpener by lazy { UrlOpener(this) }

    private fun extractInstallConfigUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        if (!data.host.equals("install-config", true)) return null
        return data.getQueryParameter("url")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private val scanLauncher = registerForActivityResult(ScanCustomCode()) { result ->
        lifecycleScope.launch {
            when (result) {
                is QRResult.QRSuccess -> {
                    val url = result.content.rawValue
                        ?: result.content.rawBytes?.let { String(it) }.orEmpty()
                    if (url.isNotEmpty()) {
                        importProfileFromUrl(url)
                    }
                }
                QRResult.QRUserCanceled -> {}
                QRResult.QRMissingPermission -> toast(R.string.import_from_qr_no_permission)
                is QRResult.QRError -> toast(R.string.import_from_qr_exception)
            }
        }
    }

    override suspend fun main() {
        runningFlow.value = clashRunning

        setContent {
            val isLoading by isLoadingFlow.collectAsStateWithLifecycle()
            val running by runningFlow.collectAsStateWithLifecycle()
            val connecting by connectingFlow.collectAsStateWithLifecycle()
            val hasProfiles by hasProfilesFlow.collectAsStateWithLifecycle()
            val activeProfile by activeProfileFlow.collectAsStateWithLifecycle()
            val updatingProfile by updatingProfileFlow.collectAsStateWithLifecycle()
            val profiles by profilesFlow.collectAsStateWithLifecycle()
            val deckUpdates by deckUpdatesFlow.collectAsStateWithLifecycle()
            val latencyTesting by latencyTestingFlow.collectAsStateWithLifecycle()
            val proxyGroups by proxyGroupsFlow.collectAsStateWithLifecycle()
            val useDots by useDotsFlow.collectAsStateWithLifecycle()
            val testingProxies by testingProxiesFlow.collectAsStateWithLifecycle()
            val notificationPrompt by notificationPromptFlow.collectAsStateWithLifecycle()
            val urlQrDialog by urlOpener.dialogState.collectAsStateWithLifecycle()
            val requestedTab by requestedTabFlow.collectAsStateWithLifecycle()

            AppTheme {
                if (notificationPrompt) {
                    NotificationPermissionDialog(
                        onAllow = ::allowNotifications,
                        onSkip = ::skipNotifications,
                        onDismiss = { notificationPromptFlow.value = false },
                    )
                }
                urlQrDialog?.let { state ->
                    UrlQrDialog(
                        title = state.title,
                        url = state.url,
                        qr = state.qr,
                        onCopyLink = { urlOpener.copyLink() },
                        onDismiss = { urlOpener.dismiss() },
                    )
                }
                MainScreen(
                    // The side rail is Android TV's only; tablets and foldables lay out by width.
                    expanded = TvUtils.isTv(this),
                    isTv = TvUtils.isTv(this),
                    requestedTab = requestedTab,
                    onTabShown = { requestedTabFlow.value = null },
                    clashRunning = running,
                    connecting = connecting,
                    isLoading = isLoading,
                    hasProfiles = hasProfiles,
                    activeProfile = activeProfile,
                    updatingProfile = updatingProfile,
                    profiles = profiles,
                    deckUpdates = deckUpdates,
                    onUpdateAll = ::updateAllProfiles,
                    onEditProfile = ::openProfile,
                    onUpdateProfileOf = { profile -> launch { withProfile { update(profile.uuid) } } },
                    onDeleteProfile = { profile -> launch { withProfile { delete(profile.uuid) } } },
                    onActivateProfile = { profile -> launch { withProfile { setActive(profile) } } },
                    latencyTesting = latencyTesting,
                    proxyGroups = proxyGroups,
                    useDots = useDots,
                    testingProxies = testingProxies,
                    onPowerToggle = ::toggleStatus,
                    onUpdateProfile = ::updateActiveProfile,
                    onManageProfiles = { startActivity(ProfilesActivity::class.intent) },
                    onModeSelector = ::openModeSelector,
                    onOpenConnections = { startActivity(ConnectionsActivity::class.intent) },
                    onOpenProviders = { startActivity(ProvidersActivity::class.intent) },
                    onOpenSupport = { urlOpener.open(it, getString(R.string.contact_support)) },
                    onOpenWebPage = { urlOpener.open(it, getString(R.string.provider_website)) },
                    onOpenRenew = { urlOpener.open(it, getString(R.string.renew_subscription)) },
                    onAdd = ::add,
                    onNavigate = ::navigate,
                    onLatencyTest = ::latencyTestSimpleMode,
                    onDisconnect = { stopClashService() },
                    onSelectProxy = ::selectProxy,
                    onUrlTest = ::urlTestGroup,
                    onTestProxy = ::testProxy,
                )
            }
        }

        fetch()

        // The other trigger (ProfileWorker, after every subscription update)
        // misses profiles whose update interval is set to manual — there is no
        // alarm for those at all. Opening the app is what's left to catch a
        // subscription that quietly expired or ran out of traffic in the
        // meantime. No dedicated alarm of our own: waking the device just to
        // check a date isn't worth it, and this already happens often enough.
        launch(Dispatchers.IO) {
            for (uuid in ImportedDao().queryAllUUIDs()) {
                reportSubscriptionAlerts(uuid)
            }
        }

        extractInstallConfigUrl(intent)?.let {
            importProfileFromUrl(it, forceAutoImport = true)
        }
        intent?.let { handleUpdateIntent(it) }
        intent?.let { handleSubscriptionAlertIntent(it) }
        if (UpdateChecker.shouldCheck(this)) {
            launch { runUpdateCheckSilent() }
        }

        val ticker = ticker(TimeUnit.SECONDS.toMillis(5))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ClashStop, Event.ClashStart -> {
                            connectingFlow.value = false
                            fetch()
                        }
                        Event.ProfileLoaded, Event.ProfileChanged -> fetch()
                        Event.ProfileUpdateCompleted, Event.ProfileUpdateFailed -> {
                            updatingProfileFlow.value = false
                            fetch()
                        }
                        else -> Unit
                    }
                }
                if (clashRunning) {
                    ticker.onReceive {
                        fetchProxyGroups()
                    }
                }
            }
        }
    }

    private suspend fun fetch() {
        runningFlow.value = clashRunning
        withProfile {
            // Only profiles fetched in full: one still being added has none of
            // its headers yet (its pxa-theme, its logo) and showed in the
            // deck in the default green until they came.
            val all = queryAll().filter { it.imported }
            // A profile just added waits for its card colour (its logo
            // downloaded), so it does not show green first; the first load
            // shows everything at once, colours as they come.
            val shown = profilesFlow.value.map { it.uuid }.toSet()
            val added = if (shown.isEmpty()) emptyList() else all.filter { it.uuid !in shown }
            if (added.isNotEmpty()) {
                withTimeoutOrNull(3_000) {
                    added.forEach { ProfileCardColours.cardColour(this@MainActivity, it, uiStore.useProfileTheme) }
                }
            }
            // None active with profiles left (one from before deleting picked a
            // neighbour): the first saved one, not the first-launch screen.
            val active = queryActive()
                ?: all.firstOrNull()?.also { setActive(it) }?.copy(active = true)
            activeProfileFlow.value = active
            hasProfilesFlow.value = all.isNotEmpty()
            profilesFlow.value = all
        }
        if (clashRunning) {
            fetchProxyGroups()
        } else {
            proxyGroupsFlow.value = emptyList()
        }
        isLoadingFlow.value = false
    }

    private suspend fun fetchProxyGroups() {
        try {
            val active = withProfile { queryActive() }
            val activeLatencyDots = active?.latencyDots ?: -1
            // Per-profile proxy sort, shared with ProxyActivity.
            val sort = uiStore.getProxySort(active?.uuid?.toString() ?: "")
            val effectiveDots = when (activeLatencyDots) {
                0 -> false
                1 -> true
                else -> uiStore.delayDisplayDots
            }
            withClash {
                val names = queryProxyGroupNames(uiStore.proxyExcludeNotSelectable)
                val visibleGroups = names.map { name ->
                    name to queryProxyGroup(name, sort)
                }.filter { !it.second.hidden }

                val knownNames = visibleGroups.map { it.first }.toHashSet()
                val nestedSmartNames = visibleGroups
                    .flatMap { it.second.proxies }
                    .filter { it.type == "Smart" && it.name !in knownNames }
                    .map { it.name }
                    .distinct()
                val nestedSmartGroups = nestedSmartNames.map { name ->
                    name to queryProxyGroup(name, sort).copy(hidden = true)
                }

                proxyGroupsFlow.value = visibleGroups + nestedSmartGroups
                useDotsFlow.value = effectiveDots
            }
        } catch (_: Exception) {
            // Proxy groups may not be available yet
        }
    }

    private fun selectProxy(group: String, proxy: String) {
        launch {
            withClash { patchSelector(group, proxy) }
            fetchProxyGroups()
        }
    }

    /** Each node's result as soon as it is in (testGroupLatency). */
    private fun urlTestGroup(group: String) {
        launch {
            val data = proxyGroupsFlow.value.firstOrNull { it.first == group }?.second
            testGroupLatency(group, data, testingProxiesFlow) { fetchProxyGroups() }
        }
    }

    /** Measure one proxy, triggered by a tap on its delay badge. */
    private fun testProxy(group: String, name: String) {
        launch {
            testingProxiesFlow.value = testingProxiesFlow.value + name
            try {
                withClash { healthCheckProxy(group, name) }
                fetchProxyGroups()
            } finally {
                testingProxiesFlow.value = testingProxiesFlow.value - name
            }
        }
    }

    private fun toggleStatus() {
        if (clashRunning) {
            stopClashService()
        } else {
            launch { startClash() }
        }
    }

    private suspend fun startClash() {
        // Asked here — at the first real connect attempt — rather than
        // unconditionally in onCreate() on every cold start: this is the
        // moment the permission actually matters (the ongoing tunnel-status
        // notification is about to appear), so it's the moment to explain why,
        // instead of an unexplained system popup before the user did anything.
        if (shouldAskNotifications()) {
            notificationPromptFlow.value = true

            return
        }

        val active = withProfile { queryActive() }
        if (active == null || !active.imported) {
            toast(R.string.no_profile_selected)
            return
        }
        connectingFlow.value = true
        val vpnRequest = startClashService()
        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )
                if (result.resultCode == RESULT_OK) {
                    startClashService()
                } else {
                    connectingFlow.value = false
                }
            }
        } catch (e: Exception) {
            connectingFlow.value = false
            toast(R.string.unable_to_start_vpn)
        }
        // Never breathing for good: a start that neither runs nor stops lets go.
        if (connectingFlow.value) {
            launch {
                kotlinx.coroutines.delay(30_000)
                if (!clashRunning) connectingFlow.value = false
            }
        }
    }

    /**
     * The deck's ↻: every subscription in turn, local files skipped, from the
     * bottom up — the active profile first, then the strips nearest it. The
     * strips show which one is updating and which one failed.
     */
    private fun updateAllProfiles() {
        if (deckUpdatesFlow.value.updating.isNotEmpty()) return
        launch {
            val all = withProfile { queryAll() }.filter { it.imported && it.type != Profile.Type.File }
            val active = all.firstOrNull { it.active }
            val order = listOfNotNull(active) + ProfileRecency.ordered(this@MainActivity, all.filter { it != active })
            deckUpdatesFlow.value = DeckUpdates()
            for (profile in order) {
                deckUpdatesFlow.value = deckUpdatesFlow.value.copy(updating = setOf(profile.uuid))
                val result = kotlinx.coroutines.withTimeoutOrNull(90_000) {
                    kotlinx.coroutines.coroutineScope {
                        val done = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { profileUpdates.first { it.first == profile.uuid } }
                        withProfile { update(profile.uuid) }
                        done.await()
                    }
                }
                val failure = when {
                    result == null -> getString(R.string.update_error_title)
                    else -> result.second
                }
                val current = deckUpdatesFlow.value
                deckUpdatesFlow.value = current.copy(
                    updating = emptySet(),
                    failures = if (failure != null) current.failures + (profile.uuid to failure) else current.failures,
                )
                fetch()
            }
        }
    }

    /** The opened profile's "Edit Profile": the Android profile editor. */
    private fun openProfile(profile: Profile) {
        startActivity(PropertiesActivity::class.intent.setUUID(profile.uuid))
    }

    private fun updateActiveProfile() {
        launch {
            val active = withProfile { queryActive() }
            if (active != null && active.imported && active.type != Profile.Type.File) {
                updatingProfileFlow.value = true
                withProfile { update(active.uuid) }
                // Never turning for good: the update's result normally stops it.
                kotlinx.coroutines.delay(60_000)
                updatingProfileFlow.value = false
            }
        }
    }

    private fun openModeSelector() {
        launch {
            val current = withClash {
                queryOverride(Clash.OverrideSlot.Session).mode ?: queryTunnelState().mode
            }
            // Resolve the choice from a suspending dialog and apply it from THIS
            // coroutine (prizrak structure). Patching from a launch{} spawned inside
            // the dialog's click listener did not take effect.
            val newMode = showModeDialog(current) ?: return@launch
            withClash {
                val o = queryOverride(Clash.OverrideSlot.Session)
                o.mode = newMode
                patchOverride(Clash.OverrideSlot.Session, o)
            }
            Toast.makeText(this@MainActivity, R.string.mode_switch_tips, Toast.LENGTH_SHORT).show()
            // Reflect the new mode immediately (the proxy view depends on it).
            fetchProxyGroups()
        }
    }

    private suspend fun showModeDialog(current: TunnelState.Mode?): TunnelState.Mode? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val modes = arrayOf(TunnelState.Mode.Rule, TunnelState.Mode.Global)
            val labels = arrayOf(
                getString(R.string.mode_rule_label),
                getString(R.string.mode_global_label),
            )
            val checked = modes.indexOf(current).coerceAtLeast(0)
            val dialog = ExpressiveDialog.Builder(this@MainActivity)
                .setTitle(R.string.mode_selector_title)
                .setSingleChoiceItems(labels, checked) { d, which ->
                    if (cont.isActive) cont.resumeWith(Result.success(modes[which]))
                    d.dismiss()
                }
                .setNegativeButton(R.string.cancel) { _, _ -> }
                .setOnDismissListener {
                    if (cont.isActive) cont.resumeWith(Result.success(null))
                }
                .show()
            cont.invokeOnCancellation { dialog.dismiss() }
        }

    private fun latencyTestSimpleMode() {
        launch {
            val firstName = withClash {
                queryProxyGroupNames(uiStore.proxyExcludeNotSelectable).firstOrNull()
            }
            if (firstName != null) {
                // Simple mode shows that group's nodes inline, so their badges
                // spin along with the button.
                val data = proxyGroupsFlow.value.firstOrNull { it.first == firstName }?.second
                latencyTestingFlow.value = true
                try {
                    testGroupLatency(firstName, data, testingProxiesFlow) { fetchProxyGroups() }
                } finally {
                    latencyTestingFlow.value = false
                }
            }
        }
    }

    private fun add(action: AddProfileAction) {
        when (action) {
            AddProfileAction.Clipboard -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
                if (text.isEmpty()) {
                    toast(R.string.empty_clipboard)
                } else {
                    launch { importProfileFromUrl(text) }
                }
            }
            AddProfileAction.ScanQr -> scanLauncher.launch(qrScannerConfig)
            AddProfileAction.File -> launch {
                val uuid = withProfile { create(Profile.Type.File, getString(R.string.new_profile)) }
                startActivity(PropertiesActivity::class.intent.setUUID(uuid))
            }
            AddProfileAction.Manually -> launch {
                val uuid = withProfile { create(Profile.Type.Url, getString(R.string.new_profile)) }
                startActivity(PropertiesActivity::class.intent.setUUID(uuid))
            }
            AddProfileAction.TvImport -> startActivity(TvImportActivity::class.intent)
        }
    }

    private fun navigate(target: SettingsNavTarget) {
        when (target) {
            SettingsNavTarget.Home -> Unit // already home
            SettingsNavTarget.Servers -> Unit // a tab of Home on Android TV
            SettingsNavTarget.Profiles -> startActivity(ProfilesActivity::class.intent)
            SettingsNavTarget.Settings -> {
                startActivity(SettingsActivity::class.intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION))
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
    }

    private suspend fun runUpdateCheckSilent() {
        when (val result = UpdateChecker.check(this)) {
            is UpdateChecker.CheckResult.UpdateAvailable ->
                UpdateChecker.showUpdateNotification(this, result.tagName, result.downloadUrl)
            else -> Unit
        }
    }

    private fun handleUpdateIntent(intent: Intent) {
        if (intent.action != UpdateChecker.ACTION_SHOW_UPDATE) return
        val tag = intent.getStringExtra(UpdateChecker.EXTRA_TAG) ?: return
        val url = intent.getStringExtra(UpdateChecker.EXTRA_URL) ?: return
        showUpdateAvailableDialog(tag, url)
    }

    private fun showUpdateAvailableDialog(tagName: String, downloadUrl: String) {
        ExpressiveDialog.Builder(this)
            .setTitle(R.string.update_available_title)
            .setMessage(getString(R.string.update_available_message, tagName))
            .setPositiveButton(R.string.update_download) { _, _ ->
                UpdateChecker.startDownload(this, downloadUrl, tagName)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleSubscriptionAlertIntent(intent: Intent) {
        val uuid = intent.getStringExtra(EXTRA_SUBSCRIPTION_ALERT_UUID)?.let {
            try { UUID.fromString(it) } catch (_: Exception) { null }
        } ?: return
        val kind = intent.getStringExtra(EXTRA_SUBSCRIPTION_ALERT_KIND) ?: return
        launch { showSubscriptionAlertDialog(uuid, kind) }
    }

    private suspend fun showSubscriptionAlertDialog(uuid: UUID, kind: String) {
        val alert = SubscriptionAlert.fromKindCode(kind) ?: return
        val profile = withProfile { queryByUUID(uuid) } ?: return

        // Worded from the profile as it is now, not from the threshold that raised
        // the alert: the notification can be hours or days old by the time it is
        // tapped, and "4 days" must not be shown for a subscription that has
        // 3 days and a few hours left.
        val texts = describeSubscriptionAlert(
            alert = alert,
            expireAtMillis = profile.expire,
            total = profile.total,
            used = profile.upload + profile.download,
            nowMillis = System.currentTimeMillis() + profile.clockSkewMillis,
        )

        val message = texts.detail?.let { "${texts.message}\n$it" } ?: texts.message

        val builder = ExpressiveDialog.Builder(this)
            .setTitle(profile.name)
            .setMessage(message)
            .setNegativeButton(R.string.ok, null)
        if (profile.renewUrl.isNotEmpty()) {
            builder.setPositiveButton(R.string.renew_subscription) { _, _ ->
                urlOpener.open(profile.renewUrl, getString(R.string.renew_subscription))
            }
        }
        builder.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readRequestedTab(intent)
        handleSubscriptionAlertIntent(intent)
        val url = extractInstallConfigUrl(intent) ?: return
        lifecycleScope.launch {
            importProfileFromUrl(url, forceAutoImport = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readRequestedTab(intent)

        setupShortcuts()
    }

    /** Ask at most once (see UiStore.notificationsAsked), and only on Tiramisu+. */
    private fun shouldAskNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (uiStore.notificationsAsked) return false

        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
    }

    private fun allowNotifications() {
        notificationPromptFlow.value = false

        launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    startActivityForResult(
                        RequestPermission(),
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    )
                } catch (e: Exception) {
                    com.github.kr328.clash.common.log.Log.w("Request notifications: $e", e)
                }
            }

            // Set on both Allow and Skip, never on a plain dismiss: this marks
            // "we asked", not "the user said yes" — either answer means the
            // question shouldn't come back on the next connect attempt.
            uiStore.notificationsAsked = true

            startClash()
        }
    }

    private fun skipNotifications() {
        uiStore.notificationsAsked = true

        notificationPromptFlow.value = false

        launch { startClash() }
    }

    private fun setupShortcuts() {
        applyDynamicShortcuts(uiStore.hideAppIcon)
    }

    private fun readRequestedTab(intent: Intent?) {
        val name = intent?.getStringExtra(EXTRA_TAB) ?: return
        intent.removeExtra(EXTRA_TAB)
        requestedTabFlow.value = runCatching { SettingsNavTarget.valueOf(name) }.getOrNull()
    }

    companion object {
        /** A [SettingsNavTarget]'s name: the tab to show (Profiles from Settings on a tablet). */
        const val EXTRA_TAB = "tab"
    }

    private fun useDrawerNav(): Boolean {
        if (TvUtils.isTv(this)) return true
        val cfg = resources.configuration
        return cfg.smallestScreenWidthDp >= 600 &&
            cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
}
