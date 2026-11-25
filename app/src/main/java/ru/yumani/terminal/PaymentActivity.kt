package ru.yumani.terminal

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class PaymentActivity : AppCompatActivity() {

    private var amount = 0
    private var code = ""
    private var paid = false
    private var successPlayer: MediaPlayer? = null
    private var failPlayer: MediaPlayer? = null
    private var nfcAdapter: NfcAdapter? = null
    private val settings by lazy { getSharedPreferences("terminal_settings", Context.MODE_PRIVATE) }

    private val readerCallback = NfcAdapter.ReaderCallback { tag -> onTagDiscovered(tag) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (settings.getBoolean("screen_on", true)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        setContentView(R.layout.activity_payment)

        amount = intent.getIntExtra("amount", 0)
        code = intent.getStringExtra("code") ?: ""

        findViewById<TextView>(R.id.tvAmount).text = "$amount ₽"
        findViewById<TextView>(R.id.tvTerminal).text = "Терминал: $code"

        if (settings.getBoolean("sound", true)) {
            successPlayer = MediaPlayer.create(this, R.raw.success)
            failPlayer = MediaPlayer.create(this, R.raw.fail)
        }

        setupNfc()
        showQr()

        findViewById<View>(R.id.btnSmile).setOnClickListener {
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), 777)
            } catch (e: Exception) {
                showSuccess("Smile ** DEMO", false)
            }
        }
    }

    // ---------- QR ----------

    private fun showQr() {
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
    }

    // ---------- NFC ----------

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            findViewById<TextView>(R.id.tvStatus).text = "NFC недоступен — оплатите по QR"
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            nfcAdapter?.enableReaderMode(
                this, readerCallback,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null
            )
        } catch (e: Exception) { /* NFC нет — не мешаем QR */ }
    }

    override fun onPause() {
        try { nfcAdapter?.disableReaderMode(this) } catch (e: Exception) {}
        super.onPause()
    }

    private fun onTagDiscovered(tag: Tag?) {
        runOnUiThread { findViewById<TextView>(R.id.tvStatus).text = "карта приложена…" }
        val card = readCard(tag)
        runOnUiThread {
            if (card == null) {
                // карта не считалась или не опознана — антифишинг: оплату не пропускаем
                reject("операция отклонена")
            } else {
                showSuccess(card, true)
            }
        }
    }

    /** Возвращает строку вида "Visa ** 4A1B" или null, если карта не опознана */
    private fun readCard(tag: Tag?): String? {
        val dep = IsoDep.get(tag) ?: return null
        return try {
            dep.connect()
            dep.timeout = 3000
            // SELECT PPSE "2PAY.SYS.DDF01"
            val ppse = byteArrayOf(
                0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E,
                0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31,
                0x00
            )
            val resp = dep.transceive(ppse) ?: return null
            val hex = resp.joinToString("") { String.format("%02X", it) }
            val scheme = when {
                hex.contains("A000000003") -> "Visa"
                hex.contains("A000000004") -> "Mastercard"
                hex.contains("A000000658") -> "МИР"
                else -> ""
            }
            if (scheme.isEmpty()) return null
            val uid = tag?.id ?: return null
            val tail = uid.takeLast(2).joinToString("") { String.format("%02X", it) }
            "$scheme ** $tail"
        } catch (e: Exception) {
            null
        } finally {
            try { dep.close() } catch (e: Exception) {}
        }
    }

    // ---------- Результат ----------

    private fun showSuccess(maskedCard: String, notify: Boolean) {
        if (paid) return
        paid = true
        successPlayer?.start()
        vibrate(longArrayOf(0, 120, 80, 160))
        findViewById<TextView>(R.id.tvStatus).text = "оплата прошла ✓"

        val overlay = findViewById<View>(R.id.layoutSuccess)
        overlay.visibility = View.VISIBLE
        val circle = overlay.findViewById<View>(R.id.ivCircle)
        circle.scaleX = 0f
        circle.scaleY = 0f
        circle.animate().scaleX(1f).scaleY(1f).setDuration(450)
            .setInterpolator(OvershootInterpolator(3f)).start()

        if (notify) sendResult(true, maskedCard)
        window.decorView.postDelayed({ finish() }, 2600)
    }

    private fun reject(reason: String) {
        if (paid) return
        paid = true
        failPlayer?.start()
        vibrate(longArrayOf(0, 60, 60, 60, 60, 200))
        val status = findViewById<TextView>(R.id.tvStatus)
        status.text = reason
        status.setTextColor(0xFFEF4444.toInt())
        findViewById<View>(R.id.layoutReject).visibility = View.VISIBLE

        sendResult(false, "")
        window.decorView.postDelayed({ finish() }, 2400)
    }

    /** Вызывается внешним обработчиком сети, когда оплата пришла с пульта */
    fun onPaymentResult(success: Boolean) {
        runOnUiThread {
            if (success) showSuccess("Visa ** REMOTE", false)
            else reject("операция отклонена")
        }
    }

    private fun sendResult(success: Boolean, card: String) {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val payload = JSONObject().apply {
                    put("type", "result")
                    put("success", success)
                    put("card", card)
                    put("amount", amount)
                }.toString().toByteArray(Charsets.UTF_8)
                val addr = InetAddress.getByName("255.255.255.255")
                socket.send(DatagramPacket(payload, payload.size, addr, 45557))
            }
        } catch (e: Exception) { /* пульт не в сети — не падаем */ }
    }

    private fun vibrate(pattern: LongArray) {
        if (!settings.getBoolean("vibration", true)) return
        val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vib?.hasVibrator() != true) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, -1)
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 777) showSuccess("Smile ** DEMO", false)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!paid) return
    }

    override fun onDestroy() {
        super.onDestroy()
        successPlayer?.release()
        failPlayer?.release()
    }
}
