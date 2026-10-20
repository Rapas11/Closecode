package com.closecode.build

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * The download's network rule: Wi-Fi (or Ethernet) always, mobile data only when the user
 * allowed it. The store asks before each file and periodically during a file, so losing
 * Wi-Fi with mobile data off pauses the download honestly ("Waiting for Wi-Fi") instead of
 * quietly spending the user's data.
 */
class AndroidNetworkGate(context: Context) : NetworkGate {

    private val app = context.applicationContext
    private val kv = com.closecode.ui.settings.SharedPreferencesKeyValueStore(app)

    override fun isWifi(): Boolean {
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    override fun mobileAllowed(): Boolean = kv.getBoolean(ToolchainStore.KEY_MOBILE_ALLOWED, false)
}

/** What the store needs to know about the network. Interface so the rules are unit testable. */
interface NetworkGate {
    fun isWifi(): Boolean
    fun mobileAllowed(): Boolean
}
