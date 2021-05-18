package me.shedaniel.linkie.utils.query

import me.shedaniel.linkie.MappingsEntry

enum class QueryDefinition(val toDefinition: MappingsEntry.() -> String?, val multiplier: Double) {
    MAPPED({ mappedName }, 1.0),
    INTERMEDIARY({ intermediaryName }, 1.0 - Double.MIN_VALUE),
    OBF_MERGED({ obfName.merged }, 1.0 - Double.MIN_VALUE - Double.MIN_VALUE),
    OBF_CLIENT({ obfName.client }, 1.0 - Double.MIN_VALUE - Double.MIN_VALUE),
    OBF_SERVER({ obfName.server }, 1.0 - Double.MIN_VALUE - Double.MIN_VALUE),
    WILDCARD({ throw IllegalStateException("Cannot get definition of type WILDCARD!") }, 1.0);

    operator fun invoke(entry: MappingsEntry): String? = toDefinition(entry)

    companion object {
        val allProper = listOf(
            MAPPED,
            INTERMEDIARY,
            OBF_MERGED,
            OBF_CLIENT,
            OBF_SERVER,
        )
    }
}
