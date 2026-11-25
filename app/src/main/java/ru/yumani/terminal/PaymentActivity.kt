package ru.yumani.terminal

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class PaymentActivity : AppCompatActivity(), TerminalListener {

    private var amount = 0
    private var successPlayer: MediaPlayer? = null
    private var failPlayer: MediaPlayer? = null
    private var nfc: NfcAdapter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var done = false

    companion object {
        // известные платёжные AID — база антифишинг-проверки
        private val AIDS = mapOf(
            "A000000003" to "Visa",
            "A000000004" to "Mastercard",
            "A000000331" to "МИР"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.keepScreen(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        setContentView(R.layout.activity_payment)

        amount = intent.getIntExtra("amount", 0)
        findViewById<TextView>(R.id.tvAmount).text = amount.toString() + " ₽"
        findViewById<TextView>(R.id.tvTerminal).text = "терминал: " + Prefs.name(this)

        successPlayer = MediaPlayer.create(this, R.raw.success)
        failPlayer = MediaPlayer.create(this, R.raw.fail)

        // QR-код
        val size = 512
        val matrix = QRCodeWriter().encode(
            "yumani://pay?amount=" + amount, BarcodeFormat.QR_CODE, size, size
        )
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                val color = if (matrix[x, y]) 0xFF22C55E.toInt() else 0xFF17171A.toInt()
                bmp.setPixel(x, y, color)
            }
        }
        findViewById<ImageView>(R.id.ivQr).setImageBitmap(bmp)

        findViewById<LinearLayout>(R.id.cardSmile).setOnClickListener { startSmile() }
        findViewById<LinearLayout>(R.id.cardNfc).setOnClickListener { prepareNfc() }

        Bus.listener = this
        nfc = NfcAdapter.getDefaultAdapter(this)
    }

    private fun status(text: String, colorRes: Int) {
        val tv = findViewById<TextView>(R.id.tvStatus)
        tv.text = text
        tv.setTextColor(getColor(colorRes))
    }

    private fun startSmile() {
        status("ищем улыбку…", R.color.gray)
        handler.postDelayed({
            if (!done) succeed()
        }, 1500)
    }

    private fun prepareNfc() {
        status("приложите карту к телефону", R.color.gray)
        if (nfc == null) {
            decline("NFC недоступен на этом телефоне")
            return
        }
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, PaymentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        val filter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        nfc?.enableForegroundDispatch(this, pi, arrayOf(filter), arrayOf(arrayOf(IsoDep::class.java.name)))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag != null) readCard(tag)
    }

    private fun readCard(tag: Tag) {
        status("считываем карту…", R.color.gray)
        Thread {
            var scheme = ""
            try {
                val dep = IsoDep.get(tag)
                if (dep == null) {
                    throw IllegalStateException("нет IsoDep")
                }
                dep.connect()
                dep.timeout = 3000
                // SELECT PPSE — карта обязана ответить 9000
                val ppse = byteArrayOf(
                    0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E,
                    0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53,
                    0x2E, 0x44, 0x44, 0x46, 0x30, 0x31, 0x00
                )
                val r1 = dep.transceive(ppse)
                if (r1.size < 2 || r1[r1.size - 2] != 0x90.toByte() || r1[r1.size - 1] != 0x00.toByte()) {
                    throw IllegalStateException("PPSE отклонён")
                }
                // антифишинг: ищем известный платёжный AID
                for ((aid, name) in AIDS) {
                    val aidBytes = aid.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    val apdu = ByteArray(5)
                    apdu[0] = 0x00
                    apdu[1] = 0xA4.toByte()
                    apdu[2] = 0x04
                    apdu[3] = 0x00
                    apdu[4] = aidBytes.size.toByte()
                    val full = apdu + aidBytes + 0x00.toByte()
                    val r = dep.transceive(full)
                    if (r.size >= 2 && r[r.size - 2] == 0x90.toByte() && r[r.size - 1] == 0x00.toByte()) {
                        scheme = name
                        break
                    }
                }
                dep.close()
            } catch (e: Exception) {
                scheme = ""
            }
            handler.post {
                if (scheme.isEmpty()) {
                    // карта не считалась или не опознана — операция отклонена
                    decline("операция отклонена: карта не опознана")
                } else {
                    val uid = tag.id
                    val okLen = uid != null && (uid.size == 4 || uid.size == 7 || uid.size == 10)
                    if (!okLen) {
                        decline("операция отклонена: карта не прошла проверку")
                    } else {
                        val last = uid.takeLast(2).joinToString("") { "%02X".format(it) }
                        Net.sendCard(scheme, "** $last")
                        succeed()
                    }
                }
            }
        }.start()
    }

    private fun decline(msg: String) {
        status(msg, R.color.red)
        if (Prefs.sound(this)) failPlayer?.start()
        if (Prefs.vibrate(this)) vibrate()
        Net.sendResult(false)
    }

    private fun succeed() {
        if (done) return
        done = true
        if (Prefs.sound(this)) successPlayer?.start()
        if (Prefs.vibrate(this)) vibrate()
        Net.sendResult(true)
        try { nfc?.disableForegroundDispatch() } catch (e: Exception) { }
        handler.postDelayed({
            startActivity(Intent(this, SuccessActivity::class.java))
            finish()
        }, 400)
    }

    private fun vibrate() {
        @Suppress("DEPRECATION")
        val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(120)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try { nfc?.disableForegroundDispatch() } catch (e: Exception) { }
    }

    override fun onConnect(from: String) {
    }

    override fun onOrder(json: String) {
    }

    override fun onPayResult(success: Boolean) {
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Bus.listener === this) Bus.listener = null
        successPlayer?.release()
        failPlayer?.release()
        handler.removeCallbacksAndMessages(null)
    }
}
