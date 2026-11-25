package ru.yumani.terminal

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class PaymentActivity : AppCompatActivity() {

    private var amount = 0
    private var code = ""
    private var successPlayer: MediaPlayer? = null
    private var failPlayer: MediaPlayer? = null

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                findViewById<TextView>(R.id.tvStatus).text = "улыбка найдена, идёт оплата…"
                window.decorView.postDelayed({ onPaymentResult(true) }, 1500)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_payment)

        amount = intent.getIntExtra("amount", 0)
        code = intent.getStringExtra("code") ?: ""

        findViewById<TextView>(R.id.tvAmount).text = "$amount ₽"
        findViewById<TextView>(R.id.tvTerminal).text = "Терминал: $code"

        successPlayer = MediaPlayer.create(this, R.raw.success)
        failPlayer = MediaPlayer.create(this, R.raw.fail)

        val payload = "yumani://pay?terminal=" + code + "&amount=" + amount
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
        }

        findViewById<Button>(R.id.btnSmile).setOnClickListener {
            findViewById<TextView>(R.id.tvStatus).text = "смотрю в камеру…"
            try {
                cameraLauncher.launch(null)
            } catch (e: Exception) {
                findViewById<TextView>(R.id.tvStatus).text = "камера недоступна, оплатите по QR"
            }
        }
    }

    fun onPaymentResult(success: Boolean) {
        if (success) {
            successPlayer?.start()
            findViewById<TextView>(R.id.tvStatus).text = "оплата прошла"
            sendResult(true)
        } else {
            failPlayer?.start()
            findViewById<TextView>(R.id.tvStatus).text = "оплата не прошла"
            sendResult(false)
        }
    }

    private fun sendResult(ok: Boolean) {
        val addr = MainActivity.lastPcAddress ?: return
        Thread {
            try {
                val socket = DatagramSocket()
                val data = if (ok) "RESULT OK" else "RESULT FAIL"
                val bytes = data.toByteArray()
                socket.send(
                    DatagramPacket(bytes, bytes.size, InetAddress.getByName(addr), 45557)
                )
                socket.close()
            } catch (e: Exception) {
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // блокируем выход во время оплаты
    }

    override fun onDestroy() {
        super.onDestroy()
        successPlayer?.release()
        failPlayer?.release()
    }
}
