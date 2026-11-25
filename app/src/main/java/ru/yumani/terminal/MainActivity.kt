package ru.yumani.terminal

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var beaconTask: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("terminal", MODE_PRIVATE)

        TerminalLink.terminalName = prefs.getString("name", "терминал") ?: "терминал"
        TerminalLink.soundsOn = prefs.getBoolean("sounds", true)

        val code = (100000..999999).random().toString()
        TerminalLink.code = code

        val spinner = findViewById<ImageView>(R.id.ivRing)
        val searching = findViewById<TextView>(R.id.tvSearching)
        val content = findViewById<View>(R.id.content)
        val tvCode = findViewById<TextView>(R.id.tvCode)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        tvCode.text = code

        // вращающееся кольцо-поиск
        ObjectAnimator.ofFloat(spinner, "rotation", 0f, 360f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        // через 2.5 секунды заставка уходит, появляется код
        handler.postDelayed({
            spinner.animate().alpha(0f).setDuration(400).start()
            searching.animate().alpha(0f).setDuration(400).start()
            content.alpha = 0f
            content.visibility = View.VISIBLE
            content.animate().alpha(1f).setDuration(450).start()
            tvCode.scaleX = 0.6f
            tvCode.scaleY = 0.6f
            tvCode.animate().scaleX(1f).scaleY(1f).setDuration(450)
                .setInterpolator(DecelerateInterpolator()).start()
        }, 2500)

        // маячок каждые 1.5 секунды
        beaconTask = object : Runnable {
            override fun run() {
                TerminalLink.sendBeacon()
                handler.postDelayed(this, 1500)
            }
        }
        handler.post(beaconTask!!)

        TerminalLink.start { amount ->
            startActivity(
                Intent(this, PaymentActivity::class.java)
                    .putExtra("amount", amount)
                    .putExtra("code", code)
            )
        }

        // тап по коду - демо-оплата с телефона
        tvCode.setOnClickListener {
            startActivity(
                Intent(this, PaymentActivity::class.java)
                    .putExtra("amount", 198)
                    .putExtra("code", code)
            )
        }

        // настройки
        findViewById<ImageView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        TerminalLink.terminalName = prefs.getString("name", "терминал") ?: "терминал"
        TerminalLink.soundsOn = prefs.getBoolean("sounds", true)
    }

    override fun onDestroy() {
        super.onDestroy()
        beaconTask?.let { handler.removeCallbacks(it) }
        TerminalLink.stop()
    }
}
