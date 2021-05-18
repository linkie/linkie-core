package me.shedaniel.linkie

import kotlinx.serialization.Serializable
import me.shedaniel.linkie.utils.capitalize

@Serializable
@Suppress("unused")
value class MappingsSource private constructor(val id: String) {
    companion object {
        val MCP_SRG = of("MCP_SRG")
        val MCP_TSRG = of("MCP_TSRG")
        val MCP_TSRG2 = of("MCP_TSRG2")
        val SRG = of("SRG")
        val TSRG = of("TSRG")
        val TSRG2 = of("TSRG2")
        val PROGUARD = of("PROGUARD")
        val TINY_V1 = of("TINY_V1")
        val TINY_V2 = of("TINY_V2")
        val CSV = of("CSV")
        val MOJANG = of("MOJANG")
        val MOJANG_TSRG = of("MOJANG_TSRG")
        val MOJANG_TSRG2 = of("MOJANG_TSRG2")
        val YARN_V1 = of("YARN_V1")
        val YARN_V2 = of("YARN_V2")
        val SPIGOT = of("SPIGOT")
        val ENGIMA = of("ENGIMA")

        fun of(id: String): MappingsSource = MappingsSource(id)
    }

    override fun toString(): String = id.lowercase().splitToSequence("_").joinToString(" ") { it.capitalize() }
}
