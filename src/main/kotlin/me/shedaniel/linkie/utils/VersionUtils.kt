package me.shedaniel.linkie.utils

import java.util.*
import java.util.regex.Pattern
import kotlin.Comparator

class Version(val major: Int, val minor: Int, val patch: Int, val snapshot: String? = null) : Comparable<Version> {
    constructor(snapshot: String? = null) : this(0, 0, 0, snapshot)
    constructor(major: Int, snapshot: String? = null) : this(major, 0, 0, snapshot)
    constructor(major: Int, minor: Int, snapshot: String? = null) : this(major, minor, 0, snapshot)

    private val version = versionOf(major, minor, patch)

    private fun versionOf(major: Int, minor: Int, patch: Int): Long {
        return major.toLong().shl(16) + minor.toLong().shl(8) + patch.toLong()
    }

    override fun toString(): String = (if (patch == 0) "$major.$minor" else "$major.$minor.$patch") + (snapshot?.let { "-$it" } ?: "")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherVersion = (other as? Version) ?: return false
        return this.version == otherVersion.version && this.snapshot == otherVersion.snapshot
    }

    override fun hashCode(): Int = Objects.hash(version, snapshot)

    @Suppress("RemoveExplicitTypeArguments")
    override fun compareTo(other: Version): Int = Comparator.comparingLong<Version> { it.version }.thenComparing(compareBy(nullsLast<String>()) { it.snapshot }).compare(this, other)

    fun isAtLeast(major: Int, minor: Int): Boolean = // this.version >= versionOf(major, minor, 0)
            this.major > major || (this.major == major &&
                    this.minor >= minor)

    fun isAtLeast(major: Int, minor: Int, patch: Int): Boolean = // this.version >= versionOf(major, minor, patch)
            this.major > major || (this.major == major &&
                    (this.minor > minor || this.minor == minor &&
                            this.patch >= patch))
}

val snapshotRegex = Pattern.compile("(?:Snapshot )?(\\d+)w([0-9]\\d*)([a-z])")!!
val bigPreReleaseRegex = ".* Pre-[Rr]elease \\d+".toRegex()
val preReleaseRegex = " Pre-[Rr]elease ".toRegex()

private val versionCache = mutableMapOf<String, Version?>()

fun String.toVersion(): Version = tryToVersion()!!
fun String.tryToVersion(): Version? = versionCache.getOrPut(this) { this.innerToVersion() }

private fun String.innerToVersion(): Version? {
    try {
        if (bigPreReleaseRegex.matches(this)) {
            return replace(preReleaseRegex, "-pre").toVersion()
        }
        val matcher = snapshotRegex.matcher(this)
        if (matcher.matches()) {
            val year = matcher.group(1).toInt()
            val week = matcher.group(2)
            val weekInt = week.toInt()
            val build = matcher.group(3).first()
            if (year == 20 && weekInt >= 6) {
                return Version(1, 16, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 19 && weekInt >= 34) {
                return Version(1, 15, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 18 && weekInt >= 43 || year == 19 && weekInt <= 14) {
                return Version(1, 14, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 18 && weekInt >= 30 && weekInt <= 33) {
                return Version(1, 13, 1, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 17 && weekInt >= 43 || year == 18 && weekInt <= 22) {
                return Version(1, 13, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 17 && weekInt == 31) {
                return Version(1, 12, 1, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 17 && weekInt >= 6 && weekInt <= 18) {
                return Version(1, 12, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 16 && weekInt == 50) {
                return Version(1, 11, 1, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 16 && weekInt >= 32 && weekInt <= 44) {
                return Version(1, 11, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 16 && weekInt >= 20 && weekInt <= 21) {
                return Version(1, 10, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 16 && weekInt >= 14 && weekInt <= 15) {
                return Version(1, 9, 3, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 15 && weekInt >= 31 || year == 16 && weekInt <= 7) {
                return Version(1, 9, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 14 && weekInt >= 2 && weekInt <= 34) {
                return Version(1, 8, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 13 && weekInt >= 47 && weekInt <= 49) {
                return Version(1, 7, 4, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 13 && weekInt >= 36 && weekInt <= 43) {
                return Version(1, 7, 2, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 13 && weekInt >= 16 && weekInt <= 26) {
                return Version(1, 6, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 13 && weekInt >= 11 && weekInt <= 12) {
                return Version(1, 5, 1, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 13 && weekInt >= 1 && weekInt <= 10) {
                return Version(1, 5, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 12 && weekInt >= 49 && weekInt <= 50) {
                return Version(1, 4, 6, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 12 && weekInt >= 32 && weekInt <= 42) {
                return Version(1, 4, 2, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 12 && weekInt >= 15 && weekInt <= 30) {
                return Version(1, 3, 1, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 12 && weekInt >= 3 && weekInt <= 8) {
                return Version(1, 2, 1, snapshot = "alpha.$year.w.$week$build")
            } else if (year == 11 && weekInt >= 47 || year == 12 && weekInt <= 1) {
                return Version(1, 1, snapshot = "alpha.$year.w.$week$build")
            }
        }
        if (contains('-')) {
            val byDash = split('-')
            val byDot = byDash.first().split('.')

            return when (byDot.size) {
                0 -> Version(snapshot = byDash.drop(1).joinToString("-"))
                1 -> Version(byDot[0].toInt(), snapshot = byDash.drop(1).joinToString("-"))
                2 -> Version(byDot[0].toInt(), byDot[1].toInt(), snapshot = byDash.drop(1).joinToString("-"))
                3 -> Version(byDot[0].toInt(), byDot[1].toInt(), byDot[2].toInt(), snapshot = byDash.drop(1).joinToString("-"))
                else -> null
            }
        }
        val byDot = split('.')

        return when (byDot.size) {
            0 -> Version()
            1 -> Version(byDot[0].toInt())
            2 -> Version(byDot[0].toInt(), byDot[1].toInt())
            3 -> Version(byDot[0].toInt(), byDot[1].toInt(), byDot[2].toInt())
            else -> null
        }
    } catch (e: Exception) {
        return null
    }
}
