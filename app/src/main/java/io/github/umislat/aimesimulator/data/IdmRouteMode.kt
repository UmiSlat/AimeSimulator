package io.github.umislat.aimesimulator.data

internal enum class IdmRouteMode {
    ORIGINAL,
    FIXED_COMPATIBILITY,
    PREFIX_COMPATIBILITY;

    companion object {
        fun fromStoredValue(value: String?): IdmRouteMode? =
            values().firstOrNull { it.name == value }
    }
}
