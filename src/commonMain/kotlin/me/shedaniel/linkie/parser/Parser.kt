package me.shedaniel.linkie.parser

import me.shedaniel.linkie.MappingsSource

interface Parser {
    val source: MappingsSource
    fun parse(visitor: MappingsVisitor)
}
