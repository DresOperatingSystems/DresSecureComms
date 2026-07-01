/* Copyright © 2026 DresOS. Licensed under the Apache License, Version 2.0. */
package com.dresos.dressecurecomms.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SmsCrypto {
    private const val PREFIX_V2 = "DSC2:"
    private const val PREFIX_V1 = "DSC1:"

    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    private const val ITER_V2 = 310_000
    private const val ITER_V1 = 100_000

    fun isEncrypted(body: String): Boolean = body.startsWith(PREFIX_V2) || body.startsWith(PREFIX_V1)

    private fun deriveKey(pass: String, salt: ByteArray, sha256: Boolean): SecretKeySpec {
        val algo = if (sha256) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val iter = if (sha256) ITER_V2 else ITER_V1
        val spec = PBEKeySpec(pass.toCharArray(), salt, iter, KEY_BITS)
        return SecretKeySpec(SecretKeyFactory.getInstance(algo).generateSecret(spec).encoded, "AES")
    }

    fun encrypt(plain: String, pass: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(pass, salt, sha256 = true), GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX_V2 + Base64.encodeToString(salt + iv + ct, Base64.NO_WRAP)
    }

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

    private val MEDIA_MAGIC = byteArrayOf(0x44, 0x53, 0x43, 0x4D)

    fun isEncryptedMedia(data: ByteArray): Boolean =
        data.size > 5 && data.copyOfRange(0, 4).contentEquals(MEDIA_MAGIC)

    fun encryptBytes(plain: ByteArray, pass: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(pass, salt, sha256 = true), GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain)
        return MEDIA_MAGIC + byteArrayOf(0x02) + salt + iv + ct
    }

    fun decryptBytes(data: ByteArray, pass: String): ByteArray {
        var off = MEDIA_MAGIC.size + 1
        val salt = data.copyOfRange(off, off + SALT_LEN); off += SALT_LEN
        val iv = data.copyOfRange(off, off + IV_LEN); off += IV_LEN
        val ct = data.copyOfRange(off, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(pass, salt, sha256 = true), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }
}
