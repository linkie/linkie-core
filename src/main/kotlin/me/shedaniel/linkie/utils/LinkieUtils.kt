package me.shedaniel.linkie.utils

import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.getClassByObfName
import java.io.File
import java.io.IOException
import java.io.StringReader
import kotlin.math.min

fun <T> Iterable<T>.dropAndTake(drop: Int, take: Int): Sequence<T> =
    asSequence().drop(drop).take(take)

fun <T, R> Iterable<T>.firstMapped(filterTransform: (entry: T) -> R?): R? {
    for (entry in this) {
        return filterTransform(entry) ?: continue
    }
    return null
}

fun <T, R> Sequence<T>.firstMapped(filterTransform: (entry: T) -> R?): R? {
    for (entry in this) {
        return filterTransform(entry) ?: continue
    }
    return null
}

fun <T> Sequence<T>.inverse(): Sequence<T> {
    return Sequence {
        val list = toList()
        var counter = list.lastIndex
        object : Iterator<T> {
            override fun hasNext(): Boolean = counter >= 1
            override fun next(): T = list[counter--]
        }
    }
}

fun <T, R> Sequence<T>.inverseMapIndexed(transform: (index: Int, entry: T) -> R): Sequence<R> {
    return Sequence {
        val list = toList()
        var counter = list.lastIndex
        object : Iterator<R> {
            override fun hasNext(): Boolean = counter >= 1
            override fun next(): R = (counter--).let {
                transform(list.size - 1 - it, list[it])
            }
        }
    }
}

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

fun String?.doesContainsOrMatchWildcard(searchTerm: String): Boolean {
    if (this == null) return false
    return if (searchTerm.onlyClassOrNull() != null) {
        contains(searchTerm, true)
    } else {
        onlyClass().contains(searchTerm, true)
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

fun String.mapFieldIntermediaryDescToNamed(mappingsContainer: MappingsContainer): String =
    remapFieldDescriptor { mappingsContainer.getClass(it)?.mappedName ?: it }

fun String.mapMethodIntermediaryDescToNamed(mappingsContainer: MappingsContainer): String =
    remapMethodDescriptor { mappingsContainer.getClass(it)?.mappedName ?: it }

fun String.mapMethodOfficialDescToNamed(mappingsContainer: MappingsContainer): String =
    remapMethodDescriptor { mappingsContainer.getClassByObfName(it)?.mappedName ?: it }

fun String.remapFieldDescriptor(classMappings: (String) -> String): String {
    return try {
        val reader = StringReader(this)
        val result = StringBuilder()
        var insideClassName = false
        val className = StringBuilder()
        while (true) {
            val c: Int = reader.read()
            if (c == -1) {
                break
            }
            if (c == ';'.toInt()) {
                insideClassName = false
                result.append(classMappings(className.toString()))
            }
            if (insideClassName) {
                className.append(c.toChar())
            } else {
                result.append(c.toChar())
                if (c == 'L'.toInt()) {
                    insideClassName = true
                    className.setLength(0)
                }
            }
        }
        result.toString()
    } catch (e: IOException) {
        throw AssertionError(e)
    }
}

fun String.remapMethodDescriptor(classMappings: (String) -> String): String {
    return try {
        val reader = StringReader(this)
        val result = StringBuilder()
        var started = false
        var insideClassName = false
        val className = StringBuilder()
        while (true) {
            val c: Int = reader.read()
            if (c == -1) {
                break
            }
            if (c == ';'.toInt()) {
                insideClassName = false
                result.append(classMappings(className.toString()))
            }
            if (insideClassName) {
                className.append(c.toChar())
            } else {
                result.append(c.toChar())
            }
            if (c == '('.toInt()) {
                started = true
            }
            if (!insideClassName && started && c == 'L'.toInt()) {
                insideClassName = true
                className.setLength(0)
            }
        }
        result.toString()
    } catch (e: IOException) {
        throw AssertionError(e)
    }
}

fun String.isValidJavaIdentifier(): Boolean {
    forEachIndexed { index, c ->
        if (index == 0) {
            if (!Character.isJavaIdentifierStart(c))
                return false
        } else {
            if (!Character.isJavaIdentifierPart(c))
                return false
        }
    }
    return isNotEmpty()
}

operator fun File.div(related: String): File = File(this, related)