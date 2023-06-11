package me.shedaniel.linkie.namespaces

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.namespaces.YarnNamespace.loadIntermediaryFromMaven
import me.shedaniel.linkie.namespaces.YarnNamespace.loadNamedFromMaven
import me.shedaniel.linkie.utils.*
import java.net.URL

object FeatherNamespace : Namespace("feather") {
    @Serializable
    data class FeatherBuild(
        val gameVersion: String,
        val separator: String,
        val build: Int,
        val maven: String,
        val version: String,
        val stable: Boolean,
    )

    const val ornitheMaven = "https://maven.ornithemc.net/releases"
    const val intermediaryGroup = "net.ornithemc.calamus-intermediary"
    const val featherGroup = "net.ornithemc.feather"
    const val ornitheMeta = "https://meta.ornithemc.net"
    const val featherEndpoint = "/v3/versions/feather"
    val featherBuilds = mutableMapOf<String, FeatherBuild>()
    val latestFeatherVersion: String?
        get() = featherBuilds.keys.filter { it.contains('.') && !it.contains('-') }
                .maxByOrNull { it.tryToVersion() ?: Version() }

    init {
        buildSupplier {
            cached()

            buildVersions {
                versions { featherBuilds.keys }
                uuid { version ->
                    featherBuilds[version]!!.maven.let { it.substring(it.lastIndexOf(':') + 1) }
                }
                mappings {
                    MappingsContainer(it, name = "Feather").apply {
                        loadIntermediaryFromMaven(version, ornitheMaven, intermediaryGroup)
                        val featherMaven = featherBuilds[version]!!.maven
                        mappingsSource = loadNamedFromMaven(featherMaven.substring(featherMaven.lastIndexOf(':') + 1),
                                showError = false, repo = ornitheMaven, group = featherGroup)
                    }
                }
            }
        }
    }

    override fun getDefaultLoadedVersions(): List<String> {
        return emptyList()
    }
    override fun getAllVersions(): Sequence<String> = featherBuilds.keys.asSequence()
    override val defaultVersion: String
        get() = latestFeatherVersion!!

    override fun supportsMixin(): Boolean = true
    override fun supportsAW(): Boolean = true
    override fun supportsSource(): Boolean = true

    override suspend fun reloadData() {
        val buildMap = LinkedHashMap<String, MutableList<FeatherBuild>>()
        json.decodeFromString(ListSerializer(FeatherBuild.serializer()), URL(ornitheMeta + featherEndpoint).readText())
                .forEach { buildMap.getOrPut(it.gameVersion) { mutableListOf() }.add(it) }
        buildMap.forEach { (version, builds) -> builds.maxByOrNull { it.build }?.apply { featherBuilds[version] = this } }
    }
}
