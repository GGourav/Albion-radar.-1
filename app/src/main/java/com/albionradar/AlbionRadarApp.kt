package com.albionradar

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.albionradar.data.DataManager

class AlbionRadarApp : Application() {

    companion object {
        const val CHANNEL_VPN = "vpn_service"
        const val CHANNEL_OVERLAY = "overlay_service"
        const val CHANNEL_ALERTS = "alerts"
        
        @Volatile
        private var instance: AlbionRadarApp? = null
        
        fun getInstance(): AlbionRadarApp {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
        
        fun createVpnNotification(context: Context, text: String): Notification {
            val intent = android.content.Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Builder(context, CHANNEL_VPN)
                .setContentTitle("Albion Radar")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }

        fun createOverlayNotification(context: Context, text: String): Notification {
            return NotificationCompat.Builder(context, CHANNEL_OVERLAY)
                .setContentTitle("Albion Radar")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        createNotificationChannels()
        DataManager.initialize(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            val vpnChannel = NotificationChannel(
                CHANNEL_VPN,
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VPN packet capture service"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(vpnChannel)
            
            val overlayChannel = NotificationChannel(
                CHANNEL_OVERLAY,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Radar overlay display"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(overlayChannel)
            
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Hostile player alerts"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }
}
