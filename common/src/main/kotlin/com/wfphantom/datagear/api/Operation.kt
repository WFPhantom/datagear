package com.wfphantom.datagear.api

enum class Operation {
    ADD,
    SET,
    MULTIPLY;

    companion object {
        fun fromString(value: String): Operation {
            return when (value.lowercase()) {
                "add" -> ADD
                "set" -> SET
                "multiply" -> MULTIPLY
                else -> throw IllegalArgumentException("Unknown operation: $value")
            }
        }
    }
}
