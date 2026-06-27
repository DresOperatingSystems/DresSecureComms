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
 * passphrase with PBKDF2 and a fresh random salt per message, so the same passphrase
 * produces different ciphertext each time and brute-forcing the passphrase is expensive.
 * The passphrase is shared by hand between the two people; both must run this app.
 *
 * Versioning:
 *   DSC2  (current)  PBKDF2-HMAC-SHA256.  All NEW messages are encrypted with this.
 *   DSC1  (legacy)   PBKDF2-HMAC-SHA1.    DECRYPT ONLY, so messages sent by an older
 *                                         build stay readable. Never used to encrypt.
 *
 * Wire format (after the version prefix), Base64: salt(16) || iv(12) || ciphertext+tag.
 */
object SmsCrypto {
    private const val PREFIX_V2 = "DSC2:"   // PBKDF2-HMAC-SHA256, used for all new messages
    private const val PREFIX_V1 = "DSC1:"   // PBKDF2-HMAC-SHA1, legacy, decrypt only

    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    // SMS bodies are decrypted eagerly when a thread renders, so this PBKDF2 cost is paid per
    // message on possibly low-end hardware. OWASP's current floor for PBKDF2-HMAC-SHA256 is
    // 600,000; 310,000 (OWASP's prior figure) is chosen here to keep thread opening responsive
    // on minSdk-24 devices while still being a large, citable improvement over the old SHA-1 KDF.
    // It is a single tunable constant; raise it if your target hardware can absorb the latency.
    private const val ITER_V2 = 310_000
    private const val ITER_V1 = 100_000     // must match the original DSC1 build to decrypt old data

    /** True for both current and legacy ciphertext, so existing messages are still recognised. */
    fun isEncrypted(body: String): Boolean = body.startsWith(PREFIX_V2) || body.startsWith(PREFIX_V1)

    private fun deriveKey(pass: String, salt: ByteArray, sha256: Boolean): SecretKeySpec {
        val algo = if (sha256) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val iter = if (sha256) ITER_V2 else ITER_V1
        val spec = PBEKeySpec(pass.toCharArray(), salt, iter, KEY_BITS)
        return SecretKeySpec(SecretKeyFactory.getInstance(algo).generateSecret(spec).encoded, "AES")
    }

    /** Always produces a current (DSC2 / SHA-256) message. */
    fun encrypt(plain: String, pass: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(pass, salt, sha256 = true), GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX_V2 + Base64.encodeToString(salt + iv + ct, Base64.NO_WRAP)
    }

    /** Decrypts current (DSC2) and legacy (DSC1) messages, picking the KDF from the prefix. */
    fun decrypt(body: String, pass: String): String {
        val sha256 = body.startsWith(PREFIX_V2)
        val payload = body.removePrefix(if (sha256) PREFIX_V2 else PREFIX_V1)
        val data = Base64.decode(payload, Base64.NO_WRAP)
        val salt = data.copyOfRange(0, SALT_LEN)
        val iv = data.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
        val ct = data.copyOfRange(SALT_LEN + IV_LEN, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(pass, salt, sha256), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
