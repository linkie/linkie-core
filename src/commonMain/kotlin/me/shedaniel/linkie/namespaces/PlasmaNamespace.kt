package me.shedaniel.linkie.namespaces

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.buildSupplier
import me.shedaniel.linkie.json
import me.shedaniel.linkie.namespace.NamespaceMetadata
import me.shedaniel.linkie.namespaces.YarnNamespace.loadIntermediaryFromTinyFile
import me.shedaniel.linkie.namespaces.YarnNamespace.loadNamedFromTinyJar
import me.shedaniel.linkie.utils.URL
import me.shedaniel.linkie.utils.readText
import me.shedaniel.linkie.utils.singleSequenceOf
import kotlin.properties.Delegates

object PlasmaNamespace : Namespace("plasma") {
    private var downloadUrl by Delegates.notNull<String>()
    private var lastId by Delegates.notNull<Long>()

    override val metadata: NamespaceMetadata = NamespaceMetadata(
        mixins = true,
    )
    override val defaultVersion: String = "b1.7.3"

    init {
        buildSupplier(cached = true) {
            version("b1.7.3") {
                uuid { "b1.7.3-$lastId" }
                mappings(name = "Plasma") {
                    edit {
                        loadIntermediaryFromTinyFile(
                            URL("https://gist.githubusercontent.com/Chocohead/b7ea04058776495a93ed2d13f34d697a/raw/Beta%201.7.3%20Merge.tiny"))
                        loadNamedFromTinyJar(URL(downloadUrl), showError = false)
                    }
                    source(MappingsSource.TINY_V1)
                }
            }
        }
    }

    override fun getDefaultLoadedVersions(): List<String> = listOf()
    override fun getAllVersions(): Sequence<String> = singleSequenceOf("b1.7.3")
    override suspend fun reloadData() {
        val element = json.parseToJsonElement(URL("https://api.github.com/repos/minecraft-cursed-legacy/Plasma/releases/latest").readText())
        downloadUrl = element.jsonObject["assets"]!!.jsonArray[0].jsonObject["browser_download_url"]!!.jsonPrimitive.content
        lastId = element.jsonObject["id"]!!.jsonPrimitive.long
    }
}
