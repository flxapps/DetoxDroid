package com.flx_apps.digitaldetox.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.security.KeyStore

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
        create(appContext, name)?.let { return it }

        appContext.deleteSharedPreferences(name)
        create(appContext, name)?.let {
            Timber.w("Discarded the unreadable contents of %s", name)
            onDiscarded()
            return it
        }

        // The wrapping key itself is unusable, so it has to go along with the file it wraps.
        deleteMasterKey()
        appContext.deleteSharedPreferences(name)
        return create(appContext, name)?.also {
            Timber.w("Discarded the unreadable contents of %s and its master key", name)
            onDiscarded()
        }
    }

    private fun create(appContext: Context, name: String): SharedPreferences? = try {
        val masterKey =
            MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            appContext,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.w(e, "Could not open the encrypted store %s", name)
        null
    }

    private fun deleteMasterKey() {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }.onFailure { Timber.w(it, "Could not delete the master key") }
    }
}
