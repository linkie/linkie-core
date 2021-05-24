package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.Mappings
import me.shedaniel.linkie.MappingsConstructingBuilder

fun interface MappingsBuilder {
    suspend fun build(version: String): Mappings
}

fun interface UuidGetter {
    suspend fun get(version: String): String
}

typealias ConstructingMappingsBuilder = suspend MappingsConstructingBuilder.() -> Unit
typealias ConstructingVersionedMappingsBuilder = suspend MappingsConstructingBuilder.(version: String) -> Unit

fun toBuilder(builder: suspend (String) -> Mappings): MappingsBuilder {
    return MappingsBuilder { version -> builder(version) }
}
