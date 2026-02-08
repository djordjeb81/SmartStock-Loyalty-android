package com.smartstock.loyalty

import android.content.Context

object SettingsPrefs {

    private const val PREFS = "loyalty_settings"

    private const val KEY_QUICK_UNLOCK_ENABLED = "quick_unlock_enabled"
    private const val KEY_LAST_EMAIL = "last_email"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isQuickUnlockEnabled(ctx: Context): Boolean =
        p(ctx).getBoolean(KEY_QUICK_UNLOCK_ENABLED, false)

    fun setQuickUnlockEnabled(ctx: Context, enabled: Boolean) {
        p(ctx).edit().putBoolean(KEY_QUICK_UNLOCK_ENABLED, enabled).apply()
    }

    fun getLastEmail(ctx: Context): String =
        p(ctx).getString(KEY_LAST_EMAIL, "")?.trim().orEmpty()

    fun setLastEmail(ctx: Context, email: String) {
        p(ctx).edit().putString(KEY_LAST_EMAIL, email.trim().lowercase()).apply()
    }

    fun clearLastEmail(ctx: Context) {
        p(ctx).edit().remove(KEY_LAST_EMAIL).apply()
    }
}
