package com.smartstock.loyalty

import android.content.Context

object SettingsPrefs {

    private const val PREFS = "loyalty_settings"

    private const val KEY_QUICK_UNLOCK_ENABLED = "quick_unlock_enabled"
    private const val KEY_LAST_EMAIL = "last_email"

    private const val KEY_SELECTED_USERS_LABEL = "selected_users_label"
    private const val KEY_SELECTED_USERS_FOLDER = "selected_users_folder"
    private const val KEY_USERS_SOURCE_READY = "users_source_ready"
    private const val KEY_PENDING_USERS_LABEL = "pending_users_label"
    private const val KEY_PENDING_USERS_FOLDER = "pending_users_folder"

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

    fun getSelectedUsersLabel(ctx: Context): String =
        p(ctx).getString(KEY_SELECTED_USERS_LABEL, "")?.trim().orEmpty()

    fun setSelectedUsersLabel(ctx: Context, label: String) {
        p(ctx).edit().putString(KEY_SELECTED_USERS_LABEL, label.trim()).apply()
    }

    fun getSelectedUsersFolder(ctx: Context): String =
        p(ctx).getString(KEY_SELECTED_USERS_FOLDER, "")?.trim().orEmpty()

    fun setSelectedUsersFolder(ctx: Context, folder: String) {
        p(ctx).edit().putString(KEY_SELECTED_USERS_FOLDER, normalizeFolder(folder)).apply()
    }

    fun isUsersSourceReady(ctx: Context): Boolean =
        p(ctx).getBoolean(KEY_USERS_SOURCE_READY, false)

    fun setUsersSourceReady(ctx: Context, ready: Boolean) {
        p(ctx).edit().putBoolean(KEY_USERS_SOURCE_READY, ready).apply()
    }

    fun clearSelectedUsersSource(ctx: Context) {
        p(ctx).edit()
            .remove(KEY_SELECTED_USERS_LABEL)
            .remove(KEY_SELECTED_USERS_FOLDER)
            .remove(KEY_PENDING_USERS_LABEL)
            .remove(KEY_PENDING_USERS_FOLDER)
            .putBoolean(KEY_USERS_SOURCE_READY, false)
            .apply()
    }

    private fun normalizeFolder(folder: String): String {
        var f = folder.trim()
        if (f.isBlank()) return ""
        if (!f.startsWith("/")) f = "/$f"
        return f.trimEnd('/')
    }
    fun getPendingUsersLabel(ctx: Context): String =
        p(ctx).getString(KEY_PENDING_USERS_LABEL, "")?.trim().orEmpty()

    fun setPendingUsersLabel(ctx: Context, label: String) {
        p(ctx).edit().putString(KEY_PENDING_USERS_LABEL, label.trim()).apply()
    }

    fun getPendingUsersFolder(ctx: Context): String =
        p(ctx).getString(KEY_PENDING_USERS_FOLDER, "")?.trim().orEmpty()

    fun setPendingUsersFolder(ctx: Context, folder: String) {
        p(ctx).edit().putString(KEY_PENDING_USERS_FOLDER, normalizeFolder(folder)).apply()
    }

    fun clearPendingUsersSource(ctx: Context) {
        p(ctx).edit()
            .remove(KEY_PENDING_USERS_LABEL)
            .remove(KEY_PENDING_USERS_FOLDER)
            .apply()
    }

    fun commitPendingUsersSource(ctx: Context) {
        val prefs = p(ctx)
        val pendingLabel = prefs.getString(KEY_PENDING_USERS_LABEL, "")?.trim().orEmpty()
        val pendingFolder = prefs.getString(KEY_PENDING_USERS_FOLDER, "")?.trim().orEmpty()

        prefs.edit()
            .putString(KEY_SELECTED_USERS_LABEL, pendingLabel)
            .putString(KEY_SELECTED_USERS_FOLDER, pendingFolder)
            .putBoolean(KEY_USERS_SOURCE_READY, pendingFolder.isNotBlank())
            .remove(KEY_PENDING_USERS_LABEL)
            .remove(KEY_PENDING_USERS_FOLDER)
            .apply()
    }
}