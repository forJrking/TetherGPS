package com.mazda.tgps.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.mazda.tgps.R
import com.mazda.tgps.socket.BROADCAST_PORT
import com.mazda.tgps.socket.IBroadcast
import com.mazda.tgps.socket.Unicast
import com.mazda.tgps.utils.Utils.checkAddressPort
import com.mazda.tgps.utils.Utils.decodeIp
import com.mazda.tgps.utils.Utils.decodeLocation
import com.mazda.tgps.utils.Utils.encodeIp
import com.mazda.tgps.utils.Utils.encodeLocation
import com.mazda.tgps.utils.Utils.getIpAddressByWifi
import com.mazda.tgps.utils.Utils.launchForegroundWithNotification
import com.mazda.tgps.utils.Utils.logger
import com.mazda.tgps.utils.Utils.mockGPSLocation
import com.mazda.tgps.utils.getAutomaticBroadcastIp
import java.lang.ref.WeakReference
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

const val NOTIFICATION_CHANNEL_ID = "T-GPS"
const val NOTIFICATION_ID_SERVER = 10
private const val WHAT_CLIENT = 1
private const val WHAT_SERVER = 2

class LocationService : Service(), LocationListener {

    private lateinit var socket: IBroadcast

    private val isAutomaticIp = AtomicBoolean(false)

    private val isServerRunning = AtomicBoolean(false)

    private val isClientRunning = AtomicBoolean(false)

    private var locationManager: LocationManager? = null

    private var receiveListener = AtomicReference<ReceiveLocationListener>(null)

    @Volatile
    private var sendLooper: Looper? = null

    @Volatile
    private var sendServiceHandler: SendServiceHandler? = null

    @Volatile
    private var receiveLooper: Looper? = null

    @Volatile
    private var receiveServiceHandler: ReceiveServiceHandler? = null

    override fun onLocationChanged(paramLocation: Location) {
        if (isServerRunning.get()) {
            receiveListener.get()?.let {
                obtainSendMessage(
                    WHAT_SERVER, Triple(it.ipv4, it.port, encodeLocation(paramLocation))
                )
                sendForegroundNotification(notificationForServer("${it.ipv4}:${it.port}"))
                it.onLocationReceived(paramLocation)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startServer(listener: ReceiveLocationListener) {
        try {
            socket.init(listener.port)
            //2.获取位置提供器，GPS
            when {
                locationManager!!.getProviders(true).contains(LocationManager.GPS_PROVIDER) -> {
                    //如果是GPS
                    logger("定位方式GPS")
                    receiveListener.set(listener)
                    isServerRunning.set(true)
                    //监视地理位置变化，第二个和第三个参数分别为更新的最短时间minTime和最短距离minDistance
                    locationManager!!.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 500L, 0.0f, this
                    )
                    sendForegroundNotification(notificationForServer(getString(R.string.gps_scanning)))
                    if (isAutomaticIp.get()) {
                        obtainReceiveMessage(WHAT_SERVER)
                    }
                    toast(getString(R.string.server_started))
                }
                else -> {
                    stopServer()
                    toast(getString(R.string.providers_disable))
                }
            }
        } catch (socketException: SocketException) {
            stopSelf()
            toast(getString(R.string.socket_creating_error))
        }
    }

    fun stopServer() {
        socket.close {
            isServerRunning.set(false)
            receiveListener.set(null)
            sendServiceHandler?.removeCallbacksAndMessages(null)
            receiveServiceHandler?.removeCallbacksAndMessages(null)
            locationManager?.removeUpdates(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                stopForeground(true)
            }
            toast(getString(R.string.server_stopped))
        }
    }

    fun startClient(listener: ReceiveLocationListener) {
        try {
            socket.init(listener.port)
            mockGPSLocation(locationManager)
            sendForegroundNotification(notificationForClient(getString(R.string.location_loading)))
            isClientRunning.set(true)
            receiveListener.set(listener)
            obtainReceiveMessage(WHAT_CLIENT)
            toast(getString(R.string.client_started))
        } catch (socketException: SocketException) {
            stopSelf()
            toast(getString(R.string.socket_creating_error))
        } catch (securityException: SecurityException) {
            stopClient()
            toast(getString(R.string.mock_location_disable))
        }
    }

    fun stopClient() {
        socket.close {
            isClientRunning.set(false)
            receiveListener.set(null)
            sendServiceHandler?.removeCallbacksAndMessages(null)
            receiveServiceHandler?.removeCallbacksAndMessages(null)
            try {
                locationManager?.takeIf { it.getProvider(LocationManager.GPS_PROVIDER) != null }
                    ?.let {
                        it.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
                        it.clearTestProviderEnabled(LocationManager.GPS_PROVIDER)
                        it.clearTestProviderStatus(LocationManager.GPS_PROVIDER)
                        it.removeTestProvider(LocationManager.GPS_PROVIDER)
                    }
            } catch (e: Exception) {
                Log.e("TAG", "stopClient removeTestProvider: $e")
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    stopForeground(true)
                }
                toast(getString(R.string.client_stopped))
            }
        }
    }

    class SendServiceHandler(looper: Looper, service: LocationService) : Handler(looper) {
        private val weakReference = WeakReference(service)
        override fun handleMessage(msg: Message) {
            weakReference.get()?.run {
                when (msg.what) {
                    WHAT_SERVER -> {
                        val msgObj = msg.obj as Triple<*, *, *>
                        socket.send(
                            msgObj.third.toString(), InetSocketAddress(
                                msgObj.first as String, msgObj.second as Int
                            )
                        )
                    }
                    WHAT_CLIENT -> {
                        val msgObj = msg.obj as Pair<*, *>
                        socket.send(
                            msgObj.second.toString(), msgObj.first as SocketAddress
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    class ReceiveServiceHandler(looper: Looper, service: LocationService) : Handler(looper) {
        private val weakReference = WeakReference(service)
        override fun handleMessage(msg: Message) = weakReference.get()?.run {
            when (msg.what) {
                WHAT_SERVER -> {
                    //服务端必定是自动ip
                    while (isAutomaticIp.get()) {
                        socket.receive(decode = { decodeIp(it) }, onError = {
                            logger("客户端Ip更新error: $it")
                        }) { ip, address ->
                            logger("客户端更新Ip: $ip 服务端IP:$address")
                            if (checkAddressPort("$ip:$BROADCAST_PORT")) {
                                receiveListener.get()?.ipv4 = ip
                                isAutomaticIp.set(false)
                            }
                        }
                    }
                }
                WHAT_CLIENT -> {
                    while (isClientRunning.get()) {
                        socket.receive(decode = { decodeLocation(it) }, onError = {
                            logger("客户端接收 error: $it")
                            receiveListener.get()?.onStatusChanged(active = false)
                            sendForegroundNotification(notificationForClient(getString(R.string.receive_error)))
                        }) { decodeLocation, address ->
                            val result = try {
                                receiveListener.get()?.onLocationReceived(decodeLocation)
                                receiveListener.get()?.onStatusChanged(active = true)
                                locationManager?.setTestProviderLocation(
                                    LocationManager.GPS_PROVIDER, decodeLocation
                                )
                                getString(R.string.mock_location_working)
                            } catch (e: Exception) {
                                receiveListener.get()?.onStatusChanged(active = false)
                                getString(R.string.mock_location_disable)
                            }
                            sendForegroundNotification(notificationForClient(result))
                            //当服务器ip是 255.255.255.255 local IP发送出去
                            obtainSendMessage(
                                WHAT_CLIENT, Pair(address, encodeIp(getIpAddressByWifi(this@run)))
                            )
                        }
                    }
                    // looper done
                    receiveListener.get()?.onStatusChanged(active = false)
                }
            }
            Unit
        } ?: Unit
    }


    private fun obtainSendMessage(what: Int, obj: Any? = null) = sendServiceHandler?.run {
        sendMessage(obtainMessage(what, obj))
    }

    private fun obtainReceiveMessage(what: Int, obj: Any? = null) = receiveServiceHandler?.run {
        sendMessage(obtainMessage(what, obj))
    }

    override fun onCreate() {
        super.onCreate()
        socket = Unicast()
        sendLooper = HandlerThread("Location-Send").run {
            start()
            looper
        }
        sendServiceHandler = SendServiceHandler(sendLooper!!, this)
        //接收数据线程
        receiveLooper = HandlerThread("Location-Receive").run {
            start()
            looper
        }
        isAutomaticIp.set(getAutomaticBroadcastIp(this))
        receiveServiceHandler = ReceiveServiceHandler(receiveLooper!!, this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        sendForegroundNotification(
            notificationForClient(
                getString(R.string.engine_starting), getString(R.string.app_name)
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        when {
            isServerRunning.get() -> stopServer()
            isClientRunning.get() -> stopClient()
        }
        locationManager = null
        sendLooper?.quit()
        sendServiceHandler = null
        receiveLooper?.quit()
        receiveServiceHandler = null
    }

    inner class LocalBinder : Binder() {
        val locationService: LocationService = this@LocationService
    }

    private val localBinder: IBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder {
        return localBinder
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun sendForegroundNotification(notification: Notification) {
        launchForegroundWithNotification(NOTIFICATION_ID_SERVER, notification)
    }

    private fun notificationForServer(content: String) =
        notificationForClient(content, getString(R.string.server))

    private fun notificationForClient(content: String, title: String = getString(R.string.client)) =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).setContentTitle(title)
            .setContentText(content).setOngoing(true).setSmallIcon(R.drawable.ic_stat_name)
            .setLocalOnly(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).build()

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(
        provider: String?,
        status: Int,
        extras: Bundle?,
    ) {
        //no required
    }
}

interface ReceiveLocationListener {
    var ipv4: String

    /*端口不可修改*/
    val port: Int
    fun onLocationReceived(location: Location)
    fun onStatusChanged(active: Boolean) {}
}
