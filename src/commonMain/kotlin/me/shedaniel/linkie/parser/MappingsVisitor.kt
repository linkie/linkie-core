package me.shedaniel.linkie.parser

interface MappingsVisitable {
    val parent: MappingsVisitable?
        get() = null

    fun visit(visitor: MappingsVisitor) {
        parent?.visit(visitor)
    }
}

interface MappingsVisitor {
    val parent: MappingsVisitor?
        get() = null

    fun visitStart(namespaces: MappingsNamespaces) {
        parent?.visitStart(namespaces)
    }

    fun visitClass(complex: MappingsEntryComplex): MappingsClassVisitor? {
        return parent?.visitClass(complex)
    }

    fun visitEnd() {
        parent?.visitEnd()
    }
}

interface MappingsClassVisitor : MappingsJavadocVisitor {
    override val parent: MappingsClassVisitor?
        get() = null

    fun visitField(complex: MappingsEntryComplex, descriptor: String?): MappingsJavadocVisitor? {
        return parent?.visitField(complex, descriptor)
    }

    fun visitMethod(complex: MappingsEntryComplex, descriptor: String): MappingsMethodVisitor? {
        return parent?.visitMethod(complex, descriptor)
    }
}

interface MappingsMethodVisitor : MappingsJavadocVisitor {
    override val parent: MappingsMethodVisitor?
        get() = null

    fun visitParameter(index: Int, complex: MappingsEntryComplex): MappingsJavadocVisitor? {
        return parent?.visitParameter(index, complex)
    }
}

interface MappingsJavadocVisitor {
    val parent: MappingsJavadocVisitor?
        get() = null

    fun visitDocs(docs: String) {
        parent?.visitDocs(docs)
    }
}

interface MappingsEntryComplex : MappingsNamespaces {
    val parent: MappingsEntryComplex?
        get() = null

    operator fun get(namespace: String?): String? {
        return parent?.get(namespace)
    }

    override val namespaces: Set<String>
        get() = parent?.namespaces ?: emptySet()

    val primaryName: String?
        get() = get(primaryNamespace)
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

fun MappingsVisitor.use(visitable: MappingsVisitable) {
    visitable.visit(this)
}
