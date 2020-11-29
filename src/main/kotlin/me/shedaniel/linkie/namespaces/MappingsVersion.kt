package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.MappingsContainer

data class MappingsVersion(
    val uuid: String,
    val container: MappingsContainerBuilder,
)

fun ofVersion(uuid: String, container: MappingsContainer) = ofVersion(uuid) { container }
fun ofVersion(uuid: String, container: MappingsContainerBuilder) = MappingsVersion(uuid, container)