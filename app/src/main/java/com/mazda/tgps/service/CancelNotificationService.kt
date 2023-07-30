package com.mazda.tgps.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder

class CancelNotificationService : Service() {
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID_SERVER, Notification())
        stopSelf()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
