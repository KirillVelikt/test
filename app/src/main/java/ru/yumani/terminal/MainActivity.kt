package ru.yumani.terminal

import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), TerminalListener {

    private lateinit var prefs: SharedPreferences
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("term", MODE_PRIVATE)

        findViewById<ImageView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // крутящееся кольцо — поиск терминалов
        findViewById<ImageView>(R.id.ivRing).startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.spin)
        )

        player = MediaPlayer.create(this, R.raw.jingle)
        Bus.listener = this
        Net.start(this)
    }

    override fun onResume() {
        super.onResume()
        Bus.listener = this
    }

    override fun onConnect(from: String) {
        findViewById<TextView>(R.id.tvStatus).text = "пульт подключён ✓"
        if (Prefs.sound(this)) player?.start()
    }

    override fun onOrder(json: String) {
        handler.post {
            startActivity(Intent(this, OrdersActivity::class.java).putExtra("json", json))
        }
    }

    override fun onPayResult(success: Boolean) {
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Bus.listener === this) Bus.listener = null
        player?.release()
        handler.removeCallbacksAndMessages(null)
    }
}
