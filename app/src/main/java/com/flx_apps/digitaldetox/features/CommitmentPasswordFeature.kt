package com.flx_apps.digitaldetox.features

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.flx_apps.digitaldetox.DetoxDroidApplication
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.data.DataStoreProperty
import com.flx_apps.digitaldetox.feature_types.Feature
import com.flx_apps.digitaldetox.feature_types.FeatureTexts
import com.flx_apps.digitaldetox.feature_types.LockableFeature
import com.flx_apps.digitaldetox.feature_types.NeedsPermissionsFeature
import com.flx_apps.digitaldetox.system_integration.DetoxDroidDeviceAdminReceiver
import com.flx_apps.digitaldetox.ui.screens.feature.commitment_password.CommitmentPasswordFeatureSettingsSection
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.mindrot.jbcrypt.BCrypt
import timber.log.Timber
import java.security.SecureRandom

val CommitmentPasswordFeatureId = Feature.createId(CommitmentPasswordFeature::class.java)

/**
 * The Commitment Password Feature adds an extra layer of commitment by requiring a passphrase to
 * unlock DetoxDroid settings. Once enabled, the selected features are locked behind the passphrase
 * and DetoxDroid cannot be stopped until the session is unlocked.
 *
 * Security:
 * - BCrypt hashing (work factor 12) stored in EncryptedSharedPreferences
 * - Max 3 attempts → 5-minute cooldown
 * - 24-hour recovery period for forgotten passphrases
 * - Device Admin required; Device Owner enables uninstall blocking
 */
object CommitmentPasswordFeature : Feature(), NeedsPermissionsFeature {
    override val texts: FeatureTexts = FeatureTexts(
        title = R.string.feature_commitmentPassword,
        subtitle = R.string.feature_commitmentPassword_subtitle,
        description = R.string.feature_commitmentPassword_description,
    )
    override val iconRes: Int = R.drawable.ic_lock
    override val settingsContent: @Composable () -> Unit = {
        CommitmentPasswordFeatureSettingsSection()
    }

    private const val PREFS_NAME = "commitment_password_prefs"
    private const val KEY_PASSWORD_HASH = "password_hash"

    const val MAX_FAILED_ATTEMPTS = 3
    const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L
    const val RECOVERY_DURATION_MS = 24 * 60 * 60 * 1000L

    private const val PASSPHRASE_WORD_COUNT = 5

    // Wordlist for passphrase generation (no ambiguous characters/words)
    private val WORD_LIST = listOf(
        "apple", "brave", "cloud", "dance", "eagle", "flame", "green", "happy", "ivory", "jumpy",
        "kneel", "lemon", "mango", "noble", "ocean", "pearl", "queen", "robin", "solar", "tiger",
        "ultra", "vivid", "wheat", "xenon", "yacht", "zebra", "amber", "blaze", "cedar", "drift",
        "ember", "frost", "grace", "heron", "inlet", "jewel", "karma", "lunar", "maple", "north",
        "onyx", "prism", "quartz", "river", "storm", "topaz", "umbra", "vapor", "willow", "xray"
    )

    /**
     * Whether the user has unlocked the settings for this session.
     * Resets to false on app restart (in-memory only).
     */
    private var sessionUnlocked: Boolean = false
    private val _stateToken = MutableStateFlow(0L)
    val stateToken: StateFlow<Long> = _stateToken.asStateFlow()

    var failedAttempts: Int by DataStoreProperty(
        intPreferencesKey("${id}_failedAttempts"), 0
    )

    var lockoutUntil: Long by DataStoreProperty(
        longPreferencesKey("${id}_lockoutUntil"), 0L
    )

    var recoveryInitiatedAt: Long by DataStoreProperty(
        longPreferencesKey("${id}_recoveryInitiatedAt"), 0L
    )

    /**
     * Set of feature IDs explicitly selected to be locked by the password.
     * If empty, falls back to locking all [LockableFeature] instances.
     */
    var lockedFeatureIds: Set<String> by DataStoreProperty(
        stringSetPreferencesKey("${id}_lockedFeatureIds"), emptySet()
    )

    private fun notifyStateChanged() {
        _stateToken.update { it + 1 }
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey =
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // region Session

    fun unlockSession() {
        sessionUnlocked = true
        notifyStateChanged()
    }

    fun lockSession() {
        sessionUnlocked = false
        notifyStateChanged()
    }

    fun isSessionUnlocked(): Boolean = sessionUnlocked

    // endregion

    // region Password state helpers

    fun isPasswordSet(context: Context): Boolean {
        return getEncryptedPrefs(context).contains(KEY_PASSWORD_HASH)
    }

    fun isLockedOut(): Boolean = System.currentTimeMillis() < lockoutUntil

    fun getRemainingLockoutTime(): Long =
        (lockoutUntil - System.currentTimeMillis()).coerceAtLeast(0L)

    fun isRecoveryInProgress(): Boolean =
        recoveryInitiatedAt > 0 && System.currentTimeMillis() < recoveryInitiatedAt + RECOVERY_DURATION_MS

    fun isRecoveryReady(): Boolean =
        recoveryInitiatedAt > 0 && System.currentTimeMillis() >= recoveryInitiatedAt + RECOVERY_DURATION_MS

    fun getRemainingRecoveryTime(): Long =
        (recoveryInitiatedAt + RECOVERY_DURATION_MS - System.currentTimeMillis()).coerceAtLeast(0L)

    // endregion

    // region Feature locking

    /**
     * Returns true if the given feature is currently locked (i.e., protected AND session is not
     * unlocked).
     */
    fun isFeatureLocked(featureId: String): Boolean {
        if (!isFeatureProtected(featureId)) return false
        return !sessionUnlocked
    }

    /**
     * Returns true if the given feature is configured to be protected by the password, regardless
     * of whether the session is currently unlocked.
     */
    fun isFeatureProtected(featureId: String): Boolean {
        if (!isActivated) return false
        if (!isPasswordSet(DetoxDroidApplication.appContext)) return false
        if (featureId == id) return true
        return if (lockedFeatureIds.isEmpty()) {
            getLockableFeatures().any { it.id == featureId }
        } else {
            lockedFeatureIds.contains(featureId)
        }
    }

    /**
     * Returns all features that implement [LockableFeature].
     */
    fun getLockableFeatures(): List<Feature> {
        return FeaturesProvider.featureList.filterIsInstance<LockableFeature>()
            .filterIsInstance<Feature>().filter { it.id != id }
    }

    /**
     * Populate [lockedFeatureIds] from the current default selections (all lockable features
     * whose [LockableFeature.lockedByDefault] is true). Called once when enabling the feature.
     */
    fun initializeLockedFeatures() {
        if (lockedFeatureIds.isEmpty()) {
            updateLockedFeatureIds(
                getLockableFeatures().filter { (it as? LockableFeature)?.lockedByDefault == true }
                    .map { it.id }.toSet()
            )
        }
    }

    fun updateLockedFeatureIds(featureIds: Set<String>) {
        lockedFeatureIds = featureIds
        notifyStateChanged()
    }

    fun updateActivationState(activated: Boolean) {
        isActivated = activated
        notifyStateChanged()
    }

    // endregion

    // region Passphrase generation & password management

    fun generatePassphrase(wordCount: Int = PASSPHRASE_WORD_COUNT): String {
        val random = SecureRandom()
        return (1..wordCount).joinToString("-") { WORD_LIST[random.nextInt(WORD_LIST.size)] }
    }

    fun setPassword(context: Context, password: String): Boolean {
        return try {
            val hash = BCrypt.hashpw(password, BCrypt.gensalt(12))
            getEncryptedPrefs(context).edit().putString(KEY_PASSWORD_HASH, hash).apply()
            failedAttempts = 0
            lockoutUntil = 0L
            recoveryInitiatedAt = 0L
            notifyStateChanged()
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to set password")
            false
        }
    }

    fun verifyPassword(context: Context, password: String): Boolean {
        if (isLockedOut()) return false
        val hash = getEncryptedPrefs(context).getString(KEY_PASSWORD_HASH, null) ?: return false
        return try {
            val valid = BCrypt.checkpw(password, hash)
            if (valid) {
                failedAttempts = 0
                lockoutUntil = 0L
            } else {
                failedAttempts++
                if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                    lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS
                    Timber.w("User locked out after $failedAttempts failed attempts")
                }
            }
            notifyStateChanged()
            valid
        } catch (e: Exception) {
            Timber.e(e, "Failed to verify password")
            false
        }
    }

    // endregion

    // region Recovery

    fun initiateRecovery(context: Context) {
        recoveryInitiatedAt = System.currentTimeMillis()
        failedAttempts = 0
        lockoutUntil = 0L
        scheduleRecoveryNotification(context)
        notifyStateChanged()
    }

    fun cancelRecovery(context: Context) {
        recoveryInitiatedAt = 0L
        com.flx_apps.digitaldetox.workers.PasswordRecoveryWorker.cancel(context)
        notifyStateChanged()
    }

    fun completeRecovery(context: Context): Boolean {
        if (!isRecoveryReady()) return false
        getEncryptedPrefs(context).edit().remove(KEY_PASSWORD_HASH).apply()
        recoveryInitiatedAt = 0L
        failedAttempts = 0
        lockoutUntil = 0L
        com.flx_apps.digitaldetox.workers.PasswordRecoveryWorker.cancel(context)
        notifyStateChanged()
        return true
    }

    fun clearPasswordData(context: Context) {
        getEncryptedPrefs(context).edit().clear().apply()
        failedAttempts = 0
        lockoutUntil = 0L
        recoveryInitiatedAt = 0L
        DetoxDroidDeviceAdminReceiver.setUninstallBlocked(context, false)
        notifyStateChanged()
    }

    private fun scheduleRecoveryNotification(context: Context) {
        com.flx_apps.digitaldetox.workers.PasswordRecoveryWorker.schedule(context)
    }

    // endregion

    // region NeedsPermissionsFeature

    override fun hasPermissions(context: Context): Boolean {
        return DetoxDroidDeviceAdminReceiver.hasDeviceAdminPermission(context)
    }

    override fun requestPermissions(context: Context, navViewModel: NavViewModel) {
        val intent = DetoxDroidDeviceAdminReceiver.createRequestDeviceAdminIntent(
            context = context,
            explanation = context.getString(R.string.feature_commitmentPassword_requiresDeviceAdmin_message)
        )

        kotlin.runCatching {
            if (context is Activity) {
                context.startActivity(intent)
            } else {
                context.startActivity(
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }.onFailure {
            Timber.e(it, "Failed to open Device Admin request")
        }
    }

    // endregion

    // region Lifecycle

    override fun onStart(context: Context) {
        if (!hasPermissions(context)) {
            Timber.w("CommitmentPasswordFeature: Device Admin not granted, deactivating")
            updateActivationState(false)
            return
        }
        lockSession()
        DetoxDroidDeviceAdminReceiver.setUninstallBlocked(context, true)
    }

    override fun onPause(context: Context) {
        // nothing to do on pause
    }

    // endregion
}
