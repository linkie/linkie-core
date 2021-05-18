package me.shedaniel.linkie.utils

class StringReader(val str: String) {
    private var cursor = 0

    fun read(): Char? = if (cursor >= str.length)
        null
    else str[cursor++]
}
