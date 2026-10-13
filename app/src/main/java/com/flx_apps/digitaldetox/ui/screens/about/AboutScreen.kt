package com.flx_apps.digitaldetox.ui.screens.about

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.LocalActivity
import com.flx_apps.digitaldetox.BuildConfig
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.features.EmergencyBrakeFeature
import com.flx_apps.digitaldetox.system_integration.DetoxDroidAccessibilityService
import com.flx_apps.digitaldetox.system_integration.ReliabilitySettings
import com.flx_apps.digitaldetox.premium.PremiumSheetController
import com.flx_apps.digitaldetox.premium.PremiumSupport
import com.flx_apps.digitaldetox.ui.screens.premium.BitcoinAddressDialog
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavigationRoutes
import com.flx_apps.digitaldetox.ui.screens.onboarding.OnboardingState
import com.flx_apps.digitaldetox.ui.theme.AppearanceSettings
import com.flx_apps.digitaldetox.ui.widgets.SectionHeader
import com.flx_apps.digitaldetox.ui.widgets.SettingsGroup
import com.flx_apps.digitaldetox.util.DebugLog
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navViewModel: NavViewModel = NavViewModel.navViewModel()) {
    val uriHandler = LocalUriHandler.current
    val activity = LocalActivity.current
    val reportIssueLink = stringResource(id = R.string.about_reportIssue_link)
    val githubLink = stringResource(id = R.string.about_github_link)
    val contactLink = stringResource(id = R.string.about_contact_link)
    val reportIssueTitle = stringResource(id = R.string.navigation_reportIssue)
    val githubTitle = stringResource(id = R.string.about_github)
    val contactTitle = stringResource(id = R.string.about_contact)
    val contactSubtitle = stringResource(id = R.string.about_contact_subtitle)
    // Donation links come from the flavor's PremiumSupport: Ko-Fi/Liberapay in FOSS, none on
    // Google Play (external payment links would violate Play's anti-steering rules).
    val supportLinkItems = PremiumSupport.supportLinks.map {
        Triple(it.icon, stringResource(it.labelRes) to stringResource(it.subtitleRes), stringResource(it.urlRes))
    }
    val premiumTitle = stringResource(id = R.string.navigation_premium)
    val premiumSubtitle = stringResource(id = R.string.premium_tile_subtitle)
    val onboardingTitle = stringResource(id = R.string.about_onboarding)
    val onboardingSubtitle = stringResource(id = R.string.about_onboarding_subtitle)
    val onboardingLockedSubtitle = stringResource(id = R.string.about_onboarding_locked_subtitle)
    val keepAliveTitle = stringResource(id = R.string.reliability_keepAlive_title)
    val keepAliveSubtitle = stringResource(id = R.string.reliability_keepAlive_subtitle)
    val keepAliveLockedSubtitle = stringResource(id = R.string.reliability_keepAlive_locked_subtitle)
    val brakingSubtitle = stringResource(id = R.string.settings_lockedByBrake_subtitle)
    val settingsSection = stringResource(id = R.string.about_section_settings)
    val backupSection = stringResource(id = R.string.about_section_backup)
    val supportSection = stringResource(id = R.string.about_section_support)
    val projectSection = stringResource(id = R.string.about_section_project)
    var keepAlive by remember { mutableStateOf(ReliabilitySettings.keepServiceAliveEnabled) }
    // observed so the tiles open up the moment the passphrase is entered on a feature screen or
    // the brake ends, and close again when either comes back
    val commitmentPasswordToken by CommitmentPasswordFeature.stateToken.collectAsState()
    val brakeToken by EmergencyBrakeFeature.stateToken.collectAsState()
    val onboardingLocked = remember(commitmentPasswordToken, brakeToken) {
        OnboardingState.isOnboardingLocked
    }
    // switching the keep-alive off and swiping DetoxDroid away is a way to stop it, which the
    // password and the brake both refuse on the home screen, so the switch follows their lock
    val braking = remember(brakeToken) { EmergencyBrakeFeature.isBraking() }
    val keepAliveLocked = remember(commitmentPasswordToken, braking) {
        braking || CommitmentPasswordFeature.isFeatureLocked(CommitmentPasswordFeature.id)
    }
    var debugLog by remember { mutableStateOf(DebugLog.isEnabled) }
    val versionTaps = remember(activity) { VersionTapCountdown(activity) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.navigation_about)) },
                navigationIcon = {
                    IconButton(onClick = { navViewModel.onBackPress() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground_cropped),
                        contentDescription = null,
                        modifier = Modifier.size(84.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.app_displayName),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = stringResource(
                            id = R.string.about_version,
                            BuildConfig.VERSION_NAME
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // a gesture detector rather than clickable: no ripple, and screen readers
                        // keep reading it as plain text
                        modifier = Modifier.pointerInput(versionTaps) {
                            detectTapGestures { versionTaps.onTap() }
                        }
                    )
                    Text(
                        text = stringResource(id = R.string.about_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            item {
                SectionHeader(settingsSection)
                SettingsGroup {
                    ThemeTile()
                    SwitchTile(
                        icon = if (keepAliveLocked) Icons.Default.Lock else Icons.Default.Notifications,
                        title = keepAliveTitle,
                        subtitle = when {
                            braking -> brakingSubtitle
                            keepAliveLocked -> keepAliveLockedSubtitle
                            else -> keepAliveSubtitle
                        },
                        checked = keepAlive,
                        enabled = !keepAliveLocked,
                        onCheckedChange = {
                            keepAlive = it
                            ReliabilitySettings.keepServiceAliveEnabled = it
                            DetoxDroidAccessibilityService.instance?.updateForegroundNotification()
                        }
                    )
                    // the wizard rewrites the app lists and budgets of the very features the
                    // commitment password protects, so it is out of reach while they are locked
                    LinkTile(
                        icon = if (onboardingLocked) Icons.Default.Lock else Icons.Default.RestartAlt,
                        title = onboardingTitle,
                        subtitle = if (onboardingLocked) onboardingLockedSubtitle else onboardingSubtitle,
                        enabled = !onboardingLocked,
                        onClick = { navViewModel.openRoute(NavigationRoutes.Onboarding) }
                    )
                }

                SectionHeader(backupSection)
                SettingsGroup {
                    ConfigBackupTiles()
                }

                SectionHeader(supportSection)
                SettingsGroup {
                    LinkTile(
                        icon = Icons.Default.WorkspacePremium,
                        title = premiumTitle,
                        subtitle = premiumSubtitle,
                        onClick = { PremiumSheetController.show() }
                    )
                    supportLinkItems.forEach { (icon, texts, url) ->
                        LinkTile(
                            icon = icon,
                            title = texts.first,
                            subtitle = texts.second,
                            onClick = { uriHandler.openUri(url) }
                        )
                    }
                    PremiumSupport.bitcoinAddress?.let { address ->
                        var showAddress by rememberSaveable { mutableStateOf(false) }
                        LinkTile(
                            icon = Icons.Default.CurrencyBitcoin,
                            title = stringResource(id = R.string.about_bitcoin),
                            subtitle = stringResource(id = R.string.about_bitcoin_subtitle),
                            onClick = { showAddress = true }
                        )
                        if (showAddress) {
                            BitcoinAddressDialog(address, onDismiss = { showAddress = false })
                        }
                    }
                    // flavor seam: a "Rate DetoxDroid" tile on Google Play, nothing in FOSS
                    StoreReviewAboutTile(activity)
                }

                SectionHeader(projectSection)
                SettingsGroup {
                    LinkTile(
                        icon = Icons.Default.BugReport,
                        title = reportIssueTitle,
                        onClick = { uriHandler.openUri(reportIssueLink) }
                    )
                    LinkTile(
                        icon = Icons.Default.Code,
                        title = githubTitle,
                        onClick = { uriHandler.openUri(githubLink) }
                    )
                    LinkTile(
                        icon = Icons.Default.AlternateEmail,
                        title = contactTitle,
                        subtitle = contactSubtitle,
                        onClick = { uriHandler.openUri(contactLink) }
                    )
                    SwitchTile(
                        icon = Icons.Default.Terminal,
                        title = stringResource(id = R.string.debugLog_title),
                        subtitle = stringResource(id = R.string.about_debugLog_subtitle),
                        checked = debugLog,
                        onCheckedChange = {
                            debugLog = it
                            DebugLog.isEnabled = it
                        }
                    )
                    if (debugLog) {
                        val entryCount = DebugLog.entries.collectAsState().value.size
                        LinkTile(
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            title = stringResource(id = R.string.about_debugLog_open),
                            subtitle = pluralStringResource(
                                id = R.plurals.debugLog_entries,
                                count = entryCount,
                                NumberFormat.getIntegerInstance().format(entryCount)
                            ),
                            onClick = { navViewModel.openRoute(NavigationRoutes.LogViewer) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The build number countdown from Android's settings, with a different reward: [TAPS] taps on the
 * version line close the app. The last few taps count down in a toast, as they do there.
 */
private class VersionTapCountdown(private val activity: Activity?) {
    private var taps = 0
    private var toast: Toast? = null

    fun onTap() {
        val activity = activity ?: return
        taps++
        val tapsLeft = TAPS - taps
        val message = when {
            tapsLeft <= 0 -> activity.getString(R.string.about_version_putItDown)
            tapsLeft <= COUNTED_DOWN_TAPS -> activity.resources.getQuantityString(
                R.plurals.about_version_tapsLeft, tapsLeft, tapsLeft
            )

            else -> return
        }
        // one toast replaces the other, instead of a queue that runs on after the last tap
        toast?.cancel()
        toast = Toast.makeText(activity.applicationContext, message, Toast.LENGTH_SHORT).also {
            it.show()
        }
        if (tapsLeft <= 0) {
            taps = 0
            activity.finish()
        }
    }

    private companion object {
        const val TAPS = 7
        const val COUNTED_DOWN_TAPS = 4
    }
}

@Composable
internal fun LinkTile(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun SwitchTile(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) }
    )
}

/**
 * Light, dark, or whatever the system says, as three segments in one row. The choice applies on
 * the spot, every open screen included, see [AppearanceSettings].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeTile() {
    val mode by AppearanceSettings.themeMode.collectAsState()
    val options = listOf(
        Triple(AppearanceSettings.ThemeMode.SYSTEM, R.string.about_theme_system, Icons.Default.BrightnessAuto),
        Triple(AppearanceSettings.ThemeMode.LIGHT, R.string.about_theme_light, Icons.Default.LightMode),
        Triple(AppearanceSettings.ThemeMode.DARK, R.string.about_theme_dark, Icons.Default.DarkMode),
    )
    ListItem(
        headlineContent = { Text(stringResource(id = R.string.about_theme)) },
        leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                options.forEachIndexed { index, (option, labelRes, icon) ->
                    SegmentedButton(
                        selected = option == mode,
                        onClick = { AppearanceSettings.setThemeMode(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        icon = {
                            SegmentedButtonDefaults.Icon(active = option == mode) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                )
                            }
                        }
                    ) {
                        Text(stringResource(id = labelRes), maxLines = 1)
                    }
                }
            }
        }
    )
}
