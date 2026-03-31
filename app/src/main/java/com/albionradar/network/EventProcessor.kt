package com.albionradar.network

import android.util.Log
import com.albionradar.data.*
import com.albionradar.photon.PhotonParser
import com.albionradar.photon.GameEvent
import kotlinx.coroutines.*

class EventProcessor {

    companion object {
        private const val TAG = "EventProcessor"
        const val EVENT_LEAVE = 1
        const val EVENT_MOVE = 3
        const val EVENT_NEW_CHARACTER = 29
        const val EVENT_BATCH_RESOURCES = 38
        const val EVENT_NEW_RESOURCE = 40
        const val EVENT_NEW_MOB = 123
        const val EVENT_HEALTH_UPDATE = 50
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
            val events = photonParser.parsePacket(payload)
            for (event in events) {
                processEvent(event)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error processing packet: ${e.message}")
        }
    }

    private fun processEvent(event: GameEvent) {
        when (event.eventCode) {
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

    private fun handleNewCharacter(event: GameEvent) {
        val id = event.getInt(0) ?: return
        val name = event.getString(1) ?: "Unknown"
        val posX = event.getFloat(2) ?: 0f
        val posY = event.getFloat(3) ?: 0f
        val faction = event.getInt(4) ?: 0
        val guild = event.getString(5) ?: ""
        val alliance = event.getString(6) ?: ""

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
            distance = calculateDistance(posX, posY),
            isHostile = faction == 255
        )

        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleNewMob(event: GameEvent) {
        val id = event.getInt(0) ?: return
        val typeId = event.getInt(1) ?: 0
        val posX = event.getFloat(2) ?: 0f
        val posY = event.getFloat(3) ?: 0f
        val health = event.getInt(4) ?: 100
        val maxHealth = event.getInt(5) ?: 100

        val dataManager = try { DataManager.getInstance() } catch (e: Exception) { null }
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
            enemyType = enemyType,
            distance = calculateDistance(posX, posY)
        )

        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleNewResource(event: GameEvent) {
        val id = event.getInt(0) ?: return
        val typeName = event.getString(1) ?: return
        val tier = event.getInt(2) ?: 0
        val posX = event.getFloat(3) ?: 0f
        val posY = event.getFloat(4) ?: 0f
        val size = event.getInt(5) ?: 0
        val enchant = event.getInt(6) ?: 0

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
            resourceType = resourceType,
            distance = calculateDistance(posX, posY)
        )

        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleBatchResources(event: GameEvent) {
        val batch = event.getArray(0) ?: return

        for (item in batch) {
            val paramMap = item.asDictionary() ?: continue
            val id = (paramMap[0] as? PhotonValue)?.asInt() ?: continue
            val typeName = (paramMap[1] as? PhotonValue)?.asString() ?: continue
            val tier = (paramMap[2] as? PhotonValue)?.asInt() ?: 0
            val posX = (paramMap[3] as? PhotonValue)?.asFloat() ?: 0f
            val posY = (paramMap[4] as? PhotonValue)?.asFloat() ?: 0f
            val size = (paramMap[5] as? PhotonValue)?.asInt() ?: 0
            val enchant = (paramMap[6] as? PhotonValue)?.asInt() ?: 0

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
                resourceType = resourceType,
                distance = calculateDistance(posX, posY)
            )

            entityManager.addOrUpdateEntity(entity)
        }
    }

    private fun handleMove(event: GameEvent) {
        val id = event.getInt(0) ?: return
        val posX = event.getFloat(1) ?: return
        val posY = event.getFloat(2) ?: return
        entityManager.updatePosition(id, posX, posY)
    }

    private fun handleLeave(event: GameEvent) {
        val id = event.getInt(0) ?: return
        entityManager.removeEntity(id)
    }

    private fun handleHealthUpdate(event: GameEvent) {
        val id = event.getInt(0) ?: return
        val health = event.getInt(1) ?: return
        entityManager.updateHealth(id, health)
    }

    private fun handleChest(event: GameEvent) {
        val id = event.getInt(0) ?: return
        val posX = event.getFloat(1) ?: 0f
        val posY = event.getFloat(2) ?: 0f
        val tier = event.getInt(3) ?: 0

        val chestRarity = when (tier) {
            in 5..8 -> "legendary"
            in 3..4 -> "rare"
            else -> "standard"
        }

        val entity = GameEntity(
            id = id,
            type = EntityType.CHEST,
            name = "Chest T$tier",
            posX = posX,
            posY = posY,
            tier = tier,
            chestRarity = chestRarity,
            distance = calculateDistance(posX, posY)
        )

        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleDungeon(event: GameEvent) {
        val id = event.getInt(0) ?: return
        val posX = event.getFloat(1) ?: 0f
        val posY = event.getFloat(2) ?: 0f
        val dungeonType = event.getInt(3) ?: 0
        val enchant = event.getInt(4) ?: 0

        val entity = GameEntity(
            id = id,
            type = EntityType.DUNGEON,
            name = "Dungeon",
            posX = posX,
            posY = posY,
            dungeonType = dungeonType,
            enchant = enchant,
            distance = calculateDistance(posX, posY)
        )

        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleFishingSpot(event: GameEvent) {
        val id = event.getInt(0) ?: return
        val posX = event.getFloat(1) ?: 0f
        val posY = event.getFloat(2) ?: 0f
        val fishSize = event.getInt(3) ?: 0
        val fishTotal = event.getInt(4) ?: 0

        val entity = GameEntity(
            id = id,
            type = EntityType.FISHING,
            name = "Fishing Spot",
            posX = posX,
            posY = posY,
            fishSize = fishSize,
            fishTotal = fishTotal,
            distance = calculateDistance(posX, posY)
        )

        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleMistPortal(event: GameEvent) {
        val id = event.getInt(0) ?: return
        val posX = event.getFloat(1) ?: 0f
        val posY = event.getFloat(2) ?: 0f
        val enchant = event.getInt(3) ?: 0

        val entity = GameEntity(
            id = id,
            type = EntityType.MIST_PORTAL,
            name = "Mist Portal",
            posX = posX,
            posY = posY,
            enchant = enchant,
            distance = calculateDistance(posX, posY)
        )

        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleWispCage(event: GameEvent) {
        val id = event.getInt(0) ?: return
        val posX = event.getFloat(1) ?: 0f
        val posY = event.getFloat(2) ?: 0f

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

    private fun getPlayerName(): String = ""
}
