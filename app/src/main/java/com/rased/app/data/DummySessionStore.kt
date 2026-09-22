package com.rased.app.data

import android.content.Context

/** Persists the local demonstration session across app restarts. */
internal class DummySessionStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("dummy_session", Context.MODE_PRIVATE)

    val isLoggedIn: Boolean
        get() = preferences.getString("token", null) == "taha22"

    fun login() {
        preferences.edit().putString("token", "taha22").apply()
    }

    fun logout() {
        preferences.edit().remove("token").apply()
    }
}
