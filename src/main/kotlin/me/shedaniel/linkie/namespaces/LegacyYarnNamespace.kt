package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.MappingsSource
import java.net.URL
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.namespaces.YarnNamespace.loadIntermediaryFromMaven
import me.shedaniel.linkie.namespaces.YarnNamespace.loadIntermediaryFromTinyFile
import me.shedaniel.linkie.namespaces.YarnNamespace.loadNamedFromGithubRepo
import me.shedaniel.linkie.namespaces.YarnNamespace.loadNamedFromMaven
import me.shedaniel.linkie.utils.readText
import me.shedaniel.linkie.utils.singleSequenceOf
import me.shedaniel.linkie.utils.toVersion
import org.dom4j.io.SAXReader

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
                    MappingsContainer(it, name = "Yarn").apply {
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
                    MappingsContainer(version, name = "Legacy Yarn").apply {
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
    override fun getDefaultVersion(channel: () -> String): String = latestLegacyFabricVersions

    override fun supportsMixin(): Boolean = true
    override fun supportsAW(): Boolean = true

    override suspend fun reloadData() {
        val pom189 = URL("$legacyFabricMaven/net/fabricmc/yarn/maven-metadata.xml").readText()
        SAXReader().read(pom189.reader()).rootElement
            .element("versioning")
            .element("versions")
            .elementIterator("version")
            .asSequence()
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
