package com.wfphantom.datagear.api

import com.google.gson.JsonObject

data class Condition(
    val property: String,
    val min: Double? = null,
    val max: Double? = null,
    val equals: Any? = null
) {
    fun test(value: Double): Boolean {
        if (min != null && value < min) return false
        if (max != null && value > max) return false
        if (equals != null) {
            val target = when (equals) {
                is Number -> equals.toDouble()
                else -> return false
            }
            if (value != target) return false
        }
        return true
    }

    fun testString(value: String): Boolean {
        if (equals != null && equals is String) return value == equals
        return true
    }
    companion object {
        fun fromJson(property: String, json: JsonObject): Condition {
            return Condition(
                property = property,
                min = if (json.has("min")) json.get("min").asDouble else null,
                max = if (json.has("max")) json.get("max").asDouble else null,
                equals = if (json.has("equals")) {
                    val elem = json.get("equals")
                    if (elem.isJsonPrimitive) {
                        val prim = elem.asJsonPrimitive
                        when {
                            prim.isNumber -> prim.asDouble
                            prim.isString -> prim.asString
                            prim.isBoolean -> prim.asBoolean
                            else -> null
                        }
                    } else null
                } else null
            )
        }
    }
}

sealed class LogicalCondition {
    data class And(val conditions: List<LogicalCondition>) : LogicalCondition()
    data class Or(val conditions: List<LogicalCondition>) : LogicalCondition()
    data class Not(val condition: LogicalCondition) : LogicalCondition()
    data class Single(val condition: Condition) : LogicalCondition()

    companion object {
        fun fromJson(json: JsonObject): LogicalCondition {
            if (json.has("AND")) {
                val arr = json.getAsJsonArray("AND")
                val conditions = arr.map { fromJson(it.asJsonObject) }
                return And(conditions)
            }
            if (json.has("OR")) {
                val arr = json.getAsJsonArray("OR")
                val conditions = arr.map { fromJson(it.asJsonObject) }
                return Or(conditions)
            }
            if (json.has("NOT")) return Not(fromJson(json.getAsJsonObject("NOT")))
            // simple property conditions, wrap multiple as AND
            val conditions = json.entrySet().map { (key, value) -> Single(Condition.fromJson(key, value.asJsonObject)) }
            return if (conditions.size == 1) conditions.first() else And(conditions)
        }
    }
}
