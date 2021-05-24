package me.shedaniel.linkie.namespaces

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.shedaniel.linkie.MappingsConstructingBuilder
import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.buildSupplier
import me.shedaniel.linkie.json
import me.shedaniel.linkie.namespace.NamespaceMetadata
import me.shedaniel.linkie.parser.apply
import me.shedaniel.linkie.parser.srg.SrgParser
import me.shedaniel.linkie.parser.srg.TsrgParser
import me.shedaniel.linkie.parser.srg.srg
import me.shedaniel.linkie.parser.srg.tsrg
import me.shedaniel.linkie.utils.URL
import me.shedaniel.linkie.utils.Version
import me.shedaniel.linkie.utils.filterNotBlank
import me.shedaniel.linkie.utils.io.bytes
import me.shedaniel.linkie.utils.io.forEachZipEntry
import me.shedaniel.linkie.utils.io.isDirectory
import me.shedaniel.linkie.utils.lines
import me.shedaniel.linkie.utils.readText
import me.shedaniel.linkie.utils.toVersion
import me.shedaniel.linkie.utils.tryToVersion

object MCPNamespace : Namespace("mcp") {
    val version113 = Version(1, 13)
    const val tmpMcpVersionsUrl = "https://gist.githubusercontent.com/shedaniel/afc2748c6d5dd827d4cde161a49687ec/raw/mcp_versions.json"
    private val mcpConfigSnapshots = mutableMapOf<Version, MutableList<String>>()
    private val newMcpVersions = mutableMapOf<Version, MCPVersion>()

    init {
        buildSupplier(cached = true) {
            versions {
                versions(::getAllBotVersions)
                uuid { "$it-${mcpConfigSnapshots[it.toVersion()]?.maxOrNull()!!}" }

                mappings(name = "MCP") {
                    val latestSnapshot = mcpConfigSnapshots[it.toVersion()]?.maxOrNull()!!
                    source(if (it.toVersion() >= version113) {
                        loadTsrgFromURLZip(URL("https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/$it/mcp_config-$it.zip"))
                        MappingsSource.MCP_TSRG
                    } else {
                        loadSrgFromURLZip(URL("https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp/$it/mcp-$it-srg.zip"))
                        MappingsSource.MCP_SRG
                    })
                    loadMCPFromURLZip(URL("https://export.mcpbot.bspk.rs/mcp_snapshot/$latestSnapshot-$it/mcp_snapshot-$latestSnapshot-$it.zip"))
                }
            }

            versions {
                versions { newMcpVersions.keys.asSequence().map(Version::toString) }
                uuid { newMcpVersions[it.toVersion()]!!.name }

                mappings(name = "MCP") {
                    val mcpVersion = newMcpVersions[it.toVersion()]!!
                    loadTsrgFromURLZip(URL(mcpVersion.mcp_config))
                    loadMCPFromURLZip(URL(mcpVersion.mcp))
                    source(MappingsSource.MCP_TSRG)
                }
            }
        }
    }

    override fun getDefaultLoadedVersions(): List<String> = listOf(defaultVersion)
    fun getAllBotVersions(): Sequence<String> = mcpConfigSnapshots.keys.asSequence().map { it.toString() }
    override fun getAllVersions(): Sequence<String> = getAllBotVersions() + newMcpVersions.keys.map(Version::toString)
    override val defaultVersion: String
        get() = getAllVersions().maxWithOrNull(nullsFirst(compareBy { it.tryToVersion() }))!!

    override val metadata: NamespaceMetadata = NamespaceMetadata(
        mixins = true,
        at = true,
        fieldDescriptor = false,
    )

    override suspend fun reloadData() {
        mcpConfigSnapshots.clear()
        json.parseToJsonElement(URL("https://export.mcpbot.bspk.rs/versions.json").readText()).jsonObject.forEach { (mcVersion, mcpVersionsObj) ->
            val list = mcpConfigSnapshots.getOrPut(mcVersion.toVersion()) { mutableListOf() }
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

    suspend fun MappingsConstructingBuilder.loadTsrgFromURLZip(url: URL) {
        url.forEachZipEntry { path, entry ->
            if (!entry.isDirectory && path.split("/").lastOrNull() == "joined.tsrg") {
                loadTsrgFrom(entry.bytes.decodeToString())
            }
        }
    }

    suspend fun MappingsConstructingBuilder.loadSrgFromURLZip(url: URL) {
        url.forEachZipEntry { path, entry ->
            if (!entry.isDirectory && path.split("/").lastOrNull() == "joined.srg") {
                loadSrgFrom(entry.bytes.decodeToString())
            }
        }
    }

    fun MappingsConstructingBuilder.loadSrgFrom(content: String) {
        apply(
            srg(content),
            obfMerged = SrgParser.NS_OBF,
            intermediary = SrgParser.NS_SRG,
        )
    }

    fun MappingsConstructingBuilder.loadTsrgFrom(content: String) {
        apply(
            tsrg(content),
            obfMerged = TsrgParser.NS_OBF,
            intermediary = TsrgParser.NS_SRG,
        )
    }

    suspend fun MappingsConstructingBuilder.loadMCPFromURLZip(url: URL) {
        url.forEachZipEntry { path, entry ->
            if (!entry.isDirectory) {
                when (path.split("/").lastOrNull()) {
                    "fields.csv" -> loadMCPFields(entry.bytes.lines())
                    "methods.csv" -> loadMCPMethodsCSV(entry.bytes.lines())
                }
            }
        }
    }

    fun MappingsConstructingBuilder.loadMCPFields(lines: Sequence<String>) {
        val map = mutableMapOf<String, String>()
        lines.filterNotBlank().forEach {
            val split = it.split(',')
            map[split[0]] = split[1]
        }
        container.classes.forEach { (_, it) ->
            it.fields.forEach { field ->
                map[field.intermediaryName]?.apply {
                    field.mappedName = this
                }
            }
        }
    }

    fun MappingsConstructingBuilder.loadMCPMethodsCSV(lines: Sequence<String>) {
        val map = mutableMapOf<String, String>()
        lines.filterNotBlank().forEach {
            val split = it.split(',')
            map[split[0]] = split[1]
        }
        container.classes.forEach { (_, it) ->
            it.methods.forEach { method ->
                map[method.intermediaryName]?.apply {
                    method.mappedName = this
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
