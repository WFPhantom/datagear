package com.wfphantom.datagear.api

import com.google.gson.JsonObject
import com.wfphantom.datagear.engine.ModifierEngine
import net.minecraft.world.item.ItemStack

interface Condition {
    val property: String
    fun test(stack: ItemStack): Boolean
    fun getFailureReason(stack: ItemStack): String?
}

data class PropertyCondition(
    override val property: String,
    val min: Double? = null,
    val max: Double? = null,
    val equals: Any? = null
) : Condition {
    override fun test(stack: ItemStack): Boolean {
        val numericValue = ModifierEngine.getPropertyValue(property, stack)
        if (numericValue != null) {
            if (min != null && numericValue < min) return false
            if (max != null && numericValue > max) return false
            if (equals != null) {
                val target = when (equals) {
                    is Number -> equals.toDouble()
                    is Boolean -> if (equals) 1.0 else 0.0
                    else -> return false
                }
                if (numericValue != target) return false
            }
            return true
        }

        val booleanValue = ModifierEngine.getBooleanPropertyValue(property, stack)
        if (booleanValue != null) {
            if (equals is Boolean) return booleanValue == equals
            return true
        }

        val stringValue = ModifierEngine.getStringPropertyValue(property, stack)
        if (stringValue != null) {
            if (equals is String) return stringValue == equals
            return true
        }
        return false
    }

    override fun getFailureReason(stack: ItemStack): String? {
        if (test(stack)) return null

        val current = ModifierEngine.getPropertyValue(property, stack)
            ?: ModifierEngine.getBooleanPropertyValue(property, stack)
            ?: ModifierEngine.getStringPropertyValue(property, stack)

        val expected = when {
            equals != null -> "\"$equals\""
            min != null && max != null -> "between $min and $max"
            min != null -> ">= $min"
            max != null -> "<= $max"
            else -> "unknown"
        }

        return if (current == null) "$property: not set (expected $expected)"
        else "$property: got \"$current\" (expected $expected)"
    }

    companion object {
        fun fromJson(property: String, json: JsonObject): PropertyCondition {
            return PropertyCondition(
                property = property,
                min = if (json.has("min")) json.get("min").asDouble else null,
                max = if (json.has("max")) json.get("max").asDouble else null
            )
        }
    }
}

sealed class LogicalCondition {
    data class And(val conditions: List<LogicalCondition>) : LogicalCondition()
    data class Or(val conditions: List<LogicalCondition>) : LogicalCondition()
    data class Not(val condition: LogicalCondition) : LogicalCondition()
    data class Single(val condition: Condition) : LogicalCondition()

    fun hasPerInstanceCondition(): Boolean = when (this) {
        is And -> conditions.any { it.hasPerInstanceCondition() }
        is Or -> conditions.any { it.hasPerInstanceCondition() }
        is Not -> condition.hasPerInstanceCondition()
        is Single -> condition.property == "custom_name"
    }

    companion object {
        fun fromJson(json: JsonObject): LogicalCondition {
            if (json.has("OR")) return Or(json.getAsJsonArray("OR").map { fromJson(it.asJsonObject) })
            if (json.has("NOT")) return Not(fromJson(json.getAsJsonObject("NOT")))
            // Multiple conditions at the same level are implicitly ANDed
            val conditions = json.entrySet().map { (key, value) ->
                if (value.isJsonPrimitive) {
                    val prim = value.asJsonPrimitive
                    val equals = when {
                        prim.isBoolean -> prim.asBoolean
                        prim.isNumber -> prim.asDouble
                        prim.isString -> prim.asString
                        else -> null
                    }
                    Single(PropertyCondition(property = key, equals = equals))
                } else Single(PropertyCondition.fromJson(key, value.asJsonObject))
            }
            return if (conditions.size == 1) conditions.first() else And(conditions)
        }
    }
}
