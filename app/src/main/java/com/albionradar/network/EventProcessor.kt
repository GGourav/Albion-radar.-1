package com.albionradar.network

import android.util.Log
import com.albionradar.data.*
import com.albionradar.photon.PhotonParser
import kotlinx.coroutines.*

class EventProcessor {

    companion object {
        private const val TAG = "EventProcessor"

        // Photon event codes from Albion Online
        const val EVENT_LEAVE = 1
        const val EVENT_MOVE = 3
        const val EVENT_NEW_CHARACTER = 29
        const val EVENT_BATCH_RESOURCES = 38
        const val EVENT_NEW_RESOURCE = 40
        const val EVENT_NEW_MOB = 123
        const val EVENT_HEALTH_UPDATE = 50
        const val EVENT_PLAYER_MOVE = 27
        const val EVENT_ZONE_CHANGE = 100
        const val EVENT_CHEST_INFO = 71
        const val EVENT_DUNGEON_INFO = 72
        const val EVENT_FISHING_SPOT = 75
        const val EVENT_MIST_PORTAL = 95
        const val EVENT_WISP_CAGE = 115
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val photonParser = PhotonParser()
    private val entityManager = EntityManager.getInstance()
    private var playerPosition = Pair(0f, 0f)

    fun start() {
        Log.i(TAG, "EventProcessor started")
    }

    fun stop() {
        scope.cancel()
        Log.i(TAG, "EventProcessor stopped")
    }

    fun processPacket(payload: ByteArray) {
        try {
            val events = photonParser.parse(payload)
            for (event in events) {
                processEvent(event)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error processing packet: ${e.message}")
        }
    }

    private fun processEvent(event: PhotonEvent) {
        when (event.code) {
            EVENT_NEW_CHARACTER -> handleNewCharacter(event)
            EVENT_NEW_MOB -> handleNewMob(event)
            EVENT_NEW_RESOURCE -> handleNewResource(event)
            EVENT_BATCH_RESOURCES -> handleBatchResources(event)
            EVENT_MOVE -> handleMove(event)
            EVENT_LEAVE -> handleLeave(event)
            EVENT_HEALTH_UPDATE -> handleHealthUpdate(event)
            EVENT_CHEST_INFO -> handleChest(event)
            EVENT_DUNGEON_INFO -> handleDungeon(event)
            EVENT_FISHING_SPOT -> handleFishingSpot(event)
            EVENT_MIST_PORTAL -> handleMistPortal(event)
            EVENT_WISP_CAGE -> handleWispCage(event)
        }
    }

    private fun handleNewCharacter(event: PhotonEvent) {
        val parameters = event.parameters ?: return

        val id = parameters[0] as? Int ?: return
        val name = parameters[1] as? String ?: "Unknown"
        val posX = (parameters[2] as? Number)?.toFloat() ?: 0f
        val posY = (parameters[3] as? Number)?.toFloat() ?: 0f
        val faction = (parameters[4] as? Number)?.toInt() ?: 0
        val guild = parameters[5] as? String ?: ""
        val alliance = parameters[6] as? String ?: ""

        if (name == getPlayerName()) {
            playerPosition = Pair(posX, posY)
            return
        }

        val entity = GameEntity(
            id = id,
            type = EntityType.PLAYER,
            name = name,
            posX = posX,
            posY = posY,
            faction = faction,
            guild = guild,
            alliance = alliance,
            distance = calculateDistance(posX, posY)
        )

        entityManager.addOrUpdateEntity(entity)
        Log.d(TAG, "New player: $name at ($posX, $posY)")
    }

    private fun handleNewMob(event: PhotonEvent) {
        val parameters = event.parameters ?: return

        val id = parameters[0] as? Int ?: return
        val typeId = parameters[1] as? Int ?: 0
        val posX = (parameters[2] as? Number)?.toFloat() ?: 0f
        val posY = (parameters[3] as? Number)?.toFloat() ?: 0f
        val health = (parameters[4] as? Number)?.toInt() ?: 100
        val maxHealth = (parameters[5] as? Number)?.toInt() ?: 100

        val dataManager = try {
            DataManager.getInstance()
        } catch (e: Exception) {
            null
        }

        val (enemyType, mobName) = dataManager?.classifyMob(typeId) ?: Pair(2, "Mob")
        val mobInfo = dataManager?.getMobInfo(typeId)
        val displayName = mobInfo?.let { dataManager.getMobDisplayName(it) } ?: mobName

        val entity = GameEntity(
            id = id,
            type = EntityType.MOB,
            name = displayName,
            posX = posX,
            posY = posY,
            health = health,
            maxHealth = maxHealth,
            tier = mobInfo?.tier ?: 0,
            distance = calculateDistance(posX, posY)
        )

        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleNewResource(event: PhotonEvent) {
        val parameters = event.parameters ?: return

        val id = parameters[0] as? Int ?: return
        val typeName = parameters[1] as? String ?: return
        val tier = (parameters[2] as? Number)?.toInt() ?: 0
        val posX = (parameters[3] as? Number)?.toFloat() ?: 0f
        val posY = (parameters[4] as? Number)?.toFloat() ?: 0f
        val size = (parameters[5] as? Number)?.toInt() ?: 0
        val enchant = (parameters[6] as? Number)?.toInt() ?: 0

        val resourceType = when {
            typeName.contains("WOOD", ignoreCase = true) -> "Logs"
            typeName.contains("ROCK", ignoreCase = true) -> "Rock"
            typeName.contains("FIBER", ignoreCase = true) -> "Fiber"
            typeName.contains("HIDE", ignoreCase = true) -> "Hide"
            typeName.contains("ORE", ignoreCase = true) -> "Ore"
            else -> typeName
        }

        val entity = GameEntity(
            id = id,
            type = EntityType.RESOURCE,
            name = resourceType,
            posX = posX,
            posY = posY,
            tier = tier,
            size = size,
            enchant = enchant,
            distance = calculateDistance(posX, posY)
        )

        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleBatchResources(event: PhotonEvent) {
        val parameters = event.parameters ?: return
        val batch = parameters[0] as? List<*> ?: return

        for (item in batch) {
            val resourceParams = item as? Map<*, *> ?: continue

            val id = (resourceParams[0] as? Number)?.toInt() ?: continue
            val typeName = resourceParams[1] as? String ?: continue
            val tier = (resourceParams[2] as? Number)?.toInt() ?: 0
            val posX = (resourceParams[3] as? Number)?.toFloat() ?: 0f
            val posY = (resourceParams[4] as? Number)?.toFloat() ?: 0f
            val size = (resourceParams[5] as? Number)?.toInt() ?: 0
            val enchant = (resourceParams[6] as? Number)?.toInt() ?: 0

            val resourceType = when {
                typeName.contains("WOOD", ignoreCase = true) -> "Logs"
                typeName.contains("ROCK", ignoreCase = true) -> "Rock"
                typeName.contains("FIBER", ignoreCase = true) -> "Fiber"
                typeName.contains("HIDE", ignoreCase = true) -> "Hide"
                typeName.contains("ORE", ignoreCase = true) -> "Ore"
                else -> typeName
            }

            val entity = GameEntity(
                id = id,
                type = EntityType.RESOURCE,
                name = resourceType,
                posX = posX,
                posY = posY,
                tier = tier,
                size = size,
                enchant = enchant,
                distance = calculateDistance(posX, posY)
            )

            entityManager.addOrUpdateEntity(entity)
        }
    }

    private fun handleMove(event: PhotonEvent) {
        val parameters = event.parameters ?: return
        val id = parameters[0] as? Int ?: return
        val posX = (parameters[1] as? Number)?.toFloat() ?: return
        val posY = (parameters[2] as? Number)?.toFloat() ?: return

        entityManager.updatePosition(id, posX, posY)
    }

    private fun handleLeave(event: PhotonEvent) {
        val parameters = event.parameters ?: return
        val id = parameters[0] as? Int ?: return
        entityManager.removeEntity(id)
    }

    private fun handleHealthUpdate(event: PhotonEvent) {
        val parameters = event.parameters ?: return
        val id = parameters[0] as? Int ?: return
        val health = (parameters[1] as? Number)?.toInt() ?: return
        entityManager.updateHealth(id, health)
    }

    private fun handleChest(event: PhotonEvent) {
        val parameters = event.parameters ?: return

        val id = parameters[0] as? Int ?: return
        val posX = (parameters[1] as? Number)?.toFloat() ?: 0f
        val posY = (parameters[2] as? Number)?.toFloat() ?: 0f
        val tier = (parameters[3] as? Number)?.toInt() ?: 0

        val entity = GameEntity(
            id = id,
            type = EntityType.CHEST,
            name = "Chest T$tier",
            posX = posX,
            posY = posY,
            tier = tier,
            distance = calculateDistance(posX, posY)
        )

        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleDungeon(event: PhotonEvent) {
        val parameters = event.parameters ?: return

        val id = parameters[0] as? Int ?: return
        val posX = (parameters[1] as? Number)?.toFloat() ?: 0f
        val posY = (parameters[2] as? Number)?.toFloat() ?: 0f
        val dungeonType = parameters[3] as? String ?: "Dungeon"

        val entity = GameEntity(
            id = id,
            type = EntityType.DUNGEON,
            name = dungeonType,
            posX = posX,
            posY = posY,
            distance = calculateDistance(posX, posY)
        )

        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleFishingSpot(event: PhotonEvent) {
        val parameters = event.parameters ?: return

        val id = parameters[0] as? Int ?: return
        val posX = (parameters[1] as? Number)?.toFloat() ?: 0f
        val posY = (parameters[2] as? Number)?.toFloat() ?: 0f

        val entity = GameEntity(
            id = id,
            type = EntityType.FISHING,
            name = "Fishing Spot",
            posX = posX,
            posY = posY,
            distance = calculateDistance(posX, posY)
        )

        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleMistPortal(event: PhotonEvent) {
        val parameters = event.parameters ?: return

        val id = parameters[0] as? Int ?: return
        val posX = (parameters[1] as? Number)?.toFloat() ?: 0f
        val posY = (parameters[2] as? Number)?.toFloat() ?: 0f
        val mistType = parameters[3] as? String ?: "Mist"

        val entity = GameEntity(
            id = id,
            type = EntityType.MIST_PORTAL,
            name = mistType,
            posX = posX,
            posY = posY,
            distance = calculateDistance(posX, posY)
        )

        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleWispCage(event: PhotonEvent) {
        val parameters = event.parameters ?: return

        val id = parameters[0] as? Int ?: return
        val posX = (parameters[1] as? Number)?.toFloat() ?: 0f
        val posY = (parameters[2] as? Number)?.toFloat() ?: 0f

        val entity = GameEntity(
            id = id,
            type = EntityType.WISP_CAGE,
            name = "Wisp Cage",
            posX = posX,
            posY = posY,
            distance = calculateDistance(posX, posY)
        )

        entityManager.addOrUpdateEntity(entity)
    }

    private fun calculateDistance(posX: Float, posY: Float): Float {
        val dx = posX - playerPosition.first
        val dy = posY - playerPosition.second
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun getPlayerName(): String {
        return ""
    }
}

data class PhotonEvent(
    val code: Int,
    val parameters: Map<Int, Any?>?
)
