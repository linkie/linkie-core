package me.shedaniel.linkie.utils

import me.shedaniel.linkie.Class
import me.shedaniel.linkie.Field
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.MappingsEntry
import me.shedaniel.linkie.MappingsEntryType
import me.shedaniel.linkie.MappingsEntryType.CLASS
import me.shedaniel.linkie.MappingsEntryType.FIELD
import me.shedaniel.linkie.MappingsEntryType.METHOD
import me.shedaniel.linkie.MappingsMember
import me.shedaniel.linkie.MappingsMetadata
import me.shedaniel.linkie.MappingsProvider
import me.shedaniel.linkie.Method
import me.shedaniel.linkie.getClassByObfName
import me.shedaniel.linkie.optimumName

typealias ClassResultSequence = Sequence<ResultHolder<Class>>
typealias FieldResultSequence = Sequence<ResultHolder<Pair<Class, Field>>>
typealias MethodResultSequence = Sequence<ResultHolder<Pair<Class, Method>>>

enum class QueryDefinition(val toDefinition: MappingsEntry.() -> String?) {
    MAPPED({ mappedName }),
    INTERMEDIARY({ intermediaryName }),
    OBF_MERGED({ obfName.merged }),
    OBF_CLIENT({ obfName.client }),
    OBF_SERVER({ obfName.server }),
    WILDCARD({ throw IllegalStateException("Cannot get definition of type WILDCARD!") });

    operator fun invoke(entry: MappingsEntry): String? = toDefinition(entry)

    companion object {
        val allProper = listOf(
            MAPPED,
            INTERMEDIARY,
            OBF_MERGED,
            OBF_CLIENT,
            OBF_SERVER,
        )
    }
}

object MappingsQuery {
    private data class MemberResultMore<T : MappingsMember>(
        val parent: Class,
        val field: T,
        val parentDef: QueryDefinition,
        val fieldDef: QueryDefinition,
    ) {
        fun toSimplePair(): Pair<Class, T> = parent to field
    }

    fun errorNoResultsFound(type: MappingsEntryType?, searchKey: String) {
        val onlyClass = searchKey.onlyClass()

        throw when {
            onlyClass.firstOrNull()?.isDigit() == true && !onlyClass.isValidJavaIdentifier() ->
                NullPointerException("No results found! `$onlyClass` is not a valid java identifier!")
            type != METHOD && (searchKey.startsWith("func_") || searchKey.startsWith("method_")) ->
                throw NullPointerException("No results found! `$searchKey` looks like a method!")
            type != FIELD && searchKey.startsWith("field_") ->
                throw NullPointerException("No results found! `$searchKey` looks like a field!")
            type != CLASS && !searchKey.startsWith("class_") && searchKey.firstOrNull()?.isLowerCase() == true ->
                throw NullPointerException("No results found! `$searchKey` doesn't look like a class!")
            else -> NullPointerException("No results found!")
        }
    }

    fun MappingsEntry.searchDefinition(classKey: String): QueryDefinition? {
        return QueryDefinition.allProper.firstOrNull { it(this).doesContainsOrMatchWildcard(classKey) }
    }

    fun MappingsEntry.searchWithDefinition(classKey: String): MatchResultWithDefinition? {
        return QueryDefinition.allProper.firstMapped { it(this).containsOrMatchWildcardOrNull(classKey, it) }
    }

    fun MappingsEntry.search(classKey: String): MatchResult? {
        return QueryDefinition.allProper.firstMapped { it(this).containsOrMatchWildcardOrNull(classKey) }
    }

    fun queryClasses(context: QueryContext): QueryResult<MappingsContainer, ClassResultSequence> {
        val searchKey = context.searchKey
        val isSearchKeyWildcard = searchKey == "*"
        val mappings = context.provider.get()

        val results: Sequence<ResultHolder<Class>> = if (isSearchKeyWildcard) {
            mappings.classes.asSequence()
                .sortedBy { it.intermediaryName }
                .mapIndexed { index, entry -> entry hold (mappings.classes.size - index + 1) * 100.0 }
        } else {
            mappings.classes.asSequence()
                .map { c ->
                    c.search(searchKey)?.let { c hold it.selfTerm.similarity(it.matchStr) }
                }
                .filterNotNull()
        }.sortedWith(compareByDescending<ResultHolder<Class>> { it.score }
            .thenBy { it.value.optimumName.onlyClass() })

        return QueryResult(mappings, results)
    }

    fun queryFields(context: QueryContext): QueryResult<MappingsContainer, FieldResultSequence> =
        queryMember(context) { it.fields.asSequence() }

    fun queryMethods(context: QueryContext): QueryResult<MappingsContainer, MethodResultSequence> =
        queryMember(context) { it.methods.asSequence() }

    fun <T : MappingsMember> queryMember(
        context: QueryContext,
        memberGetter: (Class) -> Sequence<T>,
    ): QueryResult<MappingsContainer, Sequence<ResultHolder<Pair<Class, T>>>> {
        val searchKey = context.searchKey
        val hasClassFilter = searchKey.contains('/')
        val classKey = if (hasClassFilter) searchKey.substring(0, searchKey.lastIndexOf('/')) else ""
        val fieldKey = searchKey.onlyClass()
        val isClassKeyWildcard = classKey == "*"
        val isFieldKeyWildcard = fieldKey == "*"
        val mappings = context.provider.get()

        val members: Sequence<MemberResultMore<T>> = mappings.classes.asSequence().flatMap { c ->
            val queryDefinition: QueryDefinition? = if (!hasClassFilter || isClassKeyWildcard) QueryDefinition.WILDCARD else c.searchDefinition(classKey)
            queryDefinition?.let { parentDef ->
                if (isFieldKeyWildcard) {
                    memberGetter(c)
                        .map { MemberResultMore(c, it, parentDef, QueryDefinition.WILDCARD) }
                } else {
                    memberGetter(c)
                        .map { f -> f.searchDefinition(fieldKey)?.let { MemberResultMore(c, f, parentDef, it) } }
                        .filterNotNull()
                }
            } ?: emptySequence()
        }.distinctBy { it.field }

        val sortedMembers: Sequence<ResultHolder<Pair<Class, T>>> = when {
            fieldKey == "*" && (!hasClassFilter || isClassKeyWildcard) -> {
                // Class and field both wildcard
                members.sortedWith(
                    compareBy<MemberResultMore<T>> { it.parent.optimumName.onlyClass() }
                        .thenBy { it.field.intermediaryName }
                        .reversed()
                ).mapIndexed { index, entry -> entry.toSimplePair() hold (index + 1.0) }
            }
            fieldKey == "*" -> {
                // Only field wildcard
                members.map { it.toSimplePair() hold it.parentDef(it.parent).similarityOnNull(classKey) }
            }
            hasClassFilter && !isClassKeyWildcard -> {
                // has class
                members.sortedWith(
                    compareByDescending<MemberResultMore<T>> { it.fieldDef(it.field)!!.onlyClass().similarity(fieldKey) }
                        .thenBy { it.parent.optimumName.onlyClass() }
                        .reversed()
                ).mapIndexed { index, entry -> entry.toSimplePair() hold (index + 1.0) }
            }
            else -> {
                members.map {
                        it.toSimplePair() hold it.fieldDef(it.field)!!.onlyClass().similarity(fieldKey)
                    }
            }
        }.sortedWith(compareByDescending<ResultHolder<Pair<Class, T>>> { it.score }
            .thenBy { it.value.first.optimumName.onlyClass() }
            .thenBy { it.value.second.intermediaryName })

        return QueryResult(mappings, sortedMembers)
    }
}

data class ResultHolder<T>(
    val value: T,
    val score: Double,
)

infix fun <T> T.hold(score: Double): ResultHolder<T> = ResultHolder(this, score)

data class QueryContext(
    val provider: MappingsProvider,
    val searchKey: String,
)

fun <T> QueryResult<MappingsContainer, T>.deCompound(): QueryResult<MappingsMetadata, T> =
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
