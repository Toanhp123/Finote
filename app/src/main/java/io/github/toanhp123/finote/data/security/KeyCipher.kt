package io.github.toanhp123.finote.data.security

/** Wraps the database key. A seam so the key logic runs on the JVM, where AndroidKeyStore does not exist. */
interface KeyCipher {
    /** Encrypts [key] under a new wrapping key and returns IV + ciphertext. Replaces any earlier wrapping key. */
    fun wrap(key: ByteArray): ByteArray

    /** Returns the key, or null when the wrapping key is missing or invalid or [wrapped] is corrupt. */
    fun unwrap(wrapped: ByteArray): ByteArray?
}
