package me.shedaniel.linkie.parser

import me.shedaniel.linkie.ClassBuilder
import me.shedaniel.linkie.MappingsBuilder
import me.shedaniel.linkie.utils.remapDescriptor
import kotlin.properties.Delegates

fun MappingsBuilder.apply(
    parser: Parser,
    obfClient: String? = null,
    obfServer: String? = null,
    obfMerged: String? = null,
    intermediary: String,
    named: String? = null,
) {
    parser.parse(visitor(
        obfClient = obfClient,
        obfServer = obfServer,
        obfMerged = obfMerged,
        intermediary = intermediary,
        named = named,
    ))
    source(parser.source)
}

fun MappingsBuilder.visitor(
    obfClient: String? = null,
    obfServer: String? = null,
    obfMerged: String? = null,
    intermediary: String,
    named: String? = null,
): MappingsBuilderVisitor = MappingsBuilderVisitor(
    builder = this,
    obfClient = obfClient,
    obfServer = obfServer,
    obfMerged = obfMerged,
    intermediary = intermediary,
    named = named,
)

class MappingsBuilderVisitor(
    val builder: MappingsBuilder,
    val obfClient: String?,
    val obfServer: String?,
    val obfMerged: String?,
    val intermediary: String,
    val named: String?,
) : MappingsVisitor {
    var hasObfClient = false
    var hasObfServer = false
    var hasObfMerged = false
    var hasNamed = false
    val classVisitor = ClassVisitor()
    val postAddMethods = mutableListOf<() -> Unit>()
    val classMap = mutableMapOf<String, String>()
    var directDescriptor = false

    override fun visitStart(namespaces: MappingsNamespaces) {
        hasObfClient = obfClient in namespaces.namespaces
        hasObfServer = obfServer in namespaces.namespaces
        hasObfMerged = obfMerged in namespaces.namespaces
        hasNamed = named in namespaces.namespaces
        directDescriptor = namespaces.primaryNamespace == intermediary
    }

    override fun visitClass(complex: MappingsEntryComplex): MappingsClassVisitor {
        classVisitor.classBuilder = builder.clazz(complex[intermediary]!!) {
            if (hasObfClient) obfClient(complex[obfClient])
            if (hasObfServer) obfServer(complex[obfServer])
            if (hasObfMerged) obfClass(complex[obfMerged])
            if (hasNamed) mapClass(complex[named])
        }
        classMap[complex[complex.primaryNamespace]!!] = complex[intermediary]!!
        return classVisitor
    }

    override fun visitEnd() {
        postAddMethods.forEach { it() }
        postAddMethods.clear()
    }

    inner class ClassVisitor : MappingsClassVisitor {
        var classBuilder by Delegates.notNull<ClassBuilder>()

        override fun visitField(complex: MappingsEntryComplex): MappingsJavadocVisitor? {
            classBuilder.field(complex[intermediary]!!) {
                if (hasObfClient) obfClient(complex[obfClient])
                if (hasObfServer) obfServer(complex[obfServer])
                if (hasObfMerged) obfField(complex[obfMerged])
                if (hasNamed) mapField(complex[named])
            }
            return null
        }

        override fun visitMethod(complex: MappingsEntryComplex, descriptor: String): MappingsMethodVisitor? {
            val intermediary = complex[intermediary]!!
            val obfClient = if (hasObfClient) complex[obfClient] else null
            val obfServer = if (hasObfServer) complex[obfServer] else null
            val obfMerged = if (hasObfMerged) complex[obfMerged] else null
            val named = if (hasNamed) complex[named] else null
            val builder = classBuilder
            if (directDescriptor) {
                builder.method(intermediary, descriptor) {
                    obfClient(obfClient)
                    obfServer(obfServer)
                    obfMethod(obfMerged)
                    mapMethod(named)
                }
            } else {
                postAddMethods.add {
                    builder.method(intermediary, descriptor.remapDescriptor { classMap[it] ?: it }) {
                        obfClient(obfClient)
                        obfServer(obfServer)
                        obfMethod(obfMerged)
                        mapMethod(named)
                    }
                }
            }
            return null
        }

        override fun visitDocs(docs: String) = Unit
    }
}
