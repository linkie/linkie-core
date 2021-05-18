package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.MappingsContainer
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

            buildVersions {
                versionsSeq(::getAllVersions)
                mappings {
                    val mojmap = MojangNamespace.getProvider(it).get()
                    mojmap.clone().copy(version = it, name = "Mojang (via TSRG)", mappingsSource = MOJANG_TSRG).apply {
                        make(it)
                    }
                }
            }
        }
    }

    private suspend fun MappingsContainer.make(version: String) {
        val stripIntermediary: MappingsMember.() -> Unit = {
            if (mappedName != null) {
                intermediaryName = mappedName!!
            }
            intermediaryDesc = getMappedDesc(this@make)
        }
        for (clazz in classes.values) {
            clazz.members.forEach(stripIntermediary)
            clazz.fields.forEach(stripIntermediary)
            if (clazz.mappedName != null) {
                clazz.intermediaryName = clazz.mappedName!!
            }
        }
        rearrangeClassMap()
        rewireIntermediaryFrom(MCPNamespace.getProvider(version).get(), mapClassNames = false)
    }
}
