package me.shedaniel.linkie.utils

data class Version(val major: Int, val minor: Int, val patch: Int, val snapshot: String? = null) : Comparable<Version> {
    constructor(snapshot: String? = null) : this(0, 0, 0, snapshot)
    constructor(major: Int, snapshot: String? = null) : this(major, 0, 0, snapshot)
    constructor(major: Int, minor: Int, snapshot: String? = null) : this(major, minor, 0, snapshot)

    companion object {
        private val comparator = compareBy<Version> { it.version }.thenBy(nullsLast()) { it.snapshot }
    }

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

    override fun hashCode(): Int = hashCodeOf(version, snapshot)

    override fun compareTo(other: Version): Int = comparator.compare(this, other)

    fun isAtLeast(major: Int, minor: Int): Boolean = // this.version >= versionOf(major, minor, 0)
        this.major > major || (this.major == major &&
                this.minor >= minor)

    fun isAtLeast(major: Int, minor: Int, patch: Int): Boolean = // this.version >= versionOf(major, minor, patch)
        this.major > major || (this.major == major &&
                (this.minor > minor || this.minor == minor &&
                        this.patch >= patch))
}

val snapshotRegex = "(?:Snapshot )?(\\d+)w([0-9]\\d*)([a-z])".toRegex()
val bigPreReleaseRegex = ".* Pre-[Rr]elease \\d+".toRegex()
val preReleaseRegex = " Pre-[Rr]elease ".toRegex()

private val versionCache = mutableMapOf<String, Version?>()

fun String.toVersion(): Version = tryToVersion() ?: throw IllegalArgumentException("$this is not a valid version!")
fun String.tryToVersion(): Version? = versionCache.getOrPut(this) { this.innerToVersion() }

private const val YEAR_GROUP = 1
private const val WEEK_GROUP = 2
private const val BUILD_GROUP = 3

private object MinecraftVersionLookupData {
    data class Snapshot(
        val year: Int,
        val week: Int,
    )

    val map = mutableMapOf<Version, Snapshot>()

    init {
        parse("""
1.20.5 23 51
1.20.3 23 40
1.20.2 23 31
1.20   23 12
1.19.4 23 03
1.19.3 22 42
1.19.1 22 24
1.19   22 11
1.18.2 22 03
1.18   21 37
1.17   20 45
1.16.2 20 27
1.16   20 6
1.15   19 34
1.14   18 43
1.13   18 30
1.12.1 17 31
1.12   17 6
1.11.1 16 50
1.11   16 32
1.10   16 20
1.9.3  16 14
1.9    15 31
1.8    14 2
1.7.4  13 47
1.7.2  13 36
1.6    13 16
1.5.1  13 11
1.5    13 1
1.4.6  12 49
1.4.2  12 32
1.3.1  12 15
1.2.1  12 3
1.1    11 47
        """.trimIndent())
    }

    private fun parse(data: String) {
        val regex = "\\s+".toRegex()
        data.lineSequence().forEach { line ->
            val split = line.split(regex)
            map[split[0].toVersion()] = Snapshot(split[1].toInt(), split[2].toInt())
        }
    }
}

private fun String.innerToVersion(): Version? {
    try {
        if (bigPreReleaseRegex.matches(this)) {
            return replace(preReleaseRegex, "-pre").toVersion()
        }
        val matcher = snapshotRegex.matchEntire(this)
        if (matcher != null) {
            val year = matcher.groups[YEAR_GROUP]!!.value.toInt()
            val week = matcher.groups[WEEK_GROUP]!!.value
            val weekInt = week.toInt()
            val build = matcher.groups[BUILD_GROUP]!!.value.first()
            MinecraftVersionLookupData.map.forEach { (mcVersion, snapshot) ->
                if (year > snapshot.year || (year == snapshot.year && weekInt >= snapshot.week)) {
                    return mcVersion.copy(snapshot = "alpha.$year.w.$week$build")
                }
            }
            return Version(1, snapshot = "alpha.$year.w.$week$build")
        }

        val dashIndex = indexOf('-')
        val byDot: List<String> = if (dashIndex != -1) {
            substring(0, dashIndex)
        } else {
            this
        }.split('.')
        val snapshot: String? = if (dashIndex != -1) {
            substring(dashIndex + 1, length)
        } else {
            null
        }

        fun get(index: Int): Int = byDot.getMappedOrDefault(index, 0, String::toInt)
        return Version(get(0), get(1), get(2), snapshot = snapshot)
    } catch (ignored: Exception) {
        return null
    }
}
