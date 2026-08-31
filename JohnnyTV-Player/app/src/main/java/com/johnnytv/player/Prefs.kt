package com.johnnytv.player

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("johnnytv", Context.MODE_PRIVATE)

    /** Last server the app successfully used - keeps things working if config.json is unreachable. */
    var server: String
        get() = sp.getString(KEY_SERVER, "") ?: ""
        set(value) = sp.edit().putString(KEY_SERVER, value).apply()

    var username: String
        get() = sp.getString(KEY_USER, "") ?: ""
        set(value) = sp.edit().putString(KEY_USER, value).apply()

    var password: String
        get() = sp.getString(KEY_PASS, "") ?: ""
        set(value) = sp.edit().putString(KEY_PASS, value).apply()

    /** A server typed in manually via the hidden support screen overrides config.json. */
    var manualServer: String
        get() = sp.getString(KEY_MANUAL, "") ?: ""
        set(value) = sp.edit().putString(KEY_MANUAL, value).apply()

    val isLoggedIn: Boolean
        get() = server.isNotBlank() && username.isNotBlank()

    fun saveCredentials(server: String, username: String, password: String) {
        sp.edit()
            .putString(KEY_SERVER, server)
            .putString(KEY_USER, username)
            .putString(KEY_PASS, password)
            .apply()
    }

    /** Signs the customer out but keeps the server, so they only re-enter user + password. */
    fun clearCredentials() {
        sp.edit()
            .remove(KEY_USER)
            .remove(KEY_PASS)
            .apply()
    }

    private companion object {
        const val KEY_SERVER = "server"
        const val KEY_USER = "username"
        const val KEY_PASS = "password"
        const val KEY_MANUAL = "manual_server"
    }
}
