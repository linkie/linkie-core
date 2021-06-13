package me.shedaniel.linkie.parser

import me.shedaniel.linkie.MappingsSource

interface Parser : MappingsVisitable {
    companion object {
        const val NS_INTERMEDIARY = "intermediary"
        const val NS_MAPPED = "named"
        const val NS_OBF = "official"
    }

    val source: MappingsSource
    fun parse(visitor: MappingsVisitor)

    override fun visit(visitor: MappingsVisitor) = parse(visitor)
}
