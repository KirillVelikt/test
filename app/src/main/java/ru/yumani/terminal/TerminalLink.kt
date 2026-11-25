package ru.yumani.terminal

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

object TerminalLink {

    const val BEACON_PORT = 45555
    const val COMMAND_PORT = 45557
    const val RESULT_PORT = 45558

    var code: String = "000000"
    var terminalName: String = "терминал"
    var soundsOn: Boolean = true

    private var running = false
    private var socket: DatagramSocket? = null

    @Volatile
    private var lastAddress: InetAddress? = null

    @Volatile
    private var lastPort: Int = 0

    private val main = Handler(Looper.getMainLooper())

    fun start(onCommand: (Int) -> Unit) {
        if (running) return
        running = true
        thread(name = "terminal-link") {
            try {
                val s = DatagramSocket(COMMAND_PORT)
                socket = s
                val buf = ByteArray(2048)
                while (running) {
                    val packet = DatagramPacket(buf, buf.size)
                    s.receive(packet)
                    val text = String(packet.data, 0, packet.length)
                    try {
                        val json = JSONObject(text)
                        if (json.optString("cmd") == "pay") {
                            lastAddress = packet.address
                            lastPort = packet.port
                            val amount = json.optInt("amount", 0)
                            if (amount > 0) {
                                main.post { onCommand(amount) }
                            }
                        }
                    } catch (e: Exception) {
                        // мусорный пакет - игнорируем
                    }
                }
            } catch (e: Exception) {
                running = false
            }
        }
    }

    fun stop() {
        running = false
        try {
            socket?.close()
        } catch (e: Exception) {
        }
        socket = null
    }

    fun sendBeacon() {
        thread(name = "beacon") {
            try {
                val json = JSONObject()
                json.put("code", code)
                json.put("name", terminalName)
                json.put("sounds", soundsOn)
                val data = json.toString().toByteArray(Charsets.UTF_8)
                val addr = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(data, data.size, addr, BEACON_PORT)
                val s = DatagramSocket()
                s.broadcast = true
                s.send(packet)
                s.close()
            } catch (e: Exception) {
                // нет сети - маячок пропустится
            }
        }
    }

    fun sendResult(success: Boolean) {
        thread(name = "result") {
            val addr = lastAddress ?: return@thread
            try {
                val json = JSONObject()
                json.put("code", code)
                json.put("result", if (success) "ok" else "fail")
                val data = json.toString().toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(data, data.size, addr, RESULT_PORT)
                val s = DatagramSocket()
                s.send(packet)
                s.close()
            } catch (e: Exception) {
            }
        }
    }
}
