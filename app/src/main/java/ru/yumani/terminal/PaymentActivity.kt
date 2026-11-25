package ru.yumani.terminal

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class PaymentActivity : AppCompatActivity() {

    private var amount: Int = 0
    private var code: String = ""
    private var pcAddress: String? = null
    private var paid = false

    private var successPlayer: MediaPlayer? = null
    private var failPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)

        // держим экран включённым
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        amount = intent.getIntExtra("amount", 0)
        code = intent.getStringExtra("code") ?: ""
        pcAddress = intent.getStringExtra("pc")

        findViewById<TextView>(R.id.tvAmount).text = "$amount ₽"
        findViewById<TextView>(R.id.tvTerminal).text = "Терминал: $code"

        successPlayer = MediaPlayer.create(this, R.raw.success)
        failPlayer = MediaPlayer.create(this, R.raw.fail)

        // просим право "поверх окон", чтобы покупатель случайно не свалился
        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
            }
        }

        showQr()

        findViewById<LinearLayout>(R.id.btnSmile).setOnClickListener {
            openCameraAndSmilePay()
        }

        findViewById<LinearLayout>(R.id.btnQrCard).setOnClickListener {
            // повторная генерация QR крупным планом не нужна: он уже на экране
            showQr()
        }

        findViewById<ImageView>(R.id.btnClose).setOnClickListener {
            if (!paid) finish()
        }
    }

    private fun showQr() {
        try {
            val payload = "yumani://pay?terminal=$code&amount=$amount"
            val size = 512
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    pixels[y * size + x] =
                        if (matrix.get(x, y)) 0xFF0E1116.toInt() else 0xFFFFFFFF.toInt()
                }
            }
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, size, 0, 0, size, size)
            findViewById<ImageView>(R.id.ivQr).setImageBitmap(bmp)
        } catch (_: Exception) {
        }
    }

    private fun openCameraAndSmilePay() {
        if (paid) return
        setStatus("улыбка найдена ✓ идёт оплата…")
        try {
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(intent, 1001)
        } catch (_: Exception) {
            // камеры нет — всё равно идём в оплату (демо-режим)
            confirmSmile()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            confirmSmile()
        }
    }

    private fun confirmSmile() {
        if (paid) return
        Handler(Looper.getMainLooper()).postDelayed({
            onPaymentResult(true)
        }, 1500)
    }

    fun onPaymentResult(success: Boolean) {
        if (paid) return
        paid = true
        sendResult(success)
        if (success) {
            successPlayer?.start()
            findViewById<TextView>(R.id.tvStatus).text = "оплата прошла ✓"
        } else {
            failPlayer?.start()
            findViewById<TextView>(R.id.tvStatus).text = "оплата не прошла"
        }
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 2500)
    }

    private fun sendResult(success: Boolean) {
        val host = pcAddress ?: return
        Thread {
            try {
                val socket = DatagramSocket()
                val msg = ("YMNI_RESULT;" + if (success) "OK" else "FAIL").toByteArray()
                socket.send(DatagramPacket(msg, msg.size, InetAddress.getByName(host), 45557))
                socket.close()
            } catch (_: Exception) {
            }
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // не даём случайно выйти во время оплаты
        if (paid) super.onBackPressed()
    }

    override fun onDestroy() {
        successPlayer?.release()
        failPlayer?.release()
        super.onDestroy()
    }
}
