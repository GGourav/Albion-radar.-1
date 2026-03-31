package com.albionradar.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.albionradar.R
import com.albionradar.data.GameEntity

class EntityAdapter : ListAdapter<GameEntity, EntityAdapter.EntityViewHolder>(EntityDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return EntityViewHolder(view)
    }

    override fun onBindViewHolder(holder: EntityViewHolder, position: Int) {
        val entity = currentList[position]
        holder.bind(entity)
    }

    class EntityViewHolder(
        private val view: android.view.View
    ) : RecyclerView.ViewHolder(view) {

        private val text1: android.widget.TextView = view.findViewById(android.R.id.text1)
        private val text2: android.widget.TextView = view.findViewById(android.R.id.text2)

        fun bind(entity: GameEntity) {
            text1.text = entity.displayName
            text2.text = buildString {
                append("T${entity.tier}")
                if (entity.enchant > 0) append(".${entity.enchant}")
                append(" | Dist: ${entity.distance.toInt()}m")
                if (entity.type == com.albionradar.data.EntityType.PLAYER) {
                    append(" | ${entity.guild}")
                }
                if (entity.type == com.albionradar.data.EntityType.MOB) {
                    append(" | HP: ${entity.healthPercent}%")
                }
            }

            val color = when (entity.type) {
                com.albionradar.data.EntityType.RESOURCE -> getResourceColor(entity.resourceType)
                com.albionradar.data.EntityType.MOB -> getMobColor(entity.enemyType)
                com.albionradar.data.EntityType.PLAYER -> getPlayerColor(entity.faction)
                com.albionradar.data.EntityType.DUNGEON -> view.context.getColor(R.color.dungeon)
                com.albionradar.data.EntityType.CHEST -> view.context.getColor(R.color.chest)
                com.albionradar.data.EntityType.FISHING -> view.context.getColor(R.color.fishing)
                com.albionradar.data.EntityType.MIST -> view.context.getColor(R.color.mist_portal)
            }
            text1.setTextColor(color)
            text2.setTextColor(view.context.getColor(R.color.on_surface))
        }

        private fun getResourceColor(type: String): Int {
            return when (type.uppercase()) {
                "ORE" -> view.context.getColor(R.color.resource_ore)
                "WOOD", "LOG" -> view.context.getColor(R.color.resource_wood)
                "ROCK" -> view.context.getColor(R.color.resource_rock)
                "FIBER" -> view.context.getColor(R.color.resource_fiber)
                "HIDE" -> view.context.getColor(R.color.resource_hide)
                else -> view.context.getColor(R.color.on_surface)
            }
        }

        private fun getMobColor(enemyType: Int): Int {
            return when (enemyType) {
                0, 1 -> view.context.getColor(R.color.mob_boss)
                2 -> view.context.getColor(R.color.mob_normal)
                4 -> view.context.getColor(R.color.mob_enchanted)
                5 -> view.context.getColor(R.color.mob_veteran)
                6 -> view.context.getColor(R.color.mob_boss)
                else -> view.context.getColor(R.color.mob_normal)
            }
        }

        private fun getPlayerColor(faction: Int): Int {
            return when (faction) {
                255 -> view.context.getColor(R.color.player_hostile)
                0 -> view.context.getColor(R.color.player_neutral)
                else -> view.context.getColor(R.color.player_faction)
            }
        }
    }

    private class EntityDiffCallback : DiffUtil.ItemCallback<GameEntity>() {
        override fun areItemsTheSame(oldItem: GameEntity, newItem: GameEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GameEntity, newItem: GameEntity): Boolean {
            return oldItem == newItem
        }
    }
}
