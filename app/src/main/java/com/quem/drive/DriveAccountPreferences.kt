package com.quem.drive

import android.content.Context

class DriveAccountPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(email: String) {
        prefs.edit().putString(KEY_EMAIL, email).apply()
    }

    fun load(): String? = prefs.getString(KEY_EMAIL, null)

    fun clear() {
        prefs.edit().remove(KEY_EMAIL).apply()
    }

    private companion object {
        const val PREFS_NAME = "drive_account"
        const val KEY_EMAIL  = "account_email"
    }
}
