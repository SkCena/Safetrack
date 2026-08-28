package com.example.safetrack

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AuthManager {
    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_AUTH_STATUS = "is_auth_valid"

    suspend fun verifyKey(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!NetworkUtils.isInternetAvailable(context)) {
            return@withContext prefs.getBoolean(KEY_AUTH_STATUS, true)
        }

        try {
            val url = URL("https://raw.githubusercontent.com/username/repo/main/keys.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val isValid = response.contains("valid")

            prefs.edit().putBoolean(KEY_AUTH_STATUS, isValid).apply()
            return@withContext isValid
        } catch (e: Exception) {
            return@withContext prefs.getBoolean(KEY_AUTH_STATUS, true)
        }
    }
}
