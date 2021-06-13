package me.shedaniel.linkie.utils

import me.shedaniel.linkie.utils.query.MatchAccuracy
import me.shedaniel.linkie.utils.query.QueryDefinition

val String.indentCount: Int
    get() = indexOfFirst { !it.isWhitespace() }

fun String.onlyClass(c: Char = '/'): String = onlyClassOrNull(c) ?: this

fun String.onlyClassOrNull(c: Char = '/'): String? {
    val indexOf = lastIndexOf(c)
    return if (indexOf < 0) null else substring(indexOf + 1)
}

inline fun String.remapDescriptor(classMappings: (String) -> String): String {
    val reader = StringReader(this)
    return buildString {
        var insideClassName = false
        val className = StringBuilder()
        while (true) {
            val c: Char = reader.read() ?: break
            if (c == ';') {
                insideClassName = false
                append(classMappings(className.toString()))
            }
            if (insideClassName) {
                className.append(c)
            } else {
                append(c)
            }
            if (!insideClassName && c == 'L') {
                insideClassName = true
                className.setLength(0)
            }
        }
    }
}

fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

fun String.localiseFieldDesc(): String {
    if (isEmpty()) return this
    val clear = dropWhile { it == '[' }
    val arrays = length - clear.length

    return buildString {
        clear.firstOrNull()?.let { first ->
            if (first == 'L') {
                append(clear.substring(1 until clear.length - 1).replace('/', '.'))
            } else {
                append(localisePrimitive(first))
            }
        }

        for (i in 0 until arrays) {
            append("[]")
        }
    }
}

fun localisePrimitive(char: Char): String = when (char) {
    'Z' -> "boolean"
    'C' -> "char"
    'B' -> "byte"
    'S' -> "short"
    'I' -> "int"
    'F' -> "float"
    'J' -> "long"
    'D' -> "double"
    else -> char.toString()
}

/**
 * Determines if the specified string is permissible as a Java identifier.
 */
expect fun String.isValidJavaIdentifier(): Boolean

fun CharSequence.allIndexed(predicate: (index: Int, Char) -> Boolean): Boolean {
    var index = 0
    for (char in this) {
        if (!predicate(index++, char)) {
            return false
        }
    }
    return true
}

fun hashCodeOf(vararg fields: Any?): Int {
    var result = 17
    fields.forEach { field ->
        result = 37 * result + (field?.hashCode() ?: 0)
    }
    return result
}
