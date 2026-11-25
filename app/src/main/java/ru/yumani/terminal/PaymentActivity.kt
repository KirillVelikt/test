package ru.yumani.terminal

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class PaymentActivity : AppCompatActivity() {

    private var amount = 0
    private var code = ""
    private var successPlayer: MediaPlayer? = null
    private var failPlayer: MediaPlayer? = null
    private var nfc: NfcAdapter? = null
    private var paid = false

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)

        prefs = getSharedPreferences("terminal", MODE_PRIVATE)
        if (prefs.getBoolean("keep_screen", true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        amount = intent.getIntExtra("amount", 0)
        code = intent.getStringExtra("code") ?: ""

        findViewById<TextView>(R.id.tvAmount).text = "$amount ₽"
        findViewById<TextView>(R.id.tvTerminal).text = "Терминал: $code"

        successPlayer = MediaPlayer.create(this, R.raw.success)
        failPlayer = MediaPlayer.create(this, R.raw.fail)

        // QR-код
        val payload = "yumani://pay?terminal=$code&amount=$amount"
        val size = 512
        try {
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix[x, y]) 0xFF1A1A1D.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            findViewById<ImageView>(R.id.ivQr).setImageBitmap(bmp)
        } catch (e: Exception) {
            // QR не построился - не критично
        }

        // карточка улыбки - камера
        findViewById<View>(R.id.cardSmile).setOnClickListener { openCamera() }

        // NFC-читатель
        nfc = NfcAdapter.getDefaultAdapter(this)
        enableNfc()

        findViewById<View>(R.id.cardNfc).setOnClickListener {
            findViewById<TextView>(R.id.tvStatus).text = "приложите карту к телефону"
        }
    }

    private fun enableNfc() {
        val adapter = nfc ?: return
        try {
            adapter.enableReaderMode(
                this,
                { tag -> onTagDiscovered(tag) },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B
                    or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                Bundle()
            )
        } catch (e: Exception) {
        }
    }

    private fun disableNfc() {
        try {
            nfc?.disableReaderMode(this)
        } catch (e: Exception) {
        }
    }

    private fun onTagDiscovered(tag: Tag?) {
        runOnUiThread {
            findViewById<TextView>(R.id.tvStatus).text = "карта приложена…"
            handler.postDelayed({ finishPayment(true) }, 1200)
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            startActivityForResult(intent, 77)
        } catch (e: Exception) {
            findViewById<TextView>(R.id.tvStatus).text = "камера недоступна"
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77 && resultCode == RESULT_OK) {
            findViewById<TextView>(R.id.tvStatus).text = "улыбка найдена ✓"
            handler.postDelayed({ finishPayment(true) }, 1500)
        }
    }

    /** Результат оплаты: от пульта, NFC или улыбки */
    fun finishPayment(success: Boolean) {
        if (paid) return
        paid = true

        disableNfc()

        if (success) {
            if (prefs.getBoolean("sounds", true)) {
                successPlayer?.start()
            }
            if (prefs.getBoolean("vibrate", true)) {
                val vib = getSystemService(Vibrator::class.java)
                vib?.vibrate(400)
            }
        } else {
            if (prefs.getBoolean("sounds", true)) {
                failPlayer?.start()
            }
        }

        TerminalLink.sendResult(success)

        // кружок "оплачено"
        val overlay = findViewById<View>(R.id.overlaySuccess)
        val check = findViewById<View>(R.id.ivCheck)
        val label = findViewById<TextView>(R.id.tvPaidLabel)
        overlay.visibility = View.VISIBLE
        check.scaleX = 0f
        check.scaleY = 0f
        check.animate().scaleX(1f).scaleY(1f).setDuration(500)
            .setInterpolator(OvershootInterpolator(1.6f)).start()
        label.alpha = 0f
        label.animate().alpha(1f).setStartDelay(250).setDuration(300).start()

        // экран сам закрывается через 2.6 секунды
        if (success) {
            handler.postDelayed({ finish() }, 2600)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // во время оплаты выход заблокирован
    }

    override fun onPause() {
        super.onPause()
        disableNfc()
    }

    override fun onResume() {
        super.onResume()
        if (!paid) enableNfc()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        successPlayer?.release()
        failPlayer?.release()
    }
}
