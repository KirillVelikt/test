package ru.yumani.terminal

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.nfc.NfcAdapter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
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
    private var paid = false
    private var successPlayer: MediaPlayer? = null
    private var failPlayer: MediaPlayer? = null
    private var nfcAdapter: NfcAdapter? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("yumani", MODE_PRIVATE)
        if (prefs.getBoolean("keep", true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        setContentView(R.layout.activity_payment)

        amount = intent.getIntExtra("amount", 0)
        code = intent.getStringExtra("code") ?: ""

        findViewById<TextView>(R.id.tvAmount).text = "$amount ₽"
        findViewById<TextView>(R.id.tvTerminal).text = "Терминал: $code"

        if (prefs.getBoolean("sound", true)) {
            successPlayer = MediaPlayer.create(this, R.raw.success)
            failPlayer = MediaPlayer.create(this, R.raw.fail)
        }

        makeQr()

        findViewById<View>(R.id.btnSmile).setOnClickListener { openSmileCamera() }
        findViewById<View>(R.id.btnClose).setOnClickListener {
            if (paid) finish() else finishWithResult(false)
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        try {
            nfcAdapter?.enableReaderMode(this, { finishWithResult(true) },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B, null)
        } catch (_: Exception) {}
    }

    private fun makeQr() {
        try {
            val payload = "yumani://pay?terminal=$code&amount=$amount"
            val size = 512
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix[x, y]) 0xFF1A1A1D.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            findViewById<ImageView>(R.id.ivQr).setImageBitmap(bmp)
        } catch (_: Exception) {}
    }

    private fun openSmileCamera() {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).let { Intent(it) }, 7)
        } catch (_: Exception) {
            processSmile()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7) processSmile()
    }

    private fun processSmile() {
        if (paid) return
        findViewById<TextView>(R.id.tvPayStatus).text = "улыбка найдена · идёт оплата…"
        handler.postDelayed({ finishWithResult(true) }, 1500)
    }

    @Suppress("DEPRECATION")
    private fun finishWithResult(ok: Boolean) {
        if (paid) return
        paid = true
        val prefs = getSharedPreferences("yumani", MODE_PRIVATE)
        if (ok) {
            successPlayer?.start()
            if (prefs.getBoolean("vibrate", true)) {
                (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.vibrate(120)
            }
            Net.sendResult(Net.lastSender, true, amount)
            setResult(RESULT_OK)
            // кружок успеха с анимацией
            findViewById<View>(R.id.successLayer).visibility = View.VISIBLE
            val circle = findViewById<View>(R.id.successCircle)
            circle.scaleX = 0f
            circle.scaleY = 0f
            circle.alpha = 0f
            circle.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(450).setInterpolator(OvershootInterpolator(2f)).start()
            findViewById<View>(R.id.successCheck).animate()
                .alpha(1f).setStartDelay(300).setDuration(250).start()
            findViewById<TextView>(R.id.successText).animate()
                .alpha(1f).setStartDelay(450).setDuration(300).start()
            handler.postDelayed({ finish() }, 2600)
        } else {
            failPlayer?.start()
            Net.sendResult(Net.lastSender, false, amount)
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (paid) super.onBackPressed()
        // во время оплаты выход заблокирован
    }

    override fun onDestroy() {
        super.onDestroy()
        try { nfcAdapter?.disableReaderMode(this) } catch (_: Exception) {}
        successPlayer?.release()
        failPlayer?.release()
        handler.removeCallbacksAndMessages(null)
    }
}
