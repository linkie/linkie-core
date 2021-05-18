package me.shedaniel.linkie.parser.srg

import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.parser.MappingsVisitor
import me.shedaniel.linkie.utils.filterNotBlank
import me.shedaniel.linkie.utils.indentCount

fun tsrg(content: String): AbstractTsrgParser {
    if (content.startsWith("tsrg2")) {
        return TSrg2Parser(content)
    }

    return TsrgParser(content)
}

class TsrgParser(content: String) : AbstractTsrgParser() {
    companion object {
        const val NS_OBF = "obf"
        const val NS_SRG = "srg"
    }

    val lines = content.lineSequence()
    override val namespaces = mutableMapOf(
        NS_OBF to 0,
        NS_SRG to 1,
    )
    override val source: MappingsSource
        get() = MappingsSource.TSRG

    override fun parse(visitor: MappingsVisitor) = withVisitor(visitor) {
        visitor.visitSelfStart()

        lines.filterNotBlank().forEach { line ->
            val indentCount = line.indentCount
            val split = line.trimIndent().split(' ')
            when (indentCount) {
                0 -> readClass(split)
                1 -> readSecondary(line, split)
                else -> throw IllegalArgumentException("Invalid indent in line ${line + 1}: $indentCount")
            }
        }

        visitor.visitEnd()
    }
}
