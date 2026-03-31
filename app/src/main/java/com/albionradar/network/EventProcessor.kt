package com.albionradar.network

import android.util.Log
import com.albionradar.data.*
import com.albionradar.photon.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

class EventProcessor {

    companion object {
        private const val TAG = "EventProcessor"
    }

    private val photonParser = PhotonParser()
    private val entityManager = EntityManager.getInstance()
    private val dataManager by lazy { DataManager.getInstance() }
    
    private var processingScope: CoroutineScope? = null
    private val packetQueue = ConcurrentLinkedQueue<ByteArray>()
    
    private var playerPosX = 0f
    private var playerPosY = 0f

    fun start() {
        processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        processingScope?.launch {
            dataManager.loadDatabases()
            processQueue()
        }
    }

    fun stop() {
        processingScope?.cancel()
        processingScope = null
    }

    fun processPacket(payload: ByteArray) {
        packetQueue.offer(payload)
    }

    private fun processQueue() {
        processingScope?.launch {
            while (isActive) {
                val payload = packetQueue.poll()
                if (payload != null) {
                    try {
                        processPhotonPacket(payload)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing packet", e)
                    }
                } else {
                    delay(1)
                }
            }
        }
    }

    private fun processPhotonPacket(payload: ByteArray) {
        val events = photonParser.parsePacket(payload)
        
        for (event in events) {
            handleEvent(event)
        }
    }

    private fun handleEvent(event: GameEvent) {
        when (event.eventCode) {
            AlbionEvents.LEAVE -> handleLeave(event)
            AlbionEvents.MOVE -> handleMove(event)
            AlbionEvents.NEW_CHARACTER -> handleNewCharacter(event)
            AlbionEvents.NEW_SIMPLE_HARVESTABLE_LIST -> handleHarvestableList(event)
            AlbionEvents.NEW_HARVESTABLE -> handleNewHarvestable(event)
            AlbionEvents.HARVESTABLE_CHANGE_STATE -> handleHarvestableState(event)
            AlbionEvents.NEW_MOB -> handleNewMob(event)
            AlbionEvents.MOB_CHANGE_STATE -> handleMobState(event)
            AlbionEvents.NEW_DUNGEON_EXIT -> handleNewDungeon(event)
            AlbionEvents.NEW_LOOT_CHEST -> handleNewChest(event)
            AlbionEvents.NEW_FISHING_ZONE -> handleNewFishing(event)
            AlbionEvents.NEW_MIST_PORTAL -> handleNewMist(event)
            AlbionOperations.OP_CHANGE_CLUSTER or 0x1000 -> handleChangeCluster(event)
        }
    }

    private fun handleLeave(event: GameEvent) {
        entityManager.removeEntity(event.entityId)
    }

    private fun handleMove(event: GameEvent) {
        val position = photonParser.extractPositionFromMove(event.parameters)
        if (position != null) {
            entityManager.updatePosition(event.entityId, position.first, position.second)
        }
    }

    private fun handleNewCharacter(event: GameEvent) {
        val name = event.getString(1) ?: return
        val guild = event.getString(44) ?: ""
        val alliance = event.getString(46) ?: ""
        val faction = event.getInt(51) ?: 0
        val mounted = event.getInt(55) ?: 0
        
        val posX = event.getFloat(9) ?: 0f
        val posY = event.getFloat(10) ?: 0f
        
        val entity = GameEntity(
            id = event.entityId,
            type = EntityType.PLAYER,
            name = name,
            guild = guild,
            alliance = alliance,
            faction = faction,
            mounted = mounted > 0,
            posX = posX,
            posY = posY,
            isHostile = faction == 255
        )
        
        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleHarvestableList(event: GameEvent) {
        val harvestables = event.getArray(0) ?: return
        
        for (h in harvestables) {
            try {
                val params = h.asArray() ?: continue
                if (params.size < 8) continue
                
                val typeId = params[0].asInt() ?: continue
                val posX = params[3].asFloat() ?: continue
                val posY = params[4].asFloat() ?: continue
                val size = params[5].asInt() ?: 1
                val tier = params[6].asInt() ?: 1
                val enchant = params[7].asInt() ?: 0
                
                val resourceType = ResourceTypeNumbers.getTypeName(typeId)
                
                val entity = GameEntity(
                    id = event.entityId * 1000 + typeId,
                    type = EntityType.RESOURCE,
                    posX = posX,
                    posY = posY,
                    tier = tier,
                    enchant = enchant,
                    size = size,
                    resourceType = resourceType
                )
                
                entityManager.addOrUpdateEntity(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing harvestable", e)
            }
        }
    }

    private fun handleNewHarvestable(event: GameEvent) {
        val typeId = event.getInt(1) ?: return
        val posX = event.getFloat(3) ?: return
        val posY = event.getFloat(4) ?: return
        val size = event.getInt(5) ?: 1
        val tier = event.getInt(6) ?: 1
        val enchant = event.getInt(7) ?: 0
        
        val resourceType = ResourceTypeNumbers.getTypeName(typeId)
        
        val entity = GameEntity(
            id = event.entityId,
            type = EntityType.RESOURCE,
            posX = posX,
            posY = posY,
            tier = tier,
            enchant = enchant,
            size = size,
            resourceType = resourceType
        )
        
        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleHarvestableState(event: GameEvent) {
        val size = event.getInt(1)
        val enchant = event.getInt(2)
        entityManager.updateHarvestableState(event.entityId, size, enchant)
    }

    private fun handleNewMob(event: GameEvent) {
        val typeId = event.getInt(1) ?: return
        val posX = event.getFloat(3) ?: return
        val posY = event.getFloat(4) ?: return
        val health = event.getInt(7) ?: 0
        val maxHealth = event.getInt(8) ?: 1
        
        val (enemyType, resourceName) = dataManager.classifyMob(typeId)
        val mobInfo = dataManager.getMobInfo(typeId)
        val tier = mobInfo?.tier ?: 1
        val displayName = mobInfo?.let { dataManager.getMobDisplayName(it) } ?: "Mob"
        
        val entity = GameEntity(
            id = event.entityId,
            type = EntityType.MOB,
            posX = posX,
            posY = posY,
            tier = tier,
            health = health,
            maxHealth = maxHealth,
            enemyType = enemyType,
            displayName = displayName
        )
        
        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleMobState(event: GameEvent) {
        val health = event.getInt(1) ?: return
        entityManager.updateHealth(event.entityId, health)
    }

    private fun handleNewDungeon(event: GameEvent) {
        val posX = event.getFloat(3) ?: return
        val posY = event.getFloat(4) ?: return
        val dungeonType = event.getInt(1) ?: 0
        val enchant = event.getInt(5) ?: 0
        
        val entity = GameEntity(
            id = event.entityId,
            type = EntityType.DUNGEON,
            posX = posX,
            posY = posY,
            dungeonType = dungeonType,
            enchant = enchant
        )
        
        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleNewChest(event: GameEvent) {
        val posX = event.getFloat(3) ?: return
        val posY = event.getFloat(4) ?: return
        val rarity = event.getString(1) ?: "common"
        
        val entity = GameEntity(
            id = event.entityId,
            type = EntityType.CHEST,
            posX = posX,
            posY = posY,
            chestRarity = rarity
        )
        
        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleNewFishing(event: GameEvent) {
        val posX = event.getFloat(3) ?: return
        val posY = event.getFloat(4) ?: return
        val fishSize = event.getInt(1) ?: 0
        val fishTotal = event.getInt(2) ?: 0
        
        val entity = GameEntity(
            id = event.entityId,
            type = EntityType.FISHING,
            posX = posX,
            posY = posY,
            fishSize = fishSize,
            fishTotal = fishTotal
        )
        
        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleNewMist(event: GameEvent) {
        val posX = event.getFloat(3) ?: return
        val posY = event.getFloat(4) ?: return
        val enchant = event.getInt(1) ?: 0
        
        val entity = GameEntity(
            id = event.entityId,
            type = EntityType.MIST,
            posX = posX,
            posY = posY,
            enchant = enchant
        )
        
        entityManager.addOrUpdateEntity(entity)
    }

    private fun handleChangeCluster(event: GameEvent) {
        entityManager.clearAll()
        
        val zoneName = event.getString(1) ?: "Unknown"
        entityManager.setZoneName(zoneName)
    }
}
