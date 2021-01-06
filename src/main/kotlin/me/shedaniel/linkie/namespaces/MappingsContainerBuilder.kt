package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.MappingsContainer

interface MappingsContainerBuilder {
    suspend fun build(version: String): MappingsContainer
}

fun toBuilder(builder: suspend (String) -> MappingsContainer): MappingsContainerBuilder {
    return object : MappingsContainerBuilder {
        override suspend fun build(version: String): MappingsContainer {
            return builder(version)
        }
    }
}
