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
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_MTU = 1500
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
            val builder = Builder().apply {
                setSession("AlbionRadar")
                addAddress(VPN_ADDRESS, 32)
                addRoute("0.0.0.0", 0)
                addDnsServer("8.8.8.8")
                setMtu(VPN_MTU)
                setBlocking(true)
            }

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
                return
            }

            isRunning = true

            val notification = AlbionRadarApp.createVpnNotification(this, "VPN Active - Capturing Albion packets")
            startForeground(1, notification)

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

                if (bytesRead < 20) continue

                val packet = buffer.array().sliceArray(0 until bytesRead)

                val version = (packet[0].toInt() shr 4) and 0x0F
                if (version != 4) continue

                val protocol = packet[9].toInt() and 0xFF
                if (protocol != 17) continue

                val srcPort = ((packet[20].toInt() and 0xFF) shl 8) or (packet[21].toInt() and 0xFF)
                val dstPort = ((packet[22].toInt() and 0xFF) shl 8) or (packet[23].toInt() and 0xFF)

                if (srcPort == 5056 || dstPort == 5056 || srcPort == 4531 || dstPort == 4531) {
                    val udpLength = ((packet[24].toInt() and 0xFF) shl 8) or (packet[25].toInt() and 0xFF)
                    val payloadSize = udpLength - 8

                    if (payloadSize > 0 && 28 + payloadSize <= packet.size) {
                        val payload = packet.sliceArray(28 until 28 + payloadSize)

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
                Log.e(TAG, "Error in packet processing loop", e)
            }
        }
    }
}
