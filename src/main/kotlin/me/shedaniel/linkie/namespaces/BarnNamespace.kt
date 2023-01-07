package me.shedaniel.linkie.namespaces

import kotlinx.serialization.builtins.ListSerializer
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.namespaces.YarnNamespace.loadIntermediaryFromMaven
import me.shedaniel.linkie.namespaces.YarnNamespace.loadNamedFromMaven
import me.shedaniel.linkie.utils.*
import java.net.URL

object BarnNamespace : Namespace("barn") {
    const val babricMaven = "https://maven.glass-launcher.net/babric"
    const val babricIntermediary = "babric.intermediary"
    const val barn = "babric.barn"
    val barnBuilds = mutableMapOf<String, YarnNamespace.YarnBuild>()
    val latestBarnVersion: String?
        get() = barnBuilds.keys.filter { it.contains('.') && !it.contains('-') }
                .maxByOrNull { it.tryToVersion() ?: Version() }

    init {
        buildSupplier {
            cached()

            buildVersions {
                versions { barnBuilds.keys }
                uuid { version ->
                    barnBuilds[version]!!.maven.let { it.substring(it.lastIndexOf(':') + 1) }
                }
                mappings {
                    MappingsContainer(it, name = "Barn").apply {
                        loadIntermediaryFromMaven(version, babricMaven, babricIntermediary)
                        val yarnMaven = barnBuilds[version]!!.maven
                        mappingsSource = loadNamedFromMaven(yarnMaven.substring(yarnMaven.lastIndexOf(':') + 1),
                                showError = false, repo = babricMaven, group = barn, id = "barn")
                    }
                }
            }
        }
    }

    override fun getAllVersions(): Sequence<String> = barnBuilds.keys.asSequence()
    override fun getDefaultLoadedVersions(): List<String> {
        return emptyList()
    }
    override val defaultVersion: String
        get() = latestBarnVersion!!

    override fun supportsMixin(): Boolean = true
    override fun supportsAW(): Boolean = true

    override suspend fun reloadData() {
        val buildMap = LinkedHashMap<String, MutableList<YarnNamespace.YarnBuild>>()
        json.decodeFromString(ListSerializer(YarnNamespace.YarnBuild.serializer()), URL("https://meta.babric.glass-launcher.net/v2/versions/yarn").readText())
                .forEach { buildMap.getOrPut(it.gameVersion) { mutableListOf() }.add(it) }
        buildMap.forEach { (version, builds) -> builds.maxByOrNull { it.build }?.apply { barnBuilds[version] = this } }
    }
}
