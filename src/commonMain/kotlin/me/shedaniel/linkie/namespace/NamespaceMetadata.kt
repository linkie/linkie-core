package me.shedaniel.linkie.namespace

data class NamespaceMetadata(
    val mixins: Boolean = false,
    val at: Boolean = false,
    val aw: Boolean = false,
    val fieldDescriptor: Boolean = true,
)
