package com.albionradar.network

import android.util.Log

object PacketInterceptor {

    private const val TAG = "PacketInterceptor"

    private val ALBION_IP_RANGES = listOf(
        "5.188.64.0/18",
        "193.36.117.0/24"
    )

    private const val GAME_PORT = 5056
    private const val LOGIN_PORT = 4531

    fun isAlbionPacket(sourceIp: String, sourcePort: Int, destIp: String, destPort: Int): Boolean {
        return isAlbionIP(sourceIp) || isAlbionIP(destIp) ||
               sourcePort == GAME_PORT || destPort == GAME_PORT ||
               sourcePort == LOGIN_PORT || destPort == LOGIN_PORT
    }

    private fun isAlbionIP(ip: String): Boolean {
        for (range in ALBION_IP_RANGES) {
            if (ipMatchesRange(ip, range)) {
                return true
            }
        }
        return false
    }

    private fun ipMatchesRange(ip: String, cidr: String): Boolean {
        try {
            val parts = cidr.split("/")
            val networkAddress = parts[0]
            val prefixLength = parts[1].toInt()

            val ipParts = ip.split(".")
            val networkParts = networkAddress.split(".")

            if (ipParts.size != 4 || networkParts.size != 4) return false

            val ipInt = (ipParts[0].toInt() shl 24) or
                        (ipParts[1].toInt() shl 16) or
                        (ipParts[2].toInt() shl 8) or
                        ipParts[3].toInt()

            val networkInt = (networkParts[0].toInt() shl 24) or
                             (networkParts[1].toInt() shl 16) or
                             (networkParts[2].toInt() shl 8) or
                             networkParts[3].toInt()

            val mask = if (prefixLength == 0) 0 else (-1 shl (32 - prefixLength))
            return (ipInt and mask) == (networkInt and mask)
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing IP: $ip or CIDR: $cidr", e)
            return false
        }
    }
}
