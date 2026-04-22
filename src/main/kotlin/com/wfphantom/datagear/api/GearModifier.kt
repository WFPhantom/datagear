package com.wfphantom.datagear.api

import com.google.gson.JsonObject
import net.minecraft.resources.Identifier

// represents a single gear modification entry loaded from a datapack JSON.
data class GearModifier(
    val id: Identifier,
    val targets: List<String>,
    val exclude: List<String> = emptyList(),
    val conditions: LogicalCondition?,
    val modifiers: Map<String, Double>,
    val stringModifiers: Map<String, String> = emptyMap(),
    val booleanModifiers: Map<String, Boolean> = emptyMap(),
    val operation: Operation,
    val priority: Int = 0,
    val slot: String? = null
) {
    val targetDisplay: String get() = targets.joinToString(", ") + if (exclude.isNotEmpty()) " (exclude: ${exclude.joinToString(", ")})" else ""

    // IGNORE I WILL FIX IT LMAO
    companion object {
        fun fromJson(id: Identifier, json: JsonObject): GearModifier {
            val targets =
                if (json.get("target").isJsonArray) json.getAsJsonArray("target").map { it.asString }
                else listOf(json.get("target").asString)
            val exclude =
                if (json.has("exclude")) {
                    if (json.get("exclude").isJsonArray) json.getAsJsonArray("exclude").map { it.asString }
                    else listOf(json.get("exclude").asString)
                }
                else emptyList()
            val conditions =
                if (json.has("conditions")) LogicalCondition.fromJson(json.getAsJsonObject("conditions"))
                else null
            val modifiers = mutableMapOf<String, Double>()
            val stringModifiers = mutableMapOf<String, String>()
            val booleanModifiers = mutableMapOf<String, Boolean>()
            if (json.has("modifiers")) {
                val modObj = json.getAsJsonObject("modifiers")
                for ((key, value) in modObj.entrySet()) {
                    if (value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) booleanModifiers[key] = value.asBoolean
                    else if (value.isJsonPrimitive && value.asJsonPrimitive.isString) stringModifiers[key] = value.asString
                    else modifiers[key] = value.asDouble
                }
            }
            val operation =
                if (json.has("operation")) Operation.fromString(json.get("operation").asString)
                else Operation.ADD
            val priority =
                if (json.has("priority")) json.get("priority").asInt
                else 0
            val slot =
                if (json.has("slot")) json.get("slot").asString
                else null
            return GearModifier(
                id = id,
                targets = targets,
                exclude = exclude,
                conditions = conditions,
                modifiers = modifiers,
                stringModifiers = stringModifiers,
                booleanModifiers = booleanModifiers,
                operation = operation,
                priority = priority,
                slot = slot
            )
        }
    }
}
