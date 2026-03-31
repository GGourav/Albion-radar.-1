package com.albionradar.photon

sealed class PhotonValue {
    data class NullValue(val value: Unit = Unit) : PhotonValue()
    data class ByteValue(val value: Byte) : PhotonValue()
    data class BooleanValue(val value: Boolean) : PhotonValue()
    data class ShortValue(val value: Short) : PhotonValue()
    data class IntegerValue(val value: Int) : PhotonValue()
    data class LongValue(val value: Long) : PhotonValue()
    data class FloatValue(val value: Float) : PhotonValue()
    data class DoubleValue(val value: Double) : PhotonValue()
    data class StringValue(val value: String) : PhotonValue()
    data class ByteArrayValue(val value: ByteArray) : PhotonValue() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ByteArrayValue) return false
            return value.contentEquals(other.value)
        }
        override fun hashCode(): Int = value.contentHashCode()
    }
    data class IntArrayValue(val value: IntArray) : PhotonValue() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is IntArrayValue) return false
            return value.contentEquals(other.value)
        }
        override fun hashCode(): Int = value.contentHashCode()
    }
    data class ArrayValue(val value: List<PhotonValue>) : PhotonValue()
    data class HashtableValue(val value: Map<PhotonValue, PhotonValue>) : PhotonValue()
    data class DictionaryValue(val value: Map<Any, PhotonValue>) : PhotonValue()
    
    fun asInt(): Int? = when (this) {
        is IntegerValue -> value
        is ShortValue -> value.toInt()
        is ByteValue -> value.toInt()
        is LongValue -> value.toInt()
        else -> null
    }
    
    fun asFloat(): Float? = when (this) {
        is FloatValue -> value
        is DoubleValue -> value.toFloat()
        is IntegerValue -> value.toFloat()
        is LongValue -> value.toFloat()
        is ShortValue -> value.toFloat()
        is ByteValue -> value.toFloat()
        else -> null
    }
    
    fun asString(): String? = when (this) {
        is StringValue -> value
        else -> null
    }
    
    fun asByteArray(): ByteArray? = when (this) {
        is ByteArrayValue -> value
        else -> null
    }
    
    fun asIntArray(): IntArray? = when (this) {
        is IntArrayValue -> value
        is ArrayValue -> value.mapNotNull { it.asInt() }.toIntArray()
        else -> null
    }
    
    fun asArray(): List<PhotonValue>? = when (this) {
        is ArrayValue -> value
        else -> null
    }

    fun asDictionary(): Map<Any, PhotonValue>? = when (this) {
        is DictionaryValue -> value
        is HashtableValue -> value.mapKeys { it.key.asInt() ?: it.key.asString() ?: it.key }
        else -> null
    }
    
    override fun toString(): String = when (this) {
        is NullValue -> "null"
        is ByteValue -> "Byte($value)"
        is BooleanValue -> "Boolean($value)"
        is ShortValue -> "Short($value)"
        is IntegerValue -> "Int($value)"
        is LongValue -> "Long($value)"
        is FloatValue -> "Float($value)"
        is DoubleValue -> "Double($value)"
        is StringValue -> "String(\"$value\")"
        is ByteArrayValue -> "ByteArray(${value.size} bytes)"
        is IntArrayValue -> "IntArray(${value.size} ints)"
        is ArrayValue -> "Array(${value.size} items)"
        is HashtableValue -> "Hashtable(${value.size} entries)"
        is DictionaryValue -> "Dictionary(${value.size} entries)"
    }
}
