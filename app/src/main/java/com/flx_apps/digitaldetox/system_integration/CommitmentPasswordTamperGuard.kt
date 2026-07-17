package com.flx_apps.digitaldetox.system_integration

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.content.Intent
import android.os.Handler
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.features.CommitmentPasswordFeature
import com.flx_apps.digitaldetox.ui.screens.feature.commitment_password.CommitmentPasswordTamperType
import com.flx_apps.digitaldetox.ui.screens.feature.commitment_password.CommitmentPasswordTamperWarningOverlayService
import timber.log.Timber

internal class CommitmentPasswordTamperGuard(
    private val service: DetoxDroidAccessibilityService
) {
    private data class WindowSnapshot(
        val text: String,
        val hasSwitchLikeControl: Boolean
    )

    private var tamperActionInFlight = false
    private var lastTamperActionAt = 0L
    private var lastUninstallBlockAssertionAt = 0L

    private val settingsPackages = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.oneplus.settings",
        "com.coloros.settings",
        "com.miui.securitycenter"
    )
    private val packageInstallerPackages = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.app.packageinstaller",
        "com.miui.packageinstaller"
    )

    private val appName by lazy { service.getString(R.string.app_name_).lowercase() }
    private val accessibilityServiceName by lazy {
        service.getString(R.string.app_accessibilityService_name).lowercase()
    }
    private val appPackageName by lazy { service.packageName.lowercase() }

    fun handleTamperAttempt(accessibilityEvent: AccessibilityEvent): Boolean {
        if (!CommitmentPasswordFeature.isActivated || CommitmentPasswordFeature.isSessionUnlocked()) {
            return false
        }

        assertUninstallBlockedIfPossible()
        val attemptType = detectTamperAttemptType(accessibilityEvent) ?: return false
        val now = System.currentTimeMillis()
        if (tamperActionInFlight || now - lastTamperActionAt < 1500L) {
            return true
        }

        tamperActionInFlight = true
        lastTamperActionAt = now

        val kickedOut = service.performGlobalAction(GLOBAL_ACTION_HOME)
        Timber.w("Tamper attempt blocked: $attemptType, kickedOut=$kickedOut")
        showTamperWarning(attemptType)
        Handler(service.mainLooper).postDelayed({ tamperActionInFlight = false }, 1500L)
        return true
    }

    private fun detectTamperAttemptType(accessibilityEvent: AccessibilityEvent): CommitmentPasswordTamperType? {
        val eventPackageName = accessibilityEvent.packageName?.toString()?.lowercase() ?: return null
        if (eventPackageName !in settingsPackages && eventPackageName !in packageInstallerPackages) {
            return null
        }

        val className = accessibilityEvent.className?.toString()?.lowercase().orEmpty()
        val snapshot = createWindowSnapshot(accessibilityEvent)

        if (isDetoxAccessibilityTamperFlow(className, snapshot)) {
            return CommitmentPasswordTamperType.Accessibility
        }

        if (!mentionsDetoxDroid(snapshot.text)) return null
        if (className.contains("deviceadmin")) return CommitmentPasswordTamperType.DeviceAdmin

        val isUninstallFlow = eventPackageName in packageInstallerPackages
                || className.contains("appinfo")
                || className.contains("installedappdetails")
        if (isUninstallFlow) return CommitmentPasswordTamperType.Uninstall

        return null
    }

    private fun isDetoxAccessibilityTamperFlow(
        className: String,
        snapshot: WindowSnapshot
    ): Boolean {
        val hasDetoxAccessibilityServiceMarker = snapshot.text.contains(accessibilityServiceName)
                || snapshot.text.contains("detoxdroidaccessibilityservice")
                || snapshot.text.contains("${appPackageName}.system_integration.detoxdroidaccessibilityservice")
        if (!hasDetoxAccessibilityServiceMarker) return false

        val isAccessibilityContext = className.contains("accessibility")
                || className.contains("toggleaccessibilityservice")
                || className.contains("accessibilityservicewarning")
                || className.contains("accessibilitydetails")

        val isServiceToggleOrWarningSurface = snapshot.hasSwitchLikeControl
                || className.contains("toggleaccessibilityservice")
                || className.contains("accessibilityservicewarning")

        return isAccessibilityContext || isServiceToggleOrWarningSurface
    }

    private fun mentionsDetoxDroid(text: String): Boolean {
        return text.contains(appPackageName) || text.contains(appName)
    }

    private fun createWindowSnapshot(accessibilityEvent: AccessibilityEvent): WindowSnapshot {
        val parts = mutableListOf<String>()
        parts += accessibilityEvent.text.map { it.toString() }

        var hasSwitchLikeControl = false
        val root = service.rootInActiveWindow
        if (root != null) {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var visited = 0
            while (queue.isNotEmpty() && visited < MAX_WINDOW_SNAPSHOT_NODES) {
                val node = queue.removeFirst()
                visited++

                val text = node.text?.toString().orEmpty()
                if (text.isNotBlank()) parts += text

                val contentDescription = node.contentDescription?.toString().orEmpty()
                if (contentDescription.isNotBlank()) parts += contentDescription

                val viewId = node.viewIdResourceName?.lowercase().orEmpty()
                if (viewId.isNotBlank()) parts += viewId

                val nodeClass = node.className?.toString()?.lowercase().orEmpty()
                if (nodeClass.contains("switch")
                    || viewId.contains("switch")
                    || viewId.contains("toggle")
                ) {
                    hasSwitchLikeControl = true
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let(queue::add)
                }
                node.recycle()
            }
            // recycle the nodes still queued when the node cap was hit (recycle() is a no-op on
            // API 33+, but leaks real native memory on older devices)
            while (queue.isNotEmpty()) {
                kotlin.runCatching { queue.removeFirst().recycle() }
            }
        }

        return WindowSnapshot(
            text = parts.joinToString(" ").lowercase(),
            hasSwitchLikeControl = hasSwitchLikeControl
        )
    }

    private fun assertUninstallBlockedIfPossible() {
        val now = System.currentTimeMillis()
        if (now - lastUninstallBlockAssertionAt < 60_000L) return
        lastUninstallBlockAssertionAt = now
        DetoxDroidDeviceAdminReceiver.setUninstallBlocked(service, true)
    }

    private fun showTamperWarning(attemptType: CommitmentPasswordTamperType) {
        if (Settings.canDrawOverlays(service)) {
            service.startService(
                CommitmentPasswordTamperWarningOverlayService.createStartIntent(service, attemptType)
            )
            return
        }
        Toast.makeText(service, service.getString(attemptType.toastMessageRes), Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val MAX_WINDOW_SNAPSHOT_NODES = 500
    }
}
