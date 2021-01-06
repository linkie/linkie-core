package me.shedaniel.linkie

import kotlinx.serialization.Serializable
import me.shedaniel.linkie.MappingsContainer.MappingSource
import me.shedaniel.linkie.utils.info
import me.shedaniel.linkie.utils.mapIntermediaryDescToNamed
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
    val classes: MutableList<Class> = mutableListOf(),
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

    fun getClass(intermediaryName: String): Class? =
        classes.firstOrNull { it.intermediaryName == intermediaryName }

    fun getOrCreateClass(intermediaryName: String): Class =
        getClass(intermediaryName) ?: Class(intermediaryName).also { classes.add(it) }

    fun prettyPrint() {
        buildString {
            classes.forEach {
                it.apply {
                    append("$intermediaryName: $mappedName\n")
                    methods.forEach {
                        it.apply {
                            append("  $intermediaryName $intermediaryDesc: $mappedName $mappedDesc\n")
                        }
                    }
                    fields.forEach {
                        it.apply {
                            append("  $intermediaryName $intermediaryDesc: $mappedName $mappedDesc\n")
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
    return classes.firstOrNull {
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

fun Class.getMethodByObf(obf: String, desc: String, ignoreCase: Boolean = false): Method? {
    return methods.firstOrNull {
        if (it.obfName.isMerged()) {
            it.obfMergedName.equals(obf, ignoreCase = ignoreCase)
                    && it.obfMergedDesc.equals(desc, ignoreCase = ignoreCase)
        } else {
            (it.obfClientName.equals(obf, ignoreCase = ignoreCase)
                    && it.obfClientDesc.equals(desc, ignoreCase = ignoreCase))
                    || (it.obfServerName.equals(obf, ignoreCase = ignoreCase)
                    && it.obfServerDesc.equals(desc, ignoreCase = ignoreCase))
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
    expendIntermediaryToMapped: Boolean = false,
    crossinline builder: suspend MappingsBuilder.() -> Unit,
): MappingsContainer =
    MappingsBuilder(
        fillFieldDesc,
        fillMethodDesc,
        expendIntermediaryToMapped,
        container = MappingsContainer(version, name = name)
    ).also {
        builder(it)
    }.build()

class MappingsBuilder(
    val fillFieldDesc: Boolean,
    val fillMethodDesc: Boolean,
    val expendIntermediaryToMapped: Boolean,
    var lockFill: Boolean = false,
    var container: MappingsContainer,
) {
    fun build(): MappingsContainer = container.also { fill() }

    fun fill() {
        if (lockFill) return
        container.fillMappedDescViaIntermediary(fillFieldDesc, fillMethodDesc)
        container.fillObfDescViaIntermediary(fillFieldDesc, fillMethodDesc)

        if (expendIntermediaryToMapped) {
            container.classes.forEach { clazz ->
                clazz.mappedName = clazz.mappedName ?: clazz.intermediaryName
                clazz.fields.forEach { field ->
                    field.mappedName = field.mappedName ?: field.intermediaryName
                    field.mappedDesc = field.mappedDesc ?: field.intermediaryDesc
                }
                clazz.methods.forEach { method ->
                    method.mappedName = method.mappedName ?: method.intermediaryName
                    method.mappedDesc = method.mappedDesc ?: method.intermediaryDesc
                }
            }
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

    fun lockFill(lockFill: Boolean = true) {
        this.lockFill = lockFill
    }

    fun clazz(
        intermediaryName: String,
        obf: String? = null,
        mapped: String? = null,
    ): ClassBuilder =
        ClassBuilder(container.getOrCreateClass(intermediaryName)).apply {
            obfClass(obf)
            mapClass(mapped)
        }

    inline fun clazz(
        intermediaryName: String,
        obf: String? = null,
        mapped: String? = null,
        crossinline builder: ClassBuilder.() -> Unit,
    ): ClassBuilder =
        ClassBuilder(container.getOrCreateClass(intermediaryName)).also(builder).apply {
            obfClass(obf)
            mapClass(mapped)
        }
}

fun MappingsContainer.rewireIntermediaryFrom(obf2intermediary: MappingsContainer, removeUnfound: Boolean = false) {
    val classO2I = mutableMapOf<String, Class>()
    obf2intermediary.classes.forEach { clazz -> clazz.obfName.merged?.also { classO2I[it] = clazz } }
    classes.removeIf { clazz ->
        val replacement = classO2I[clazz.obfName.merged]
        if (replacement != null) {
            clazz.intermediaryName = replacement.intermediaryName

            clazz.methods.removeIf { method ->
                val replacementMethod = replacement.getMethodByObf(method.obfName.merged!!, method.obfDesc.merged!!)
                if (replacementMethod != null) {
                    method.intermediaryName = replacementMethod.intermediaryName
                    method.intermediaryDesc = replacementMethod.intermediaryDesc
                }
                replacementMethod == null && removeUnfound
            }
            clazz.fields.removeIf { field ->
                val replacementField = replacement.getFieldByObfName(field.obfName.merged!!)
                if (replacementField != null) {
                    field.intermediaryName = replacementField.intermediaryName
                    field.intermediaryDesc = replacementField.intermediaryDesc
                }
                replacementField == null && removeUnfound
            }
        }
        replacement == null && removeUnfound
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
        FieldBuilder(clazz.getOrCreateField(intermediaryName, "")).apply {
            intermediaryDesc(intermediaryDesc)
        }

    inline fun field(
        intermediaryName: String,
        intermediaryDesc: String? = null,
        crossinline builder: FieldBuilder.() -> Unit,
    ): FieldBuilder =
        FieldBuilder(clazz.getOrCreateField(intermediaryName, "")).also(builder).apply {
            intermediaryDesc(intermediaryDesc)
        }

    fun method(
        intermediaryName: String,
        intermediaryDesc: String? = null,
    ): MethodBuilder =
        MethodBuilder(clazz.getOrCreateMethod(intermediaryName, "")).apply {
            intermediaryDesc(intermediaryDesc)
        }

    inline fun method(
        intermediaryName: String,
        intermediaryDesc: String? = null,
        crossinline builder: MethodBuilder.() -> Unit,
    ): MethodBuilder =
        MethodBuilder(clazz.getOrCreateMethod(intermediaryName, "")).also(builder).apply {
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

fun MappingsContainer.fillObfDescViaIntermediary(fillFieldDesc: Boolean, fillMethodDesc: Boolean) {
    classes.forEach { clazz ->
        val fillDesc: (MappingsMember) -> Unit = {
            it.obfMergedDesc = it.obfMergedDesc ?: it.intermediaryDesc.remapDescriptor { descClass ->
                getClass(descClass)?.obfName?.merged ?: descClass
            }
        }
        if (fillFieldDesc) {
            clazz.fields.forEach(fillDesc)
        }
        if (fillMethodDesc) {
            clazz.methods.forEach(fillDesc)
        }
    }
}

fun MappingsContainer.fillMappedDescViaIntermediary(fillFieldDesc: Boolean, fillMethodDesc: Boolean) {
    classes.forEach { clazz ->
        val fillDesc: (MappingsMember) -> Unit = {
            it.obfMergedDesc = it.obfMergedDesc ?: it.intermediaryDesc.mapIntermediaryDescToNamed(this)
        }
        if (fillFieldDesc) {
            clazz.fields.forEach(fillDesc)
        }
        if (fillMethodDesc) {
            clazz.methods.forEach(fillDesc)
        }
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
    val obfDesc: Obf
    var mappedDesc: String?
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

var MappingsMember.obfClientDesc: String?
    get() = obfDesc.client
    set(value) {
        obfDesc.client = value
    }

var MappingsMember.obfServerDesc: String?
    get() = obfDesc.server
    set(value) {
        obfDesc.server = value
    }

var MappingsMember.obfMergedDesc: String?
    get() = obfDesc.merged
    set(value) {
        obfDesc.merged = value
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

    fun getMethod(intermediaryName: String): Method? =
        methods.firstOrNull { it.intermediaryName == intermediaryName }

    fun getOrCreateMethod(intermediaryName: String, intermediaryDesc: String): Method =
        getMethod(intermediaryName) ?: Method(intermediaryName, intermediaryDesc).also { methods.add(it) }

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
    override val obfDesc: Obf = Obf(),
    override var mappedName: String? = null,
    override var mappedDesc: String? = null,
) : MappingsMember

@Serializable
data class Field(
    override var intermediaryName: String,
    override var intermediaryDesc: String,
    override val obfName: Obf = Obf(),
    override val obfDesc: Obf = Obf(),
    override var mappedName: String? = null,
    override var mappedDesc: String? = null,
) : MappingsMember

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
