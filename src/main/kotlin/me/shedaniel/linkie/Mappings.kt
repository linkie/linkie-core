package me.shedaniel.linkie

import kotlinx.serialization.Serializable
import me.shedaniel.linkie.MappingsContainer.MappingSource
import me.shedaniel.linkie.utils.StringPool
import me.shedaniel.linkie.utils.info
import me.shedaniel.linkie.utils.remapDescriptor
import me.shedaniel.linkie.utils.singleSequenceOf

interface MappingsMetadata {
    val version: String
    val name: String
    var mappingSource: MappingSource?
    var namespace: String
}

data class SimpleMappingsMetadata(
    override val version: String,
    override val name: String,
    override var mappingSource: MappingSource? = null,
    override var namespace: String = "",
) : MappingsMetadata

@Serializable
data class MappingsContainer(
    override val version: String,
    val classes: MutableMap<String, Class> = mutableMapOf(),
    override val name: String,
    override var mappingSource: MappingSource? = null,
    override var namespace: String = "",
) : MappingsMetadata, me.shedaniel.linkie.namespaces.MappingsContainerBuilder {
    override suspend fun build(version: String): MappingsContainer = this

    fun toSimpleMappingsMetadata(): MappingsMetadata = SimpleMappingsMetadata(
        version = version,
        name = name,
        mappingSource = mappingSource,
        namespace = namespace
    )

    fun getClass(intermediaryName: String): Class? = classes[intermediaryName]

    fun getOrCreateClass(intermediaryName: String): Class =
        classes.getOrPut(intermediaryName) { Class(intermediaryName) }

    fun prettyPrint() {
        buildString {
            classes.forEach { _, clazz ->
                clazz.apply {
                    append("$intermediaryName: $mappedName\n")
                    methods.forEach {
                        it.apply {
                            append("  $intermediaryName $intermediaryDesc: $mappedName ${getMappedDesc(this@MappingsContainer)}\n")
                        }
                    }
                    fields.forEach {
                        it.apply {
                            append("  $intermediaryName $intermediaryDesc: $mappedName ${getMappedDesc(this@MappingsContainer)}\n")
                        }
                    }
                    append("\n")
                }
            }
        }.also { info(it) }
    }

    @Serializable
    @Suppress("unused")
    enum class MappingSource {
        MCP_SRG,
        MCP_TSRG,
        MOJANG,
        YARN_V1,
        YARN_V2,
        SPIGOT,
        ENGIMA;

        override fun toString(): String = name.toLowerCase().split("_").joinToString(" ") { it.capitalize() }
    }
}

fun MappingsContainer.getClassByObfName(obf: String, ignoreCase: Boolean = false): Class? {
    return classes.values.firstOrNull {
        if (it.obfName.isMerged()) {
            it.obfMergedName.equals(obf, ignoreCase = ignoreCase)
        } else {
            it.obfClientName.equals(obf, ignoreCase = ignoreCase)
                    || it.obfServerName.equals(obf, ignoreCase = ignoreCase)
        }
    }
}

fun Class.getMethodByObfName(obf: String, ignoreCase: Boolean = false): Method? {
    return methods.firstOrNull {
        if (it.obfName.isMerged()) {
            it.obfMergedName.equals(obf, ignoreCase = ignoreCase)
        } else {
            it.obfClientName.equals(obf, ignoreCase = ignoreCase)
                    || it.obfServerName.equals(obf, ignoreCase = ignoreCase)
        }
    }
}

fun Class.getMethodByObf(container: MappingsContainer, obf: String, desc: String, ignoreCase: Boolean = false): Method? {
    return methods.firstOrNull {
        if (it.obfName.isMerged()) {
            it.obfMergedName.equals(obf, ignoreCase = ignoreCase)
                    && it.getObfMergedDesc(container).equals(desc, ignoreCase = ignoreCase)
        } else {
            (it.obfClientName.equals(obf, ignoreCase = ignoreCase)
                    && it.getObfClientDesc(container).equals(desc, ignoreCase = ignoreCase))
                    || (it.obfServerName.equals(obf, ignoreCase = ignoreCase)
                    && it.getObfServerDesc(container).equals(desc, ignoreCase = ignoreCase))
        }
    }
}

fun Class.getFieldByObfName(obf: String): Field? {
    fields.forEach {
        if (it.obfName.isMerged()) {
            if (it.obfName.merged.equals(obf, ignoreCase = false))
                return it
        } else if (it.obfName.client.equals(obf, ignoreCase = false))
            return it
        else if (it.obfName.server.equals(obf, ignoreCase = false))
            return it
    }
    return null
}

suspend inline fun buildMappings(
    version: String,
    name: String,
    fillFieldDesc: Boolean = true,
    fillMethodDesc: Boolean = true,
    crossinline builder: suspend MappingsBuilder.() -> Unit,
): MappingsContainer =
    MappingsBuilder(
        fillFieldDesc,
        fillMethodDesc,
        container = MappingsContainer(version, name = name)
    ).also {
        builder(it)
    }.build()

class MappingsBuilder(
    val fillFieldDesc: Boolean,
    val fillMethodDesc: Boolean,
    var container: MappingsContainer,
) {
    val pool = StringPool()

    fun build(): MappingsContainer = container.also {
        fun clean(entry: MappingsEntry) {
            entry.intermediaryName = pool[entry.intermediaryName]
            entry.mappedName = pool[entry.mappedName]
            if (entry.obfName.isMerged()) {
                entry.obfMergedName = pool[entry.obfMergedName]
            } else {
                entry.obfClientName = pool[entry.obfClientName]
                entry.obfServerName = pool[entry.obfServerName]
            }
            if (entry is MappingsMember) {
                entry.intermediaryDesc = pool[entry.intermediaryDesc]
            }
        }

        container.classes.forEach { (_, clazz) ->
            val members = clazz.methods.asSequence() + clazz.fields.asSequence()
            members.forEach(::clean)
            clean(clazz)
        }
    }

    fun source(mappingSource: MappingSource?) {
        container.mappingSource = mappingSource
    }

    suspend fun edit(operator: suspend MappingsContainer.() -> Unit) {
        operator(container)
    }

    fun replace(operator: MappingsContainer.() -> MappingsContainer) {
        container = operator(container)
    }

    fun clazz(
        intermediaryName: String,
        obfName: String? = null,
        mappedName: String? = null,
    ): ClassBuilder =
        ClassBuilder(container.getOrCreateClass(pool[intermediaryName])).apply {
            obfClass(obfName)
            mapClass(mappedName)
        }

    inline fun clazz(
        intermediaryName: String,
        obfName: String? = null,
        mappedName: String? = null,
        crossinline builder: ClassBuilder.() -> Unit,
    ): ClassBuilder =
        ClassBuilder(container.getOrCreateClass(pool[intermediaryName])).also(builder).apply {
            obfClass(obfName)
            mapClass(mappedName)
        }
}

fun MappingsContainer.rewireIntermediaryFrom(obf2intermediary: MappingsContainer, removeUnfound: Boolean = false) {
    val classO2I = mutableMapOf<String, Class>()
    obf2intermediary.classes.forEach { (_, clazz) -> clazz.obfMergedName?.also { classO2I[it] = clazz } }
    classes.values.removeIf { clazz ->
        val replacement = classO2I[clazz.obfName.merged]
        if (replacement != null) {
            clazz.mappedName = clazz.intermediaryName
            clazz.intermediaryName = replacement.intermediaryName

            clazz.methods.removeIf { method ->
                val replacementMethod = replacement.getMethodByObf(obf2intermediary, method.obfMergedName!!, method.getObfMergedDesc(this))
                if (replacementMethod != null) {
                    method.mappedName = method.intermediaryName
                    method.intermediaryName = replacementMethod.intermediaryName
                    method.intermediaryDesc = replacementMethod.intermediaryDesc
                }
                replacementMethod == null && removeUnfound
            }
            clazz.fields.removeIf { field ->
                val replacementField = replacement.getFieldByObfName(field.obfMergedName!!)
                if (replacementField != null) {
                    field.mappedName = field.intermediaryName
                    field.intermediaryName = replacementField.intermediaryName
                    field.intermediaryDesc = replacementField.intermediaryDesc
                }
                replacementField == null && removeUnfound
            }
        }
        replacement == null && removeUnfound
    }

    val list = classes.values.toMutableList()
    classes.clear()
    list.forEach { clazz ->
        classes[clazz.intermediaryName] = clazz
    }
}

inline class ClassBuilder(val clazz: Class) {
    fun obfClass(obf: String?) {
        clazz.obfName.merged = obf
    }

    fun mapClass(mapped: String?) {
        clazz.mappedName = mapped
    }

    fun field(
        intermediaryName: String,
        intermediaryDesc: String? = null,
    ): FieldBuilder =
        FieldBuilder(clazz.getOrCreateField(intermediaryName, intermediaryDesc ?: "")).apply {
            intermediaryDesc(intermediaryDesc)
        }

    inline fun field(
        intermediaryName: String,
        intermediaryDesc: String? = null,
        crossinline builder: FieldBuilder.() -> Unit,
    ): FieldBuilder =
        FieldBuilder(clazz.getOrCreateField(intermediaryName, intermediaryDesc ?: "")).also(builder).apply {
            intermediaryDesc(intermediaryDesc)
        }

    fun method(
        intermediaryName: String,
        intermediaryDesc: String? = null,
    ): MethodBuilder =
        MethodBuilder(clazz.getOrCreateMethod(intermediaryName, intermediaryDesc ?: "")).apply {
            intermediaryDesc(intermediaryDesc)
        }

    inline fun method(
        intermediaryName: String,
        intermediaryDesc: String? = null,
        crossinline builder: MethodBuilder.() -> Unit,
    ): MethodBuilder =
        MethodBuilder(clazz.getOrCreateMethod(intermediaryName, intermediaryDesc ?: "")).also(builder).apply {
            intermediaryDesc(intermediaryDesc)
        }
}

inline class FieldBuilder(val field: Field) {
    fun intermediaryDesc(intermediaryDesc: String?) = intermediaryDesc?.also {
        field.intermediaryDesc = it
    }

    fun obfField(obfName: String?) {
        field.obfName.merged = obfName
    }

    fun mapField(mappedName: String?) {
        field.mappedName = mappedName
    }
}

inline class MethodBuilder(val method: Method) {
    fun intermediaryDesc(intermediaryDesc: String?) = intermediaryDesc?.also {
        method.intermediaryDesc = it
    }

    fun obfMethod(obfName: String?) {
        method.obfName.merged = obfName
    }

    fun mapMethod(mappedName: String?) {
        method.mappedName = mappedName
    }
}

enum class MappingsEntryType {
    CLASS,
    FIELD,
    METHOD
}

interface MappingsEntry {
    var intermediaryName: String
    val obfName: Obf
    var mappedName: String?
}

interface MappingsMember : MappingsEntry {
    var intermediaryDesc: String
}

val MappingsEntry.optimumName: String
    get() = mappedName ?: intermediaryName

var MappingsEntry.obfClientName: String?
    get() = obfName.client
    set(value) {
        obfName.client = value
    }

var MappingsEntry.obfServerName: String?
    get() = obfName.server
    set(value) {
        obfName.server = value
    }

var MappingsEntry.obfMergedName: String?
    get() = obfName.merged
    set(value) {
        obfName.merged = value
    }

@Serializable
data class Class(
    override var intermediaryName: String,
    override val obfName: Obf = Obf(),
    override var mappedName: String? = null,
    val methods: MutableList<Method> = mutableListOf(),
    val fields: MutableList<Field> = mutableListOf(),
) : MappingsEntry {
    val members: Sequence<MappingsMember>
        get() = methods.asSequence() + fields.asSequence()

    fun getMethod(intermediaryName: String, intermediaryDesc: String): Method? =
        methods.firstOrNull {
            it.intermediaryName == intermediaryName && it.intermediaryDesc == intermediaryDesc
        }

    fun getOrCreateMethod(intermediaryName: String, intermediaryDesc: String): Method =
        getMethod(intermediaryName, intermediaryDesc) ?: Method(intermediaryName, intermediaryDesc).also { methods.add(it) }

    fun getField(intermediaryName: String): Field? =
        fields.firstOrNull { it.intermediaryName == intermediaryName }

    fun getOrCreateField(intermediaryName: String, intermediaryDesc: String): Field =
        getField(intermediaryName) ?: Field(intermediaryName, intermediaryDesc).also { fields.add(it) }
}

@Serializable
data class Method(
    override var intermediaryName: String,
    override var intermediaryDesc: String,
    override val obfName: Obf = Obf(),
    override var mappedName: String? = null,
) : MappingsMember

@Serializable
data class Field(
    override var intermediaryName: String,
    override var intermediaryDesc: String,
    override val obfName: Obf = Obf(),
    override var mappedName: String? = null,
) : MappingsMember

fun MappingsMember.getMappedDesc(container: MappingsContainer): String =
    intermediaryDesc.remapDescriptor { container.getClass(it)?.mappedName ?: it }

fun MappingsMember.getObfMergedDesc(container: MappingsContainer): String =
    intermediaryDesc.remapDescriptor { container.getClass(it)?.obfMergedName ?: it }

fun MappingsMember.getObfClientDesc(container: MappingsContainer): String =
    intermediaryDesc.remapDescriptor { container.getClass(it)?.obfClientName ?: it }

fun MappingsMember.getObfServerDesc(container: MappingsContainer): String =
    intermediaryDesc.remapDescriptor { container.getClass(it)?.obfServerName ?: it }

@Serializable
data class Obf(
    var client: String? = null,
    var server: String? = null,
    var merged: String? = null,
) {
    companion object {
        private val empty = Obf()

        fun empty(): Obf = empty
    }

    fun sequence(): Sequence<String> = when {
        isMerged() -> singleSequenceOf(merged!!)
        client == null -> server?.let(::singleSequenceOf) ?: emptySequence()
        server == null -> client?.let(::singleSequenceOf) ?: emptySequence()
        else -> {
            sequenceOf(server!!, client!!)
        }
    }

    fun list(): List<String> = sequence().toList()

    fun isMerged(): Boolean = merged != null
    fun isEmpty(): Boolean = client == null && server == null && merged == null
}
