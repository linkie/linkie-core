package me.shedaniel.linkie.utils

import com.google.common.collect.Streams
import com.google.common.graph.ElementOrder.sorted
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
import me.shedaniel.linkie.optimumName
import me.shedaniel.linkie.optimumSimpleName
import java.util.Objects
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.math.pow
import kotlin.streams.toList

data class MemberEntry<T : MappingsMember>(
    val owner: Class,
    val member: T,
)

typealias ClassResultList = List<ResultHolder<Class>>
typealias FieldResultList = List<ResultHolder<MemberEntry<Field>>>
typealias MethodResultList = List<ResultHolder<MemberEntry<Method>>>

enum class QueryDefinition(val toDefinition: MappingsEntry.() -> String?, val multiplier: Double) {
    MAPPED({ mappedName }, 1.0),
    INTERMEDIARY({ intermediaryName }, 1.0 - Double.MIN_VALUE),
    OBF_MERGED({ obfName.merged }, 1.0 - Double.MIN_VALUE - Double.MIN_VALUE),
    OBF_CLIENT({ obfName.client }, 1.0 - Double.MIN_VALUE - Double.MIN_VALUE),
    OBF_SERVER({ obfName.server }, 1.0 - Double.MIN_VALUE - Double.MIN_VALUE),
    WILDCARD({ throw IllegalStateException("Cannot get definition of type WILDCARD!") }, 1.0);

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

data class MatchAccuracy(val accuracy: Double) {
    companion object {
        val Exact = MatchAccuracy(1.0)
        val Fuzzy = MatchAccuracy(0.5)
    }
}

fun MatchAccuracy.isExact(): Boolean = this == MatchAccuracy.Exact
fun MatchAccuracy.isNotExact(): Boolean = this != MatchAccuracy.Exact

private val classPower = 0.9
private val parentPower = 0.6

object MappingsQuery {
    private data class MemberResultMore<T : MappingsMember>(
        val parent: Class,
        val member: T,
        val parentDef: QueryDefinition,
        val memberDef: QueryDefinition,
    ) {
        fun toSimplePair(): MemberEntry<T> = MemberEntry(parent, member)
    }

    fun errorNoResultsFound(type: MappingsEntryType?, searchKey: String) {
        val onlyClass = searchKey.onlyClass()

        throw when {
            onlyClass.firstOrNull()?.isDigit()?.not() == true && !onlyClass.isValidJavaIdentifier() ->
                NullPointerException("No results found! `$onlyClass` is not a valid java identifier!")
            type != METHOD && (searchKey.startsWith("func_") || searchKey.startsWith("method_")) ->
                NullPointerException("No results found! `$searchKey` looks like a method!")
            type != FIELD && searchKey.startsWith("field_") ->
                NullPointerException("No results found! `$searchKey` looks like a field!")
            type == CLASS && !searchKey.startsWith("class_") && searchKey.firstOrNull()?.isLowerCase() == true ->
                NullPointerException("No results found! `$searchKey` doesn't look like a class!")
            else -> NullPointerException("No results found!")
        }
    }

    fun MappingsEntry.searchDefinition(isClass: Boolean, classKey: String, searchTermOnlyClass: String?, accuracy: MatchAccuracy, onlyClass: Boolean): QueryDefinition? {
        return QueryDefinition.allProper.maxOfIgnoreNullSelf {
            it(this)?.let { str ->
                str.matchWithSimilarity(
                    if (searchTermOnlyClass == null && isClass) str.onlyClass() else null,
                    classKey, searchTermOnlyClass, accuracy, onlyClass
                )
            }?.times(it.multiplier)
        }
    }

    fun MappingsEntry.searchWithSimilarity(isClass: Boolean, classKey: String, searchTermOnlyClass: String?, accuracy: MatchAccuracy, onlyClass: Boolean): Double? {
        return QueryDefinition.allProper.maxOfIgnoreNull {
            it(this)?.let { str ->
                str.matchWithSimilarity(
                    if (searchTermOnlyClass == null && isClass) str.onlyClass() else null,
                    classKey, searchTermOnlyClass, accuracy, onlyClass
                )
            }?.times(it.multiplier)
        }
    }

    private fun <T> Double.holdBy(value: T) = value hold this
    private fun <T> Stream<T>.limit(limit: Long?): Stream<T> {
        if (limit == null) return this
        return this.limit(limit)
    }

    suspend fun queryClasses(context: QueryContext): QueryResult<MappingsContainer, ClassResultList> {
        val searchKey = context.searchKey
        val searchKeyOnlyClass = context.searchKey.onlyClassOrNull()
        val mappings = context.get()
        val isClassKeyPackageSpecific = searchKey.contains('/')

        val results = if (searchKey == "*") {
            Streams.mapWithIndex(mappings.allClasses.parallelStream().sorted(compareBy { it.intermediaryName }).limit(context.limit)) { entry, index ->
                entry hold 1.0 - index * Double.MIN_VALUE
            }
        } else {
            mappings.allClasses.parallelStream()
                .mapNotNull { it.searchWithSimilarity(true, searchKey, searchKeyOnlyClass, context.accuracy, !isClassKeyPackageSpecific)?.pow(classPower)?.holdBy(it) }
                .sorted(compareByDescending<ResultHolder<Class>> { it.score }
                    .thenBy { it.value.optimumSimpleName })
                .limit(context.limit)
        }

        return QueryResult(mappings, results.collect(Collectors.toList()))
    }

    suspend fun queryFields(context: QueryContext): QueryResult<MappingsContainer, FieldResultList> =
        queryMember(context) { it.fields.stream() }

    suspend fun queryMethods(context: QueryContext): QueryResult<MappingsContainer, MethodResultList> =
        queryMember(context) { it.methods.stream() }

    fun <T, R> Stream<T>.mapNotNull(function: (T) -> R?): Stream<R> =
        map(function).filter(Objects::nonNull) as Stream<R>

    fun <T, R> Stream<T>.flatMapNotNull(function: (T) -> Stream<R>?): Stream<R> =
        flatMap { function(it) ?: Stream.empty() }.filter(Objects::nonNull)

    suspend fun <T> queryClassFilter(context: QueryContext, classKey: String, action: (Class, QueryDefinition) -> Stream<T>): Stream<T> {
        val classKeyOnlyClass = classKey.onlyClassOrNull()
        val mappings = context.get()
        val isWildcard = classKey.isBlank() || classKey == "*"
        val isClassKeyPackageSpecific = classKey.contains('/')
        return mappings.allClasses.parallelStream().flatMapNotNull { c ->
            when {
                isWildcard -> QueryDefinition.WILDCARD
                else -> c.searchDefinition(true, classKey, classKeyOnlyClass, context.accuracy, !isClassKeyPackageSpecific)
            }?.let { action(c, it) }
        }
    }

    suspend fun <T : MappingsMember> queryMember(
        context: QueryContext,
        memberGetter: (Class) -> Stream<T>,
    ): QueryResult<MappingsContainer, List<ResultHolder<MemberEntry<T>>>> {
        val searchKey = context.searchKey
        val classKey = if (searchKey.contains('/')) searchKey.substringBeforeLast('/') else ""
        val memberKey = searchKey.onlyClass()
        val memberKeyOnlyClass = memberKey.onlyClassOrNull()
        val isClassKeyPackageSpecific = classKey.contains('/')
        val isClassWildcard = classKey.isBlank() || classKey == "*"
        val isMemberWildcard = memberKey == "*"
        val mappings = context.get()

        val observedMember = HashSet<T>()
        val observedParent = HashSet<Class>()

        val members: Stream<MemberResultMore<T>> = queryClassFilter(context, classKey) { c, parentDef ->
            if (isMemberWildcard) {
                memberGetter(c).map { MemberResultMore(c, it, parentDef, QueryDefinition.WILDCARD) }
            } else {
                memberGetter(c).mapNotNull { f ->
                    f.searchDefinition(false, memberKey, memberKeyOnlyClass, context.accuracy, !isClassKeyPackageSpecific)
                        ?.let { MemberResultMore(c, f, parentDef, it) }
                }
            }
        }.filter {
            val add1 = observedMember.add(it.member)
            val add2 = observedParent.add(it.parent)
            add1 || add2
        }

        var sortedMembers: Stream<ResultHolder<MemberEntry<T>>> = when {
            // Class and member both wildcard
            isClassWildcard && isMemberWildcard -> Streams.mapWithIndex(
                members.sorted(
                    compareBy<MemberResultMore<T>> { it.parent.optimumName.onlyClass() }
                        .thenBy { it.member.intermediaryName }
                        .reversed()
                ).limit(context.limit)
            ) { entry, index ->
                entry.toSimplePair() hold 1.0 - index * Double.MIN_VALUE
            }
            // Only member wildcard
            isMemberWildcard -> members.map {
                it.toSimplePair() hold it.parentDef(it.parent)!!.similarity(classKey, !isClassKeyPackageSpecific).pow(parentPower)
            }
            // Has class filter
            classKey.isNotBlank() && !isClassWildcard -> members.map {
                it.toSimplePair() hold it.memberDef(it.member)!!.similarity(memberKey) *
                        it.parentDef(it.parent)!!.similarity(classKey, !isClassKeyPackageSpecific).pow(parentPower)
            }
            // Simple search
            else -> members.map { it.toSimplePair() hold it.memberDef(it.member)!!.similarity(memberKey) }
        }

        if (!(isClassWildcard && isMemberWildcard)) {
            sortedMembers = sortedMembers.sorted(compareByDescending<ResultHolder<MemberEntry<T>>> { it.score }
                .thenBy { it.value.owner.optimumName.onlyClass() }
                .thenBy { it.value.member.intermediaryName })
                .limit(context.limit)
        }

        return QueryResult(mappings, sortedMembers.collect(Collectors.toList()))
    }
}

data class ResultHolder<T>(
    val value: T,
    val score: Double,
)

infix fun <T> T.hold(score: Double): ResultHolder<T> = ResultHolder(this, score)

data class QueryContext(
    val provider: suspend () -> MappingsContainer,
    val searchKey: String,
    val accuracy: MatchAccuracy = MatchAccuracy.Exact,
    val limit: Long? = null,
) {
    constructor(
        provider: MappingsProvider,
        searchKey: String,
        accuracy: MatchAccuracy = MatchAccuracy.Exact,
        limit: Long? = null,
    ) : this({ provider.get() }, searchKey, accuracy, limit)
}

suspend fun QueryContext.get(): MappingsContainer = provider()

fun <T> QueryResult<MappingsContainer, T>.toSimpleMappingsMetadata(): QueryResult<MappingsMetadata, T> =
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
