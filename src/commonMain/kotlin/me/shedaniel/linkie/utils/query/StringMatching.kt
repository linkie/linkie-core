package me.shedaniel.linkie.utils.query

import me.shedaniel.linkie.utils.onlyClass
import me.shedaniel.linkie.utils.onlyClassOrNull
import me.shedaniel.linkie.utils.similarity

fun String?.matchWithSimilarity(searchTerm: String, accuracy: MatchAccuracy, doesOnlyClass: Boolean): Double? {
    if (this == null) return null
    val searchOnlyClass = searchTerm.onlyClassOrNull()
    var similarity = -1.0
    fun getSimilarity(): Double {
        if (similarity == -1.0) {
            similarity = this.similarity(searchTerm, doesOnlyClass)
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
