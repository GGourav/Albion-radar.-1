package com.albionradar.photon

object PhotonTypes {
    const val TYPE_NULL: Byte = 0x42
    const val TYPE_BYTE: Byte = 0x62
    const val TYPE_BOOLEAN: Byte = 0x6B
    const val TYPE_SHORT: Byte = 0x73
    const val TYPE_INTEGER: Byte = 0x69
    const val TYPE_LONG: Byte = 0x6C
    const val TYPE_FLOAT: Byte = 0x66
    const val TYPE_DOUBLE: Byte = 0x64
    const val TYPE_STRING: Byte = 0x73
    const val TYPE_BYTE_ARRAY: Byte = 0x78
    const val TYPE_INT_ARRAY: Byte = 0x6E
    const val TYPE_ARRAY: Byte = 0x79
    const val TYPE_HASHTABLE: Byte = 0x68
    const val TYPE_DICTIONARY: Byte = 0x61
    const val TYPE_EVENT_DATA: Byte = 0x65
    const val TYPE_OPERATION_REQUEST: Byte = 0x71
    const val TYPE_OPERATION_RESPONSE: Byte = 0x70
}

object MessageTypes {
    const val TYPE_REQUEST = 2
    const val TYPE_RESPONSE = 3
    const val TYPE_EVENT = 4
}

object CommandTypes {
    const val TYPE_DISCONNECT = 4
    const val TYPE_RELIABLE = 6
    const val TYPE_UNRELIABLE = 7
}

object AlbionEvents {
    const val LEAVE = 1
    const val MOVE = 3
    const val HEALTH_UPDATE = 6
    const val HEALTH_UPDATES = 7
    const val NEW_CHARACTER = 29
    const val NEW_SIMPLE_HARVESTABLE_LIST = 38
    const val NEW_HARVESTABLE = 40
    const val HARVESTABLE_CHANGE_STATE = 46
    const val MOB_CHANGE_STATE = 47
    const val NEW_MOB = 123
    const val COMBAT_STATE_UPDATE = 273
    const val NEW_DUNGEON_EXIT = 319
    const val NEW_LOOT_CHEST = 387
    const val NEW_TREASURE_CHEST = 388
    const val NEW_FISHING_ZONE = 389
    const val NEW_MIST_PORTAL = 525
}

object AlbionOperations {
    const val OP_JOIN = 2
    const val OP_CHANGE_CLUSTER = 4
    const val OP_MOVE = 21
}

object ResourceTypeNumbers {
    const val WOOD_START = 0
    const val WOOD_END = 5
    const val ROCK_START = 6
    const val ROCK_END = 10
    const val FIBER_START = 11
    const val FIBER_END = 15
    const val HIDE_START = 16
    const val HIDE_END = 22
    const val ORE_START = 23
    const val ORE_END = 27
    
    fun getTypeName(typeNumber: Int): String {
        return when {
            typeNumber in WOOD_START..WOOD_END -> "WOOD"
            typeNumber in ROCK_START..ROCK_END -> "ROCK"
            typeNumber in FIBER_START..FIBER_END -> "FIBER"
            typeNumber in HIDE_START..HIDE_END -> "HIDE"
            typeNumber in ORE_START..ORE_END -> "ORE"
            else -> "UNKNOWN"
        }
    }
    
    fun isResource(typeNumber: Int): Boolean {
        return typeNumber in 0..27
    }
}

object PlayerFactions {
    const val PASSIVE = 0
    const val BRIDGEWATCH = 1
    const val MARTLOCK = 2
    const val THETFORD = 3
    const val FORTSTERLING = 4
    const val LYMHURST = 5
    const val CAERLEON = 6
    const val HOSTILE = 255
    
    fun isHostile(faction: Int): Boolean = faction == HOSTILE
    fun isFaction(faction: Int): Boolean = faction in 1..6
    fun isPassive(faction: Int): Boolean = faction == PASSIVE
}
