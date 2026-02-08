// File: app/src/main/java/com/smartstock/loyalty/DropboxAuth.kt
package com.smartstock.loyalty

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object DropboxAuth {

    /**
     * ⚠️ Ako nemaš server, ovo mora biti u app-u.
     * Ako je app javna na Play Store, ovo je sigurnosni rizik.
     */
    private const val APP_KEY = "mxf5eu6h9heo464"
    private const val APP_SECRET = "h9eocvo1eh2t2qk"
    private const val REFRESH_TOKEN = "KDzkcKxGeVEAAAAAAAAAAZZfSq3IINVVoY7ckGMeURUWW1Cuif4HLi_4PUJnteyz"

    private val http = OkHttpClient()

    @Volatile private var cachedAccessToken: String? = null
    @Volatile private var cachedExpiresAtMs: Long = 0L

    /**
     * Vraća validan access token (osvežava po potrebi).
     */
    fun getAccessToken(): String {
        val now = System.currentTimeMillis()
        val token = cachedAccessToken
        if (token != null && now < cachedExpiresAtMs - 30_000) { // 30s buffer
            return token
        }

        val reqBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", REFRESH_TOKEN)
            .add("client_id", APP_KEY)
            .add("client_secret", APP_SECRET)
            .build()

        val req = Request.Builder()
            .url("https://api.dropbox.com/oauth2/token")
            .post(reqBody)
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw RuntimeException("Token refresh failed: HTTP ${resp.code} $body")
            }

            val json = JSONObject(body)
            val accessToken = json.getString("access_token")
            val expiresInSec = json.optLong("expires_in", 14400) // ako ne dođe, pretpostavi 4h

            cachedAccessToken = accessToken
            cachedExpiresAtMs = System.currentTimeMillis() + expiresInSec * 1000L
            return accessToken
        }
    }
}
