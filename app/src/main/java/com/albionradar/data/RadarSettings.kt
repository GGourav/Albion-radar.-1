package com.albionradar.data

import android.content.Context
import android.content.SharedPreferences

class RadarSettings private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("radar_settings", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var instance: RadarSettings? = null

        fun getInstance(context: Context): RadarSettings {
            return instance ?: synchronized(this) {
                instance ?: RadarSettings(context.applicationContext).also { instance = it }
            }
        }
    }

    var zoomLevel: Float
        get() = prefs.getFloat("zoom_level", 1.0f)
        set(value) = prefs.edit().putFloat("zoom_level", value).apply()

    var showGrid: Boolean
        get() = prefs.getBoolean("show_grid", true)
        set(value) = prefs.edit().putBoolean("show_grid", value).apply()

    var showLabels: Boolean
        get() = prefs.getBoolean("show_labels", true)
        set(value) = prefs.edit().putBoolean("show_labels", value).apply()

    var alertSound: Boolean
        get() = prefs.getBoolean("alert_sound", false)
        set(value) = prefs.edit().putBoolean("alert_sound", value).apply()

    var hostileAlert: Boolean
        get() = prefs.getBoolean("hostile_alert", true)
        set(value) = prefs.edit().putBoolean("hostile_alert", value).apply()

    var showOre: Boolean
        get() = prefs.getBoolean("show_ore", true)
        set(value) = prefs.edit().putBoolean("show_ore", value).apply()

    var showWood: Boolean
        get() = prefs.getBoolean("show_wood", true)
        set(value) = prefs.edit().putBoolean("show_wood", value).apply()

    var showRock: Boolean
        get() = prefs.getBoolean("show_rock", true)
        set(value) = prefs.edit().putBoolean("show_rock", value).apply()

    var showFiber: Boolean
        get() = prefs.getBoolean("show_fiber", true)
        set(value) = prefs.edit().putBoolean("show_fiber", value).apply()

    var showHide: Boolean
        get() = prefs.getBoolean("show_hide", true)
        set(value) = prefs.edit().putBoolean("show_hide", value).apply()

    fun getShowOre(): Boolean = showOre
    fun getShowWood(): Boolean = showWood
    fun getShowRock(): Boolean = showRock
    fun getShowFiber(): Boolean = showFiber
    fun getShowHide(): Boolean = showHide

    var showNormalMobs: Boolean
        get() = prefs.getBoolean("show_normal_mobs", true)
        set(value) = prefs.edit().putBoolean("show_normal_mobs", value).apply()

    var showBosses: Boolean
        get() = prefs.getBoolean("show_bosses", true)
        set(value) = prefs.edit().putBoolean("show_bosses", value).apply()

    var showVeterans: Boolean
        get() = prefs.getBoolean("show_veterans", true)
        set(value) = prefs.edit().putBoolean("show_veterans", value).apply()

    fun getShowNormalMobs(): Boolean = showNormalMobs
    fun getShowBosses(): Boolean = showBosses
    fun getShowVeterans(): Boolean = showVeterans

    var showPlayers: Boolean
        get() = prefs.getBoolean("show_players", true)
        set(value) = prefs.edit().putBoolean("show_players", value).apply()

    var hostileOnly: Boolean
        get() = prefs.getBoolean("hostile_only", false)
        set(value) = prefs.edit().putBoolean("hostile_only", value).apply()

    fun getShowPlayers(): Boolean = showPlayers
    fun getHostileOnly(): Boolean = hostileOnly

    var showDungeons: Boolean
        get() = prefs.getBoolean("show_dungeons", true)
        set(value) = prefs.edit().putBoolean("show_dungeons", value).apply()

    var showChests: Boolean
        get() = prefs.getBoolean("show_chests", true)
        set(value) = prefs.edit().putBoolean("show_chests", value).apply()

    var showFishing: Boolean
        get() = prefs.getBoolean("show_fishing", true)
        set(value) = prefs.edit().putBoolean("show_fishing", value).apply()

    var showMist: Boolean
        get() = prefs.getBoolean("show_mist", true)
        set(value) = prefs.edit().putBoolean("show_mist", value).apply()

    fun getShowDungeons(): Boolean = showDungeons
    fun getShowChests(): Boolean = showChests
    fun getShowFishing(): Boolean = showFishing
    fun getShowMist(): Boolean = showMist

    var minTier: Int
        get() = prefs.getInt("min_tier", 1)
        set(value) = prefs.edit().putInt("min_tier", value.coerceIn(1, 8)).apply()

    var minEnchant: Int
        get() = prefs.getInt("min_enchant", 0)
        set(value) = prefs.edit().putInt("min_enchant", value.coerceIn(0, 4)).apply()

    var overlaySize: Int
        get() = prefs.getInt("overlay_size", 300)
        set(value) = prefs.edit().putInt("overlay_size", value.coerceIn(150, 800)).apply()

    var overlayX: Int
        get() = prefs.getInt("overlay_x", 100)
        set(value) = prefs.edit().putInt("overlay_x", value).apply()

    var overlayY: Int
        get() = prefs.getInt("overlay_y", 500)
        set(value) = prefs.edit().putInt("overlay_y", value).apply()

    var playerName: String
        get() = prefs.getString("player_name", "") ?: ""
        set(value) = prefs.edit().putString("player_name", value).apply()

    fun shouldShowPlayer(faction: Int): Boolean {
        if (!showPlayers) return false
        if (hostileOnly && faction != 255) return false
        return true
    }

    fun shouldShowResource(resourceType: String): Boolean {
        return when (resourceType.uppercase()) {
            "ORE" -> showOre
            "WOOD", "LOG", "LOGS" -> showWood
            "ROCK" -> showRock
            "FIBER" -> showFiber
            "HIDE" -> showHide
            else -> true
        }
    }

    fun shouldShowMob(enemyType: Int): Boolean {
        return when (enemyType) {
            6 -> showBosses
            5 -> showVeterans
            4 -> showVeterans
            else -> showNormalMobs
        }
    }
}
