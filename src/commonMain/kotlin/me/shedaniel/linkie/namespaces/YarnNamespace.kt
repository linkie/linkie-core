package me.shedaniel.linkie.namespaces

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import me.shedaniel.linkie.Class
import me.shedaniel.linkie.Mappings
import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.Method
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.buildSupplier
import me.shedaniel.linkie.json
import me.shedaniel.linkie.namespace.NamespaceMetadata
import me.shedaniel.linkie.utils.URL
import me.shedaniel.linkie.utils.bytes
import me.shedaniel.linkie.utils.filterNotBlank
import me.shedaniel.linkie.utils.io.ZipFile
import me.shedaniel.linkie.utils.io.bytes
import me.shedaniel.linkie.utils.io.forEachEntry
import me.shedaniel.linkie.utils.io.forEachZipEntry
import me.shedaniel.linkie.utils.io.isDirectory
import me.shedaniel.linkie.utils.io.readZip
import me.shedaniel.linkie.utils.lines
import me.shedaniel.linkie.utils.loadNamedFromEngimaStream
import me.shedaniel.linkie.utils.readBytes
import me.shedaniel.linkie.utils.readText
import me.shedaniel.linkie.utils.warn
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

object YarnNamespace : Namespace("yarn") {
    @Serializable
    data class YarnBuild(
        val gameVersion: String,
        val separator: String,
        val build: Int,
        val maven: String,
        val version: String,
        val stable: Boolean,
    )

    val yarnBuilds = mutableMapOf<String, YarnBuild>()
    val latestYarnVersion: String?
        get() = yarnBuilds.keys.firstOrNull { it.contains('.') && !it.contains('-') }

    init {
        buildSupplier {
            cached()

            buildVersion {
                versions { yarnBuilds.keys }
                uuid { version ->
                    yarnBuilds[version]!!.maven.let { it.substring(it.lastIndexOf(':') + 1) }
                }
                mappings {
                    Mappings(it, name = "Yarn").apply {
                        loadIntermediaryFromMaven(version)
                        val yarnMaven = yarnBuilds[version]!!.maven
                        mappingsSource = loadNamedFromMaven(yarnMaven.substring(yarnMaven.lastIndexOf(':') + 1), showError = false)
                    }
                }
            }
        }
    }

    override fun getDefaultLoadedVersions(): List<String> {
        return listOfNotNull(latestYarnVersion)
    }

    override fun getAllVersions(): Sequence<String> = yarnBuilds.keys.asSequence()

    override val metadata: NamespaceMetadata = NamespaceMetadata(
        mixins = true,
        aw = true,
    )

    override suspend fun reloadData() {
        val buildMap = LinkedHashMap<String, MutableList<YarnBuild>>()
        json.decodeFromString(ListSerializer(YarnBuild.serializer()), URL("https://meta.fabricmc.net/v2/versions/yarn").readText())
            .forEach { buildMap.getOrPut(it.gameVersion) { mutableListOf() }.add(it) }
        buildMap.forEach { (version, builds) -> builds.maxByOrNull { it.build }?.apply { yarnBuilds[version] = this } }
    }

    override val defaultVersion: String
        get() = latestYarnVersion!!

    suspend fun Mappings.loadIntermediaryFromMaven(
        mcVersion: String,
        repo: String = "https://maven.fabricmc.net",
        group: String = "net.fabricmc.intermediary",
    ) =
        loadIntermediaryFromTinyJar(URL("$repo/${group.replace('.', '/')}/$mcVersion/intermediary-$mcVersion.jar"))

    suspend fun Mappings.loadIntermediaryFromTinyJar(url: URL) {
        url.forEachZipEntry { path, entry ->
            if (!entry.isDirectory && path.split("/").lastOrNull() == "mappings.tiny") {
                loadIntermediaryFromTinyInputStream(entry.bytes)
            }
        }
    }

    suspend fun Mappings.loadIntermediaryFromTinyFile(url: URL) {
        loadIntermediaryFromTinyInputStream(url.readBytes())
    }

    suspend fun Mappings.loadIntermediaryFromTinyInputStream(bytes: ByteArray) {
        val mappings = net.fabricmc.mappings.MappingsProvider.readTinyMappings(stream, false)
        val isSplit = !mappings.namespaces.contains("official")
        mappings.classEntries.forEach { entry ->
            val intermediary = entry["intermediary"]
            getOrCreateClass(intermediary).apply {
                if (isSplit) {
                    obfName.client = entry["client"]
                    obfName.server = entry["server"]
                } else obfName.merged = entry["official"]
            }
        }
        mappings.methodEntries.forEach { entry ->
            val intermediaryTriple = entry["intermediary"]
            getOrCreateClass(intermediaryTriple.owner).apply {
                getOrCreateMethod(intermediaryTriple.name, intermediaryTriple.desc).apply {
                    if (isSplit) {
                        val clientTriple = entry["client"]
                        val serverTriple = entry["server"]
                        obfName.client = clientTriple?.name
                        obfName.server = serverTriple?.name
                    } else {
                        val officialTriple = entry["official"]
                        obfName.merged = officialTriple?.name
                    }
                }
            }
        }
        mappings.fieldEntries.forEach { entry ->
            val intermediaryTriple = entry["intermediary"]
            getOrCreateClass(intermediaryTriple.owner).apply {
                getOrCreateField(intermediaryTriple.name, intermediaryTriple.desc).apply {
                    if (isSplit) {
                        val clientTriple = entry["client"]
                        val serverTriple = entry["server"]
                        obfName.client = clientTriple?.name
                        obfName.server = serverTriple?.name
                    } else {
                        val officialTriple = entry["official"]
                        obfName.merged = officialTriple?.name
                    }
                }
            }
        }
    }

    suspend fun Mappings.loadNamedFromMaven(
        yarnVersion: String,
        repo: String = "https://maven.fabricmc.net",
        group: String = "net.fabricmc.yarn",
        id: String = "yarn",
        showError: Boolean = true,
    ): MappingsSource {
        return if (id == "yarn" && YarnV2BlackList.blacklist.contains(yarnVersion)) {
            loadNamedFromTinyJar(URL("$repo/${group.replace('.', '/')}/$yarnVersion/$id-$yarnVersion.jar"), showError)
            MappingsSource.YARN_V1
        } else {
            try {
                loadNamedFromTinyJar(URL("$repo/${group.replace('.', '/')}/$yarnVersion/$id-$yarnVersion-v2.jar"), showError)
                MappingsSource.YARN_V2
            } catch (ignored: Throwable) {
                loadNamedFromTinyJar(URL("$repo/${group.replace('.', '/')}/$yarnVersion/$id-$yarnVersion.jar"), showError)
                MappingsSource.YARN_V1
            }
        }
    }

    suspend fun Mappings.loadNamedFromTinyJar(url: URL, showError: Boolean = true) {
        url.forEachZipEntry { path, entry ->
            if (!entry.isDirectory && path.split("/").lastOrNull() == "mappings.tiny") {
                loadNamedFromTinyInputStream(entry.bytes, showError)
            }
        }
    }

    suspend fun Mappings.loadNamedFromTinyFile(url: URL, showError: Boolean = true) {
        loadNamedFromTinyInputStream(url.bytes, showError)
    }

    fun Mappings.loadNamedFromTinyInputStream(bytes: ByteArray, showError: Boolean = true) {
        val mappings = net.fabricmc.mappings.MappingsProvider.readTinyMappings(stream, false)
        mappings.classEntries.forEach { entry ->
            val intermediary = entry["intermediary"]
            val clazz = getClass(intermediary)
            if (clazz == null) {
                if (showError) warn("Class $intermediary does not have intermediary name! Skipping!")
            } else clazz.apply {
                if (mappedName == null)
                    mappedName = entry["named"]
            }
        }
        mappings.methodEntries.forEach { entry ->
            val intermediaryTriple = entry["intermediary"]
            val clazz = getClass(intermediaryTriple.owner)
            if (clazz == null) {
                if (showError) warn("Class ${intermediaryTriple.owner} does not have intermediary name! Skipping!")
            } else clazz.apply {
                val method = getMethod(intermediaryTriple.name, intermediaryTriple.desc)
                if (method == null) {
                    if (showError) warn("Method ${intermediaryTriple.name} in ${intermediaryTriple.owner} does not have intermediary name! Skipping!")
                } else method.apply {
                    val namedTriple = entry["named"]
                    if (mappedName == null)
                        mappedName = namedTriple?.name
                }
            }
        }
        mappings.fieldEntries.forEach { entry ->
            val intermediaryTriple = entry["intermediary"]
            val clazz = getClass(intermediaryTriple.owner)
            if (clazz == null) {
                if (showError) warn("Class ${intermediaryTriple.owner} does not have intermediary name! Skipping!")
            } else clazz.apply {
                val field = getField(intermediaryTriple.name)
                if (field == null) {
                    if (showError) warn("Field ${intermediaryTriple.name} in ${intermediaryTriple.owner} does not have intermediary name! Skipping!")
                } else field.apply {
                    val namedTriple = entry["named"]
                    if (mappedName == null)
                        mappedName = namedTriple?.name
                }
            }
        }
    }

    suspend fun Mappings.loadNamedFromGithubRepo(repo: String, branch: String, showError: Boolean = true, ignoreError: Boolean = false) =
        loadNamedFromEngimaZip(URL("https://github.com/$repo/archive/$branch.zip"), showError, ignoreError)

    suspend fun Mappings.loadNamedFromEngimaZip(url: URL, showError: Boolean = true, ignoreError: Boolean = false) =
        loadNamedFromEngimaStream(url.readZip(), showError, ignoreError)
}
