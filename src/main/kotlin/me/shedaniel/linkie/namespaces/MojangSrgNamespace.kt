package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.MappingsSource.MOJANG_TSRG
import me.shedaniel.linkie.MappingsMember
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.getMappedDesc
import me.shedaniel.linkie.rearrangeClassMap
import me.shedaniel.linkie.rewireIntermediaryFrom
import me.shedaniel.linkie.utils.tryToVersion

object MojangSrgNamespace : Namespace("mojang_srg") {
    override fun getDependencies(): Set<Namespace> = setOf(MCPNamespace, MojangNamespace)
    override fun getDefaultLoadedVersions(): List<String> = emptyList()
    override fun supportsFieldDescription(): Boolean = false
    override fun getAllVersions(): Sequence<String> = MCPNamespace.getAllVersions().filter {
        MojangNamespace.getAllVersions().contains(it)
    }

    override fun getDefaultVersion(channel: () -> String): String = getAllVersions().maxWithOrNull(nullsFirst(compareBy { it.tryToVersion() }))!!
    override fun supportsAT(): Boolean = true
    override fun supportsMixin(): Boolean = true

    override suspend fun reloadData() {}

    init {
        buildSupplier {
            cached()

            buildVersion("1.16.5-N") {
                mappings {
                    val mojmap = MojangNamespace.getProvider(it).get()
                    mojmap.copy(version = it, name = "Mojang (via TSRGv2)", mappingsSource = MOJANG_TSRG).apply {
                        val stripIntermediary: MappingsMember.() -> Unit = {
                            if (mappedName != null && (intermediaryName.startsWith("method_") || intermediaryName.startsWith("field_"))) {
                                intermediaryName = mappedName!!
                                intermediaryDesc = getMappedDesc(this@apply)
                            }
                        }
                        for (`class` in classes.values) {
                            `class`.members.forEach(stripIntermediary)
                            `class`.fields.forEach(stripIntermediary)
                            if (`class`.mappedName != null && `class`.intermediaryName.startsWith("net/minecraft/class_")) {
                                `class`.intermediaryName = `class`.mappedName!!
                            }
                        }
                        rearrangeClassMap()
                        rewireIntermediaryFrom(MCPNamespace.getProvider(it).get(), mapClassNames = false)
                    }
                }
            }
            buildVersions {
                versionsSeq(::getAllVersions)
                mappings {
                    val mojmap = MojangNamespace.getProvider(it).get()
                    mojmap.copy(version = it, name = "Mojang (via TSRG)", mappingsSource = MOJANG_TSRG).apply {
                        val stripIntermediary: MappingsMember.() -> Unit = {
                            if (mappedName != null && (intermediaryName.startsWith("method_") || intermediaryName.startsWith("field_"))) {
                                intermediaryName = mappedName!!
                                intermediaryDesc = getMappedDesc(this@apply)
                            }
                        }
                        for (`class` in classes.values) {
                            `class`.members.forEach(stripIntermediary)
                            `class`.fields.forEach(stripIntermediary)
                            if (`class`.mappedName != null && `class`.intermediaryName.startsWith("net/minecraft/class_")) {
                                `class`.intermediaryName = `class`.mappedName!!
                            }
                        }
                        rearrangeClassMap()
                        rewireIntermediaryFrom(MCPNamespace.getProvider(it).get(), mapClassNames = false)
                    }
                }
            }
        }
    }
}
