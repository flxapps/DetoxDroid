package com.flx_apps.digitaldetox.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.AEADBadTagException

/**
 * Opens an [EncryptedSharedPreferences] file, and throws it away when the device can no longer
 * read it back.
 *
 * Tink keeps its keyset inside the preferences file itself and wraps it with a key from the
 * AndroidKeyStore. The two can drift apart: a restored backup carries the file to a device whose
 * keystore never held the matching key, and some keystores invalidate keys of their own accord.
 * Decryption then fails with an AEADBadTagException, and nothing can undo that: the key that
 * could is gone. Rather than let the failure escape into whatever screen happened to ask, it is
 * treated as what it is, the contents are lost, and an empty store takes their place.
 *
 * Every other failure is taken as the wait it usually is. The keystore daemon can refuse for a
 * moment right after boot, and a store thrown away over that would be a lock the user set
 * gone for nothing. Such a failure gets one more try and then a null.
 */
object EncryptedPrefs {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /**
     * Returns the store under [name], or null on a device that will not give one at all. Callers
     * have to cope with null rather than assume a working keystore.
     *
     * @param onDiscarded Called when the previous contents had to be dropped to get a working
     * store, so the caller can clean up whatever referred to them.
     */
    fun open(
        context: Context, name: String, onDiscarded: () -> Unit = {}
    ): SharedPreferences? {
        val appContext = context.applicationContext
        val first = runCatching { create(appContext, name) }
        first.getOrNull()?.let { return it }
        val error = first.exceptionOrNull()
        Timber.w(error, "Could not open the encrypted store %s", name)
        if (!isUnreadable(error)) {
            return runCatching { create(appContext, name) }
                .onFailure { Timber.w(it, "Could not open the encrypted store %s twice", name) }
                .getOrNull()
        }

        appContext.deleteSharedPreferences(name)
        runCatching { create(appContext, name) }.getOrNull()?.let {
            Timber.w("Discarded the unreadable contents of %s", name)
            onDiscarded()
            return it
        }

        // The wrapping key itself is unusable, so it has to go along with the file it wraps.
        deleteMasterKey()
        appContext.deleteSharedPreferences(name)
        return runCatching { create(appContext, name) }
            .onFailure { Timber.w(it, "Could not open %s even with a fresh master key", name) }
            .getOrNull()?.also {
                Timber.w("Discarded the unreadable contents of %s and its master key", name)
                onDiscarded()
            }
    }

    /** Whether [error] says the contents can no longer be decrypted, as opposed to not right now. */
    private fun isUnreadable(error: Throwable?): Boolean =
        generateSequence(error) { it.cause }.any { it is AEADBadTagException }

    private fun create(appContext: Context, name: String): SharedPreferences {
        val masterKey =
            MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            appContext,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun deleteMasterKey() {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }.onFailure { Timber.w(it, "Could not delete the master key") }
    }
}
