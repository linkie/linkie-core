package me.shedaniel.linkie.namespaces

import kotlinx.serialization.builtins.ListSerializer
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.namespaces.YarnNamespace.loadIntermediaryFromMaven
import me.shedaniel.linkie.namespaces.YarnNamespace.loadNamedFromMaven
import me.shedaniel.linkie.utils.*
import java.net.URL

object LegacyYarnNamespace : Namespace("legacy-yarn") {
    const val legacyFabricMaven = "https://maven.legacyfabric.net"
    const val legacyFabricIntermediary = "net.legacyfabric.intermediary"
    const val legacyFabricYarn = "net.legacyfabric.yarn"
    val legacyYarnBuilds = mutableMapOf<String, YarnNamespace.YarnBuild>()
    val latestLegacyYarnVersion: String?
        get() = legacyYarnBuilds.keys.filter { it.contains('.') && !it.contains('-') }
                .maxByOrNull { it.tryToVersion() ?: Version() }

    init {
        buildSupplier {
            cached()

            buildVersions {
                versions { legacyYarnBuilds.keys }
                uuid { version ->
                    legacyYarnBuilds[version]!!.maven.let { it.substring(it.lastIndexOf(':') + 1) }
                }
                mappings {
                    MappingsContainer(it, name = "Legacy Yarn").apply {
                        loadIntermediaryFromMaven(version, legacyFabricMaven, legacyFabricIntermediary)
                        val yarnMaven = legacyYarnBuilds[version]!!.maven
                        mappingsSource = loadNamedFromMaven(yarnMaven.substring(yarnMaven.lastIndexOf(':') + 1),
                                showError = false, repo = legacyFabricMaven, group = legacyFabricYarn)
                    }
                }
            }
        }
    }

    override fun getAllVersions(): Sequence<String> = legacyYarnBuilds.keys.asSequence()
    override fun getDefaultLoadedVersions(): List<String> {
        return emptyList()
    }
    override val defaultVersion: String
        get() = latestLegacyYarnVersion!!

    override fun supportsMixin(): Boolean = true
    override fun supportsAW(): Boolean = true

    override suspend fun reloadData() {
        val buildMap = LinkedHashMap<String, MutableList<YarnNamespace.YarnBuild>>()
        json.decodeFromString(ListSerializer(YarnNamespace.YarnBuild.serializer()), URL("https://meta.legacyfabric.net/v2/versions/yarn").readText())
                .forEach { buildMap.getOrPut(it.gameVersion) { mutableListOf() }.add(it) }
        buildMap.forEach { (version, builds) -> builds.maxByOrNull { it.build }?.apply { legacyYarnBuilds[version] = this } }
    }
}
