package me.shedaniel.linkie.parser.srg

import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.parser.AbstractParser
import me.shedaniel.linkie.parser.ProvidedEntryComplex
import me.shedaniel.linkie.parser.MappingsClassVisitor
import me.shedaniel.linkie.parser.MappingsEntryComplex
import me.shedaniel.linkie.parser.MappingsNamespaces
import me.shedaniel.linkie.parser.MappingsVisitor
import me.shedaniel.linkie.utils.filterNotBlank
import kotlin.properties.Delegates

fun srg(content: String): SrgParser = SrgParser(content)

class SrgParser(content: String) : AbstractParser<SrgParser.SimpleEntryComplex>(::SimpleEntryComplex) {
    companion object {
        const val NS_OBF = "obf"
        const val NS_SRG = "srg"
    }

    val groups = content.lineSequence().filterNotBlank().groupBy { it.split(' ')[0] }
    override val namespaces: MutableMap<String, Int> = mutableMapOf(
        NS_OBF to 0,
        NS_SRG to 1,
    )
    override val source: MappingsSource
        get() = MappingsSource.SRG

    class SimpleEntryComplex(val parser: AbstractParser<*>) : MappingsEntryComplex {
        override val namespaces: Set<String>
            get() = parser.namespaces.keys

        var obf by Delegates.notNull<String>()
        var srg by Delegates.notNull<String>()

        override fun get(namespace: String?): String? = when (namespace) {
            NS_OBF -> obf
            NS_SRG -> srg
            else -> null
        }
    }

    override fun parse(visitor: MappingsVisitor) = withVisitor(visitor) {
        val lastClass by lazy { ProvidedEntryComplex(this) }
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
                lastField.obf = split[0].substringAfterLast('/')
                lastField.srg = split[1].substringAfterLast('/')
                visitor.visitField(lastField, null)
            }
        }
        groups["MD:"]?.forEach { fieldLine ->
            val split = fieldLine.substring(4).split(" ")
            val obfClass = split[0].substringBeforeLast('/')
            classVisitors[obfClass]?.also { visitor ->
                val obfDesc = split[1]
                lastMethod.obf = split[0].substringAfterLast('/')
                lastMethod.srg = split[2].substringAfterLast('/')
                visitor.visitMethod(lastMethod, obfDesc)
            }
        }
        visitor.visitEnd()
    }
}
