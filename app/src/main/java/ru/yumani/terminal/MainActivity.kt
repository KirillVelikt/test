package ru.yumani.terminal

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    companion object {
        const val BEACON_PORT = 45555
    }

    var code = ""
    private var tickPlayer: MediaPlayer? = null
    private var bootPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bootPlayer = MediaPlayer.create(this, R.raw.boot)
        tickPlayer = MediaPlayer.create(this, R.raw.tick)
        bootPlayer?.start()

        code = (10000000..99999999).random().toString()
        findViewById<TextView>(R.id.tvCode).text = code
        findViewById<TextView>(R.id.tvTerminal).text = "Терминал: $code"
        findViewById<TextView>(R.id.tvStatus).text = "ожидание пульта…"

        thread(name = "beacon-listener") { listenBeacons() }
    }

    private fun setStatus(s: String) {
        runOnUiThread {
            findViewById<TextView>(R.id.tvStatus).text = s
        }
    }

    private fun listenBeacons() {
        try {
            DatagramSocket(BEACON_PORT).use { socket ->
                val buf = ByteArray(256)
                val packet = DatagramPacket(buf, buf.size)
                while (true) {
                    socket.receive(packet)
                    val msg = String(packet.data, 0, packet.length).trim()
                    if (msg == "PAIR:$code") {
                        val ack = "ACK:$code".toByteArray()
                        socket.send(DatagramPacket(ack, ack.size, packet.address, packet.port))
                        setStatus("подключено ✓")
                        tickPlayer?.start()
                    } else if (msg.startsWith("PAY:")) {
                        val amount = msg.removePrefix("PAY:").toIntOrNull() ?: continue
                        val i = Intent(this, PaymentActivity::class.java)
                        i.putExtra("amount", amount)
                        i.putExtra("code", code)
                        startActivity(i)
                    }
                }
            }
        } catch (e: Exception) {
            // активность закрыта — выходим тихо
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bootPlayer?.release()
        tickPlayer?.release()
    }
}
