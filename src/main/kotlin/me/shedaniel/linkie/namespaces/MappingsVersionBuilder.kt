package me.shedaniel.linkie.namespaces

fun interface MappingsVersionBuilder {
    fun build(version: String): MappingsVersion
}
