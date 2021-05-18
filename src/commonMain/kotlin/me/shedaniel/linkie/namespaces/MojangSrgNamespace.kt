package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.MappingsMember
import me.shedaniel.linkie.MappingsSource.Companion.MOJANG_TSRG
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.buildSupplier
import me.shedaniel.linkie.get
import me.shedaniel.linkie.getMappedDesc
import me.shedaniel.linkie.namespace.NamespaceMetadata
import me.shedaniel.linkie.rearrangeClassMap
import me.shedaniel.linkie.rewireIntermediaryFrom
import me.shedaniel.linkie.utils.tryToVersion

object MojangSrgNamespace : Namespace("mojang_srg") {
    override val dependencies: Set<Namespace> = setOf(MCPNamespace, MojangNamespace)
    override fun getDefaultLoadedVersions(): List<String> = emptyList()
    override fun getAllVersions(): Sequence<String> = MCPNamespace.getAllVersions().filter {
        MojangNamespace.getAllVersions().contains(it)
    }

    override suspend fun reloadData() = Unit

    override val defaultVersion: String
        get() = getAllVersions().maxWithOrNull(nullsFirst(compareBy { it.tryToVersion() }))!!

    override val metadata: NamespaceMetadata = NamespaceMetadata(
        mixins = true,
        at = true,
        fieldDescriptor = false,
    )

    init {
        buildSupplier {
            cached()

            buildVersion("1.16.5-N") {
                mappings {
                    val mojmap = MojangNamespace[it].get()
                    mojmap.copy(version = it, name = "Mojang (via TSRGv2)", mappingsSource = MOJANG_TSRG).apply {
                        val stripIntermediary: MappingsMember.() -> Unit = {
                            if (mappedName != null && (intermediaryName.startsWith("method_") || intermediaryName.startsWith("field_"))) {
                                intermediaryName = mappedName!!
                                intermediaryDesc = getMappedDesc(this@apply)
                            }
                        }
                        for (clazz in classes.values) {
                            clazz.members.forEach(stripIntermediary)
                            clazz.fields.forEach(stripIntermediary)
                            if (clazz.mappedName != null && clazz.intermediaryName.startsWith("net/minecraft/class_")) {
                                clazz.intermediaryName = clazz.mappedName!!
                            }
                        }
                        rearrangeClassMap()
                        rewireIntermediaryFrom(MCPNamespace[it].get(), mapClassNames = false)
                    }
                }
            }
            buildVersions {
                versionsSeq(::getAllVersions)
                mappings {
                    val mojmap = MojangNamespace[it].get()
                    mojmap.copy(version = it, name = "Mojang (via TSRG)", mappingsSource = MOJANG_TSRG).apply {
                        val stripIntermediary: MappingsMember.() -> Unit = {
                            if (mappedName != null && (intermediaryName.startsWith("method_") || intermediaryName.startsWith("field_"))) {
                                intermediaryName = mappedName!!
                                intermediaryDesc = getMappedDesc(this@apply)
                            }
                        }
                        for (clazz in classes.values) {
                            clazz.members.forEach(stripIntermediary)
                            clazz.fields.forEach(stripIntermediary)
                            if (clazz.mappedName != null && clazz.intermediaryName.startsWith("net/minecraft/class_")) {
                                clazz.intermediaryName = clazz.mappedName!!
                            }
                        }
                        rearrangeClassMap()
                        rewireIntermediaryFrom(MCPNamespace[it].get(), mapClassNames = false)
                    }
                }
            }
        }
    }
}
