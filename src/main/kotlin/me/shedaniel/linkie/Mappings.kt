package me.shedaniel.linkie

import kotlinx.serialization.Serializable
import me.shedaniel.linkie.MappingsContainer.MappingSource
import me.shedaniel.linkie.utils.*

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
) : MappingsMetadata {
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

fun MappingsContainer.getClassByObfName(obf: String): Class? {
    classes.forEach {
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

fun Class.getMethodByObfName(obf: String): Method? {
    methods.forEach {
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

fun Class.getMethodByObfNameAndDesc(obf: String, desc: String): Method? {
    methods.filter {
        if (it.obfName.isMerged()) {
            if (it.obfName.merged.equals(obf, ignoreCase = false))
                return@filter true
        } else if (it.obfName.client.equals(obf, ignoreCase = false))
            return@filter true
        else if (it.obfName.server.equals(obf, ignoreCase = false))
            return@filter true
        return@filter false
    }.forEach {
        if (it.obfDesc.isMerged()) {
            if (it.obfDesc.merged.equals(desc, ignoreCase = false))
                return it
        } else if (it.obfDesc.client.equals(desc, ignoreCase = false))
            return it
        else if (it.obfDesc.server.equals(desc, ignoreCase = false))
            return it
    }
    return null
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

inline fun buildMappings(
    version: String,
    name: String,
    fillFieldDesc: Boolean = true,
    fillMethodDesc: Boolean = true,
    expendIntermediaryToMapped: Boolean = false,
    crossinline builder: MappingsContainerBuilder.() -> Unit,
): MappingsContainer =
    MappingsContainerBuilder(version, name, fillFieldDesc, fillMethodDesc, expendIntermediaryToMapped).also(builder).build()

class MappingsContainerBuilder(
    val version: String,
    val name: String,
    val fillFieldDesc: Boolean,
    val fillMethodDesc: Boolean,
    val expendIntermediaryToMapped: Boolean,
    var lockFill: Boolean = false,
    var container: MappingsContainer = MappingsContainer(version, name = name),
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

    fun edit(operator: MappingsContainer.() -> Unit) {
        operator(container)
    }

    fun replace(operator: MappingsContainer.() -> MappingsContainer) {
        container = operator(container)
    }

    fun lockFill(lockFill: Boolean = true) {
        this.lockFill = lockFill
    }

    fun clazz(intermediaryName: String, obf: String? = null, mapped: String? = null): ClassBuilder =
        ClassBuilder(container.getOrCreateClass(intermediaryName)).apply {
            obfClass(obf)
            mapClass(mapped)
        }

    inline fun clazz(intermediaryName: String, obf: String? = null, mapped: String? = null, crossinline builder: ClassBuilder.() -> Unit): ClassBuilder =
        ClassBuilder(container.getOrCreateClass(intermediaryName)).also(builder).apply {
            obfClass(obf)
            mapClass(mapped)
        }
}

fun MappingsContainer.rewireIntermediaryFrom(obf2intermediary: MappingsContainer) {
    val classO2I = mutableMapOf<String, Class>()
    obf2intermediary.classes.forEach { clazz -> clazz.obfName.merged?.also { classO2I[it] = clazz } }
    classes.forEach { clazz ->
        classO2I[clazz.obfName.merged]?.also { replacement ->
            clazz.intermediaryName = replacement.intermediaryName

            clazz.methods.forEach { method ->
                replacement.getMethodByObfNameAndDesc(method.obfName.merged!!, method.obfDesc.merged!!)?.also { replacementMethod ->
                    method.intermediaryName = replacementMethod.intermediaryName
                    method.intermediaryDesc = replacementMethod.intermediaryDesc
                }
            }
            clazz.fields.forEach { field ->
                replacement.getFieldByObfName(field.obfName.merged!!)?.also { replacementField ->
                    field.intermediaryName = replacementField.intermediaryName
                    field.intermediaryDesc = replacementField.intermediaryDesc
                }
            }
        }
    }
}

inline class ClassBuilder(val clazz: Class) {
    fun obfClass(obf: String?) {
        clazz.obfName.merged = obf
    }

    fun mapClass(mapped: String?) {
        clazz.mappedName = mapped
    }

    fun field(intermediaryName: String, intermediaryDesc: String? = null): FieldBuilder =
        FieldBuilder(clazz.getOrCreateField(intermediaryName, "")).apply {
            intermediaryDesc(intermediaryDesc)
        }

    inline fun field(intermediaryName: String, intermediaryDesc: String? = null, crossinline builder: FieldBuilder.() -> Unit): FieldBuilder =
        FieldBuilder(clazz.getOrCreateField(intermediaryName, "")).also(builder).apply {
            intermediaryDesc(intermediaryDesc)
        }

    fun method(intermediaryName: String, intermediaryDesc: String? = null): MethodBuilder =
        MethodBuilder(clazz.getOrCreateMethod(intermediaryName, "")).apply {
            intermediaryDesc(intermediaryDesc)
        }

    inline fun method(intermediaryName: String, intermediaryDesc: String? = null, crossinline builder: MethodBuilder.() -> Unit): MethodBuilder =
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
        if (fillFieldDesc)
            clazz.fields.forEach { field ->
                field.obfDesc.merged = field.obfDesc.merged ?: field.intermediaryDesc.remapFieldDescriptor { descClass ->
                    getClass(descClass)?.obfName?.merged ?: descClass
                }
            }
        if (fillMethodDesc)
            clazz.methods.forEach { method ->
                method.obfDesc.merged = method.obfDesc.merged ?: method.intermediaryDesc.remapMethodDescriptor { descClass ->
                    getClass(descClass)?.obfName?.merged ?: descClass
                }
            }
    }
}

fun MappingsContainer.fillMappedDescViaIntermediary(fillFieldDesc: Boolean, fillMethodDesc: Boolean) {
    classes.forEach { clazz ->
        if (fillFieldDesc)
            clazz.fields.forEach { field ->
                field.mappedDesc = field.mappedDesc ?: field.intermediaryDesc.mapFieldIntermediaryDescToNamed(this)
            }
        if (fillMethodDesc)
            clazz.methods.forEach { method ->
                method.mappedDesc = method.mappedDesc ?: method.intermediaryDesc.mapMethodIntermediaryDescToNamed(this)
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

@Serializable
data class Class(
    override var intermediaryName: String,
    override val obfName: Obf = Obf(),
    override var mappedName: String? = null,
    val methods: MutableList<Method> = mutableListOf(),
    val fields: MutableList<Field> = mutableListOf(),
) : MappingsEntry {
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

    fun sequence(): Sequence<String> {
        if (isMerged()) return sequenceOf(merged!!)
        if (client == null) {
            if (server == null) {
                return emptySequence()
            }
            return sequenceOf(server!!)
        } else {
            if (client == null) {
                return emptySequence()
            }
            return sequenceOf(client!!)
        }
    }

    fun list(): List<String> {
        val list = mutableListOf<String>()
        if (client != null) list.add(client!!)
        if (server != null) list.add(server!!)
        if (merged != null) list.add(merged!!)
        return list
    }

    fun isMerged(): Boolean = merged != null
    fun isEmpty(): Boolean = client == null && server == null && merged == null
}