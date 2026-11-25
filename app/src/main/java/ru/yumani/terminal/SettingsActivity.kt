package ru.yumani.terminal

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etName = findViewById<EditText>(R.id.etName)
        val swSound = findViewById<Switch>(R.id.swSound)
        val swKeep = findViewById<Switch>(R.id.swKeep)
        val swVibrate = findViewById<Switch>(R.id.swVibrate)

        etName.setText(Prefs.name(this))
        swSound.isChecked = Prefs.sound(this)
        swKeep.isChecked = Prefs.keepScreen(this)
        swVibrate.isChecked = Prefs.vibrate(this)

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            Prefs.setName(this, etName.text.toString().ifBlank { "yumani-terminal" })
            Prefs.setSound(this, swSound.isChecked)
            Prefs.setKeepScreen(this, swKeep.isChecked)
            Prefs.setVibrate(this, swVibrate.isChecked)
            finish()
        }
    }
}
