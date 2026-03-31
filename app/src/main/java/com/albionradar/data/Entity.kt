package com.albionradar.data

data class GameEntity(
    val id: Int,
    val type: EntityType,
    val name: String = "",
    val guild: String = "",
    val alliance: String = "",
    val faction: Int = 0,
    val mounted: Boolean = false,
    val posX: Float = 0f,
    val posY: Float = 0f,
    var displayX: Float = posX,
    var displayY: Float = posY,
    val tier: Int = 1,
    val enchant: Int = 0,
    val size: Int = 0,
    val health: Int = 0,
    val maxHealth: Int = 1,
    val resourceType: String = "",
    val enemyType: Int = 0,
    val displayName: String = "",
    val dungeonType: Int = 0,
    val chestRarity: String = "common",
    val fishSize: Int = 0,
    val fishTotal: Int = 0,
    val isHostile: Boolean = false,
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    val distance: Float
        get() = kotlin.math.sqrt(posX * posX + posY * posY)
    
    val healthPercent: Int
        get() = if (maxHealth > 0) (health * 100 / maxHealth) else 0
    
    val isStale: Boolean
        get() = System.currentTimeMillis() - lastUpdateTime > 30000
}
