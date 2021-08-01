package me.shedaniel.linkie.namespaces

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.MappingsMember
import me.shedaniel.linkie.MappingsSource.MOJANG_TSRG
import me.shedaniel.linkie.MappingsSource.MOJANG_TSRG2
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.buildMappings
import me.shedaniel.linkie.getMappedDesc
import me.shedaniel.linkie.namespaces.MCPNamespace.loadTsrgFromURLZip
import me.shedaniel.linkie.rearrangeClassMap
import me.shedaniel.linkie.rewireIntermediaryFrom
import me.shedaniel.linkie.utils.Version
import me.shedaniel.linkie.utils.readText
import me.shedaniel.linkie.utils.toVersion
import java.net.URL

object MojangSrgNamespace : Namespace("mojang_srg") {
    const val tmpTsrg2VersionsUrl = "https://gist.githubusercontent.com/shedaniel/68c5a5f8a56a30f48f994e105351b15f/raw/d6aeaa5996f9d18764a03edd74f1d08a70fc312e/official-mcpconfig.json"
    private var newMcpVersions = listOf<Version>()

    override fun getDependencies(): Set<Namespace> = setOf(MCPNamespace, MojangNamespace)
    override fun getDefaultLoadedVersions(): List<String> = emptyList()
    override fun supportsFieldDescription(): Boolean = false
    val legacy: Sequence<String>
        get() = MCPNamespace.getAllVersions().filter {
            MojangNamespace.getAllVersions().contains(it)
        }
    override fun getAllVersions(): Sequence<String> = legacy + newMcpVersions.asSequence().map(Version::toString)

    override fun supportsAT(): Boolean = true
    override fun supportsMixin(): Boolean = true

    override suspend fun reloadData() {
        newMcpVersions = json.decodeFromString(ListSerializer(String.serializer()), URL(tmpTsrg2VersionsUrl).readText())
            .map(String::toVersion)
    }

    init {
        buildSupplier {
            cached()

            buildVersions {
                versionsSeq(::getAllVersions)
                mappings {
                    val mojmap = MojangNamespace.getProvider(it).get()
                    mojmap.clone().copy(version = it, name = "Mojang (via TSRG)", mappingsSource = MOJANG_TSRG).apply {
                        makeFromMcp(it)
                    }
                }
            }

            buildVersions {
                versionsSeq { newMcpVersions.asSequence().map(Version::toString) }
                mappings {
                    val mojmap = MojangNamespace.getProvider(it).get()
                    mojmap.clone().copy(version = it, name = "Mojang (via TSRG2)", mappingsSource = MOJANG_TSRG2).apply {
                        makeFromMcpConfig(it)
                    }
                }
            }
        }
    }

    private suspend fun MappingsContainer.makeFromMcp(version: String) {
        val stripIntermediary: MappingsMember.() -> Unit = {
            if (mappedName != null) {
                intermediaryName = mappedName!!
            }
            intermediaryDesc = getMappedDesc(this@makeFromMcp)
        }
        for (clazz in classes.values) {
            clazz.members.forEach(stripIntermediary)
            clazz.fields.forEach(stripIntermediary)
            if (clazz.mappedName != null) {
                clazz.intermediaryName = clazz.mappedName!!
            }
        }
        rearrangeClassMap()
        rewireIntermediaryFrom(MCPNamespace.getProvider(version).get(), mapClassNames = false)
    }

    private suspend fun MappingsContainer.makeFromMcpConfig(version: String) {
        val stripIntermediary: MappingsMember.() -> Unit = {
            if (mappedName != null) {
                intermediaryName = mappedName!!
            }
            intermediaryDesc = getMappedDesc(this@makeFromMcpConfig)
        }
        for (clazz in classes.values) {
            clazz.members.forEach(stripIntermediary)
            clazz.fields.forEach(stripIntermediary)
            if (clazz.mappedName != null) {
                clazz.intermediaryName = clazz.mappedName!!
            }
        }
        rearrangeClassMap()
        rewireIntermediaryFrom(buildMappings(
            version = "BRUH",
            name = "BRUH",
        ) {
            loadTsrgFromURLZip(URL("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/$version/mcp_config-$version.zip"))
        })
    }
}
