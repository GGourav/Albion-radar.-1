package com.albionradar.vpn

import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import com.albionradar.AlbionRadarApp
import com.albionradar.MainActivity
import com.albionradar.data.EntityManager
import com.albionradar.network.EventProcessor
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class AlbionVpnService : VpnService() {

    companion object {
        private const val TAG = "AlbionVpnService"
        const val ACTION_CONNECT = "com.albionradar.action.CONNECT"
        const val ACTION_DISCONNECT = "com.albionradar.action.DISCONNECT"
        
        private const val VPN_ADDRESS = "10.8.0.2"
        private const val VPN_MTU = 2048
        private const val VPN_ROUTE = "0.0.0.0"
        private const val GOOGLE_DNS_1 = "8.8.8.8"
        private const val GOOGLE_DNS_2 = "8.8.4.4"
        
        // Albion Online package names
        private val ALBION_PACKAGES = listOf(
            "com.albiononline.mobile",  // Official mobile version
            "com.albiononline",          // Alternative package
            "com.sandboxol.mobile",      // Some regions
            "com.albiononline.mobile.release"
        )
        
        @Volatile
        private var instance: AlbionVpnService? = null
        
        fun isRunning(): Boolean = instance?.isRunning?.get() ?: false
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private var vpnThread: Thread? = null
    private var eventProcessor: EventProcessor? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instance = this
        eventProcessor = EventProcessor()
        eventProcessor?.start()
        Log.i(TAG, "VPN Service created")
    }

    override fun onDestroy() {
        Log.i(TAG, "VPN Service destroying")
        stopVpn()
        eventProcessor?.stop()
        eventProcessor = null
        instance = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        
        when (intent?.action) {
            ACTION_CONNECT -> {
                if (!isRunning.get()) {
                    startVpn()
                }
            }
            ACTION_DISCONNECT -> {
                stopVpn()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        Log.i(TAG, "Starting VPN...")
        
        // Start as foreground service FIRST
        val notification = createNotification("VPN Active - Capturing packets")
        try {
            startForeground(1, notification)
            Log.i(TAG, "Foreground service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            stopSelf()
            return
        }

        // Start VPN in a separate thread
        vpnThread = Thread({
            try {
                runVpn()
            } catch (e: Exception) {
                Log.e(TAG, "VPN thread error", e)
                if (isRunning.get()) {
                    handler.post {
                        stopVpn()
                        stopSelf()
                    }
                }
            }
        }, "VPN-Thread")
        
        vpnThread?.start()
    }

    private fun runVpn() {
        Log.i(TAG, "VPN thread started")
        
        // Wait until prepared
        var prepareIntent: Intent?
        var retryCount = 0
        while (prepare(this).also { prepareIntent = it } != null) {
            retryCount++
            if (retryCount > 100) {
                Log.e(TAG, "VPN prepare timeout")
                return
            }
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                return
            }
        }
        
        Log.i(TAG, "VPN prepared, establishing interface...")

        // Build VPN interface
        val builder = Builder().apply {
            setSession("AlbionRadar")
            setMtu(VPN_MTU)
            addAddress(VPN_ADDRESS, 32)
            addRoute(VPN_ROUTE, 0)
            addDnsServer(GOOGLE_DNS_1)
            addDnsServer(GOOGLE_DNS_2)
            
            // Allow specific packages
            var packageAdded = false
            for (pkg in ALBION_PACKAGES) {
                try {
                    addAllowedApplication(pkg)
                    packageAdded = true
                    Log.i(TAG, "Added allowed package: $pkg")
                } catch (e: Exception) {
                    Log.d(TAG, "Package not found or error: $pkg")
                }
            }
            
            // Always allow our own app
            try {
                addAllowedApplication(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Could not add own package")
            }
        }

        try {
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                handler.post { stopSelf() }
                return
            }
            
            Log.i(TAG, "VPN interface established")
            isRunning.set(true)
            
            // Notify that VPN started
            handler.post {
                MainActivity.vpnStarted()
            }
            
            // Start packet processing
            processPackets()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error establishing VPN", e)
            vpnInterface?.close()
            vpnInterface = null
            handler.post { stopSelf() }
        }
    }

    private fun processPackets() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(fd)
        val outputStream = FileOutputStream(fd)
        val buffer = ByteBuffer.allocate(VPN_MTU)
        
        Log.i(TAG, "Starting packet processing loop")

        try {
            while (isRunning.get() && vpnInterface != null) {
                // Read packet
                val bytesRead = inputStream.read(buffer.array())
                if (bytesRead <= 0) {
                    Thread.sleep(1)
                    continue
                }

                // Minimum IP header check
                if (bytesRead < 20) continue

                val packet = buffer.array()
                
                // IPv4 check
                val version = (packet[0].toInt() shr 4) and 0x0F
                if (version != 4) continue

                // Get protocol
                val protocol = packet[9].toInt() and 0xFF
                
                // Process TCP (protocol 6) and UDP (protocol 17)
                when (protocol) {
                    17 -> { // UDP
                        if (bytesRead < 28) continue
                        
                        // Get UDP ports
                        val srcPort = ((packet[20].toInt() and 0xFF) shl 8) or (packet[21].toInt() and 0xFF)
                        val dstPort = ((packet[22].toInt() and 0xFF) shl 8) or (packet[23].toInt() and 0xFF)
                        
                        // Check for Albion ports (5056 is main, 4531 is alternative)
                        if (srcPort == 5056 || dstPort == 5056 || srcPort == 4531 || dstPort == 4531) {
                            val udpLength = ((packet[24].toInt() and 0xFF) shl 8) or (packet[25].toInt() and 0xFF)
                            val payloadSize = udpLength - 8
                            
                            if (payloadSize > 0 && 28 + payloadSize <= bytesRead) {
                                val payload = packet.sliceArray(28 until 28 + payloadSize)
                                try {
                                    eventProcessor?.processPacket(payload)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Packet processing error: ${e.message}")
                                }
                            }
                        }
                    }
                }
                
                buffer.clear()
            }
        } catch (e: Exception) {
            if (isRunning.get()) {
                Log.e(TAG, "Error in packet loop", e)
            }
        } finally {
            Log.i(TAG, "Packet processing loop ended")
        }
    }

    private fun stopVpn() {
        if (!isRunning.getAndSet(false)) return
        
        Log.i(TAG, "Stopping VPN...")
        
        // Notify that VPN stopped
        handler.post {
            MainActivity.vpnStopped()
        }
        
        // Close VPN interface
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        
        // Interrupt thread
        vpnThread?.interrupt()
        vpnThread = null
        
        Log.i(TAG, "VPN stopped")
    }

    private fun createNotification(text: String): Notification {
        val intent = android.content.Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        return androidx.core.app.NotificationCompat.Builder(this, AlbionRadarApp.CHANNEL_VPN)
            .setContentTitle("Albion Radar")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
