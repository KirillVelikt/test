package ru.yumani.terminal

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private var lastSender: InetAddress? = null
    private var serverRunning = true
    private var connectPlayer: MediaPlayer? = null
    private var startPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val code = (intent?.getStringExtra("code") ?: "").ifEmpty {
            (100000..999999).random().toString()
        }
        findViewById<TextView>(R.id.tvCode).text = code
        findViewById<TextView>(R.id.tvTerminal).text = "Терминал: $code"
        setStatus("ожидание пульта…")

        startPlayer = MediaPlayer.create(this, R.raw.start)
        connectPlayer = MediaPlayer.create(this, R.raw.connect)
        startPlayer?.start()

        Thread { udpLoop(code) }.start()
    }

    private fun setStatus(text: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.tvStatus).text = text
        }
    }

    private fun udpLoop(code: String) {
        try {
            val socket = DatagramSocket(45555)
            socket.broadcast = true
            val buf = ByteArray(2048)
            while (serverRunning) {
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                val msg = String(packet.data, 0, packet.length).trim()
                val addr = packet.address
                if (msg.startsWith("YMNI_DISCOVER")) {
                    val resp = "YMNI;$code".toByteArray()
                    socket.send(DatagramPacket(resp, resp.size, addr, 45557))
                } else if (msg.startsWith("YMNI_PAY")) {
                    val parts = msg.split(";")
                    val amount = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    lastSender = addr
                    runOnUiThread {
                        setStatus("подключено ✓")
                        connectPlayer?.start()
                        val i = Intent(this, PaymentActivity::class.java)
                        i.putExtra("amount", amount)
                        i.putExtra("code", code)
                        i.putExtra("pc", addr.hostAddress)
                        startActivity(i)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        serverRunning = false
        connectPlayer?.release()
        startPlayer?.release()
        super.onDestroy()
    }
}
