package com.albionradar.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

data class MobInfo(
    val uniqueName: String,
    val tier: Int,
    val category: String,
    val namelocatag: String?,
    val lootType: String?,
    val lootTier: Int?
)

data class HarvestableInfo(
    val tier: Int,
    val enchant: Int
)

class DataManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "DataManager"
        private const val MOB_DB_FILE = "mobs.json"
        private const val HARVESTABLE_DB_FILE = "harvestables.json"

        @Volatile
        private var instance: DataManager? = null

        fun initialize(context: Context) {
            instance = DataManager(context.applicationContext)
        }

        fun getInstance(): DataManager {
            return instance ?: throw IllegalStateException("DataManager not initialized")
        }
    }

    private val mobsById = mutableMapOf<Int, MobInfo>()
    private val mobsByName = mutableMapOf<String, MobInfo>()
    private val validHarvestables = mutableSetOf<String>()

    fun isLoaded(): Boolean = mobsById.isNotEmpty()

    fun loadDatabases() {
        loadMobDatabase()
        loadHarvestableDatabase()
    }

    private fun loadMobDatabase() {
        try {
            val json = loadAsset(MOB_DB_FILE) ?: return
            val gson = Gson()
            val type = object : TypeToken<List<MobInfo>>() {}.type
            val mobList: List<MobInfo> = gson.fromJson(json, type)

            mobList.forEachIndexed { index, mob ->
                val typeId = index + 15
                if (!mob.uniqueName.startsWith("SILVERCOINS") &&
                    !mob.uniqueName.startsWith("DEADRAT")) {
                    mobsById[typeId] = mob
                    mobsByName[mob.uniqueName] = mob
                }
            }
            Log.i(TAG, "Loaded ${mobsById.size} mobs into database")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load mob database", e)
        }
    }

    private fun loadHarvestableDatabase() {
        try {
            val json = loadAsset(HARVESTABLE_DB_FILE) ?: return
            val gson = Gson()
            val type = object : TypeToken<Map<String, List<HarvestableInfo>>>() {}.type
            val resources: Map<String, List<HarvestableInfo>> = gson.fromJson(json, type)

            val resourceTypes = listOf("WOOD", "ROCK", "FIBER", "HIDE", "ORE")
            for (resType in resourceTypes) {
                val list = resources[resType] ?: continue
                for (item in list) {
                    validHarvestables.add("${resType}-${item.tier}-${item.enchant}")
                }
            }
            Log.i(TAG, "Loaded ${validHarvestables.size} valid harvestable combinations")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load harvestable database", e)
        }
    }

    fun getMobInfo(typeId: Int): MobInfo? = mobsById[typeId]

    fun getMobInfoByName(name: String): MobInfo? = mobsByName[name]

    fun isValidHarvestable(type: String, tier: Int, enchant: Int): Boolean {
        return validHarvestables.contains("$type-$tier-$enchant")
    }

    fun classifyMob(typeId: Int): Pair<Int, String> {
        val info = mobsById[typeId] ?: return Pair(10, "")
        return when {
            info.lootType != null -> {
                val normalizedType = normalizeResourceType(info.lootType)
                val enemyType = if (normalizedType == "Hide") 1 else 0
                Pair(enemyType, normalizedType)
            }
            info.category.isEmpty() || info.category == "standard" || info.category == "trash" -> Pair(2, info.uniqueName)
            info.category.contains("VETERAN", ignoreCase = true) &&
                !info.category.contains("CHAMPION", ignoreCase = true) -> Pair(5, info.uniqueName)
            info.category.contains("ELITE", ignoreCase = true) -> Pair(5, info.uniqueName)
            info.category.contains("BOSS", ignoreCase = true) &&
                !info.category.contains("MINIBOSS", ignoreCase = true) -> Pair(6, info.uniqueName)
            info.category == "boss" -> Pair(6, info.uniqueName)
            info.category == "miniboss" -> Pair(5, info.uniqueName)
            info.category == "champion" -> Pair(4, info.uniqueName)
            info.category.contains("rd_elite", ignoreCase = true) ||
                info.category.contains("rd_veteran", ignoreCase = true) -> Pair(5, info.uniqueName)
            info.category.contains("rd_solo", ignoreCase = true) -> Pair(4, info.uniqueName)
            else -> Pair(2, info.uniqueName)
        }
    }

    fun getMobDisplayName(info: MobInfo): String {
        val namelocatag = info.namelocatag ?: info.uniqueName
        val cleanedName = namelocatag
            .removePrefix("@ITEMS_")
            .removePrefix("@MOBS_")
            .replace("_", " ")
            .trim()
        return cleanedName
    }

    private fun normalizeResourceType(type: String): String {
        return when (type.uppercase()) {
            "HIDE" -> "Hide"
            "FIBER" -> "Fiber"
            "WOOD" -> "Log"
            "ROCK" -> "Rock"
            "ORE" -> "Ore"
            else -> type
        }
    }

    private fun loadAsset(filename: String): String? {
        return try {
            context.assets.open(filename).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.w(TAG, "Asset file not found: $filename")
            null
        }
    }
}
