package me.shedaniel.linkie.utils

import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.getClassByObfName
import java.io.IOException
import java.io.StringReader
import java.util.*
import java.util.regex.Pattern
import kotlin.Comparator
import kotlin.math.min

fun <T> Iterable<T>.dropAndTake(drop: Int, take: Int): Sequence<T> =
        asSequence().drop(drop).take(take)

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

fun String.onlyClass(c: Char = '/'): String {
    val indexOf = lastIndexOf(c)
    return if (indexOf < 0) this else substring(indexOf + 1)
}

fun String?.containsOrMatchWildcard(searchTerm: String): MatchResult {
    return when {
        this == null -> MatchResult(false)
        searchTerm.contains('/') -> MatchResult(contains(searchTerm, true), searchTerm, this)
        else -> MatchResult(onlyClass().contains(searchTerm.onlyClass(), true), searchTerm.onlyClass(), onlyClass())
    }
}

data class MatchResult(val matched: Boolean, val matchStr: String? = null, val selfTerm: String? = null)

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
