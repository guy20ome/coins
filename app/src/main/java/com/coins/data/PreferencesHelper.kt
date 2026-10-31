package com.coins.data

import android.content.Context
import android.content.SharedPreferences

object PreferencesHelper {
    private const val PREF_NAME = "CoinsPrefs"
    private const val KEY_IS_REGISTERED = "isRegistered"
    private const val KEY_FULL_NAME = "fullName"
    private const val KEY_EMAIL = "email"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun setRegistered(context: Context, isRegistered: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_IS_REGISTERED, isRegistered).apply()
    }

    fun isRegistered(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IS_REGISTERED, false)
    }

    fun setFullName(context: Context, fullName: String) {
        getPreferences(context).edit().putString(KEY_FULL_NAME, fullName).apply()
    }

    fun getFullName(context: Context): String {
        return getPreferences(context).getString(KEY_FULL_NAME, "") ?: ""
    }

    fun setEmail(context: Context, email: String) {
        getPreferences(context).edit().putString(KEY_EMAIL, email).apply()
    }

    fun getEmail(context: Context): String {
        return getPreferences(context).getString(KEY_EMAIL, "") ?: ""
    }
}
