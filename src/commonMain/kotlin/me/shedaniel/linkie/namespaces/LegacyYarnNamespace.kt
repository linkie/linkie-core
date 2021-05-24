package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.Mappings
import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.buildSupplier
import me.shedaniel.linkie.namespace.NamespaceMetadata
import me.shedaniel.linkie.namespaces.YarnNamespace.loadIntermediaryFromMaven
import me.shedaniel.linkie.namespaces.YarnNamespace.loadIntermediaryFromTinyFile
import me.shedaniel.linkie.namespaces.YarnNamespace.loadNamedFromGithubRepo
import me.shedaniel.linkie.namespaces.YarnNamespace.loadNamedFromMaven
import me.shedaniel.linkie.utils.URL
import me.shedaniel.linkie.utils.readText
import me.shedaniel.linkie.utils.singleSequenceOf
import me.shedaniel.linkie.utils.toVersion
import me.shedaniel.linkie.utils.xml.parseXml

object LegacyYarnNamespace : Namespace("legacy-yarn") {
    const val intermediary125 = "https://gist.githubusercontent.com/Chocohead/b7ea04058776495a93ed2d13f34d697a/raw/1.2.5%20Merge.tiny"
    const val legacyFabricMaven = "https://maven.legacyfabric.net"
    val legacyFabricVersions = mutableMapOf<String, String?>(
        "1.6.4" to null,
        "1.7.10" to null,
        "1.8.9" to null,
        "1.12.2" to null,
        "1.13.2" to null,
    )
    var latestLegacyFabricVersions = ""
    val workingLegacyFabricVersions = mutableListOf<String>()

    init {
        buildSupplier {
            buildVersion("1.2.5") {
                mappings {
                    Mappings(it, name = "Yarn").apply {
                        loadIntermediaryFromTinyFile(URL(intermediary125))
                        loadNamedFromGithubRepo("Blayyke/yarn", "1.2.5", showError = false)
                        mappingsSource = MappingsSource.ENGIMA
                    }
                }
            }
        }
        buildSupplier {
            cached()

            buildVersions {
                versions { workingLegacyFabricVersions }
                uuid { version -> "$version-${legacyFabricVersions[version]}" }
                mappings { version ->
                    Mappings(version, name = "Legacy Yarn").apply {
                        loadIntermediaryFromMaven(version, repo = legacyFabricMaven)
                        mappingsSource = loadNamedFromMaven(
                            yarnVersion = legacyFabricVersions[version]!!,
                            repo = legacyFabricMaven,
                            showError = false
                        )
                    }
                }
            }
        }
    }

    override fun getAllVersions(): Sequence<String> = workingLegacyFabricVersions.asSequence() + singleSequenceOf("1.2.5")
    override fun getDefaultLoadedVersions(): List<String> = listOf()
    override val defaultVersion: String
        get() = latestLegacyFabricVersions

    override val metadata: NamespaceMetadata = NamespaceMetadata(
        mixins = true,
        aw = true,
    )

    override suspend fun reloadData() {
        val pom189 = URL("$legacyFabricMaven/net/fabricmc/yarn/maven-metadata.xml").readText()
        parseXml(pom189)["versioning"]["versions"].getAll("version")
            .map { it.text }
            .groupBy { it.substringBefore('+') }
            .forEach { (mcVersion, builds) ->
                if (legacyFabricVersions.containsKey(mcVersion)) {
                    legacyFabricVersions[mcVersion] = builds.last()
                }
            }
        workingLegacyFabricVersions.clear()
        workingLegacyFabricVersions.addAll(legacyFabricVersions.asSequence()
            .filter { it.value != null }
            .map { it.key }
        )
        latestLegacyFabricVersions = workingLegacyFabricVersions.maxByOrNull { it.toVersion() }!!
    }
}
