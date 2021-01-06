package me.shedaniel.linkie.namespaces

import com.soywiz.korio.net.URL
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.namespaces.YarnNamespace.loadIntermediaryFromTinyFile
import me.shedaniel.linkie.namespaces.YarnNamespace.loadNamedFromTinyJar
import me.shedaniel.linkie.simpleCachedSupplier
import me.shedaniel.linkie.utils.readText
import me.shedaniel.linkie.utils.singleSequenceOf
import kotlin.properties.Delegates

object PlasmaNamespace : Namespace("plasma") {
    private var downloadUrl by Delegates.notNull<String>()
    private var lastId by Delegates.notNull<Long>()

    init {
        registerSupplier(simpleCachedSupplier("b1.7.3", { "b1.7.3-$lastId" }) {
            MappingsContainer(it, name = "Plasma").apply {
                loadIntermediaryFromTinyFile(URL("https://gist.githubusercontent.com/Chocohead/b7ea04058776495a93ed2d13f34d697a/raw/Beta%201.7.3%20Merge.tiny"))
                loadNamedFromTinyJar(URL(downloadUrl), showError = false)
                mappingSource = MappingsContainer.MappingSource.YARN_V1
            }
        })
    }

    override fun getDefaultLoadedVersions(): List<String> = listOf()
    override fun getAllVersions(): Sequence<String> = singleSequenceOf("b1.7.3")
    override suspend fun reloadData() {
        val element = json.parseToJsonElement(URL("https://api.github.com/repos/minecraft-cursed-legacy/Plasma/releases/latest").readText())
        downloadUrl = element.jsonObject["assets"]!!.jsonArray[0].jsonObject["browser_download_url"]!!.jsonPrimitive.content
        lastId = element.jsonObject["id"]!!.jsonPrimitive.long
    }

    override fun supportsMixin(): Boolean = true
    override fun getDefaultVersion(channel: () -> String): String = "b1.7.3"
}
