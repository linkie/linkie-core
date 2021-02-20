package me.shedaniel.linkie.utils

import com.soywiz.korio.file.VfsFile
import java.io.StringReader
import kotlin.math.min

fun <T> Iterable<T>.dropAndTake(drop: Int, take: Int): Sequence<T> =
    asSequence().drop(drop).take(take)

inline fun <T, R> Iterable<T>.firstMapped(filterTransform: (entry: T) -> R?): R? {
    for (entry in this) {
        return filterTransform(entry) ?: continue
    }
    return null
}

inline fun <T, R> Sequence<T>.firstMapped(filterTransform: (entry: T) -> R?): R? = asIterable().firstMapped(filterTransform)

inline fun <T, R : Comparable<R>> Iterable<T>.maxOfIgnoreNull(filterTransform: (entry: T) -> R?): R? {
    val iterator = iterator()
    if (!iterator.hasNext()) return null
    var maxValue = filterTransform(iterator.next())
    while (iterator.hasNext()) {
        val v = filterTransform(iterator.next())
        maxValue = when {
            maxValue == null -> v
            v == null -> maxValue
            else -> maxOf(maxValue, v)
        }
    }
    return maxValue
}

inline fun <T, R : Comparable<R>> Sequence<T>.maxOfIgnoreNull(filterTransform: (entry: T) -> R?): R? = asIterable().maxOfIgnoreNull(filterTransform)

inline fun <T, R : Comparable<R>> Iterable<T>.maxOfIgnoreNullSelf(filterTransform: (entry: T) -> R?): T? {
    val iterator = iterator()
    if (!iterator.hasNext()) return null
    var max = iterator.next()
    var maxValue = filterTransform(max)
    while (iterator.hasNext()) {
        val next = iterator.next()
        val v = filterTransform(next)

        if (v != null && (maxValue == null || v > maxValue)) {
            maxValue = v
            max = next
        }
    }
    return if (maxValue == null) null else max
}

inline fun <T, R : Comparable<R>> Sequence<T>.maxOfIgnoreNullSelf(filterTransform: (entry: T) -> R?): R? = asIterable().maxOfIgnoreNull(filterTransform)

private fun editDistance(s11: String, s22: String): Int {
    val costs = IntArray(s22.length + 1)
    for (i in 0..s11.length) {
        var lastValue = i
        for (j in 0..s22.length) {
            if (i == 0)
                costs[j] = j
            else {
                if (j > 0) {
                    var newValue = costs[j - 1]
                    if (s11[i - 1] != s22[j - 1])
                        newValue = min(min(newValue, lastValue), costs[j]) + 1
                    costs[j - 1] = lastValue
                    lastValue = newValue
                }
            }
        }
        if (i > 0)
            costs[s22.length] = lastValue
    }
    return costs[s22.length]
}

fun String?.similarityOnNull(other: String?): Double = if (this == null || other == null) 0.0 else similarity(other)

fun String.similarity(other: String): Double {
    val s11 = this.onlyClass().toLowerCase()
    val s22 = other.onlyClass().toLowerCase()
    var longer = s11
    var shorter = s22
    if (s11.length < s22.length) { // longer should always have greater length
        longer = s22
        shorter = s11
    }
    val longerLength = longer.length
    return if (longerLength == 0) {
        1.0 /* both strings are zero length */
    } else (longerLength - editDistance(longer, shorter)) / longerLength.toDouble()
}

fun String.onlyClass(c: Char = '/'): String = onlyClassOrNull(c) ?: this

fun String.onlyClassOrNull(c: Char = '/'): String? {
    val indexOf = lastIndexOf(c)
    return if (indexOf < 0) null else substring(indexOf + 1)
}

fun String?.matchWithSimilarity(searchTerm: String, accuracy: MatchAccuracy): Double? {
    if (this == null) return null
    val searchOnlyClass = searchTerm.onlyClassOrNull()
    var similarity = -1.0
    fun getSimilarity(): Double {
        if (similarity == -1.0) {
            similarity = this.similarity(searchTerm)
        }
        return similarity
    }
    return if (searchOnlyClass != null) {
        getSimilarity().takeIf { contains(searchTerm, true) || (accuracy.isNotExact() && it >= accuracy.accuracy) }
    } else {
        val onlyClass = onlyClass()
        getSimilarity().takeIf { onlyClass.contains(searchTerm, true) || (accuracy.isNotExact() && it >= accuracy.accuracy) }
    }
}

fun String?.containsOrMatchWildcardOrNull(searchTerm: String): MatchResult? {
    if (this == null) return null
    val searchOnlyClass = searchTerm.onlyClassOrNull()
    return if (searchOnlyClass != null) {
        if (contains(searchTerm, true)) {
            MatchResult(searchTerm, this)
        } else {
            null
        }
    } else {
        val onlyClass = onlyClass()
        if (onlyClass.contains(searchTerm, true)) {
            MatchResult(searchTerm, onlyClass)
        } else {
            null
        }
    }
}

fun String?.containsOrMatchWildcardOrNull(searchTerm: String, definition: QueryDefinition): MatchResultWithDefinition? {
    if (this == null) return null
    val searchOnlyClass = searchTerm.onlyClassOrNull()
    return if (searchOnlyClass != null) {
        if (contains(searchTerm, true)) {
            MatchResultWithDefinition(searchTerm, this, definition)
        } else {
            null
        }
    } else {
        val onlyClass = onlyClass()
        if (onlyClass.contains(searchTerm, true)) {
            MatchResultWithDefinition(searchTerm, onlyClass, definition)
        } else {
            null
        }
    }
}

data class MatchResult(val matchStr: String, val selfTerm: String)
data class MatchResultWithDefinition(val matchStr: String, val selfTerm: String, val definition: QueryDefinition)

fun String.remapDescriptor(classMappings: (String) -> String): String {
    val reader = StringReader(this)
    return buildString {
        var insideClassName = false
        val className = StringBuilder()
        while (true) {
            val c: Int = reader.read()
            if (c == -1) {
                break
            }
            if (c == ';'.toInt()) {
                insideClassName = false
                append(classMappings(className.toString()))
            }
            if (insideClassName) {
                className.append(c.toChar())
            } else {
                append(c.toChar())
            }
            if (!insideClassName && c == 'L'.toInt()) {
                insideClassName = true
                className.setLength(0)
            }
        }
    }
}

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
fun String.isValidJavaIdentifier(): Boolean {
    return isNotEmpty() && allIndexed { index, c ->
        if (index == 0) {
            Character.isJavaIdentifierStart(c)
        } else {
            Character.isJavaIdentifierPart(c)
        }
    }
}

fun CharSequence.allIndexed(predicate: (index: Int, Char) -> Boolean): Boolean {
    var index = 0
    for (char in this) {
        if (!predicate(index++, char)) {
            return false
        }
    }
    return true
}

operator fun VfsFile.div(related: String): VfsFile = this[related]

fun <T> singleSequenceOf(value: T): Sequence<T> = SingleSequence(value)

inline fun <T, R> List<T>.getMappedOrDefault(index: Int, default: R, transform: (T) -> R): R {
    return getOrNull(index)?.let(transform) ?: default
}

inline fun <T, R> List<T>.getMappedOrDefaulted(index: Int, transform: (T) -> R, default: (Int) -> R): R {
    return getOrNull(index)?.let(transform) ?: default(index)
}

fun Sequence<String>.filterNotBlank(): Sequence<String> = filterNot(String::isBlank)

private class SingleSequence<T>(private var value: T?) : Iterator<T>, Sequence<T> {
    private var first = true
    override fun iterator(): Iterator<T> =
        this.takeIf { first } ?: throw UnsupportedOperationException()

    override fun hasNext(): Boolean = first
    override fun next(): T {
        if (first) {
            val answer: T = value!!
            first = false
            value = null
            return answer
        }
        throw NoSuchElementException()
    }
}

fun hashCodeOf(vararg fields: Any?): Int {
    var result = 17
    fields.forEach { field ->
        result = 37 * result + (field?.hashCode() ?: 0)
    }
    return result
}
