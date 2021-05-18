package me.shedaniel.linkie.utils.query

import kotlin.jvm.JvmInline

@JvmInline
value class MatchAccuracy(val accuracy: Double) {
    companion object {
        val Exact = MatchAccuracy(1.0)
        val Fuzzy = MatchAccuracy(0.5)
    }
}

fun MatchAccuracy.isExact(): Boolean = this == MatchAccuracy.Exact
fun MatchAccuracy.isNotExact(): Boolean = this != MatchAccuracy.Exact
