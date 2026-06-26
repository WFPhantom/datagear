package com.wfphantom.datagear.api

import com.google.gson.JsonObject
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

/** represents a single gear modification entry loaded from a datapack JSON. */
data class GearModifier(
    val id: Identifier,
    val targets: List<String>,
    val exclude: List<String> = emptyList(),
    val conditions: LogicalCondition?,
    val modifiers: Map<String, Double>,
    val stringModifiers: Map<String, String> = emptyMap(),
    val booleanModifiers: Map<String, Boolean> = emptyMap(),
    val listModifiers: Map<String, List<String>> = emptyMap(),
    val operation: Operation,
    val priority: Int = 0,
    val slot: String? = null
) {
    val targetDisplay: String get() {
        val base = targets.joinToString(", ")
        val excl = if (exclude.isNotEmpty()) " (exclude: ${exclude.joinToString(", ")})" else ""
        return "$base$excl"
    }

    companion object {
        private val logger = LoggerFactory.getLogger("datagear")

        fun fromJson(id: Identifier, json: JsonObject): GearModifier {
            val targets = json.getStringList("target")
            if (targets.isEmpty()) logger.warn("DataGear: Modifier '$id' has no targets")
            val exclude = json.getStringList("exclude")
            val conditions = if (json.has("conditions")) LogicalCondition.fromJson(json.getAsJsonObject("conditions")) else null
            val modifiers = mutableMapOf<String, Double>()
            val stringModifiers = mutableMapOf<String, String>()
            val booleanModifiers = mutableMapOf<String, Boolean>()
            val listModifiers = mutableMapOf<String, List<String>>()
            if (json.has("components")) {
                val modObj = json.getAsJsonObject("components")
                for ((key, value) in modObj.entrySet()) {
                    when {
                        value.isJsonArray -> listModifiers[key] = value.asJsonArray.map { it.asString }
                        value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> booleanModifiers[key] = value.asBoolean
                        value.isJsonPrimitive && value.asJsonPrimitive.isString -> stringModifiers[key] = value.asString
                        else -> modifiers[key] = value.asDouble
                    }
                }
            }
            return GearModifier(
                id = id,
                targets = targets,
                exclude = exclude,
                conditions = conditions,
                modifiers = modifiers,
                stringModifiers = stringModifiers,
                booleanModifiers = booleanModifiers,
                listModifiers = listModifiers,
                operation = json.getStringOrNull("operation")?.let {
                    try { Operation.fromString(it) }
                    catch (_: IllegalArgumentException) {
                        logger.warn("DataGear: Unknown operation '$it' in '$id', defaulting to ADD")
                        Operation.ADD
                    }
                } ?: Operation.ADD,
                priority = json.getIntOrNull("priority") ?: 0,
                slot = json.getStringOrNull("slot")
            )
        }

        private fun JsonObject.getStringList(key: String): List<String> {
            val elem = get(key)?.takeIf { it.isJsonPrimitive || it.isJsonArray } ?: return emptyList()
            return if (elem.isJsonArray) elem.asJsonArray.map { it.asString } else listOf(elem.asString)
        }

        private fun JsonObject.getIntOrNull(key: String): Int? = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt

        private fun JsonObject.getStringOrNull(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    }
}
