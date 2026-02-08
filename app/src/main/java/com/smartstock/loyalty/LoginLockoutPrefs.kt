package com.smartstock.loyalty

import android.content.Context

object LoginLockoutPrefs {

    private const val PREFS = "ssl_loyalty_login_lockout"
    private const val KEY_FAIL_COUNT = "fail_count"
    private const val KEY_LOCK_UNTIL_MS = "lock_until_ms"

    // Podešavanja (možeš kasnije prebaciti u Config)
    const val MAX_FAILS = 5
    const val LOCK_MINUTES = 5

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getFailCount(ctx: Context): Int =
        prefs(ctx).getInt(KEY_FAIL_COUNT, 0)

    fun getLockUntilMs(ctx: Context): Long =
        prefs(ctx).getLong(KEY_LOCK_UNTIL_MS, 0L)

    fun isLocked(ctx: Context, nowMs: Long = System.currentTimeMillis()): Boolean =
        getLockUntilMs(ctx) > nowMs

    fun remainingMs(ctx: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val until = getLockUntilMs(ctx)
        return (until - nowMs).coerceAtLeast(0L)
    }

    fun recordFailedAttempt(ctx: Context) {
        val p = prefs(ctx)
        val current = p.getInt(KEY_FAIL_COUNT, 0) + 1

        val e = p.edit().putInt(KEY_FAIL_COUNT, current)

        // Ako smo dostigli limit, zaključaj na LOCK_MINUTES
        if (current >= MAX_FAILS) {
            val lockUntil = System.currentTimeMillis() + LOCK_MINUTES * 60_000L
            e.putLong(KEY_LOCK_UNTIL_MS, lockUntil)
        }

        e.apply()
    }

    fun reset(ctx: Context) {
        prefs(ctx).edit()
            .putInt(KEY_FAIL_COUNT, 0)
            .putLong(KEY_LOCK_UNTIL_MS, 0L)
            .apply()
    }
}
