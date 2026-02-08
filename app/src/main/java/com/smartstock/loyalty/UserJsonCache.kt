package com.smartstock.loyalty

import android.content.Context
import org.json.JSONObject
import java.io.File

object UserJsonCache {

    private fun safeKey(email: String): String =
        email.trim().lowercase().replace(Regex("[^a-z0-9@._-]"), "_")

    private fun jsonFile(ctx: Context, email: String): File =
        File(ctx.filesDir, "user_cache_${safeKey(email)}.json")

    private fun metaFile(ctx: Context, email: String): File =
        File(ctx.filesDir, "user_cache_${safeKey(email)}.meta")

    /**
     * Sačuvaj cache ZA KONKRETAN EMAIL.
     */
    fun save(ctx: Context, email: String, jsonText: String, cachedAt: String) {
        jsonFile(ctx, email).writeText(jsonText, Charsets.UTF_8)
        metaFile(ctx, email).writeText(cachedAt, Charsets.UTF_8)
    }

    /**
     * Učitaj cache ZA KONKRETAN EMAIL.
     * Vraća null ako ne postoji ili ako JSON ne pripada tom email-u.
     */
    fun loadJson(ctx: Context, email: String): String? {
        val f = jsonFile(ctx, email)
        if (!f.exists()) return null

        val text = try { f.readText(Charsets.UTF_8) } catch (_: Exception) { return null }
        if (text.isBlank()) return null

        // Bezbednost: proveri da li cached JSON stvarno pripada tom email-u
        val cachedEmail = try {
            JSONObject(text).optJSONObject("user")?.optString("email", "")?.trim()?.lowercase().orEmpty()
        } catch (_: Exception) { "" }

        if (cachedEmail.isBlank()) return null
        if (cachedEmail != email.trim().lowercase()) return null

        return text
    }

    fun loadCachedAt(ctx: Context, email: String): String? {
        val f = metaFile(ctx, email)
        if (!f.exists()) return null
        return try { f.readText(Charsets.UTF_8) } catch (_: Exception) { null }
    }

    /**
     * Obriši cache za jedan email.
     */
    fun clear(ctx: Context, email: String) {
        runCatching { jsonFile(ctx, email).delete() }
        runCatching { metaFile(ctx, email).delete() }
    }

    /**
     * Obriši SVE cache fajlove (logout).
     */
    fun clearAll(ctx: Context) {
        ctx.filesDir.listFiles()?.forEach { f ->
            if (f.name.startsWith("user_cache_")) runCatching { f.delete() }
        }
    }
}
