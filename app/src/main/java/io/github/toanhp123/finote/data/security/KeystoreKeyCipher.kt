package io.github.toanhp123.finote.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM key in AndroidKeyStore. No user-auth needed: the notification listener writes while the device is locked. */
class KeystoreKeyCipher(
    private val alias: String = DEFAULT_ALIAS,
) : KeyCipher {
    override fun wrap(key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, createWrappingKey())
        return cipher.iv + cipher.doFinal(key)
    }

    /**
     * Null only for a clear loss. `UnrecoverableKeyException` from `getKey` propagates: [DatabaseKeyStore] counts it.
     * Any other failure (busy or locked Keystore) also throws, so a retry can still succeed.
     */
    override fun unwrap(wrapped: ByteArray): ByteArray? {
        val wrappingKey = keyStore().getKey(alias, null) as? SecretKey
        return if (wrappingKey == null || wrapped.size < IV_BYTES + TAG_BYTES) null else decrypt(wrappingKey, wrapped)
    }

    private fun decrypt(
        wrappingKey: SecretKey,
        wrapped: ByteArray,
    ): ByteArray? =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(TAG_BITS, wrapped, 0, IV_BYTES))
            cipher.doFinal(wrapped, IV_BYTES, wrapped.size - IV_BYTES)
        } catch (_: AEADBadTagException) {
            null
        } catch (_: KeyPermanentlyInvalidatedException) {
            null
        }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun createWrappingKey(): SecretKey {
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "finote_db_key_wrapper"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val TAG_BYTES = 16
        const val IV_BYTES = 12
    }
}
