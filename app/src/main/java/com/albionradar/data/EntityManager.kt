package com.albionradar.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class EntityManager private constructor() {

    companion object {
        @Volatile
        private var instance: EntityManager? = null

        fun getInstance(): EntityManager {
            return instance ?: synchronized(this) {
                instance ?: EntityManager().also { instance = it }
            }
        }
    }

    // Private backing map for entity storage
    private val entityMap = ConcurrentHashMap<Int, GameEntity>()

    // Public StateFlow for observing entities
    private val _entityList = MutableStateFlow<List<GameEntity>>(emptyList())
    val entities: StateFlow<List<GameEntity>> = _entityList.asStateFlow()

    private val _zoneName = MutableStateFlow("Unknown")
    val zoneName: StateFlow<String> = _zoneName.asStateFlow()

    fun addOrUpdateEntity(entity: GameEntity) {
        val existing = entityMap[entity.id]
        if (existing != null) {
            val updated = entity.copy(
                displayX = existing.displayX,
                displayY = existing.displayY,
                lastUpdateTime = System.currentTimeMillis()
            )
            entityMap[entity.id] = updated
        } else {
            entityMap[entity.id] = entity.copy(
                displayX = entity.posX,
                displayY = entity.posY
            )
        }
        notifyChanged()
    }

    fun removeEntity(id: Int) {
        entityMap.remove(id)
        notifyChanged()
    }

    fun getEntity(id: Int): GameEntity? = entityMap[id]

    fun getEntitiesByType(type: EntityType): List<GameEntity> {
        return entityMap.values.filter { it.type == type }.sortedBy { it.distance }
    }

    fun getPlayerCount(): Int = entityMap.values.count { it.type == EntityType.PLAYER }
    fun getMobCount(): Int = entityMap.values.count { it.type == EntityType.MOB }
    fun getResourceCount(): Int = entityMap.values.count { it.type == EntityType.RESOURCE }

    fun updatePosition(entityId: Int, posX: Float, posY: Float) {
        val entity = entityMap[entityId] ?: return
        entityMap[entityId] = entity.copy(
            posX = posX,
            posY = posY,
            lastUpdateTime = System.currentTimeMillis()
        )
        notifyChanged()
    }

    fun updateHealth(entityId: Int, health: Int) {
        val entity = entityMap[entityId] ?: return
        entityMap[entityId] = entity.copy(
            health = health,
            lastUpdateTime = System.currentTimeMillis()
        )
        notifyChanged()
    }

    fun updatePlayerFaction(entityId: Int, faction: Int) {
        val entity = entityMap[entityId] ?: return
        entityMap[entityId] = entity.copy(
            faction = faction,
            lastUpdateTime = System.currentTimeMillis()
        )
        notifyChanged()
    }

    fun updateHarvestableState(entityId: Int, size: Int?, enchant: Int?) {
        val entity = entityMap[entityId] ?: return
        if (size == null) {
            removeEntity(entityId)
            return
        }
        entityMap[entityId] = entity.copy(
            size = size,
            enchant = enchant ?: entity.enchant,
            lastUpdateTime = System.currentTimeMillis()
        )
        notifyChanged()
    }

    fun clearAll() {
        entityMap.clear()
        notifyChanged()
    }

    fun setZoneName(name: String) {
        _zoneName.value = name
    }

    fun cleanupStale() {
        val stale = entityMap.entries.removeIf { it.value.isStale }
        if (stale) notifyChanged()
    }

    private fun notifyChanged() {
        _entityList.value = entityMap.values.toList()
    }
}
