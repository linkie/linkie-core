package me.shedaniel.linkie.namespaces

data class MappingsVersion(
    val uuid: String,
    val container: MappingsBuilder,
)

fun ofVersion(uuid: String, container: MappingsBuilder) = MappingsVersion(uuid, container)
