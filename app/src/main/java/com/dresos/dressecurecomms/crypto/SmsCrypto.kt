/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption for SMS bodies. The AES key is derived from the shared
 * passphrase with PBKDF2-HMAC-SHA1 and a fresh random salt per message, so the
 * same passphrase produces different ciphertext each time and brute-forcing the
 * passphrase is expensive. A short prefix marks our ciphertext. The passphrase is
 * shared by hand between the two people; both must run this app.
 *
 * Wire format (after the prefix), Base64: salt(16) || iv(12) || ciphertext+tag.
 */
object SmsCrypto {
    private const val PREFIX = "DSC1:"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val ITERATIONS = 100_000

    fun isEncrypted(body: String): Boolean = body.startsWith(PREFIX)

    private fun deriveKey(pass: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pass.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun encrypt(plain: String, pass: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(pass, salt), GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(salt + iv + ct, Base64.NO_WRAP)
    }

    fun decrypt(body: String, pass: String): String {
        val data = Base64.decode(body.removePrefix(PREFIX), Base64.NO_WRAP)
        val salt = data.copyOfRange(0, SALT_LEN)
        val iv = data.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
        val ct = data.copyOfRange(SALT_LEN + IV_LEN, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(pass, salt), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
