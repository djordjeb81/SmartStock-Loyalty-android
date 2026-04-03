package com.smartstock.loyalty

import android.content.Context

object ManualPrefs {
    private const val PREFS = "manual_prefs"
    private const val KEY_VERSION = "manual_version"
    private const val KEY_FILE_NAME = "manual_file_name"

    fun getVersion(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_VERSION, 0)

    fun setVersion(context: Context, version: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_VERSION, version)
            .apply()
    }

    fun getFileName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FILE_NAME, "uputstvo.pdf")
            .orEmpty()

    fun setFileName(context: Context, fileName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FILE_NAME, fileName)
            .apply()
    }
}