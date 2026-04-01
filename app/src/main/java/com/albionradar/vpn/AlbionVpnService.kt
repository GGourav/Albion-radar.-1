package com.albionradar.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.albionradar.AlbionRadarApp
import com.albionradar.network.EventProcessor
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.nio.ByteBuffer

class AlbionVpnService : VpnService() {

    companion object {
        private const val TAG = "AlbionVpnService"
        const val ACTION_CONNECT = "com.albionradar.action.CONNECT"
        const val ACTION_DISCONNECT = "com.albionradar.action.DISCONNECT"
        private const val VPN_ADDRESS = "10.200.0.2"
        private const val VPN_MTU = 1500
        private const val ALBION_PORT = 5056
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var scope: CoroutineScope? = null
    private var eventProcessor: EventProcessor? = null

    override fun onCreate() {
        super.onCreate()
        eventProcessor = EventProcessor()
        eventProcessor?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        eventProcessor?.stop()
        eventProcessor = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> startVpn()
            ACTION_DISCONNECT -> {
                stopVpn()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) {
            Log.w(TAG, "VPN already running")
            return
        }

        try {
            // Configure VPN - only intercept UDP traffic
            val builder = Builder().apply {
                setSession("AlbionRadar")
                addAddress(VPN_ADDRESS, 24)
                // Only route UDP traffic, not all traffic
                // This prevents the VPN from blocking itself
                setMtu(VPN_MTU)
                setBlocking(false)
            }

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
                return
            }

            isRunning = true

            // Start foreground service with notification
            val notification = AlbionRadarApp.createVpnNotification(this, "VPN Active - Monitoring Albion traffic")
            startForeground(1, notification)

            // Start packet processing
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            scope?.launch {
                processPackets()
            }

            Log.i(TAG, "VPN started successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn()
            stopSelf()
        }
    }

    private fun stopVpn() {
        if (!isRunning) return

        isRunning = false
        scope?.cancel()
        scope = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null

        Log.i(TAG, "VPN stopped")
    }

    private fun processPackets() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(fd)
        val buffer = ByteBuffer.allocate(VPN_MTU)

        try {
            while (isRunning && vpnInterface != null) {
                val bytesRead = inputStream.read(buffer.array())
                if (bytesRead <= 0) continue

                // Minimum IP header size
                if (bytesRead < 20) continue

                val packet = buffer.array().sliceArray(0 until bytesRead)

                // Check IP version
                val version = (packet[0].toInt() shr 4) and 0x0F
                if (version != 4) continue // Only IPv4

                // Get protocol (byte 9)
                val protocol = packet[9].toInt() and 0xFF
                
                // We only care about UDP (protocol 17)
                if (protocol != 17) continue

                // Parse IP header to get total length
                val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
                if (ipHeaderLength < 20 || bytesRead < ipHeaderLength + 8) continue

                // Get UDP ports
                val srcPort = ((packet[ipHeaderLength].toInt() and 0xFF) shl 8) or 
                              (packet[ipHeaderLength + 1].toInt() and 0xFF)
                val dstPort = ((packet[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or 
                              (packet[ipHeaderLength + 3].toInt() and 0xFF)

                // Check if this is Albion traffic (port 5056)
                val isAlbionPacket = srcPort == ALBION_PORT || dstPort == ALBION_PORT

                if (isAlbionPacket) {
                    // Get UDP length
                    val udpLength = ((packet[ipHeaderLength + 4].toInt() and 0xFF) shl 8) or 
                                    (packet[ipHeaderLength + 5].toInt() and 0xFF)
                    val payloadSize = udpLength - 8 // Subtract UDP header size

                    // Extract UDP payload
                    val payloadStart = ipHeaderLength + 8
                    if (payloadSize > 0 && payloadStart + payloadSize <= bytesRead) {
                        val payload = packet.sliceArray(payloadStart until payloadStart + payloadSize)

                        try {
                            eventProcessor?.processPacket(payload)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error processing packet: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "Error in packet processing", e)
            }
        }
    }
}
