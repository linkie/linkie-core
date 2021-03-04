package me.shedaniel.linkie.parser

import kotlin.properties.Delegates

abstract class AbstractParser : Parser {
    abstract val namespaces: MutableMap<String, Int>
    var visitor by Delegates.notNull<MappingsVisitor>()
    val lastClass by lazy(::EntryComplex)
    var lastClassVisitor: MappingsClassVisitor? = null
    val lastField by lazy(::EntryComplex)
    val lastMethod by lazy(::EntryComplex)
    var lastMethodVisitor: MappingsMethodVisitor? = null
    val lastMethodParameter by lazy(::EntryComplex)

    inner class EntryComplex : MappingsEntryComplex {
        var split by Delegates.notNull<(namespaceIndex: Int) -> String>()
        override val namespaces: Set<String>
            get() = this@AbstractParser.namespaces.keys

        override fun get(namespace: String?): String =
            split(this@AbstractParser.namespaces[namespace]!!)
    }

    inline fun withVisitor(visitor: MappingsVisitor, action: () -> Unit) {
        this@AbstractParser.visitor = visitor
        action()
    }
}
