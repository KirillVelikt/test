package ru.yumani.terminal

import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var code = ""
    private var jingle: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val pollCommands = object : Runnable {
        override fun run() {
            val cmd = Net.pollCommand()
            if (cmd != null) {
                val (amount, sender) = cmd
                Net.lastSender = sender
                @Suppress("DEPRECATION")
                startActivityForResult(
                    Intent(this@MainActivity, PaymentActivity::class.java)
                        .putExtra("amount", amount)
                        .putExtra("code", code), 1)
            } else {
                handler.postDelayed(this, 250)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("yumani", MODE_PRIVATE)
        val saved = prefs.getString("code", null)
        code = if (saved != null) saved else {
            val fresh = (100000..999999).random().toString()
            prefs.edit().putString("code", fresh).apply()
            fresh
        }
        val name = prefs.getString("name", null) ?: "Терминал $code"

        Net.listenCommands()
        Net.broadcastBeacon(code, name)
        handler.post(pollCommands)

        findViewById<TextView>(R.id.tvCode).text = code
        findViewById<View>(R.id.mainLayer).visibility = View.GONE

        // заставка с крутящимся поиском терминалов
        handler.postDelayed({
            val splash = findViewById<View>(R.id.splash)
            splash.animate().alpha(0f).setDuration(350).withEndAction {
                splash.visibility = View.GONE
                val layer = findViewById<View>(R.id.mainLayer)
                layer.visibility = View.VISIBLE
                layer.scaleX = 0.6f
                layer.scaleY = 0.6f
                layer.alpha = 0f
                layer.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(400).start()
                if (prefs.getBoolean("sound", true)) {
                    jingle = MediaPlayer.create(this@MainActivity, R.raw.jingle)
                    jingle?.start()
                }
            }.start()
        }, 2500)

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            findViewById<TextView>(R.id.tvStatus).text =
                if (resultCode == RESULT_OK) "оплачено ✓" else "ожидание пульта…"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        jingle?.release()
    }
}
