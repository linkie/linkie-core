package me.shedaniel.linkie.namespaces

import com.soywiz.korio.net.URL
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.utils.Version
import me.shedaniel.linkie.utils.forEachEntry
import me.shedaniel.linkie.utils.lines
import me.shedaniel.linkie.utils.readText
import me.shedaniel.linkie.utils.toAsyncZip
import me.shedaniel.linkie.utils.toVersion
import me.shedaniel.linkie.utils.tryToVersion

object MCPNamespace : Namespace("mcp") {
    const val tmpMcpVersionsUrl = "https://gist.githubusercontent.com/shedaniel/afc2748c6d5dd827d4cde161a49687ec/raw/037c5ac977da967e0aab8766b78ea425bec1e8f6/mcp_versions.json"
    private val mcpConfigSnapshots = mutableMapOf<Version, MutableList<String>>()
    private val newMcpVersions = mutableMapOf<Version, MCPVersion>()

    init {
        buildSupplier {
            cached()

            buildVersions {
                versionsSeq(::getAllBotVersions)
                uuid {
                    "$it-${mcpConfigSnapshots[it.toVersion()]?.maxOrNull()!!}"
                }
                mappings {
                    MappingsContainer(it, name = "MCP").apply {
                        val latestSnapshot = mcpConfigSnapshots[it.toVersion()]?.maxOrNull()!!
                        mappingSource = if (it.toVersion() >= Version(1, 13)) {
                            loadTsrgFromURLZip(URL("http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/$it/mcp_config-$it.zip"))
                            MappingsContainer.MappingSource.MCP_TSRG
                        } else {
                            loadSrgFromURLZip(URL("http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp/$it/mcp-$it-srg.zip"))
                            MappingsContainer.MappingSource.MCP_SRG
                        }
                        loadMCPFromURLZip(URL("http://export.mcpbot.bspk.rs/mcp_snapshot/$latestSnapshot-$it/mcp_snapshot-$latestSnapshot-$it.zip"))
                    }
                }
            }
            buildVersions {
                versions {
                    newMcpVersions.keys.map(Version::toString)
                }
                uuid {
                    newMcpVersions[it.toVersion()]!!.name
                }
                mappings {
                    MappingsContainer(it, name = "MCP").apply {
                        val mcpVersion = newMcpVersions[it.toVersion()]!!
                        loadTsrgFromURLZip(URL(mcpVersion.mcp_config))
                        loadMCPFromURLZip(URL(mcpVersion.mcp))
                        mappingSource = MappingsContainer.MappingSource.MCP_TSRG
                    }
                }
            }
        }
    }

    override fun supportsFieldDescription(): Boolean = false
    override fun getDefaultLoadedVersions(): List<String> = listOf(getDefaultVersion())
    fun getAllBotVersions(): Sequence<String> = mcpConfigSnapshots.keys.asSequence().map { it.toString() }
    override fun getAllVersions(): Sequence<String> = getAllBotVersions() + newMcpVersions.keys.map(Version::toString)
    override fun getDefaultVersion(channel: () -> String): String = getAllVersions().maxWithOrNull(nullsFirst(compareBy { it.tryToVersion() }))!!

    override fun supportsAT(): Boolean = true
    override suspend fun reloadData() {
        mcpConfigSnapshots.clear()
        json.parseToJsonElement(URL("http://export.mcpbot.bspk.rs/versions.json").readText()).jsonObject.forEach { mcVersion, mcpVersionsObj ->
            val list = mcpConfigSnapshots.getOrPut(mcVersion.toVersion(), { mutableListOf() })
            mcpVersionsObj.jsonObject["snapshot"]?.jsonArray?.forEach {
                list.add(it.jsonPrimitive.content)
            }
        }
        mcpConfigSnapshots.filterValues { it.isEmpty() }.keys.toMutableList().forEach { mcpConfigSnapshots.remove(it) }
        newMcpVersions.clear()
        val tmpMcpVersionsJson = json.decodeFromString(MapSerializer(String.serializer(), MCPVersion.serializer()), URL(tmpMcpVersionsUrl).readText())
        tmpMcpVersionsJson.forEach { (mcVersion, mcpVersion) ->
            newMcpVersions[mcVersion.toVersion()] = mcpVersion
        }
    }

    suspend fun MappingsContainer.loadTsrgFromURLZip(url: URL) {
        url.toAsyncZip().forEachEntry { path, entry ->
            if (!entry.isDirectory && path.split("/").lastOrNull() == "joined.tsrg") {
                loadTsrgFromInputStream(entry.headerEntry.lines())
            }
        }
    }

    suspend fun MappingsContainer.loadSrgFromURLZip(url: URL) {
        url.toAsyncZip().forEachEntry { path, entry ->
            if (!entry.isDirectory && path.split("/").lastOrNull() == "joined.srg") {
                loadSrgFromInputStream(entry.headerEntry.lines())
            }
        }
    }

    private fun MappingsContainer.loadSrgFromInputStream(lines: Sequence<String>) {
        val groups = lines.groupBy { it.split(' ')[0] }
        groups["CL:"]?.forEach { classLine ->
            val split = classLine.substring(4).split(" ")
            val obf = split[0]
            val named = split[1]
            getOrCreateClass(named).apply {
                obfName.merged = obf
            }
        }
        groups["FD:"]?.forEach { fieldLine ->
            val split = fieldLine.substring(4).split(" ")
            val obfClass = split[0].substring(0, split[0].lastIndexOf('/'))
            val obf = split[0].substring(obfClass.length + 1)
            val namedClass = split[1].substring(0, split[1].lastIndexOf('/'))
            val intermediary = split[1].substring(namedClass.length + 1)
            getClass(namedClass)?.apply {
                getOrCreateField(intermediary, "").apply {
                    obfName.merged = obf
                    obfDesc.merged = ""
                }
            }
        }
        groups["MD:"]?.forEach { fieldLine ->
            val split = fieldLine.substring(4).split(" ")
            val obfClass = split[0].substring(0, split[0].lastIndexOf('/'))
            val obf = split[0].substring(obfClass.length + 1)
            val obfDesc = split[1]
            val namedClass = split[2].substring(0, split[2].lastIndexOf('/'))
            val intermediary = split[2].substring(namedClass.length + 1)
            val namedDesc = split[3]
            getClass(namedClass)?.apply {
                getOrCreateMethod(intermediary, namedDesc).also { method ->
                    method.obfName.merged = obf
                    method.obfDesc.merged = obfDesc
                }
            }
        }
    }

    private fun MappingsContainer.loadTsrgFromInputStream(lines: Sequence<String>) {
        var lastClass: String? = null
        lines.forEach {
            val split = it.trimIndent().split(" ")
            if (!it.startsWith('\t')) {
                val obf = split[0]
                val named = split[1]
                getOrCreateClass(named).apply {
                    obfName.merged = obf
                }
                lastClass = named
            } else {
                val clazz = lastClass?.let(this::getOrCreateClass) ?: return@forEach
                when (split.size) {
                    2 -> {
                        val obf = split[0]
                        val tsrg = split[1]
                        clazz.apply {
                            getOrCreateField(tsrg, "").apply {
                                obfName.merged = obf
                                obfDesc.merged = ""
                            }
                        }
                    }
                    3 -> {
                        val obf = split[0]
                        val obfDesc = split[1]
                        val tsrg = split[2]
                        clazz.apply {
                            getOrCreateMethod(tsrg, "").also { method ->
                                method.obfName.merged = obf
                                method.obfDesc.merged = obfDesc
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun MappingsContainer.loadMCPFromURLZip(url: URL) {
        url.toAsyncZip().forEachEntry { path, entry ->
            if (!entry.isDirectory) {
                when (path.split("/").lastOrNull()) {
                    "fields.csv" -> loadMCPFieldsCSVFromInputStream(entry.headerEntry.lines())
                    "methods.csv" -> loadMCPMethodsCSVFromInputStream(entry.headerEntry.lines())
                }
            }
        }
    }

    private fun MappingsContainer.loadMCPFieldsCSVFromInputStream(lines: Sequence<String>) {
        val map = mutableMapOf<String, String>()
        lines.forEach {
            val split = it.split(',')
            map[split[0]] = split[1]
        }
        classes.forEach {
            it.fields.forEach { field ->
                map[field.intermediaryName]?.apply {
                    field.mappedName = this
                    field.mappedDesc = ""
                }
            }
        }
    }

    private fun MappingsContainer.loadMCPMethodsCSVFromInputStream(lines: Sequence<String>) {
        val map = mutableMapOf<String, String>()
        lines.forEach {
            val split = it.split(',')
            map[split[0]] = split[1]
        }
        classes.forEach {
            it.methods.forEach { method ->
                map[method.intermediaryName]?.apply {
                    method.mappedName = this
                    method.mappedDesc = ""
                }
            }
        }
    }

    @Serializable
    data class MCPVersion(
        val name: String,
        val mcp_config: String,
        val mcp: String,
    )
}
