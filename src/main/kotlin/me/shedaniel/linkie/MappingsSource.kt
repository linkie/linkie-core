package me.shedaniel.linkie

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
@Suppress("unused")
enum class MappingsSource {
    MCP_SRG,
    MCP_TSRG,
    MCP_TSRG2,
    SRG,
    TSRG,
    TSRG2,
    PROGUARD,
    TINY_V1,
    TINY_V2,
    CSV,
    MOJANG,
    MOJANG_TSRG,
    MOJANG_TSRG2,
    MOJANG_HASHED,
    YARN_V1,
    YARN_V2,
    SPIGOT,
    ENGIMA,
    QUILT_MAPPINGS,
    FEATHER;

    override fun toString(): String = name.toLowerCase(Locale.ROOT).split("_").joinToString(" ") { it.capitalize() }
}
