package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.namespaces.YarnNamespace.loadIntermediaryFromMaven
import me.shedaniel.linkie.namespaces.YarnNamespace.loadNamedFromMaven
import me.shedaniel.linkie.utils.readText
import me.shedaniel.linkie.utils.singleSequenceOf
import org.dom4j.io.SAXReader
import java.net.URL

object YarrnNamespace : Namespace("yarrn") {
    private var yarrnBuildInf20100618 = ""

    init {
        buildSupplier {
            cached()

            buildVersion("infdev") {
                uuid { yarrnBuildInf20100618.replaceFirst("inf", "infdev") }
                mappings {
                    MappingsContainer(it, name = "Yarrn").apply {
                        loadIntermediaryFromMaven(
                            mcVersion = yarrnBuildInf20100618.substringBefore('+'),
                            repo = "https://maven.concern.i.ng/releases",
                            group = "net.textilemc.intermediary"
                        )
                        mappingsSource = loadNamedFromMaven(
                            yarnVersion = yarrnBuildInf20100618,
                            repo = "https://maven.concern.i.ng/releases",
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
    override fun supportsMixin(): Boolean = true
    override fun supportsAW(): Boolean = true

    override suspend fun reloadData() {
        val pomYarrn = URL("https://maven.concern.i.ng/releases/net/textilemc/yarrn/maven-metadata.xml").readText()
        yarrnBuildInf20100618 = SAXReader().read(pomYarrn.reader()).rootElement
            .element("versioning")
            .element("versions")
            .elementIterator("version")
            .asSequence()
            .map { it.text }
            .filter { it.startsWith("inf-20100618+build") }
            .last()
    }
}
