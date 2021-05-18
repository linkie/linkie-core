package me.shedaniel.linkie.utils.query

import me.shedaniel.linkie.Mappings
import me.shedaniel.linkie.MappingsProvider

data class QueryContext(
    val provider: suspend () -> Mappings,
    val searchKey: String,
    val accuracy: MatchAccuracy = MatchAccuracy.Exact,
) {
    constructor(
        provider: MappingsProvider,
        searchKey: String,
        accuracy: MatchAccuracy = MatchAccuracy.Exact,
    ) : this({ provider.get() }, searchKey, accuracy)

    suspend fun get(): Mappings = provider()
}
