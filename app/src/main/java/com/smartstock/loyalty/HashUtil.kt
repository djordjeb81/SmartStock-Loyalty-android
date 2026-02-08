package com.smartstock.loyalty

import android.util.Base64
import java.security.MessageDigest
import java.util.Locale

object HashUtil {

    /** SHA-256 bytes */
    fun sha256Bytes(input: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray(Charsets.UTF_8))
    }

    /** Bytes -> lowercase hex */
    fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            val hi = v ushr 4
            val lo = v and 0x0F
            sb.append("0123456789abcdef"[hi])
            sb.append("0123456789abcdef"[lo])
        }
        return sb.toString()
    }

    /** Bytes -> Base64 (NO_WRAP) */
    fun toBase64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    /**
     * Uporedi očekivani hash iz JSON-a (HEX ili Base64) sa stvarnim SHA-256 bytes.
     * - Ako očekivani liči na HEX (64 heks znaka) -> HEX poređenje
     * - Inače -> Base64 poređenje
     */
    fun matchesExpected(expectedFromJson: String, actualSha256Bytes: ByteArray): Boolean {
        val expected = expectedFromJson.trim()
        val expLower = expected.lowercase(Locale.US)

        val looksLikeHex =
            expLower.length == 64 && expLower.all { it in "0123456789abcdef" }

        return if (looksLikeHex) {
            val actualHex = toHex(actualSha256Bytes)
            actualHex.equals(expLower, ignoreCase = true)
        } else {
            val actualB64 = toBase64(actualSha256Bytes)
            actualB64 == expected || actualB64.trim() == expected
        }
    }
}
