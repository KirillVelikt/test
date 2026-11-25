package ru.yumani.terminal

import android.content.Context

object Prefs {
    private const val FILE = "term"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun name(c: Context): String = sp(c).getString("name", "yumani-terminal") ?: "yumani-terminal"
    fun setName(c: Context, v: String) { sp(c).edit().putString("name", v).apply() }

    fun sound(c: Context): Boolean = sp(c).getBoolean("sound", true)
    fun vibrate(c: Context): Boolean = sp(c).getBoolean("vibrate", true)
    fun keepScreen(c: Context): Boolean = sp(c).getBoolean("keep", true)

    fun setSound(c: Context, v: Boolean) { sp(c).edit().putBoolean("sound", v).apply() }
    fun setVibrate(c: Context, v: Boolean) { sp(c).edit().putBoolean("vibrate", v).apply() }
    fun setKeepScreen(c: Context, v: Boolean) { sp(c).edit().putBoolean("keep", v).apply() }
}
