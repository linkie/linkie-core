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
import me.shedaniel.linkie.optimumName
import me.shedaniel.linkie.optimumSimpleName
import kotlin.math.pow

typealias ClassResultList = List<ResultHolder<Class>>
typealias FieldResultList = List<ResultHolder<Pair<Class, Field>>>
typealias MethodResultList = List<ResultHolder<Pair<Class, Method>>>

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
        fun toSimplePair(): Pair<Class, T> = parent to member
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

    fun MappingsEntry.searchDefinition(classKey: String, accuracy: MatchAccuracy, onlyClass: Boolean): QueryDefinition? {
        return QueryDefinition.allProper.maxOfIgnoreNullSelf { it(this).matchWithSimilarity(classKey, accuracy, onlyClass)?.times(it.multiplier) }
    }

    fun MappingsEntry.searchWithSimilarity(classKey: String, accuracy: MatchAccuracy, onlyClass: Boolean): Double? {
        return QueryDefinition.allProper.maxOfIgnoreNull { it(this).matchWithSimilarity(classKey, accuracy, onlyClass)?.times(it.multiplier) }
    }

    private fun <T> Double.holdBy(value: T) = value hold this

    suspend fun queryClasses(context: QueryContext): QueryResult<MappingsContainer, ClassResultList> {
        val searchKey = context.searchKey
        val mappings = context.get()
        val isClassKeyPackageSpecific = searchKey.contains('/')

        val results = if (searchKey == "*") {
            mappings.allClasses.asSequence().sortedBy { it.intermediaryName }
                .mapIndexed { index, entry -> entry hold 1.0 - index * Double.MIN_VALUE }
        } else {
            mappings.allClasses.asSequence()
                .mapNotNull { it.searchWithSimilarity(searchKey, context.accuracy, !isClassKeyPackageSpecific)?.pow(classPower)?.holdBy(it) }
        }.toList().sortedWith(compareByDescending<ResultHolder<Class>> { it.score }
            .thenBy { it.value.optimumSimpleName })

        return QueryResult(mappings, results)
    }

    suspend fun queryFields(context: QueryContext): QueryResult<MappingsContainer, FieldResultList> =
        queryMember(context) { it.fields.asSequence() }

    suspend fun queryMethods(context: QueryContext): QueryResult<MappingsContainer, MethodResultList> =
        queryMember(context) { it.methods.asSequence() }

    suspend fun <T> queryClassFilter(context: QueryContext, classKey: String, action: (Class, QueryDefinition) -> T): Sequence<T> {
        val mappings = context.get()
        val isWildcard = classKey.isBlank() || classKey == "*"
        val isClassKeyPackageSpecific = classKey.contains('/')
        return mappings.allClasses.asSequence().mapNotNull { c ->
            when {
                isWildcard -> QueryDefinition.WILDCARD
                else -> c.searchDefinition(classKey, context.accuracy, !isClassKeyPackageSpecific)
            }?.let { action(c, it) }
        }
    }

    suspend fun <T : MappingsMember> queryMember(
        context: QueryContext,
        memberGetter: (Class) -> Sequence<T>,
    ): QueryResult<MappingsContainer, List<ResultHolder<Pair<Class, T>>>> {
        val searchKey = context.searchKey
        val classKey = if (searchKey.contains('/')) searchKey.substringBeforeLast('/') else ""
        val memberKey = searchKey.onlyClass()
        val isClassKeyPackageSpecific = classKey.contains('/')
        val isClassWildcard = classKey.isBlank() || classKey == "*"
        val isMemberWildcard = memberKey == "*"
        val mappings = context.get()

        val members: List<MemberResultMore<T>> = queryClassFilter(context, classKey) { c, parentDef ->
            if (isMemberWildcard) {
                memberGetter(c).map { MemberResultMore(c, it, parentDef, QueryDefinition.WILDCARD) }
            } else {
                memberGetter(c).mapNotNull { f ->
                    f.searchDefinition(memberKey, context.accuracy, !isClassKeyPackageSpecific)
                        ?.let { MemberResultMore(c, f, parentDef, it) }
                }
            }
        }.flatMap { it }.distinctTwoBy({ it.member }, { it.parent }).toList()

        val sortedMembers: List<ResultHolder<Pair<Class, T>>> = when {
            // Class and member both wildcard
            isClassWildcard && isMemberWildcard -> members.sortedWith(
                compareBy<MemberResultMore<T>> { it.parent.optimumName.onlyClass() }
                    .thenBy { it.member.intermediaryName }
                    .reversed()
            ).mapIndexed { index, entry -> entry.toSimplePair() hold 1.0 - index * Double.MIN_VALUE }
            // Only member wildcard
            isMemberWildcard -> members.map {
                it.toSimplePair() hold it.parentDef(it.parent)!!.similarity(classKey, !isClassKeyPackageSpecific).pow(parentPower)
            }
            // Has class filter
            classKey.isNotBlank() && !isClassWildcard -> members
                .map {
                    it.toSimplePair() hold it.memberDef(it.member)!!.similarity(memberKey) *
                            it.parentDef(it.parent)!!.similarity(classKey, !isClassKeyPackageSpecific).pow(parentPower)
                }
            // Simple search
            else -> members.map { it.toSimplePair() hold it.memberDef(it.member)!!.similarity(memberKey) }
        }.sortedWith(compareByDescending<ResultHolder<Pair<Class, T>>> { it.score }
            .thenBy { it.value.first.optimumName.onlyClass() }
            .thenBy { it.value.second.intermediaryName })

        return QueryResult(mappings, sortedMembers)
    }
}

private fun <T, K, V> Sequence<T>.distinctTwoBy(selector: (T) -> K, secondKeySelector: (T) -> V): Sequence<T> {
    return DistinctTwoSequence(this, selector, secondKeySelector)
}

private class DistinctTwoSequence<T, K, V>(
    private val source: Sequence<T>,
    private val keySelector: (T) -> K,
    private val secondKeySelector: (T) -> V,
) : Sequence<T> {
    override fun iterator(): Iterator<T> = DistinctTwoIterator(source.iterator(), keySelector, secondKeySelector)
}

private class DistinctTwoIterator<T, K, V>(
    private val source: Iterator<T>,
    private val keySelector: (T) -> K,
    private val secondKeySelector: (T) -> V,
) : AbstractIterator<T>() {
    private val observed = HashSet<K>()
    private val observedSecond = HashSet<V>()

    override fun computeNext() {
        while (source.hasNext()) {
            val next = source.next()
            val key = keySelector(next)
            val secondKey = secondKeySelector(next)
            val b1 = observed.add(key)
            val b2 = observedSecond.add(secondKey)

            if (b1 || b2) {
                setNext(next)
                return
            }
        }

        done()
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
) {
    constructor(
        provider: MappingsProvider,
        searchKey: String,
        accuracy: MatchAccuracy = MatchAccuracy.Exact,
    ) : this({ provider.get() }, searchKey, accuracy)
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
