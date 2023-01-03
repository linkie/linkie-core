package me.shedaniel.linkie.namespaces

import com.soywiz.korio.stream.readAvailable
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.shedaniel.linkie.MappingsBuilder
import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.parser.apply
import me.shedaniel.linkie.parser.srg
import me.shedaniel.linkie.parser.tsrg
import me.shedaniel.linkie.parser.visitor
import me.shedaniel.linkie.utils.Version
import me.shedaniel.linkie.utils.filterNotBlank
import me.shedaniel.linkie.utils.lines
import me.shedaniel.linkie.utils.readText
import me.shedaniel.linkie.utils.toAsyncZip
import me.shedaniel.linkie.utils.toVersion
import me.shedaniel.linkie.utils.tryToVersion
import java.net.URL

object MCPNamespace : Namespace("mcp") {
    const val tmpMcpVersionsUrl = "https://gist.githubusercontent.com/shedaniel/afc2748c6d5dd827d4cde161a49687ec/raw/mcp_versions.json"
    private val mcpConfigSnapshots = mutableMapOf<Version, MutableList<String>>()
    private val newMcpVersions = mutableMapOf<Version, MCPVersion>()

    // Found on Minecraft fandom MCP page.
    private val oldMcpVersions = mapOf(
            Pair("1.7.2", "https://www.mediafire.com/file/q97ptg3ng85tpra/mcp903.zip/file")
    )
    private val sidedMcpVersions = mapOf(
            Pair("1.6.4", "https://www.mediafire.com/file/96mrmeo57cdf6zv/mcp811.zip/file"),
            Pair("1.6.2", "https://www.mediafire.com/file/xcjy2o2zsdol7cu/mcp805.zip/file"),
            Pair("1.6.1", "https://www.mediafire.com/file/fu9b8voz4xeu29n/mcp803.zip/file"),
            Pair("1.5.2", "https://www.mediafire.com/file/95vlzp1a4n4wjqw/mcp751.zip/file"),
            Pair("1.5.1", "https://www.mediafire.com/file/2s29h4469m2ysao/mcp744.zip/file"),
            Pair("1.5", "https://www.mediafire.com/file/bbgk21dw4mp02sp/mcp742.zip/file"),
            Pair("13w09c", "https://www.mediafire.com/file/t23e247mudahtam/mcp739.zip/file"),
            Pair("13w05b", "https://www.mediafire.com/file/690vfbejvfe8q0m/mcp734.zip/file"),
            Pair("13w02b", "https://www.mediafire.com/file/8amwnl6gt6p6gc5/mcp730c.zip/file"),
            Pair("1.4.7", "https://www.mediafire.com/file/07d59w314ewjfth/mcp726a.zip/file"),
            Pair("1.4.6", "https://www.mediafire.com/file/4kzs5swcm5ypqo6/mcp725.zip/file"),
            Pair("1.4.5", "https://www.mediafire.com/file/spaiyzpccxkx6cg/mcp723.zip/file"),
            Pair("1.4.4", "https://www.mediafire.com/file/i27oi6miadssyp9/mcp721.zip/file"),
            Pair("1.4.2", "https://www.mediafire.com/file/rz8dnqj1bxrz85q/mcp719.zip/file"),
            Pair("1.3.2", "https://www.mediafire.com/file/38vjh7hrpprrw1b/mcp72.zip/file"),
            Pair("1.3.1", "https://www.mediafire.com/file/hxui27dv5q4k8v4/mcp70a.zip/file"),
            Pair("12w26a", "https://www.mediafire.com/file/dhhvhzezje6zx59/mcp615.zip/file"),
            Pair("12w17a", "https://www.mediafire.com/file/0nxeeitb1s54x1e/mcp65.zip/file"),
            Pair("1.2.5", "https://www.mediafire.com/file/c6liau295225253/mcp62.zip/file"),
            Pair("1.2.4", "https://www.mediafire.com/file/hl1t281w442wfxf/mcp61.zip/file"),
            Pair("1.2.3", "https://www.mediafire.com/file/emz17agmzr3ed7e/mcp60.zip/file")
    )

    init {
        buildSupplier {
            cached()

            buildVersions {
                versions { sidedMcpVersions.keys }
                uuid { "mcp-${it}-client" }

                buildMappings(name = "MCP-Client") {
                    val url = oldMcpVersions[it]!!
                    source(MappingsSource.MCP_SRG)
                    loadSrgFromURLZip(URL(url), "client")
                }
            }

            buildVersions {
                versions { sidedMcpVersions.keys }
                uuid { "mcp-${it}-server" }

                buildMappings(name = "MCP-Server") {
                    val url = oldMcpVersions[it]!!
                    source(MappingsSource.MCP_SRG)
                    loadSrgFromURLZip(URL(url), "server")
                }
            }

            buildVersions {
                versions { oldMcpVersions.keys }
                uuid { "mcp-${it}" }

                buildMappings(name = "MCP") {
                    val url = oldMcpVersions[it]!!
                    source(MappingsSource.MCP_SRG)
                    loadSrgFromURLZip(URL(url))
                }
            }

            buildVersions {
                versionsSeq(::getAllBotVersions)
                uuid { "$it-${mcpConfigSnapshots[it.toVersion()]?.maxOrNull()!!}" }

                buildMappings(name = "MCP") {
                    val latestSnapshot = mcpConfigSnapshots[it.toVersion()]?.maxOrNull()!!
                    source(if (it.toVersion() >= Version(1, 13)) {
                        loadTsrgFromURLZip(URL("http://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/$it/mcp_config-$it.zip"))
                        MappingsSource.MCP_TSRG
                    } else {
                        loadSrgFromURLZip(URL("http://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/$it/mcp-$it-srg.zip"))
                        MappingsSource.MCP_SRG
                    })
                    loadMCPFromURLZip(URL("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_snapshot/$latestSnapshot-$it/mcp_snapshot-$latestSnapshot-$it.zip"))
                }
            }

            buildVersions {
                versions { newMcpVersions.keys.map(Version::toString) }
                uuid { newMcpVersions[it.toVersion()]!!.name }

                buildMappings(name = "MCP") {
                    val mcpVersion = newMcpVersions[it.toVersion()]!!
                    loadTsrgFromURLZip(URL(mcpVersion.mcp_config))
                    loadMCPFromURLZip(URL(mcpVersion.mcp))
                    source(MappingsSource.MCP_TSRG)
                }
            }
        }
    }

    override fun supportsFieldDescription(): Boolean = false
    override fun getDefaultLoadedVersions(): List<String> = listOf(defaultVersion)
    fun getAllBotVersions(): Sequence<String> = mcpConfigSnapshots.keys.asSequence().map { it.toString() }
    override fun getAllVersions(): Sequence<String> = getAllBotVersions() + newMcpVersions.keys.map(Version::toString) + oldMcpVersions.keys + sidedMcpVersions.keys

    override fun supportsAT(): Boolean = true
    override fun supportsMixin(): Boolean = true
    override fun supportsSource(): Boolean = true
    override suspend fun reloadData() {
        mcpConfigSnapshots.clear()
        json.parseToJsonElement(URL("https://maven.minecraftforge.net/de/oceanlabs/mcp/versions.json").readText()).jsonObject.forEach { mcVersion, mcpVersionsObj ->
            val list = mcpConfigSnapshots.getOrPut(mcVersion.toVersion(), ::mutableListOf)
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

    suspend fun MappingsBuilder.loadTsrgFromURLZip(url: URL) {
        url.toAsyncZip().forEachEntry { path, entry ->
            if (!entry.isDirectory && path.split("/").lastOrNull() == "joined.tsrg") {
                loadTsrgFromInputStream(entry.bytes.decodeToString())
            }
        }
    }

    suspend fun MappingsBuilder.loadSrgFromURLZip(url: URL, filename: String = "joined") {
        url.toAsyncZip().forEachEntry { path, entry ->
            if (!entry.isDirectory && path.split("/").lastOrNull() == "${filename}.srg") {
                loadSrgFromInputStream(entry.bytes.decodeToString())
            }
        }
    }

    private fun MappingsBuilder.loadSrgFromInputStream(content: String) {
        apply(srg(content),
            obfMerged = "obf",
            intermediary = "srg",
        )
    }

    private fun MappingsBuilder.loadTsrgFromInputStream(content: String) {
        apply(tsrg(content),
            obfMerged = "obf",
            intermediary = "srg",
        )
    }

    suspend fun MappingsBuilder.loadMCPFromURLZip(url: URL) {
        url.toAsyncZip().forEachEntry { path, entry ->
            if (!entry.isDirectory) {
                when (path.split("/").lastOrNull()) {
                    "fields.csv" -> loadMCPFieldsCSVFromInputStream(entry.bytes.lines())
                    "methods.csv" -> loadMCPMethodsCSVFromInputStream(entry.bytes.lines())
                }
            }
        }
    }

    private fun MappingsBuilder.loadMCPFieldsCSVFromInputStream(lines: Sequence<String>) {
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

    private fun MappingsBuilder.loadMCPMethodsCSVFromInputStream(lines: Sequence<String>) {
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
