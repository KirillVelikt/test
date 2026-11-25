package ru.yumani.terminal

import android.os.Bundle
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val prefs = getSharedPreferences("yumani", MODE_PRIVATE)

        val etName = findViewById<EditText>(R.id.etName)
        val swSound = findViewById<Switch>(R.id.swSound)
        val swKeep = findViewById<Switch>(R.id.swKeep)
        val swVibrate = findViewById<Switch>(R.id.swVibrate)

        etName.setText(prefs.getString("name", ""))
        swSound.isChecked = prefs.getBoolean("sound", true)
        swKeep.isChecked = prefs.getBoolean("keep", true)
        swVibrate.isChecked = prefs.getBoolean("vibrate", true)

        findViewById<TextView>(R.id.btnSave).setOnClickListener {
            val name = etName.text.toString().trim()
            prefs.edit()
                .putString("name", if (name.isEmpty()) "Терминал" else name)
                .putBoolean("sound", swSound.isChecked)
                .putBoolean("keep", swKeep.isChecked)
                .putBoolean("vibrate", swVibrate.isChecked)
                .apply()
            Toast.makeText(this, "сохранено", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
