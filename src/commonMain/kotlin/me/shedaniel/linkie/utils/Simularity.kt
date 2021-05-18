package me.shedaniel.linkie.utils

import kotlin.math.min

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
        if (i > 0) {
            costs[s22.length] = lastValue
        }
    }
    return costs[s22.length]
}

fun String?.similarityOnNull(other: String?, onlyClass: Boolean = true): Double = if (this == null || other == null) 0.0 else similarity(other, onlyClass)

fun String.similarity(other: String, doesOnlyClass: Boolean = true): Double {
    val s11 = if (doesOnlyClass) this.onlyClass() else this
    val s12 = s11.lowercase()
    val s21 = if (doesOnlyClass) other.onlyClass() else other
    val s22 = s21.lowercase()
    return if (s11 != s12 || s21 != s22) {
        (innerSimilarity(s11, s21) + innerSimilarity(s12, s22)) / 2.0
    } else innerSimilarity(s11, s21)
}

private fun innerSimilarity(s11: String, s21: String): Double {
    var longer = s11
    var shorter = s21
    if (s11.length < s21.length) { // longer should always have greater length
        longer = s21
        shorter = s11
    }
    val longerLength = longer.length
    return if (longerLength == 0) {
        1.0 /* both strings are zero length */
    } else (longerLength - editDistance(longer, shorter)) / longerLength.toDouble()
}
