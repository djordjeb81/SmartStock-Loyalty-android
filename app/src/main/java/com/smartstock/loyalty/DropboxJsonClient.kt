package com.smartstock.loyalty

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class UserNotFoundException(message: String) : RuntimeException(message)

data class DropboxUsersSource(
    val label: String,
    val usersFolder: String
)

object DropboxJsonClient {

    private val http = OkHttpClient()

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

    fun downloadJsonByPath(path: String): String = downloadTextByPath(path)

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
     * Novi model:
     * server eksportuje u:
     * /Loyalty/FirmaA/users/<email>.json
     * /Loyalty/FirmaB/users/<email>.json
     */
    fun pathForEmail(email: String, usersFolder: String): String {
        val safe = email.trim().lowercase()
        val folder = normalizeFolder(usersFolder)
        return "$folder/$safe.json"
    }

    /**
     * Fallback za staru logiku ako folder nije podešen.
     */
    fun pathForEmail(email: String): String {
        val safe = email.trim().lowercase()
        return "/Loyalty/users/$safe.json"
    }

    /**
     * Lista firmi:
     * čita podfoldere ispod /Loyalty i vraća one koji imaju /users podfolder.
     */
    fun listUserSources(): List<DropboxUsersSource> {
        val rootEntries = listFolder("/Loyalty")

        val result = mutableListOf<DropboxUsersSource>()

        for (entry in rootEntries) {
            val tag = entry.optString(".tag")
            if (tag != "folder") continue

            val rawName = entry.optString("name").trim()
            val pathLower = entry.optString("path_lower").trim()

            if (rawName.isBlank() || pathLower.isBlank()) continue
            if (!rawName.startsWith("Firma ", ignoreCase = true)) continue

            val usersFolder = "$pathLower/users"

            if (folderExists(usersFolder)) {
                val label = rawName.removePrefix("Firma ").trim().ifBlank { rawName }

                result += DropboxUsersSource(
                    label = label,
                    usersFolder = usersFolder
                )
            }
        }

        return result.sortedBy { it.label.lowercase() }
    }

    fun pathForHelpDoc(docKey: String): String {
        return when (docKey.trim().lowercase()) {
            "opste" -> "/Loyalty/help/opste.txt"
            "clanarina" -> "/Loyalty/help/clanarina.txt"
            "isplate" -> "/Loyalty/help/isplate.txt"
            else -> "/Loyalty/help/opste.txt"
        }
    }

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

    private fun folderExists(path: String): Boolean {
        return try {
            listFolder(path)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun listFolder(path: String): List<JSONObject> {
        val accessToken = DropboxAuth.getAccessToken()

        val bodyJson = JSONObject()
            .put("path", normalizeFolder(path))
            .put("recursive", false)
            .put("include_deleted", false)
            .put("include_has_explicit_shared_members", false)
            .put("include_mounted_folders", true)

        val req = Request.Builder()
            .url("https://api.dropboxapi.com/2/files/list_folder")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()

            if (!resp.isSuccessful) {
                val msg = extractDropboxErrorSummary(body)
                throw RuntimeException("Dropbox list_folder greška: HTTP ${resp.code} ${msg.ifBlank { body }}")
            }

            val json = JSONObject(body)
            val entries = json.optJSONArray("entries") ?: JSONArray()

            val result = ArrayList<JSONObject>(entries.length())
            for (i in 0 until entries.length()) {
                val obj = entries.optJSONObject(i) ?: continue
                result += obj
            }
            return result
        }
    }

    private fun normalizeFolder(folder: String): String {
        var f = folder.trim()
        if (!f.startsWith("/")) f = "/$f"
        return f.trimEnd('/')
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