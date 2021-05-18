package me.shedaniel.linkie.namespaces

import com.soywiz.korio.async.runBlockingNoJs
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.shedaniel.linkie.MappingsConstructingBuilder
import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.buildSupplier
import me.shedaniel.linkie.get
import me.shedaniel.linkie.json
import me.shedaniel.linkie.namespace.NamespaceMetadata
import me.shedaniel.linkie.parser.apply
import me.shedaniel.linkie.parser.proguard.ProguardParser
import me.shedaniel.linkie.parser.proguard.proguard
import me.shedaniel.linkie.rewireIntermediaryFrom
import me.shedaniel.linkie.utils.URL
import me.shedaniel.linkie.utils.readText
import me.shedaniel.linkie.utils.singleSequenceOf
import me.shedaniel.linkie.utils.toVersion
import me.shedaniel.linkie.utils.tryToVersion

object MojangNamespace : Namespace("mojang") {
    val versionJsonMap = mutableMapOf<String, String>()
    private var latestRelease = ""
    private var latestSnapshot = ""

    override val dependencies: Set<Namespace> = setOf(YarnNamespace)

    init {
        fun getName(version: String): String = if (YarnNamespace[version].isEmpty()) "Mojang" else "Mojang (via Intermediary)"

        buildSupplier {
            cached()

            buildVersion("1.14.4") {
                buildMappings(name = ::getName) {
                    readMojangMappings(
                        client = "https://launcher.mojang.com/v1/objects/c0c8ef5131b7beef2317e6ad80ebcd68c4fb60fa/client.txt",
                        server = "https://launcher.mojang.com/v1/objects/448ccb7b455f156bb5cb9cdadd7f96cd68134dbd/server.txt"
                    )
                    source(MappingsSource.MOJANG)

                    val yarn = YarnNamespace[it]
                    if (!yarn.isEmpty()) {
                        edit {
                            rewireIntermediaryFrom(yarn.get())
                        }
                    }
                }
            }
            buildVersions {
                versions { versionJsonMap.keys }
                uuid { if (!YarnNamespace[it].isEmpty()) "$it-intermediary" else it }

                buildMappings(name = ::getName) {
                    val url = URL(versionJsonMap[it]!!)
                    val versionJson = json.parseToJsonElement(url.readText()).jsonObject
                    val downloads = versionJson["downloads"]!!.jsonObject
                    readMojangMappings(
                        client = downloads["client_mappings"]!!.jsonObject["url"]!!.jsonPrimitive.content,
                        server = downloads["server_mappings"]!!.jsonObject["url"]!!.jsonPrimitive.content
                    )
                    source(MappingsSource.MOJANG)

                    val yarn = YarnNamespace[it]
                    if (!yarn.isEmpty()) {
                        edit {
                            rewireIntermediaryFrom(yarn.get())
                        }
                    }
                }
            }
        }
    }

    override val metadata: NamespaceMetadata = NamespaceMetadata(
        mixins = true,
        aw = true,
    )

    override fun getDefaultLoadedVersions(): List<String> = listOf(latestRelease)

    override fun getAllVersions(): Sequence<String> =
        singleSequenceOf("1.14.4") + versionJsonMap.keys.asSequence()

    override suspend fun reloadData() {
        versionJsonMap.clear()
        val versionManifest = json.parseToJsonElement(URL("https://launchermeta.mojang.com/mc/game/version_manifest.json").readText())
        val lowestVersionWithMojmap = "19w36a".toVersion()
        versionManifest.jsonObject["versions"]!!.jsonArray.forEach { versionElement ->
            val versionString = versionElement.jsonObject["id"]!!.jsonPrimitive.content
            val version = versionString.tryToVersion() ?: return@forEach
            if (version >= lowestVersionWithMojmap) {
                val urlString = versionElement.jsonObject["url"]!!.jsonPrimitive.content
                versionJsonMap[versionString] = urlString
            }
        }
        latestRelease = versionManifest.jsonObject["latest"]!!.jsonObject["release"]!!.jsonPrimitive.content
        latestSnapshot = versionManifest.jsonObject["latest"]!!.jsonObject["snapshot"]!!.jsonPrimitive.content
    }

    override val defaultVersion: String
        get() = latestRelease

    private fun MappingsConstructingBuilder.readMojangMappings(client: String, server: String) {
        var clientMappings: String? = null
        var serverMappings: String? = null
        runBlockingNoJs {
            launch { clientMappings = URL(client).readText() }
            launch { serverMappings = URL(server).readText() }
        }
        readMappings(clientMappings!!)
        readMappings(serverMappings!!)
    }

    private fun MappingsConstructingBuilder.readMappings(content: String) {
        apply(
            proguard(content),
            obfMerged = ProguardParser.NS_OBF,
            intermediary = ProguardParser.NS_MAPPED
        )
    }
}
