package com.albionradar.data

data class GameEntity(
    val id: Int,
    val type: EntityType,
    val name: String,
    val posX: Float,
    val posY: Float,
    val displayX: Float = posX,
    val displayY: Float = posY,
    val health: Int = 100,
    val maxHealth: Int = 100,
    val faction: Int = 0,
    val guild: String = "",
    val alliance: String = "",
    val tier: Int = 0,
    val size: Int = 0,
    val enchant: Int = 0,
    val distance: Float = 0f,
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    val isStale: Boolean
        get() = System.currentTimeMillis() - lastUpdateTime > 30000 // 30 seconds

    val healthPercent: Float
        get() = if (maxHealth > 0) health.toFloat() / maxHealth else 1f

    val displayName: String
        get() = when (type) {
            EntityType.RESOURCE -> {
                val tierStr = if (tier > 0) "T$tier" else ""
                val enchantStr = if (enchant > 0) ".$enchant" else ""
                "$name $tierStr$enchantStr".trim()
            }
            else -> name
        }
}
