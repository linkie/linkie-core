package me.shedaniel.linkie.utils

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
