package me.shedaniel.linkie.parser

import me.shedaniel.linkie.Mappings
import me.shedaniel.linkie.parser.tiny.Tiny2Parser
import me.shedaniel.linkie.serializer.Tiny2Serializer

fun main() {
    val config = NamespaceConfig.DEFAULT
    run {
        val parser = Tiny2Parser("imagine this as the tiny file content")
        val reader = MappingsReader(Mappings(name = "Yarn", version = "1.0.0"), config)
        parser.visit(reader)
        val mappings = reader.mappings // Read mappings

        val serializer = Tiny2Serializer()
        mappings.visit(serializer, config)
        val content = serializer.toString() // serialized mappings
        println(content)
    }

    run {
        val parser = Tiny2Parser("imagine this as the tiny file content")
        parser.visit(object : DelegatingMappingsVisitor(Tiny2Serializer()) {
            val lastComplex = entryComplex { namespace, base ->
                if (namespace == config.intermediary) {
                    base() + "_lol"
                } else null
            }

            override fun visitClass(complex: MappingsEntryComplex): MappingsClassVisitor? {
                lastComplex.parent = complex
                return super.visitClass(lastComplex)
            }
        })
    }
}
