package me.shedaniel.linkie.parser

import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.utils.filterNotBlank
import kotlin.properties.Delegates

fun srg(content: String): SrgParser = SrgParser(content)

class SrgParser(content: String) : AbstractParser() {
    val groups = content.lineSequence().filterNotBlank().groupBy { it.split(' ')[0] }
    override val namespaces: MutableMap<String, Int> = mutableMapOf(
        "obf" to 0,
        "srg" to 1,
    )
    override val source: MappingsSource
        get() = MappingsSource.SRG

    val field by lazy(::SimpleEntryComplex)
    val method by lazy(::SimpleEntryComplex)

    inner class SimpleEntryComplex : MappingsEntryComplex {
        override val namespaces: Set<String>
            get() = this@SrgParser.namespaces.keys

        var obf by Delegates.notNull<String>()
        var srg by Delegates.notNull<String>()

        override fun get(namespace: String?): String? = when (namespace) {
            "obf" -> obf
            "srg" -> srg
            else -> null
        }
    }

    override fun parse(visitor: MappingsVisitor) = withVisitor(visitor) {
        visitor.visitStart(MappingsNamespaces.of(namespaces.keys))
        val classVisitors = mutableMapOf<String, MappingsClassVisitor>()
        groups["CL:"]?.forEach { classLine ->
            val split = classLine.substring(4).split(" ")
            lastClass.split = split::get
            visitor.visitClass(lastClass)?.also { classVisitors[split[0]] = it }
        }
        groups["FD:"]?.forEach { fieldLine ->
            val split = fieldLine.substring(4).split(" ")
            val obfClass = split[0].substringBeforeLast('/')
            classVisitors[obfClass]?.also { visitor ->
                field.obf = split[0].substringAfterLast('/')
                field.srg = split[1].substringAfterLast('/')
                visitor.visitField(field)
            }
        }
        groups["MD:"]?.forEach { fieldLine ->
            val split = fieldLine.substring(4).split(" ")
            val obfClass = split[0].substringBeforeLast('/')
            classVisitors[obfClass]?.also { visitor ->
                val obfDesc = split[1]
                method.obf = split[0].substringAfterLast('/')
                method.srg = split[2].substringAfterLast('/')
                visitor.visitMethod(method, obfDesc)
            }
        }
        visitor.visitEnd()
    }
}
