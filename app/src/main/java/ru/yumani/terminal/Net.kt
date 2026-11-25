package ru.yumani.terminal

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/** Сеть терминала: маячок на 45555, приём оплаты на 45557, ответ пульту. */
object Net {
    const val BEACON_PORT = 45555
    const val CMD_PORT = 45557

    @Volatile var lastSender: InetSocketAddress? = null
    private val cmdQueue = ArrayDeque<Pair<Int, InetSocketAddress>>()

    fun broadcastBeacon(code: String, name: String) {
        Thread {
            val sock = DatagramSocket()
            sock.broadcast = true
            val data = "YUMANI_BEACON|$code|$name".toByteArray()
            val packet = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), BEACON_PORT)
            while (true) {
                try { sock.send(packet) } catch (_: Exception) {}
                try { Thread.sleep(2000) } catch (_: InterruptedException) { return@Thread }
            }
        }.start()
    }

    fun listenCommands() {
        Thread {
            val sock = DatagramSocket(CMD_PORT)
            val buf = ByteArray(2048)
            while (true) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    sock.receive(packet)
                } catch (_: Exception) { continue }
                val text = String(packet.data, 0, packet.length)
                if (text.startsWith("PAY|")) {
                    val amount = text.substringAfter("PAY|").trim().toIntOrNull() ?: continue
                    synchronized(cmdQueue) {
                        cmdQueue.addLast(amount to InetSocketAddress(packet.address, packet.port))
                    }
                }
            }
        }.start()
    }

    fun pollCommand(): Pair<Int, InetSocketAddress>? {
        synchronized(cmdQueue) { return cmdQueue.removeFirstOrNull() }
    }

    fun sendResult(target: InetSocketAddress?, ok: Boolean, amount: Int) {
        if (target == null) return
        Thread {
            try {
                val sock = DatagramSocket()
                val data = ("RESULT|" + (if (ok) "ok" else "fail") + "|" + amount).toByteArray()
                sock.send(DatagramPacket(data, data.size, target.address, target.port))
                sock.close()
            } catch (_: Exception) {}
        }.start()
    }
}
