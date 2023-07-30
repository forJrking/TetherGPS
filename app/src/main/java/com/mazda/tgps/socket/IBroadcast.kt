package com.mazda.tgps.socket

import androidx.annotation.WorkerThread
import java.net.SocketAddress

// 组播地址
//const val MULTI_BROADCAST_IP = "224.9.9.9"
const val TIME_TO_LIVE = 2000L
const val BROADCAST_PORT = 3725
const val UNI_BROADCAST_IP = "255.255.255.255"
const val BUFFER_SIZE = 1024

interface IBroadcast {
    fun init(port: Int)

    @WorkerThread
    fun send(msg: String, address: SocketAddress)

    @WorkerThread
    fun <T> receive(
        decode: (String) -> T?,
        onError: (Exception) -> Unit,
        block: (T, SocketAddress) -> Unit
    )

    fun close(block: () -> Unit = {})
}
