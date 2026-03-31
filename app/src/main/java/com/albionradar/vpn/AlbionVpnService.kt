package com.albionradar.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.albionradar.AlbionRadarApp
import com.albionradar.network.EventProcessor
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class AlbionVpnService : VpnService() {

    companion object {
        private const val TAG = "AlbionVpnService"
        const val ACTION_CONNECT = "com.albionradar.action.CONNECT"
        const val ACTION_DISCONNECT = "com.albionradar.action.DISCONNECT"

        private const val VPN_MTU = 1500
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var scope: CoroutineScope? = null
    private val eventProcessor = EventProcessor()

    override fun onCreate() {
        super.onCreate()
        eventProcessor.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        eventProcessor.stop()
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
        if (isRunning) return

        try {
            val builder = Builder()
            builder.setSession("AlbionRadar")
            builder.addAddress("10.0.0.2", 32)
            builder.addRoute("0.0.0.0", 0)
            builder.addDnsServer("8.8.8.8")
            builder.setMtu(VPN_MTU)
            builder.setBlocking(true)
            
            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
                return
            }

            isRunning = true
            val notification = AlbionRadarApp.createVpnNotification(this, "VPN Active - Capturing packets")
            startForeground(1, notification)

            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            scope?.launch {
                handlePackets()
            }

            Log.i(TAG, "VPN started successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopVpn()
            stopSelf()
        }
    }

    private fun stopVpn() {
        isRunning = false
        scope?.cancel()
        scope = null

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN", e)
        }

        Log.i(TAG, "VPN stopped")
    }

    private fun handlePackets() {
        val vpnFd = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(vpnFd)

        val buffer = ByteArray(VPN_MTU)

        try {
            while (isRunning) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead <= 0) continue

                val packet = buffer.copyOfRange(0, bytesRead)
                val (dstAddr, dstPort, payload) = parseUdpPacket(packet)
                
                if (payload != null && shouldCapture(dstAddr, dstPort)) {
                    eventProcessor.processPacket(payload)
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "Packet handling error", e)
            }
        }
    }

    private fun shouldCapture(address: String, port: Int): Boolean {
        val albionRanges = listOf(
            "5.188.64.",
            "5.188.65.",
            "5.188.66.",
            "5.188.67.",
            "193.36.117.",
            "193.36.118.",
            "193.36.119."
        )
        return albionRanges.any { address.startsWith(it) } || port == 4531 || port == 5056
    }

    private fun parseUdpPacket(packet: ByteArray): Triple<String, Int, ByteArray?> {
        if (packet.size < 28) return Triple("", 0, null)

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return Triple("", 0, null)

        val dstAddr = String.format(
            "%d.%d.%d.%d",
            packet[16].toInt() and 0xFF,
            packet[17].toInt() and 0xFF,
            packet[18].toInt() and 0xFF,
            packet[19].toInt() and 0xFF
        )

        val dstPort = ((packet[22].toInt() and 0xFF) shl 8) or (packet[23].toInt() and 0xFF)

        val udpLength = ((packet[24].toInt() and 0xFF) shl 8) or (packet[25].toInt() and 0xFF)
        val payloadSize = udpLength - 8

        val payload = if (payloadSize > 0 && 28 + payloadSize <= packet.size) {
            packet.copyOfRange(28, 28 + payloadSize)
        } else null

        return Triple(dstAddr, dstPort, payload)
    }
}
