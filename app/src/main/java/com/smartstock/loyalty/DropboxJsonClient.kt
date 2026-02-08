// File: app/src/main/java/com/smartstock/loyalty/DropboxJsonClient.kt
package com.smartstock.loyalty

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Specijalni izuzetak koji koristimo za jasne poruke u UI-u.
 */
class UserNotFoundException(message: String) : RuntimeException(message)

object DropboxJsonClient {

    private val http = OkHttpClient()

    /**
     * Skida fajl sa Dropbox-a po path-u (JSON ili TXT).
     * Koristi Dropbox Content API: https://content.dropboxapi.com/2/files/download
     */
    fun downloadTextByPath(path: String): String {
        val accessToken = DropboxAuth.getAccessToken()

        val arg = JSONObject().put("path", path).toString()
        val emptyBody = ByteArray(0).toRequestBody("application/octet-stream".toMediaType())

        val req = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/download")
            .post(emptyBody)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Dropbox-API-Arg", arg)
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()

            if (!resp.isSuccessful) {
                val msg = extractDropboxErrorSummary(body)

                if (msg.contains("path/not_found", ignoreCase = true) ||
                    msg.contains("not_found", ignoreCase = true)
                ) {
                    throw UserNotFoundException("Fajl ne postoji na Dropbox-u.")
                }

                throw RuntimeException("Dropbox greška: HTTP ${resp.code} ${msg.ifBlank { body }}")
            }

            return body
        }
    }

    /** Kompatibilnost sa starim pozivima. */
    fun downloadJsonByPath(path: String): String = downloadTextByPath(path)

    /**
     * Očekujemo user fajl na: /Loyalty/users/<emailLower>.json
     */
    fun pathForEmail(email: String): String {
        val safe = email.trim().lowercase()
        return "/Loyalty/users/$safe.json"
    }

    /**
     * Help fajlovi (2-3 komada) koje možeš kačiti na Dropbox.
     *
     * Predlog:
     *  /Loyalty/help/opste.txt
     *  /Loyalty/help/clanarina.txt
     *  /Loyalty/help/isplate.txt
     */
    fun pathForHelpDoc(docKey: String): String {
        return when (docKey.trim().lowercase()) {
            "opste" -> "/Loyalty/help/opste.txt"
            "clanarina" -> "/Loyalty/help/clanarina.txt"
            "isplate" -> "/Loyalty/help/isplate.txt"
            else -> "/Loyalty/help/opste.txt"
        }
    }

    private fun extractDropboxErrorSummary(body: String): String {
        return try {
            val j = JSONObject(body)
            j.optString("error_summary", "").trim()
        } catch (_: Exception) {
            ""
        }
    }
}
