package me.shedaniel.linkie.utils.query

import me.shedaniel.linkie.Class
import me.shedaniel.linkie.Field
import me.shedaniel.linkie.Mappings
import me.shedaniel.linkie.MappingsEntry
import me.shedaniel.linkie.MappingsEntryType
import me.shedaniel.linkie.MappingsEntryType.CLASS
import me.shedaniel.linkie.MappingsEntryType.FIELD
import me.shedaniel.linkie.MappingsEntryType.METHOD
import me.shedaniel.linkie.MappingsMember
import me.shedaniel.linkie.Method
import me.shedaniel.linkie.optimumName
import me.shedaniel.linkie.optimumSimpleName
import me.shedaniel.linkie.utils.isValidJavaIdentifier
import me.shedaniel.linkie.utils.maxOfIgnoreNull
import me.shedaniel.linkie.utils.maxOfIgnoreNullSelf
import me.shedaniel.linkie.utils.onlyClass
import me.shedaniel.linkie.utils.similarity
import kotlin.math.pow

typealias ClassResultList = List<ResultHolder<Class>>
typealias FieldResultList = List<ResultHolder<Pair<Class, Field>>>
typealias MethodResultList = List<ResultHolder<Pair<Class, Method>>>

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

    suspend fun queryClasses(context: QueryContext): QueryResult<Mappings, ClassResultList> {
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

    suspend fun queryFields(context: QueryContext): QueryResult<Mappings, FieldResultList> =
        queryMember(context) { it.fields.asSequence() }

    suspend fun queryMethods(context: QueryContext): QueryResult<Mappings, MethodResultList> =
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
    ): QueryResult<Mappings, List<ResultHolder<Pair<Class, T>>>> {
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
        }.flatMap { it }.distinctBy { it.member }.toList()

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
