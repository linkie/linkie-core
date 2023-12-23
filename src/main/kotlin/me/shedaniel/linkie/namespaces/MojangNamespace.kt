package me.shedaniel.linkie.namespaces

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.*
import kotlin.collections.List
import kotlin.collections.Set
import kotlin.collections.asSequence
import kotlin.collections.forEach
import kotlin.collections.listOf
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlin.collections.setOf

object MojangNamespace : Namespace("mojang") {
    val versionJsonMap = mutableMapOf<String, String>()
    private var latestRelease = ""
    private var latestSnapshot = ""

    override fun getDependencies(): Set<Namespace> = setOf(YarnNamespace)

    init {
        suspend fun getName(version: String): String = if (YarnNamespace.getProvider(version).isEmpty()) "Mojang" else "Mojang (via Intermediary)"

        buildSupplier {
            cached()

            buildVersion("1.14.4") {
                buildMappings(name = ::getName) {
                    readMojangMappings(
                        client = "https://launcher.mojang.com/v1/objects/c0c8ef5131b7beef2317e6ad80ebcd68c4fb60fa/client.txt",
                        server = "https://launcher.mojang.com/v1/objects/448ccb7b455f156bb5cb9cdadd7f96cd68134dbd/server.txt"
                    )
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
                uuid { if (!YarnNamespace.getProvider(it).isEmpty()) "$it-intermediary" else it }

                buildMappings(name = ::getName) {
                    val url = URL(versionJsonMap[it]!!)
                    val versionJson = json.parseToJsonElement(url.readText()).jsonObject
                    val downloads = versionJson["downloads"]!!.jsonObject
                    readMojangMappings(
                        client = downloads["client_mappings"]!!.jsonObject["url"]!!.jsonPrimitive.content,
                        server = downloads["server_mappings"]!!.jsonObject["url"]!!.jsonPrimitive.content
                    )
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

    override fun getDefaultLoadedVersions(): List<String> = listOf(latestRelease)

    override fun getAllVersions(): Sequence<String> =
        versionJsonMap.keys.asSequence() + singleSequenceOf("1.14.4")

    override suspend fun reloadData() {
        versionJsonMap.clear()
        val versionManifest = json.parseToJsonElement(URL("https://piston-meta.mojang.com/mc/game/version_manifest.json").readText())
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

    private fun MappingsBuilder.readMojangMappings(client: String, server: String) {
        var clientMappings: Sequence<String>? = null
        var serverMappings: Sequence<String>? = null
        runBlocking {
            launch(Dispatchers.IO) {
                clientMappings = URL(client).readLines()
            }
            launch(Dispatchers.IO) {
                serverMappings = URL(server).readLines()
            }
        }
        readMappings(clientMappings!!)
        readMappings(serverMappings!!)
    }

    private fun MappingsBuilder.readMappings(lines: Sequence<String>) {
        fun String.toActualDescription(): String {
            if (endsWith("[]")) return "[" + substring(0, length - 2).toActualDescription()
            return when (this) {
                "boolean" -> "Z"
                "char" -> "C"
                "byte" -> "B"
                "short" -> "S"
                "int" -> "I"
                "float" -> "F"
                "long" -> "J"
                "double" -> "D"
                "void" -> "V"
                "" -> ""
                else -> "L${replace('.', '/')};"
            }
        }

        fun getActualDescription(body: String, returnType: String): String {
            val splitClass = body.trimStart('(').trimEnd(')').splitToSequence(',')
            return "(${splitClass.joinToString("") { it.toActualDescription() }})${returnType.toActualDescription()}"
        }

        var lastClass: ClassBuilder? = null
        lines.forEach {
            if (it.startsWith('#') || it.isBlank()) return@forEach
            if (it.startsWith("    ")) {
                val trim = it.trimIndent().substringAfterLast(':')
                val obf = trim.substringAfterLast(" -> ")
                val type = trim.substringBefore(' ')
                val self = trim.substring((type.length + 1) until (trim.length - 4 - obf.length))
                if (trim.contains('(') && trim.contains(')')) {
                    lastClass!!.apply {
                        val methodName = self.substringBefore('(')
                        method(intermediaryName = methodName, intermediaryDesc = getActualDescription(self.substring(methodName.length), type)) {
                            obfMethod(obf)
                        }
                    }
                } else {
                    lastClass!!.apply {
                        field(intermediaryName = self, intermediaryDesc = type.toActualDescription()) {
                            obfField(obf)
                        }
                    }
                }
            } else {
                val split = it.trimIndent().trimEnd(':').split(" -> ")
                val className = split[0].replace('.', '/')
                val obf = split[1].replace('.', '/')
                if (className.onlyClass() != "package-info") {
                    lastClass = clazz(
                        intermediaryName = className,
                        obfName = obf
                    )
                }
            }
        }
    }
}
