package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.namespaces.MojangRawNamespace.versionJsonMap
import me.shedaniel.linkie.rewireIntermediaryFrom

object MojangNamespace : Namespace("mojang") {
    override fun getDependencies(): Set<Namespace> = setOf(MojangRawNamespace, YarnNamespace)

    init {
        buildSupplier {
            cached()

            buildVersion("1.14.4") {
                buildMappings(name = "Mojang (via Intermediary)") {
                    val raw = MojangRawNamespace.getProvider(it)
                    replace { raw.get().clone() }
                    source(MappingsSource.MOJANG)

                    val yarn = YarnNamespace.getProvider(it)
                    if (!yarn.isEmpty()) {
                        edit {
                            rewireIntermediaryFrom(yarn.get())
                        }
                    }
                }
            }
            buildVersions {
                versions { versionJsonMap.keys }
                uuid {
                    (if (!YarnNamespace.getProvider(it).isEmpty()) "$it-intermediary" else it).let { uuid ->
                        if (MojangRawNamespace.hasMethodArgs(it)) "$uuid-parchment-${MojangRawNamespace.parchmentVersionMap[it]}" else uuid
                    }
                }

                buildMappings(name = "Mojang (via Intermediary)") {
                    val raw = MojangRawNamespace.getProvider(it)
                    replace { raw.get().clone() }
                    source(MappingsSource.MOJANG)

                    val yarn = YarnNamespace.getProvider(it)
                    if (!yarn.isEmpty()) {
                        edit {
                            rewireIntermediaryFrom(yarn.get())
                        }
                    }
                }
            }
        }
    }

    override fun supportsMixin(): Boolean = true
    override fun supportsAW(): Boolean = true
    override fun supportsSource(): Boolean = true
    override fun hasMethodArgs(version: String): Boolean = MojangRawNamespace.hasMethodArgs(version)

    override fun getDefaultLoadedVersions(): List<String> = defaultVersion?.let(::listOf) ?: listOf()

    override fun getAllVersions(): Sequence<String> =
        MojangRawNamespace.getAllVersions().filter { YarnNamespace.hasProvider(it) }

    override suspend fun reloadData() = Unit
}
