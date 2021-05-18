package me.shedaniel.linkie.parser.tiny

import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.parser.AbstractParser
import me.shedaniel.linkie.parser.ProvidedEntryComplex
import me.shedaniel.linkie.parser.MappingsVisitor
import me.shedaniel.linkie.utils.StringReader
import me.shedaniel.linkie.utils.columnView
import me.shedaniel.linkie.utils.indentedColumnView

class Tiny2Parser(val reader: StringReader) : AbstractParser<ProvidedEntryComplex>(::ProvidedEntryComplex) {
    override val namespaces = mutableMapOf<String, Int>()

    override val source: MappingsSource
        get() = MappingsSource.TINY_V2

    override fun parse(visitor: MappingsVisitor) = withVisitor(visitor) {
        visitHeader()
        visitor.visitEnd()
    }

    private fun visitHeader() {
        val headerLine = reader.readLine()?.columnView()?.takeIf { it[0] == "tiny" }
            ?: throw IllegalStateException("The first line must start with 'tiny'!")
        val majorVersion = headerLine[1].toInt()
        val minorVersion = headerLine[2].toInt()
        require(majorVersion == 2) { "Assumed tiny major version as 2!" }
        require(minorVersion == 0) { "Assumed tiny minor version as 0!" }
        headerLine.asSequence().drop(3).forEachIndexed { index, namespace -> 
            namespaces[namespace] = index
        }
        visitor.visitSelfStart()
        while (reader.peek != 'c') {
            val line = reader.readLine()?.columnView(1) ?: return
        }
        val line = reader.readLine()?.indentedColumnView() ?: return visitor.visitEnd()
        val type = line[0]
        when {
            line.indent == 0 && type == "c" -> {
                // Class Declaration
            }
            type == "c" -> {
                // Comment Declaration
            }
            line.indent == 1 && type == "m" -> {
                // Method Declaration
            }
            line.indent == 1 && type == "f" -> {
                // Field Declaration
            }
            line.indent == 2 && type == "p" -> {
                // Method Args Declaration
            }
            line.indent == 2 && type == "v" -> {
                // Method Locals Declaration
            }
        }
    }
}
