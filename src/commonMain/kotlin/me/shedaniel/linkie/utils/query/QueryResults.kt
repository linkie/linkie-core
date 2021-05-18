package me.shedaniel.linkie.utils.query

import me.shedaniel.linkie.Mappings
import me.shedaniel.linkie.MappingsMetadata

data class ResultHolder<T>(
    val value: T,
    val score: Double,
)

infix fun <T> T.hold(score: Double): ResultHolder<T> = ResultHolder(this, score)

fun <T> QueryResult<Mappings, T>.toSimpleMappingsMetadata(): QueryResult<MappingsMetadata, T> =
    mapKey { it.toSimpleMappingsMetadata() }

data class QueryResult<A : MappingsMetadata, T>(
    val mappings: A,
    val value: T,
)

inline fun <A : MappingsMetadata, T, B : MappingsMetadata> QueryResult<A, T>.mapKey(transformer: (A) -> B): QueryResult<B, T> {
    return QueryResult(transformer(mappings), value)
}

inline fun <A : MappingsMetadata, T, V> QueryResult<A, T>.map(transformer: (T) -> V): QueryResult<A, V> {
    return QueryResult(mappings, transformer(value))
}
