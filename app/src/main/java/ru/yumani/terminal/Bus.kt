package ru.yumani.terminal

interface TerminalListener {
    fun onConnect(from: String) {}
    fun onOrder(json: String) {}
    fun onPayResult(success: Boolean) {}
}

object Bus {
    var listener: TerminalListener? = null
}
