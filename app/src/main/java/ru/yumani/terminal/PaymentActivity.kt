package ru.yumani.terminal

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class PaymentActivity : AppCompatActivity() {

    private var amount = 0
    private var code = ""
    private var successPlayer: MediaPlayer? = null
    private var failPlayer: MediaPlayer? = null
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        amount = intent.getIntExtra("amount", 0)
        code = intent.getStringExtra("code") ?: ""

        findViewById<TextView>(R.id.tvAmount).text = "$amount ₽"
        findViewById<TextView>(R.id.tvTerminal).text = "Терминал: $code"
        showQr("yumani://pay?terminal=$code&amount=$amount")

        successPlayer = MediaPlayer.create(this, R.raw.success)
        failPlayer = MediaPlayer.create(this, R.raw.fail)

        // право "поверх других окон" — чтобы случайно не свернуть оплату
        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                // настройка недоступна на этом устройстве — работаем без неё
            }
        }

        findViewById<View>(R.id.cardSmile).setOnClickListener {
            findViewById<TextView>(R.id.tvStatus).text = "улыбнись в камеру :)"
            try {
                startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), 42)
            } catch (e: Exception) {
                // камеры нет — подтверждаем оплату сразу
                thread { Thread.sleep(800); finishPayment(true) }
            }
        }

        findViewById<View>(R.id.btnClose).setOnClickListener {
            finishPayment(false)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42) {
            findViewById<TextView>(R.id.tvStatus).text = "улыбка найдена ✓ идёт оплата…"
            thread {
                Thread.sleep(1500)
                finishPayment(true)
            }
        }
    }

    private fun showQr(text: String) {
        try {
            val size = 512
            val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                val row = bits.getRow(y, BooleanArray(size))
                for (x in 0 until size) {
                    pixels[y * size + x] = if (row[x]) Color.BLACK else Color.WHITE
                }
            }
            bmp.setPixels(pixels, 0, size, 0, 0, size, size)
            findViewById<ImageView>(R.id.ivQr).setImageBitmap(bmp)
        } catch (e: Exception) {
            // QR не критичен для работы
        }
    }

    private fun finishPayment(ok: Boolean) {
        if (finished) return
        finished = true
        thread { sendResult(if (ok) "RESULT:OK" else "RESULT:FAIL") }
        runOnUiThread {
            (if (ok) successPlayer else failPlayer)?.start()
            findViewById<TextView>(R.id.tvStatus).text =
                if (ok) "оплата прошла ✓" else "оплата не прошла"
        }
        thread {
            Thread.sleep(2500)
            runOnUiThread { finish() }
        }
    }

    private fun sendResult(msg: String) {
        try {
            DatagramSocket().use { s ->
                val bytes = "$msg:$code".toByteArray()
                s.broadcast = true
                s.send(
                    DatagramPacket(
                        bytes, bytes.size,
                        InetAddress.getByName("255.255.255.255"), 45557
                    )
                )
            }
        } catch (e: Exception) {
            // не удалось отправить — пульт узнает при следующем опросе
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // во время оплаты выход блокируем — оплата завершится сама
    }

    override fun onDestroy() {
        super.onDestroy()
        successPlayer?.release()
        failPlayer?.release()
    }
}
