package me.shedaniel.linkie.parser.srg

import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.parser.MappingsVisitor
import me.shedaniel.linkie.utils.filterNotBlank
import me.shedaniel.linkie.utils.indentCount

class TSrg2Parser(content: String) : AbstractTsrgParser() {
    val lines = content.lineSequence()
    override val namespaces = mutableMapOf<String, Int>()
    override val source: MappingsSource
        get() = MappingsSource.TSRG2

    override fun parse(visitor: MappingsVisitor) = withVisitor(visitor) {
        lines.filterNotBlank().forEachIndexed { index, line ->
            if (index == 0) {
                readHeader(line)
            } else {
                val indentCount = line.indentCount
                val split = line.trimIndent().split(' ')
                when (indentCount) {
                    0 -> readClass(split)
                    1 -> readSecondary(line, split)
                    2 -> readParameter(split)
                    else -> throw IllegalArgumentException("Invalid indent in line ${line + 1}: $indentCount")
                }
            }
        }

        visitor.visitEnd()
    }

    private fun readHeader(line: String) {
        require(line.startsWith("tsrg2")) { "The first line must start with tsrg2!" }
        line.splitToSequence(' ').drop(1).forEachIndexed { namespaceIndex, namespace ->
            namespaces[namespace] = namespaceIndex
        }
        visitor.visitSelfStart()
    }

    private fun readParameter(split: List<String>) {
        if (lastMethodVisitor != null) {
            if (split.size > 1) {
                lastMethodParameter.split = { namespaceIndex ->
                    split[namespaceIndex + 1]
                }
                lastMethodVisitor!!.visitParameter(split[0].toInt(), lastMethodParameter)
            }
        }
    }
}
