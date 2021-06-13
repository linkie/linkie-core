package me.shedaniel.linkie.parser

import me.shedaniel.linkie.Mappings
import me.shedaniel.linkie.MappingsEntry
import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.obfClientName
import me.shedaniel.linkie.obfMergedName
import me.shedaniel.linkie.obfServerName
import kotlin.properties.Delegates

class MappingsBinder(
    val mappings: Mappings,
    val config: NamespaceConfig,
) : Parser {
    override val source: MappingsSource
        get() = mappings.mappingsSource ?: MappingsSource.of("unknown")

    override fun parse(visitor: MappingsVisitor) {
        val namespaces = mutableSetOf<String>()
        namespaces.add(config.intermediary)
        config.obfClient?.apply(namespaces::add)
        config.obfServer?.apply(namespaces::add)
        config.obfMerged?.apply(namespaces::add)
        config.named?.apply(namespaces::add)
        visitor.visitStart(MappingsNamespaces.of(namespaces))
        val classComplex = ImplementedComplex(namespaces)
        val fieldComplex = ImplementedComplex(namespaces)
        val methodComplex = ImplementedComplex(namespaces)
        mappings.allClasses.forEach { clazz ->
            classComplex.entry = clazz
            visitor.visitClass(classComplex)?.let { classVisitor ->
                for (method in clazz.methods) {
                    methodComplex.entry = method
                    classVisitor.visitMethod(methodComplex, method.intermediaryDesc)
                }
                for (field in clazz.fields) {
                    fieldComplex.entry = field
                    classVisitor.visitField(fieldComplex, field.intermediaryDesc)
                }
            }
        }
        visitor.visitEnd()
    }

    inner class ImplementedComplex(override val namespaces: Set<String>) : MappingsEntryComplex {
        var entry by Delegates.notNull<MappingsEntry>()
        override fun get(namespace: String?): String? {
            return when (namespace) {
                null -> null
                config.intermediary -> entry.intermediaryName
                config.obfClient -> entry.obfClientName
                config.obfServer -> entry.obfServerName
                config.obfMerged -> entry.obfMergedName
                config.named -> entry.mappedName
                else -> null
            }
        }
    }
}
