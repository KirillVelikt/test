package ru.yumani.terminal

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("terminal", MODE_PRIVATE)

        val etName = findViewById<EditText>(R.id.etName)
        val swSounds = findViewById<Switch>(R.id.swSounds)
        val swKeep = findViewById<Switch>(R.id.swKeepScreen)
        val swVibrate = findViewById<Switch>(R.id.swVibrate)

        etName.setText(prefs.getString("name", "терминал"))
        swSounds.isChecked = prefs.getBoolean("sounds", true)
        swKeep.isChecked = prefs.getBoolean("keep_screen", true)
        swVibrate.isChecked = prefs.getBoolean("vibrate", true)

        etName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val v = etName.text.toString().trim()
                val finalName = if (v.isEmpty()) "терминал" else v
                prefs.edit().putString("name", finalName).apply()
                TerminalLink.terminalName = finalName
            }
        }
        swSounds.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("sounds", checked).apply()
            TerminalLink.soundsOn = checked
        }
        swKeep.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("keep_screen", checked).apply()
        }
        swVibrate.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("vibrate", checked).apply()
        }

        findViewById<TextView>(R.id.btnDone).setOnClickListener { finish() }
    }
}
