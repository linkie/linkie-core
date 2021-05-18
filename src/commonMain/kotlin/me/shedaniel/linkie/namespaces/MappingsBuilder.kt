package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.Mappings

interface MappingsBuilder {
    suspend fun build(version: String): Mappings
}

fun toBuilder(builder: suspend (String) -> Mappings): MappingsBuilder {
    return object : MappingsBuilder {
        override suspend fun build(version: String): Mappings {
            return builder(version)
        }
    }
}
