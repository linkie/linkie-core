package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.MappingsContainer

fun interface MappingsContainerBuilder {
    fun build(version: String): MappingsContainer
}