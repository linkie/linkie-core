package me.shedaniel.linkie.parser

import kotlin.properties.Delegates

abstract class AbstractParser<T : MappingsEntryComplex>(
    entryComplex: (AbstractParser<T>) -> T,
) : Parser {
    abstract val namespaces: MutableMap<String, Int>
    var visitor by Delegates.notNull<MappingsVisitor>()
    val lastClass by lazy { entryComplex(this) }
    var lastClassVisitor: MappingsClassVisitor? = null
    val lastField by lazy { entryComplex(this) }
    val lastMethod by lazy { entryComplex(this) }
    var lastMethodVisitor: MappingsMethodVisitor? = null
    val lastMethodParameter by lazy { entryComplex(this) }

    inline fun withVisitor(visitor: MappingsVisitor, action: () -> Unit) {
        this@AbstractParser.visitor = visitor
        action()
    }

    fun MappingsVisitor.visitSelfStart() {
        visitStart(MappingsNamespaces.of(namespaces.keys))
    }
}

open class ProvidedEntryComplex(val parser: AbstractParser<*>) : MappingsEntryComplex {
    var split by Delegates.notNull<(namespaceIndex: Int) -> String>()
    override val namespaces: Set<String>
        get() = parser.namespaces.keys

    override fun get(namespace: String?): String =
        split(parser.namespaces[namespace]!!)
}

class ArrayEntryComplex(val parser: AbstractParser<*>) : MappingsEntryComplex {
    val array = arrayOfNulls<String>(namespaces.size)
    override val namespaces: Set<String>
        get() = parser.namespaces.keys

    override fun get(namespace: String?): String? =
        array[parser.namespaces[namespace]!!]

    operator fun set(namespace: String, value: String) {
        array[parser.namespaces[namespace]!!] = value
    }

    operator fun set(namespace: Int, value: String) {
        array[namespace] = value
    }
}
