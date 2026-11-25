package ru.yumani.terminal

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MainActivity : AppCompatActivity() {

    companion object {
        var lastPcAddress: String? = null
        var terminalCode: String = ""
    }

    private var jinglePlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        terminalCode = (100000..999999).random().toString()
        findViewById<TextView>(R.id.tvCode).text = terminalCode
        findViewById<TextView>(R.id.tvStatus).text = "ожидание пульта…"

        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
            }
        }

        try {
            jinglePlayer = MediaPlayer.create(this, R.raw.jingle)
            jinglePlayer?.start()
        } catch (e: Exception) {
        }

        startBeacon()
        startListener()

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            startActivity(
                Intent(this, PaymentActivity::class.java)
                    .putExtra("amount", 198)
                    .putExtra("code", terminalCode)
            )
        }
    }

    private fun startBeacon() {
        Thread {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                val addr = InetAddress.getByName("255.255.255.255")
                while (true) {
                    val data = "YUMANI|$terminalCode".toByteArray()
                    socket.send(DatagramPacket(data, data.size, addr, 45555))
                    Thread.sleep(2000)
                }
            } catch (e: Exception) {
            }
        }.start()
    }

    private fun startListener() {
        Thread {
            try {
                val socket = DatagramSocket(45557)
                val buf = ByteArray(256)
                while (true) {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val msg = String(packet.data, 0, packet.length).trim()
                    if (msg.startsWith("PAY")) {
                        lastPcAddress = packet.address.hostAddress
                        val amount = msg.split(" ").getOrNull(1)?.toIntOrNull() ?: 0
                        runOnUiThread {
                            startActivity(
                                Intent(this, PaymentActivity::class.java)
                                    .putExtra("amount", amount)
                                    .putExtra("code", terminalCode)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        jinglePlayer?.release()
    }
}
