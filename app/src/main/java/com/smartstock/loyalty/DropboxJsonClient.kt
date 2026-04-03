// File: app/src/main/java/com/smartstock/loyalty/DropboxJsonClient.kt
package com.smartstock.loyalty

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

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
     * Skida binarni fajl sa Dropbox-a i upisuje ga u targetFile.
     * Koristi se za PDF uputstvo.
     */
    fun downloadFileByPath(path: String, targetFile: File) {
        val accessToken = DropboxAuth.getAccessToken()

        val arg = JSONObject().put("path", path).toString()
        val emptyBody = ByteArray(0).toRequestBody("application/octet-stream".toMediaType())

        val req = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/download")
            .post(emptyBody)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Dropbox-API-Arg", arg)
            .build()

        targetFile.parentFile?.mkdirs()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                val msg = extractDropboxErrorSummary(body)

                if (msg.contains("path/not_found", ignoreCase = true) ||
                    msg.contains("not_found", ignoreCase = true)
                ) {
                    throw UserNotFoundException("PDF fajl ne postoji na Dropbox-u.")
                }

                throw RuntimeException("Dropbox greška: HTTP ${resp.code} ${msg.ifBlank { body }}")
            }

            val body = resp.body ?: throw RuntimeException("Dropbox odgovor nema sadržaj.")
            targetFile.outputStream().use { out ->
                body.byteStream().copyTo(out)
            }
        }
    }

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

    /**
     * Manifest za opširno PDF uputstvo.
     * Primer fajla na Dropbox-u:
     *
     * /Loyalty/manual/manual_manifest.json
     *
     * {
     *   "version": 3,
     *   "fileName": "uputstvo_loyalty_v3.pdf",
     *   "pdfPath": "/Loyalty/manual/uputstvo_loyalty_v3.pdf",
     *   "title": "Opširno uputstvo"
     * }
     */
    fun pathForManualManifest(): String {
        return "/Loyalty/manual/manual_manifest.json"
    }

    fun downloadManualManifest(): ManualManifestDto {
        val text = downloadTextByPath(pathForManualManifest())
        val json = JSONObject(text)

        return ManualManifestDto(
            version = json.optInt("version", 0),
            fileName = json.optString("fileName", "uputstvo.pdf"),
            pdfPath = json.optString("pdfPath", "/Loyalty/manual/uputstvo.pdf"),
            title = json.optString("title", "Uputstvo")
        )
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