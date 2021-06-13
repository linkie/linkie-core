package me.shedaniel.linkie.utils

class StringReader(val str: String) {
    private var cursor = 0

    fun read(): Char? = if (cursor >= str.length) null
    else str[cursor++]

    val peek: Char?
        get() = if (cursor >= str.length) null
        else str[cursor]

    fun readLine(): String? {
        if (cursor >= str.length) return null
        val indexOf = str.indexOf('\n', cursor + 1)
        return if (indexOf == -1) {
            cursor = str.length
            str.substring(cursor)
        } else {
            val s = str.substring(cursor, indexOf)
            cursor = indexOf + 1
            s
        }
    }
}

fun String.columnView(offset: Int = 0) = ColumnView(lines(), offset)

fun String.indentedColumnView(indent: String = "\t", offset: Int = 0): IndentedColumnView {
    val indentCount = indentCount
    return IndentedColumnView(indentCount / indent.length, substring(indentCount).lines(), offset)
}

class ColumnView(val split: List<String>, var offset: Int = 0) {
    operator fun get(index: Int): String = split[index + offset]
    
    fun asSequence() = split.asSequence()
}

class IndentedColumnView(val indent: Int, val split: List<String>, var offset: Int = 0) {
    operator fun get(index: Int): String = split[index + offset]

    fun asSequence() = split.asSequence()
}
