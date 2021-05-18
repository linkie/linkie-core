package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.Mappings
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.buildSupplier
import me.shedaniel.linkie.namespace.NamespaceMetadata
import me.shedaniel.linkie.namespaces.YarnNamespace.loadIntermediaryFromMaven
import me.shedaniel.linkie.namespaces.YarnNamespace.loadNamedFromMaven
import me.shedaniel.linkie.utils.URL
import me.shedaniel.linkie.utils.readText
import me.shedaniel.linkie.utils.singleSequenceOf
import me.shedaniel.linkie.utils.xml.parseXml

object YarrnNamespace : Namespace("yarrn") {
    private var yarrnBuildInf20100618 = ""

    init {
        buildSupplier {
            cached()

            buildVersion("infdev") {
                uuid { yarrnBuildInf20100618.replaceFirst("inf", "infdev") }
                mappings {
                    Mappings(it, name = "Yarrn").apply {
                        loadIntermediaryFromMaven(
                            mcVersion = yarrnBuildInf20100618.substringBefore('+'),
                            repo = "https://maven.concern.i.ng",
                            group = "net.textilemc.intermediary"
                        )
                        mappingsSource = loadNamedFromMaven(
                            yarnVersion = yarrnBuildInf20100618,
                            repo = "https://maven.concern.i.ng",
                            group = "net.textilemc.yarrn",
                            id = "yarrn",
                            showError = false
                        )
                    }
                }
            }
        }
    }

    override fun getAllVersions(): Sequence<String> = singleSequenceOf("infdev")
    override fun getDefaultLoadedVersions(): List<String> = listOf()
    override val defaultVersion: String = "infdev"

    override val metadata: NamespaceMetadata = NamespaceMetadata(
        mixins = true,
        aw = true,
    )

    override suspend fun reloadData() {
        val pomYarrn = URL("https://maven.concern.i.ng/net/textilemc/yarrn/maven-metadata.xml").readText()
        yarrnBuildInf20100618 = parseXml(pomYarrn)["versioning"]["versions"].getAll("version")
            .map { it.text }
            .filter { it.startsWith("inf-20100618+build") }
            .last()
    }
}
