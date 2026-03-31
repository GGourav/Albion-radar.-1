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

    private val entities = ConcurrentHashMap<Int, GameEntity>()
    private val _entityList = MutableStateFlow<List<GameEntity>>(emptyList())
    val entities: StateFlow<List<GameEntity>> = _entityList.asStateFlow()

    private val _zoneName = MutableStateFlow("Unknown")
    val zoneName: StateFlow<String> = _zoneName.asStateFlow()

    fun addOrUpdateEntity(entity: GameEntity) {
        val existing = entities[entity.id]
        if (existing != null) {
            val updated = entity.copy(
                displayX = existing.displayX,
                displayY = existing.displayY,
                lastUpdateTime = System.currentTimeMillis()
            )
            entities[entity.id] = updated
        } else {
            entities[entity.id] = entity.copy(
                displayX = entity.posX,
                displayY = entity.posY
            )
        }
        notifyChanged()
    }

    fun removeEntity(id: Int) {
        entities.remove(id)
        notifyChanged()
    }

    fun getEntity(id: Int): GameEntity? = entities[id]

    fun getEntitiesByType(type: EntityType): List<GameEntity> {
        return entities.values.filter { it.type == type }.sortedBy { it.distance }
    }

    fun getPlayerCount(): Int = entities.values.count { it.type == EntityType.PLAYER }
    fun getMobCount(): Int = entities.values.count { it.type == EntityType.MOB }
    fun getResourceCount(): Int = entities.values.count { it.type == EntityType.RESOURCE }

    fun updatePosition(entityId: Int, posX: Float, posY: Float) {
        val entity = entities[entityId] ?: return
        entities[entityId] = entity.copy(
            posX = posX,
            posY = posY,
            lastUpdateTime = System.currentTimeMillis()
        )
        notifyChanged()
    }

    fun updateHealth(entityId: Int, health: Int) {
        val entity = entities[entityId] ?: return
        entities[entityId] = entity.copy(
            health = health,
            lastUpdateTime = System.currentTimeMillis()
        )
        notifyChanged()
    }

    fun updatePlayerFaction(entityId: Int, faction: Int) {
        val entity = entities[entityId] ?: return
        entities[entityId] = entity.copy(
            faction = faction,
            lastUpdateTime = System.currentTimeMillis()
        )
        notifyChanged()
    }

    fun updateHarvestableState(entityId: Int, size: Int?, enchant: Int?) {
        val entity = entities[entityId] ?: return
        if (size == null) {
            removeEntity(entityId)
            return
        }
        entities[entityId] = entity.copy(
            size = size,
            enchant = enchant ?: entity.enchant,
            lastUpdateTime = System.currentTimeMillis()
        )
        notifyChanged()
    }

    fun clearAll() {
        entities.clear()
        notifyChanged()
    }

    fun setZoneName(name: String) {
        _zoneName.value = name
    }

    fun cleanupStale() {
        val stale = entities.entries.removeIf { it.value.isStale }
        if (stale) notifyChanged()
    }

    private fun notifyChanged() {
        _entityList.value = entities.values.toList()
    }
}
