package com.smartstock.loyalty

import android.content.Context

object PhoneUnlockPrefs {
    private const val PREFS = "phone_unlock_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_EMAIL = "last_email"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun disable(ctx: Context) {
        setEnabled(ctx, false)
    }

    fun getLastEmail(ctx: Context): String =
        prefs(ctx).getString(KEY_LAST_EMAIL, "")?.trim().orEmpty()

    fun setLastEmail(ctx: Context, email: String) {
        prefs(ctx).edit().putString(KEY_LAST_EMAIL, email.trim().lowercase()).apply()
    }
}
