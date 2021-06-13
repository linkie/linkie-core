package me.shedaniel.linkie.parser

import me.shedaniel.linkie.ClassBuilder
import me.shedaniel.linkie.Mappings
import me.shedaniel.linkie.MappingsConstructingBuilder
import me.shedaniel.linkie.utils.remapDescriptor
import kotlin.properties.Delegates

fun MappingsConstructingBuilder.apply(
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

fun MappingsConstructingBuilder.apply(
    parser: Parser,
    config: NamespaceConfig,
) {
    parser.parse(visitor(config))
    source(parser.source)
}

fun MappingsConstructingBuilder.visitor(
    obfClient: String? = null,
    obfServer: String? = null,
    obfMerged: String? = null,
    intermediary: String,
    named: String? = null,
): MappingsBuilderVisitor = MappingsBuilderVisitor(
    constructingBuilder = this,
    config = NamespaceConfig(
        obfClient = obfClient,
        obfServer = obfServer,
        obfMerged = obfMerged,
        intermediary = intermediary,
        named = named,
    )
)

fun MappingsConstructingBuilder.visitor(
    config: NamespaceConfig,
): MappingsBuilderVisitor = MappingsBuilderVisitor(this, config)

fun MappingsReader(
    mappings: Mappings,
    config: NamespaceConfig,
): MappingsBuilderVisitor = MappingsConstructingBuilder(mappings)
    .visitor(config)

class MappingsBuilderVisitor(
    val constructingBuilder: MappingsConstructingBuilder,
    val config: NamespaceConfig,
) : MappingsVisitor {
    val mappings: Mappings
        get() = constructingBuilder.build()
    var hasObfClient = false
    var hasObfServer = false
    var hasObfMerged = false
    var hasNamed = false
    val classVisitor = ClassVisitor()
    val postAddMethods = mutableListOf<() -> Unit>()
    val classMap = mutableMapOf<String, String>()
    var directDescriptor = false

    override fun visitStart(namespaces: MappingsNamespaces) {
        hasObfClient = config.obfClient in namespaces.namespaces
        hasObfServer = config.obfServer in namespaces.namespaces
        hasObfMerged = config.obfMerged in namespaces.namespaces
        hasNamed = config.named in namespaces.namespaces
        directDescriptor = namespaces.primaryNamespace == config.intermediary
    }

    override fun visitClass(complex: MappingsEntryComplex): MappingsClassVisitor {
        classVisitor.classBuilder = constructingBuilder.clazz(complex[config.intermediary]!!) {
            if (hasObfClient) obfClient(complex[config.obfClient])
            if (hasObfServer) obfServer(complex[config.obfServer])
            if (hasObfMerged) obfClass(complex[config.obfMerged])
            if (hasNamed) mapClass(complex[config.named])
        }
        classMap[complex[complex.primaryNamespace]!!] = complex[config.intermediary]!!
        return classVisitor
    }

    override fun visitEnd() {
        postAddMethods.forEach { it() }
        postAddMethods.clear()
    }

    inner class ClassVisitor : MappingsClassVisitor {
        var classBuilder by Delegates.notNull<ClassBuilder>()

        override fun visitField(complex: MappingsEntryComplex, descriptor: String?): MappingsJavadocVisitor? {
            classBuilder.field(complex[config.intermediary]!!, descriptor ?: "") {
                if (hasObfClient) obfClient(complex[config.obfClient])
                if (hasObfServer) obfServer(complex[config.obfServer])
                if (hasObfMerged) obfField(complex[config.obfMerged])
                if (hasNamed) mapField(complex[config.named])
            }
            return null
        }

        override fun visitMethod(complex: MappingsEntryComplex, descriptor: String): MappingsMethodVisitor? {
            val intermediary = complex[config.intermediary]!!
            val obfClient = if (hasObfClient) complex[config.obfClient] else null
            val obfServer = if (hasObfServer) complex[config.obfServer] else null
            val obfMerged = if (hasObfMerged) complex[config.obfMerged] else null
            val named = if (hasNamed) complex[config.named] else null
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
