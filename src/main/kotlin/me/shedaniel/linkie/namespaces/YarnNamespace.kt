package me.shedaniel.linkie.namespaces

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import me.shedaniel.linkie.Class
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Method
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.utils.ZipFile
import me.shedaniel.linkie.utils.filterNotBlank
import me.shedaniel.linkie.utils.lines
import me.shedaniel.linkie.utils.readBytes
import me.shedaniel.linkie.utils.readText
import me.shedaniel.linkie.utils.toAsyncZip
import me.shedaniel.linkie.utils.warn
import java.io.InputStream
import java.net.URL
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
        YarnV2BlackList.loadData()
        buildSupplier {
            cached()

            buildVersion {
                versions { yarnBuilds.keys }
                uuid { version ->
                    yarnBuilds[version]!!.maven.let { it.substring(it.lastIndexOf(':') + 1) }
                }
                mappings {
                    MappingsContainer(it, name = "Yarn").apply {
                        loadIntermediaryFromMaven(version)
                        val yarnMaven = yarnBuilds[version]!!.maven
                        mappingSource = loadNamedFromMaven(yarnMaven.substring(yarnMaven.lastIndexOf(':') + 1), showError = false)
                    }
                }
            }
        }
    }

    override fun getDefaultLoadedVersions(): List<String> {
        return latestYarnVersion?.let(::listOf) ?: listOf()
    }

    override fun getAllVersions(): Sequence<String> = yarnBuilds.keys.asSequence()

    override fun supportsMixin(): Boolean = true
    override fun supportsAW(): Boolean = true

    override suspend fun reloadData() {
        val buildMap = LinkedHashMap<String, MutableList<YarnBuild>>()
        json.decodeFromString(ListSerializer(YarnBuild.serializer()), URL("https://meta.fabricmc.net/v2/versions/yarn").readText())
            .forEach { buildMap.getOrPut(it.gameVersion, { mutableListOf() }).add(it) }
        buildMap.forEach { (version, builds) -> builds.maxByOrNull { it.build }?.apply { yarnBuilds[version] = this } }
    }

    override fun getDefaultVersion(channel: () -> String): String =
        when (channel()) {
            "patchwork" -> "1.14.4"
            "snapshot" -> yarnBuilds.keys.first()
            else -> latestYarnVersion!!
        }

    override fun getAvailableMappingChannels(): List<String> = listOf(
        "release",
        "snapshot",
        "patchwork",
    )

    suspend fun MappingsContainer.loadIntermediaryFromMaven(
        mcVersion: String,
        repo: String = "https://maven.fabricmc.net",
        group: String = "net.fabricmc.intermediary",
    ) =
        loadIntermediaryFromTinyJar(URL("$repo/${group.replace('.', '/')}/$mcVersion/intermediary-$mcVersion.jar"))

    suspend fun MappingsContainer.loadIntermediaryFromTinyJar(url: URL) {
        url.toAsyncZip().forEachEntry { path, entry ->
            if (!entry.isDirectory && path.split("/").lastOrNull() == "mappings.tiny") {
                loadIntermediaryFromTinyInputStream(entry.bytes.inputStream())
            }
        }
    }

    suspend fun MappingsContainer.loadIntermediaryFromTinyFile(url: URL) {
        loadIntermediaryFromTinyInputStream(url.readBytes().inputStream())
    }

    suspend fun MappingsContainer.loadIntermediaryFromTinyInputStream(stream: InputStream) {
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

    suspend fun MappingsContainer.loadNamedFromMaven(
        yarnVersion: String,
        repo: String = "https://maven.fabricmc.net",
        group: String = "net.fabricmc.yarn",
        id: String = "yarn",
        showError: Boolean = true,
    ): MappingsContainer.MappingSource {
        return if (id == "yarn" && YarnV2BlackList.blacklist.contains(yarnVersion)) {
            loadNamedFromTinyJar(URL("$repo/${group.replace('.', '/')}/$yarnVersion/$id-$yarnVersion.jar"), showError)
            MappingsContainer.MappingSource.YARN_V1
        } else {
            try {
                loadNamedFromTinyJar(URL("$repo/${group.replace('.', '/')}/$yarnVersion/$id-$yarnVersion-v2.jar"), showError)
                MappingsContainer.MappingSource.YARN_V2
            } catch (ignored: Throwable) {
                loadNamedFromTinyJar(URL("$repo/${group.replace('.', '/')}/$yarnVersion/$id-$yarnVersion.jar"), showError)
                MappingsContainer.MappingSource.YARN_V1
            }
        }
    }

    suspend fun MappingsContainer.loadNamedFromTinyJar(url: URL, showError: Boolean = true) {
        url.toAsyncZip().forEachEntry { path, entry ->
            if (!entry.isDirectory && path.split("/").lastOrNull() == "mappings.tiny") {
                loadNamedFromTinyInputStream(entry.bytes.inputStream(), showError)
            }
        }
    }

    suspend fun MappingsContainer.loadNamedFromTinyFile(url: URL, showError: Boolean = true) {
        loadNamedFromTinyInputStream(url.readBytes().inputStream(), showError)
    }

    fun MappingsContainer.loadNamedFromTinyInputStream(stream: InputStream, showError: Boolean = true) {
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

    suspend fun MappingsContainer.loadNamedFromGithubRepo(repo: String, branch: String, showError: Boolean = true, ignoreError: Boolean = false) =
        loadNamedFromEngimaZip(URL("https://github.com/$repo/archive/$branch.zip"), showError, ignoreError)

    suspend fun MappingsContainer.loadNamedFromEngimaZip(url: URL, showError: Boolean = true, ignoreError: Boolean = false) =
        loadNamedFromEngimaStream(url.toAsyncZip(), showError, ignoreError)

    suspend fun MappingsContainer.loadNamedFromEngimaStream(zip: ZipFile, showError: Boolean = true, ignoreError: Boolean = false) {
        zip.forEachEntry { path, entry ->
            if (!entry.isDirectory && path.endsWith(".mapping")) {
                val lines = entry.bytes.lines()
                    .filterNotBlank()
                    .map { EngimaLine(it, it.count { it == '\t' }, MappingsType.getByString(it.replace("\t", "").split(" ")[0])) }
                val levels = mutableListOf<Class?>()
                repeat(lines.filter { it.type != MappingsType.UNKNOWN }.map { it.indent }.maxOrNull()!! + 1) { levels.add(null) }
                lines.forEach { line ->
                    if (line.type == MappingsType.CLASS) {
                        var className = line.split[1]
                        for (i in 0 until line.indent)
                            className = "${levels[i]!!.intermediaryName}\$$className"
                        levels[line.indent] = if (ignoreError) getOrCreateClass(className).apply {
                            mappedName = if (line.split.size >= 3) line.split[2] else null
                        } else getClass(className)?.apply {
                            mappedName = if (line.split.size >= 3) line.split[2] else null
                        }
                        if (levels[line.indent] == null && showError) warn("Class $className does not have intermediary name! Skipping!")
                    } else if (line.type == MappingsType.METHOD) {
                        if (levels[line.indent - 1] == null) {
                            if (showError) warn("Class of ${line.split[1]} does not have intermediary name! Skipping!")
                        } else {
                            levels[line.indent - 1]!!.apply {
                                val method = when {
                                    line.split[1] == "<init>" -> Method("<init>", line.split.last()).also { methods.add(it) }
                                    ignoreError -> getOrCreateMethod(line.split[1], line.split.last())
                                    else -> getMethod(line.split[1], line.split.last())
                                }
                                if (method == null && showError) {
                                    warn("Method ${line.split[1]} in ${levels[line.indent - 1]!!.intermediaryName} does not have intermediary name! Skipping!")
                                }
                                if (line.split.size == 4) {
                                    method?.apply {
                                        mappedName = line.split[2]
                                    }
                                }
                            }
                        }
                    } else if (line.type == MappingsType.FIELD) {
                        if (levels[line.indent - 1] == null) {
                            if (showError) warn("Class of ${line.split[1]} does not have intermediary name! Skipping!")
                        } else {
                            levels[line.indent - 1]!!.apply {
                                val field = when {
                                    ignoreError -> getOrCreateField(line.split[1], line.split.last())
                                    else -> getField(line.split[1])
                                }
                                if (field == null && showError) {
                                    warn("Field ${line.split[1]} in ${levels[line.indent - 1]!!.intermediaryName} does not have intermediary name! Skipping!")
                                }
                                if (line.split.size == 4) {
                                    field?.apply {
                                        mappedName = line.split[2]
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private data class EngimaLine(
        val text: String,
        val indent: Int,
        val type: MappingsType,
    ) {
        val split: List<String> by lazy { text.trimStart('\t').split(" ") }
    }

    private enum class MappingsType {
        CLASS,
        FIELD,
        METHOD,
        UNKNOWN;

        companion object {
            fun getByString(string: String): MappingsType =
                values().firstOrNull { it.name.equals(string, true) } ?: UNKNOWN
        }
    }

    private object YarnV2BlackList {
        val blacklist: MutableList<String> = mutableListOf()
        val blacklistString = """
        1.14 Pre-Release 1+build.10
        1.14 Pre-Release 1+build.11
        1.14 Pre-Release 1+build.12
        1.14 Pre-Release 1+build.2
        1.14 Pre-Release 1+build.3
        1.14 Pre-Release 1+build.4
        1.14 Pre-Release 1+build.5
        1.14 Pre-Release 1+build.6
        1.14 Pre-Release 1+build.7
        1.14 Pre-Release 1+build.8
        1.14 Pre-Release 1+build.9
        1.14 Pre-Release 2+build.1
        1.14 Pre-Release 2+build.10
        1.14 Pre-Release 2+build.11
        1.14 Pre-Release 2+build.12
        1.14 Pre-Release 2+build.13
        1.14 Pre-Release 2+build.14
        1.14 Pre-Release 2+build.2
        1.14 Pre-Release 2+build.3
        1.14 Pre-Release 2+build.4
        1.14 Pre-Release 2+build.5
        1.14 Pre-Release 2+build.6
        1.14 Pre-Release 2+build.7
        1.14 Pre-Release 2+build.8
        1.14 Pre-Release 2+build.9
        1.14 Pre-Release 3+build.1
        1.14 Pre-Release 3+build.2
        1.14 Pre-Release 3+build.3
        1.14 Pre-Release 3+build.4
        1.14 Pre-Release 4+build.1
        1.14 Pre-Release 4+build.2
        1.14 Pre-Release 4+build.3
        1.14 Pre-Release 4+build.4
        1.14 Pre-Release 4+build.5
        1.14 Pre-Release 4+build.6
        1.14 Pre-Release 4+build.7
        1.14 Pre-Release 5+build.1
        1.14 Pre-Release 5+build.2
        1.14 Pre-Release 5+build.3
        1.14 Pre-Release 5+build.4
        1.14 Pre-Release 5+build.5
        1.14 Pre-Release 5+build.6
        1.14 Pre-Release 5+build.7
        1.14 Pre-Release 5+build.8
        1.14+build.1
        1.14+build.10
        1.14+build.11
        1.14+build.12
        1.14+build.13
        1.14+build.14
        1.14+build.15
        1.14+build.16
        1.14+build.17
        1.14+build.18
        1.14+build.19
        1.14+build.2
        1.14+build.20
        1.14+build.21
        1.14+build.3
        1.14+build.4
        1.14+build.5
        1.14+build.6
        1.14+build.7
        1.14+build.8
        1.14+build.9
        1.14.1 Pre-Release 1+build.1
        1.14.1 Pre-Release 1+build.2
        1.14.1 Pre-Release 1+build.3
        1.14.1 Pre-Release 1+build.4
        1.14.1 Pre-Release 1+build.5
        1.14.1 Pre-Release 1+build.6
        1.14.1 Pre-Release 2+build.1
        1.14.1 Pre-Release 2+build.2
        1.14.1 Pre-Release 2+build.3
        1.14.1 Pre-Release 2+build.4
        1.14.1 Pre-Release 2+build.5
        1.14.1 Pre-Release 2+build.6
        1.14.1+build.1
        1.14.1+build.10
        1.14.1+build.2
        1.14.1+build.3
        1.14.1+build.4
        1.14.1+build.5
        1.14.1+build.6
        1.14.1+build.7
        1.14.1+build.8
        1.14.1+build.9
        1.14.2 Pre-Release 1+build.1
        1.14.2 Pre-Release 2+build.1
        1.14.2 Pre-Release 2+build.2
        1.14.2 Pre-Release 2+build.3
        1.14.2 Pre-Release 2+build.4
        1.14.2 Pre-Release 2+build.5
        1.14.2 Pre-Release 2+build.6
        1.14.2 Pre-Release 3+build.2
        1.14.2 Pre-Release 3+build.3
        1.14.2 Pre-Release 4+build.1
        1.14.2+build.1
        1.14.2+build.2
        1.14.2+build.3
        1.14.2+build.4
        1.14.2+build.5
        1.14.2+build.6
        1.14.2+build.7
        1.14.3+build.1
        1.14.3+build.10
        1.14.3+build.11
        1.14.3+build.12
        1.14.3+build.13
        1.14.3+build.2
        1.14.3+build.3
        1.14.3+build.4
        1.14.3+build.5
        1.14.3+build.6
        1.14.3+build.7
        1.14.3+build.8
        1.14.3-pre1+build.1
        1.14.3-pre1+build.2
        1.14.3-pre1+build.3
        1.14.3-pre1+build.4
        1.14.3-pre1+build.5
        1.14.3-pre1+build.6
        1.14.3-pre2+build.1
        1.14.3-pre2+build.10
        1.14.3-pre2+build.11
        1.14.3-pre2+build.12
        1.14.3-pre2+build.13
        1.14.3-pre2+build.14
        1.14.3-pre2+build.15
        1.14.3-pre2+build.16
        1.14.3-pre2+build.17
        1.14.3-pre2+build.18
        1.14.3-pre2+build.2
        1.14.3-pre2+build.3
        1.14.3-pre2+build.4
        1.14.3-pre2+build.5
        1.14.3-pre2+build.6
        1.14.3-pre2+build.7
        1.14.3-pre2+build.8
        1.14.3-pre2+build.9
        1.14.3-pre3+build.1
        1.14.3-pre4+build.1
        1.14.3-pre4+build.2
        1.14.3-pre4+build.3
        18w49a.1
        18w49a.10
        18w49a.11
        18w49a.12
        18w49a.13
        18w49a.14
        18w49a.15
        18w49a.16
        18w49a.17
        18w49a.18
        18w49a.2
        18w49a.20
        18w49a.21
        18w49a.22
        18w49a.3
        18w49a.4
        18w49a.5
        18w49a.6
        18w49a.7
        18w49a.8
        18w49a.9
        18w50a.1
        18w50a.10
        18w50a.100
        18w50a.11
        18w50a.12
        18w50a.13
        18w50a.14
        18w50a.15
        18w50a.16
        18w50a.17
        18w50a.18
        18w50a.19
        18w50a.2
        18w50a.20
        18w50a.21
        18w50a.22
        18w50a.23
        18w50a.24
        18w50a.25
        18w50a.26
        18w50a.27
        18w50a.28
        18w50a.29
        18w50a.3
        18w50a.30
        18w50a.31
        18w50a.32
        18w50a.33
        18w50a.34
        18w50a.35
        18w50a.36
        18w50a.37
        18w50a.38
    """.trimIndent()

        fun loadData() {
            blacklistString.split('\n').forEach { blacklist.add(it) }
        }
    }
}
