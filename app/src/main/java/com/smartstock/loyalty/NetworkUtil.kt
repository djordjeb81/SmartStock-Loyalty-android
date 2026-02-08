// File: app/src/main/java/com/smartstock/loyalty/NetworkUtil.kt
package com.smartstock.loyalty

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkUtil {

    /**
     * Proverava da li postoji aktivna mreža sa internet capability.
     * Ne garantuje da internet radi 100%, ali je dovoljno za "obavezno internet" logiku.
     */
    fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false

        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
