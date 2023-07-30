package com.mazda.tgps.utils

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.LocationProvider
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter.formatIpAddress
import android.util.Log
import com.mazda.tgps.BuildConfig
import com.mazda.tgps.service.CancelNotificationService
import com.mazda.tgps.service.NOTIFICATION_CHANNEL_ID
import com.mazda.tgps.socket.BROADCAST_PORT
import com.mazda.tgps.socket.UNI_BROADCAST_IP
import java.net.DatagramSocket
import kotlin.math.abs


object Utils {

    fun checkAddressPort(address: String): Boolean =
        address.matches(
            Regex("""^((2[0-4]\d|25[0-5]|[1]?\d\d?)\.){3}(2[0-4]\d|25[0-5]|[1]?\d\d?)\:([1-9]|[1-9][0-9]|[1-9][0-9][0-9]|[1-9][0-9][0-9][0-9]|[1-6][0-5][0-5][0-3][0-5])$""")
        )

    fun String.getAddressPort(): Pair<String, Int> {
        val split = this.split(":")
        return split[0] to split[1].toInt()
    }

    fun covertGps(info: Double): String {
        val du = info.toInt()
        val tp = (info - du) * 60
        val fen = tp.toInt()
        return "$du°${abs(fen)}'${String.format("%.2f", abs((tp - fen) * 60))}\""
    }

    private const val DATA_FLAG = "T.GPS"

    fun encodeLocation(paramLocation: Location): String {
        return paramLocation.run {
            "$DATA_FLAG,$latitude,$longitude,$altitude,$bearing,$accuracy,$speed,$time,$elapsedRealtimeNanos"
        }
    }

    fun decodeLocation(paramLocation: String): Location? {
        return paramLocation.takeIf { it.isNotEmpty() }?.split(",".toRegex())?.toTypedArray()
            ?.takeIf {
                it.firstOrNull() == DATA_FLAG
            }?.let {
                Location("").apply {
                    latitude = it[1].toDouble()
                    longitude = it[2].toDouble()
                    altitude = it[3].toDouble()
                    bearing = it[4].toFloat()
                    accuracy = it[5].toFloat()
                    speed = it[6].toFloat()
                    time = it[7].toLong()
                    elapsedRealtimeNanos = it[8].toLong()
                }
            }
    }

    fun encodeIp(string: String): String {
        return string.run {
            "$DATA_FLAG,$string"
        }
    }

    fun decodeIp(string: String): String? {
        return string.takeIf { it.isNotEmpty() }?.split(",".toRegex())?.toTypedArray()?.takeIf {
            it.firstOrNull() == DATA_FLAG && it.size >= 2
        }?.get(1)
    }

    fun Service.launchForegroundWithNotification(foregroundId: Int, notification: Notification) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                val service = getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
                // 创建通知通道
                var channel = service.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
                if (channel == null) {
                    channel = NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "T-Gps",
                        NotificationManager.IMPORTANCE_NONE
                    )
                    channel.lightColor = Color.BLUE
                    channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    // 正式创建
                    service.createNotificationChannel(channel)
                }
                // 开启前台进程 , API 26 以上无法关闭通知栏
                startForeground(foregroundId, notification)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 -> {
                startForeground(foregroundId, Notification())
                // API 18 ~ 25 以上的设备 , 启动相同 id 的前台服务 , 并关闭 , 可以关闭通知
                startService(Intent(this, CancelNotificationService::class.java))
            }
            else -> {
                // 将该服务转为前台服务
                // 需要设置 ID 和 通知
                // 设置 ID 为 0 , 就不显示已通知了 , 但是 oom_adj 值会变成后台进程 11
                // 设置 ID 为 1 , 会在通知栏显示该前台服务
                // 8.0 以上该用法报错
                startForeground(foregroundId, Notification())
            }
        }
    }

    /**
     * 模拟位置是否启用
     * 若启用，则addTestProvider
     * 是否成功addTestProvider，默认为true，软件启动时为防止意外退出导致未重置，重置一次
     * Android 6.0系统以下，可以通过Setting.Secure.ALLOW_MOCK_LOCATION获取是否【允许模拟位置】，
     * 当【允许模拟位置】开启时，可addTestProvider；
     * Android 6.0系统及以上，弃用Setting.Secure.ALLOW_MOCK_LOCATION变量，没有【允许模拟位置】选项，
     * 增加【选择模拟位置信息应用】，此时需要选择当前应用，才可以addTestProvider，
     * 但未找到获取当前选择应用的方法，因此通过addTestProvider是否成功来判断是否可用模拟位置。
     */
    fun checkMockGPSLocation(
        context: Context,
        providerStr: String = LocationManager.GPS_PROVIDER
    ): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        // Android 6.0及以上，需要【选择模拟位置信息应用】，未找到方法，因此通过addTestProvider是否可用判断
        return if (Build.VERSION.SDK_INT > 22) {
            try {
                mockGPSLocation(locationManager, providerStr)
                true
            } catch (e: Exception) {
                logger("checkMockGPSLocationError: $e")
                false
            }
        } else {
            // Android 6.0以下，通过Setting.Secure.ALLOW_MOCK_LOCATION判断
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ALLOW_MOCK_LOCATION,
                0
            ) != 0
        }
    }

    @SuppressLint("WrongConstant")
    fun mockGPSLocation(
        locationManager: LocationManager?,
        providerStr: String = LocationManager.GPS_PROVIDER
    ) {
        val provider = locationManager!!.getProvider(providerStr)
        if (provider != null) {
            locationManager.addTestProvider(
                provider.name,
                provider.requiresNetwork(),
                provider.requiresSatellite(),
                provider.requiresCell(),
                provider.hasMonetaryCost(),
                provider.supportsAltitude(),
                provider.supportsSpeed(),
                provider.supportsBearing(),
                provider.powerRequirement,
                provider.accuracy
            )
        } else {
            when (providerStr) {
                LocationManager.GPS_PROVIDER -> {
                    locationManager.addTestProvider(
                        providerStr,
                        true,
                        true,
                        false,
                        false,
                        true,
                        true,
                        true,
                        Criteria.POWER_HIGH,
                        Criteria.ACCURACY_FINE
                    )
                }
                LocationManager.NETWORK_PROVIDER -> {
                    locationManager.addTestProvider(
                        providerStr,
                        true,
                        false,
                        true,
                        false,
                        false,
                        false,
                        false,
                        Criteria.POWER_LOW,
                        Criteria.ACCURACY_FINE
                    )
                }
                else -> {
                    locationManager.addTestProvider(
                        providerStr,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        true,
                        Criteria.POWER_LOW,
                        Criteria.ACCURACY_FINE
                    )
                }
            }
        }
        locationManager.setTestProviderEnabled(providerStr, true)
        locationManager.setTestProviderStatus(
            providerStr,
            LocationProvider.AVAILABLE,
            null,
            System.currentTimeMillis()
        )
    }

    fun portAvailable(portNo: Int, timeoutMillis: Int = 250): Boolean = try {
        DatagramSocket(portNo).use {
            it.soTimeout = timeoutMillis
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    fun getIpAddressByWifi(current: Context): String {
        val wm = current.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wm?.dhcpInfo?.ipAddress?.let { formatIpAddress(it) }.orEmpty()
    }

    fun isWifiConnected(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val networkCapabilities = manager.getNetworkCapabilities(manager.activeNetwork)
                if (networkCapabilities != null) {
                    return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                }
            } else {
                val networkInfo = manager.activeNetworkInfo
                return networkInfo != null && networkInfo.isConnected && networkInfo.type == ConnectivityManager.TYPE_WIFI
            }
        }
        return false
    }

    fun logger(text: String) {
        if (BuildConfig.DEBUG) Log.d("MAZDA", text)
    }
}

private const val SP_NAME = "t-gps"
private const val AUTOMATIC_IP = "AUTOMATIC_IP"
private const val ADDRESS = "IP_PORT"

fun getAutomaticBroadcastIp(current: Context): Boolean {
    return current.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).run {
        getBoolean(AUTOMATIC_IP, true)
    }
}

fun setAutomaticBroadcastIp(current: Context, value: Boolean) {
    return current.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).run {
        edit().putBoolean(AUTOMATIC_IP, value).apply()
    }
}

fun getBroadcastAddress(current: Context): String {
    return current.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).run {
        getString(ADDRESS, "$UNI_BROADCAST_IP:$BROADCAST_PORT").orEmpty()
    }
}

fun setBroadcastAddress(current: Context, value: String) {
    return current.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).run {
        edit().putString(ADDRESS, value).apply()
    }
}
