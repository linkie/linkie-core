package me.shedaniel.linkie.namespaces

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.*
import kotlin.collections.set

object MojangRawNamespace : Namespace("mojang_raw") {
    val versionJsonMap = mutableMapOf<String, String>()
    val parchmentVersionMap = mutableMapOf<String, String>()
    private var latestRelease = ""
    private var latestSnapshot = ""

    init {
        buildSupplier {
            cached()

            buildVersion("1.14.4") {
                buildMappings(name = "Mojang") {
                    readMojangMappings(
                        client = "https://launcher.mojang.com/v1/objects/c0c8ef5131b7beef2317e6ad80ebcd68c4fb60fa/client.txt",
                        server = "https://launcher.mojang.com/v1/objects/448ccb7b455f156bb5cb9cdadd7f96cd68134dbd/server.txt"
                    )
                    source(MappingsSource.MOJANG)
                }
            }
            buildVersions {
                versions { versionJsonMap.keys }
                uuid { if (parchmentVersionMap.contains(it)) "${it}-parchment-${parchmentVersionMap[it]}" else it }

                buildMappings(name = "Mojang") {
                    val url = URL(versionJsonMap[it]!!)
                    val versionJson = json.parseToJsonElement(url.readText()).jsonObject
                    val downloads = versionJson["downloads"]!!.jsonObject
                    readMojangMappings(
                        client = downloads["client_mappings"]!!.jsonObject["url"]!!.jsonPrimitive.content,
                        server = downloads["server_mappings"]!!.jsonObject["url"]!!.jsonPrimitive.content
                    )
                    source(MappingsSource.MOJANG)

                    if (parchmentVersionMap.contains(it)) {
                        runCatching {
                            val parchmentVersion = parchmentVersionMap[it]
                            URL("https://maven.parchmentmc.org/org/parchmentmc/data/parchment-$it/$parchmentVersion/parchment-$it-$parchmentVersion.zip").toAsyncZip()
                                .forEachEntry { path, entry ->
                                    if (!entry.isDirectory && path.split("/").lastOrNull() == "parchment.json") {
                                        appendParchment(json.parseToJsonElement(entry.bytes.decodeToString()))
                                    }
                                }
                        }
                    }
                }
            }
        }
    }

    private fun MappingsBuilder.appendParchment(json: JsonElement) {
        json.jsonObject["classes"]?.jsonArray?.map { it.jsonObject }?.forEach { classJson ->
            val name = classJson["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            container.getClass(name)?.also { clazz ->
                classJson["methods"]?.jsonArray?.map { it.jsonObject }?.forEach inner@{ methodJson ->
                    val methodName = methodJson["name"]?.jsonPrimitive?.contentOrNull ?: return@inner
                    val methodDesc = methodJson["descriptor"]?.jsonPrimitive?.contentOrNull ?: return@inner
                    clazz.getMethod(methodName, methodDesc)?.also { method ->
                        methodJson["parameters"]?.jsonArray?.map { it.jsonObject }?.forEach parameter@{ parameterJson ->
                            val index = parameterJson["index"]?.jsonPrimitive?.intOrNull ?: return@parameter
                            val name = parameterJson["name"]?.jsonPrimitive?.contentOrNull ?: return@parameter
                            if (method.args == null) method.args = mutableListOf()
                            method.args!!.add(MethodArg(index, name))
                        }
                    }
                }
            }
        }
    }

    override fun supportsMixin(): Boolean = true
    override fun supportsAW(): Boolean = true
    override fun supportsAT(): Boolean = true
    override fun supportsSource(): Boolean = true
    override fun hasMethodArgs(version: String): Boolean = parchmentVersionMap.contains(version)

    override fun getDefaultLoadedVersions(): List<String> = listOf(latestRelease)

    override fun getAllVersions(): Sequence<String> =
        versionJsonMap.keys.asSequence() + singleSequenceOf("1.14.4")

    override suspend fun reloadData() {
        versionJsonMap.clear()
        parchmentVersionMap.clear()
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
        runCatching {
            json.parseToJsonElement(URL("https://versioning.parchmentmc.org/versions").readText()).jsonObject["releases"]!!.jsonObject.forEach { (key, value) ->
                parchmentVersionMap[key] = value.jsonPrimitive.content
            }
        }
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
