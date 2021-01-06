package me.shedaniel.linkie.namespaces

data class MappingsVersion(
    val uuid: String,
    val container: MappingsContainerBuilder,
)

fun ofVersion(uuid: String, container: MappingsContainerBuilder) = MappingsVersion(uuid, container)
