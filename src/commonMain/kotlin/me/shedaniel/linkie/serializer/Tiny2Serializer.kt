package me.shedaniel.linkie.serializer

import me.shedaniel.linkie.parser.MappingsClassVisitor
import me.shedaniel.linkie.parser.MappingsEntryComplex
import me.shedaniel.linkie.parser.MappingsJavadocVisitor
import me.shedaniel.linkie.parser.MappingsMethodVisitor
import me.shedaniel.linkie.parser.MappingsNamespaces
import me.shedaniel.linkie.parser.MappingsVisitor

class Tiny2Serializer(val builder: StringBuilder = StringBuilder()) : MappingsVisitor {
    var built: Boolean? = null

    override fun visitStart(namespaces: MappingsNamespaces) {
        require(built == null) { "Expected Tiny2Serializer visitStart() only when empty!" }
        builder.clear()
        built = false
        builder.append("tiny\t")
        builder.append(2)
        builder.append('\t')
        builder.append(0)
        for (namespace in namespaces.namespaces) {
            builder.append('\t')
            builder.append(namespace)
        }
        builder.append('\n')
    }

    override fun visitClass(complex: MappingsEntryComplex): MappingsClassVisitor? {
        builder.append('c')
        writeMapped(complex)
        return object : BaseJavaDocVisitor(), MappingsClassVisitor {
            override val indent: Int
                get() = 1

            override fun visitField(complex: MappingsEntryComplex, descriptor: String?): MappingsJavadocVisitor? {
                require(descriptor != null) { "Tiny2Serializer does not allow null field descriptor: ${complex.primaryName}" }
                builder.append('\t').append('f')
                builder.append('\t').append(descriptor)
                writeMapped(complex)
                return object : BaseJavaDocVisitor() {
                    override val indent: Int
                        get() = 2
                }
            }

            override fun visitMethod(complex: MappingsEntryComplex, descriptor: String): MappingsMethodVisitor? {
                builder.append('\t').append('m')
                builder.append('\t').append(descriptor)
                writeMapped(complex)
                return object : BaseJavaDocVisitor(), MappingsMethodVisitor {
                    override val indent: Int
                        get() = 2

                    override fun visitParameter(index: Int, complex: MappingsEntryComplex): MappingsJavadocVisitor? {
                        builder.append("\t\tp\t").append(index)
                        writeMapped(complex)
                        return null
                    }
                }
            }
        }
    }

    private fun writeMapped(complex: MappingsEntryComplex) {
        for (namespace in complex.namespaces) {
            builder.append('\t')
            builder.append(complex[namespace])
        }
        builder.append('\n')
    }

    override fun visitEnd() {
        require(built == false) { "Expected Tiny2Serializer visitEnd() after visitStart()!" }
        built = true
    }

    override fun toString(): String {
        require(built == true) { "Expected Tiny2Serializer toString() after visitEnd()!" }
        return builder.toString()
    }

    abstract inner class BaseJavaDocVisitor : MappingsJavadocVisitor {
        abstract val indent: Int

        override fun visitDocs(docs: String) {
            if (indent >= 0) {
                for (i in 0 until indent) {
                    builder.append('\t')
                }
                builder.append('c').append('\t').append(docs.escapeString()).append('\n')
            }
        }
    }

    private fun String.escapeString(): StringBuilder {
        val builder = StringBuilder()
        for (element in this) {
            when (element) {
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                '\u0000' -> builder.append("\\0")
                else -> builder.append(element)
            }
        }
        return builder
    }
}
