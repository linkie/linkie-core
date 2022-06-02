package me.shedaniel.linkie.utils

import kotlin.math.max

interface StringLike {
    val length: Int

    operator fun get(index: Int): Char
    fun lowercase(): StringLike
    fun uppercase(): StringLike

    fun contains(other: StringLike, ignoreCase: Boolean = false): Boolean {
        for (index in 0..length) {
            if (other.regionMatchesImpl(0, this, index, other.length, ignoreCase))
                return true
        }
        return false
    }

    fun indexOf(c: Char): Int {
        for (index in 0..length) {
            if (this[index] == c)
                return index
        }
        return -1
    }

    fun contains(c: Char): Boolean = indexOf(c) != -1
    fun lastIndexOf(c: Char): Int {
        for (index in length downTo 0) {
            if (this[index] == c)
                return index
        }
        return -1
    }

    val string: String

    fun withOffset(offset: Int): StringLike
}

val String.like: StringLike
    get() = StringLikeImpl(this, 0)

val String.likeCopy: StringLike
    get() = StringLikeArrayBacked(toCharArray(), 0)

class StringLikeImpl(val _str: String, val offset: Int) : StringLike {
    override val length: Int
        get() = _str.length - offset

    override fun get(index: Int): Char =
        _str[index + offset]

    override fun lowercase(): StringLike =
        StringLikeImpl(_str.lowercase(), offset)

    override fun uppercase(): StringLike =
        StringLikeImpl(_str.uppercase(), offset)

    override fun indexOf(c: Char): Int =
        _str.indexOf(c, startIndex = offset) - offset

    override fun lastIndexOf(c: Char): Int =
        max(-1, _str.lastIndexOf(c) - offset)

    override fun contains(other: StringLike, ignoreCase: Boolean): Boolean {
        if (other is StringLikeImpl) { // smart cast
            for (index in 0..length) {
                if (other.string.regionMatches(0, _str, index + offset, other.length, ignoreCase))
                    return true
            }
        } else {
            for (index in 0..length) {
                if (other.regionMatchesImpl(0, this, index, other.length, ignoreCase))
                    return true
            }
        }
        return false
    }

    override fun withOffset(offset: Int): StringLike =
        StringLikeImpl(_str, this.offset + offset)

    override val string: String
        get() = if (offset == 0) _str else _str.substring(offset)

    override fun toString(): String {
        return string
    }

    override fun equals(other: Any?): Boolean {
        if (other is StringLike) {
            return other.string == string
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return string.hashCode()
    }
}

class StringLikeArrayBacked(val arr: CharArray, val offset: Int) : StringLike {
    override val length: Int
        get() = arr.size - offset

    override fun get(index: Int): Char =
        arr[index + offset]

    override fun lowercase(): StringLike {
        val newArr = CharArray(length - offset)
        for (index in newArr.indices) {
            newArr[index] = Character.toLowerCase(arr[index + offset])
        }
        return StringLikeArrayBacked(newArr, 0)
    }

    override fun uppercase(): StringLike {
        val newArr = CharArray(length - offset)
        for (index in newArr.indices) {
            newArr[index] = Character.toUpperCase(arr[index + offset])
        }
        return StringLikeArrayBacked(newArr, 0)
    }

    override fun withOffset(offset: Int): StringLike =
        StringLikeArrayBacked(arr, this.offset + offset)

    override val string: String
        get() = if (offset == 0) arr.concatToString() else arr.concatToString(startIndex = offset)

    override fun toString(): String {
        return string
    }

    override fun equals(other: Any?): Boolean {
        if (other is StringLike) {
            return other.string == string
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return string.hashCode()
    }
}

internal fun StringLike.regionMatchesImpl(thisOffset: Int, other: StringLike, otherOffset: Int, length: Int, ignoreCase: Boolean): Boolean {
    if ((otherOffset < 0) || (thisOffset < 0) || (thisOffset > this.length - length) || (otherOffset > other.length - length)) {
        return false
    }

    for (index in 0 until length) {
        if (!this[thisOffset + index].equals(other[otherOffset + index], ignoreCase))
            return false
    }
    return true
}
