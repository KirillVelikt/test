package ru.yumani.terminal

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object Net {
    private const val BEACON_PORT = 45555
    private const val CMD_PORT = 45556
    private var socket: DatagramSocket? = null
    private var pcAddr: InetAddress? = null
    private var pcPort = 45557
    private val handler = Handler(Looper.getMainLooper())
    private var started = false

    fun code(): String = "246813"

    fun start(context: Context) {
        if (started) return
        started = true
        val s = DatagramSocket(CMD_PORT)
        socket = s

        Thread {
            val buf = ByteArray(8192)
            while (true) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    pcAddr = p.address
                    pcPort = p.port
                    val text = String(p.data, 0, p.length)
                    val obj = JSONObject(text)
                    when (obj.optString("type")) {
                        "connect" -> {
                            val from = p.address.hostAddress ?: ""
                            handler.post { Bus.listener?.onConnect(from) }
                        }
                        "order" -> {
                            handler.post { Bus.listener?.onOrder(text) }
                        }
                    }
                } catch (e: Exception) {
                }
            }
        }.start()

        Thread {
            val beacon = JSONObject()
                .put("type", "beacon")
                .put("code", code())
                .put("name", Prefs.name(context))
                .toString().toByteArray()
            while (true) {
                try {
                    val addr = InetAddress.getByName("255.255.255.255")
                    s.send(DatagramPacket(beacon, beacon.size, addr, BEACON_PORT))
                } catch (e: Exception) {
                }
                try { Thread.sleep(1000) } catch (e: InterruptedException) { }
            }
        }.start()
    }

    private fun send(obj: JSONObject) {
        try {
            val a = pcAddr ?: return
            val data = obj.toString().toByteArray()
            socket?.send(DatagramPacket(data, data.size, a, pcPort))
        } catch (e: Exception) {
        }
    }

    fun sendResult(success: Boolean) {
        send(JSONObject().put("type", "payment_result").put("success", success))
    }

    fun sendCard(scheme: String, masked: String) {
        send(JSONObject().put("type", "card").put("scheme", scheme).put("masked", masked))
    }
}
