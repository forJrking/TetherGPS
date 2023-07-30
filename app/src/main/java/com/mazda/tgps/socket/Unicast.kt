package com.mazda.tgps.socket

import android.os.Build
import com.mazda.tgps.utils.Utils.logger
import java.net.InetSocketAddress
import java.net.PortUnreachableException
import java.net.SocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

class Unicast : IBroadcast {

    private var udpChannel: DatagramChannel? = null

    private val byteBuffer = ByteBuffer.allocate(BUFFER_SIZE)

    private val lock = Object()

    @Throws(SocketException::class)
    override fun init(port: Int) {
        synchronized(lock) {
            if (udpChannel?.isOpen == false) {
                udpChannel?.use {
                    logger("udpChannel reset")
                }
            }
            udpChannel = DatagramChannel.open().apply {
                configureBlocking(false)
                socket().broadcast = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    bind(InetSocketAddress(port))
                } else {
                    socket().bind(InetSocketAddress(port))
                }
            }
        }
    }

    override fun send(msg: String, address: SocketAddress) {
        synchronized(lock) {
            try {
                byteBuffer.put(msg.toByteArray())
                byteBuffer.flip()
                udpChannel?.send(byteBuffer, address)
            } catch (e: SocketException) {
                logger("sendPacket SocketException")
            } catch (e: Exception) {
                logger("sendPacket Exception $e")
            } finally {
                byteBuffer.clear()
            }
        }
    }

    override fun <T> receive(
        decode: (String) -> T?,
        onError: (Exception) -> Unit,
        block: (T, SocketAddress) -> Unit,
    ) {
        synchronized(lock) {
            try {
                val address = udpChannel?.receive(byteBuffer)
                byteBuffer.flip()
                if (address != null) {
                    decode(String(byteBuffer.array(), 0, byteBuffer.limit()))?.run {
                        byteBuffer.clear()
                        block(this, address)
                    } ?: throw IllegalArgumentException("decode is null")
                } else {
                    //issue BufferOverflowException
                    byteBuffer.clear()
                    lock.wait(TIME_TO_LIVE)
                    throw PortUnreachableException("address is null")
                }
            } catch (e: Exception) {
                byteBuffer.clear()
                onError(e)
            }
        }
    }

    override fun close(block: () -> Unit) {
        synchronized(lock) {
            udpChannel.use {
                lock.notifyAll()
                block()
                logger("udpChannel close")
            }
            udpChannel = null
        }
    }
}