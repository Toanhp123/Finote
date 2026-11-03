package io.github.toanhp123.finote.data.security

/** Stands in for AndroidKeyStore, which Robolectric does not provide. */
class FakeKeyCipher : KeyCipher {
    var wrapCount = 0
        private set
    var isKeyLost = false
    var failure: Exception? = null

    override fun wrap(key: ByteArray): ByteArray {
        wrapCount++
        isKeyLost = false
        return key.reversedArray()
    }

    override fun unwrap(wrapped: ByteArray): ByteArray? {
        failure?.let { throw it }
        return if (isKeyLost) null else wrapped.reversedArray()
    }
}
