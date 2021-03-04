package me.shedaniel.linkie.parser

interface MappingsVisitor {
    fun visitStart(namespaces: MappingsNamespaces)
    fun visitClass(complex: MappingsEntryComplex): MappingsClassVisitor?
    fun visitEnd()
}

interface MappingsClassVisitor : MappingsJavadocVisitor {
    fun visitField(complex: MappingsEntryComplex): MappingsJavadocVisitor?
    fun visitMethod(complex: MappingsEntryComplex, descriptor: String): MappingsMethodVisitor?
}

interface MappingsMethodVisitor : MappingsJavadocVisitor {
    fun visitParameter(index: Int, complex: MappingsEntryComplex): MappingsJavadocVisitor?
}

interface MappingsJavadocVisitor {
    fun visitDocs(docs: String)
}

interface MappingsEntryComplex : MappingsNamespaces {
    operator fun get(namespace: String?): String?
}

interface MappingsNamespaces {
    val namespaces: Set<String>
    val primaryNamespace: String
        get() = namespaces.first()

    companion object {
        fun of(namespaces: Set<String>): MappingsNamespaces = object : MappingsNamespaces {
            override val namespaces: Set<String>
                get() = namespaces
        }
    }
}
