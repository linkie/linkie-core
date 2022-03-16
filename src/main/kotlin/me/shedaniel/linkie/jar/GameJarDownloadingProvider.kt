package me.shedaniel.linkie.jar

import com.soywiz.klock.measureTime
import com.soywiz.klock.minutes
import com.soywiz.korio.async.async
import com.soywiz.korio.file.VfsFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.shedaniel.linkie.LinkieConfig
import me.shedaniel.linkie.utils.debug
import me.shedaniel.linkie.utils.div
import me.shedaniel.linkie.utils.readText
import me.shedaniel.linkie.utils.valueKeeper
import java.net.URL

class GameJarDownloadingProvider(private val config: LinkieConfig) : GameJarProvider {
    val manifest by valueKeeper(30.minutes) { Json { ignoreUnknownKeys = true }.decodeFromString(VersionManifest.serializer(), URL("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json").readText()) }

    override suspend fun canProvideVersion(version: String): Boolean =
        getMinecraftJarFileStore(version).exists() || manifest.versions.any { it.id == version }

    override suspend fun provide(version: String): GameJarProvider.Result {
        val manifestData = Json { ignoreUnknownKeys = true }
            .decodeFromString(VersionJson.serializer(), URL(manifest.versions.first { it.id == version }.url).readText())
        return withContext(Dispatchers.IO) {
            val minecraftJarFile = async { getMinecraftJarFileAsync(version, manifestData) }
            val librariesFiles = manifestData.libraries
                .filter { it.downloads.artifact.path.endsWith(".jar") }
                .map { async { getLibraryFile(it) } }
            GameJarProvider.Result(minecraftJarFile.await(), librariesFiles.awaitAll())
        }
    }

    suspend fun getMinecraftJarFileAsync(version: String, manifestData: VersionJson): VfsFile {
        val file = getMinecraftJarFileStore(version)
        if (!file.exists()) {
            measureTime {
                file.write(URL(manifestData.downloads.client.url).readBytes())
            }.also { debug("Downloaded minecraft jar in ${it.millisecondsLong}ms") }
            require(file.exists())
        }
        return file
    }

    suspend fun getLibraryFile(library: Library): VfsFile {
        val file = getLibraryFileStore(library)
        if (!file.exists()) {
            delay(10)
            measureTime {
                file.write(URL(library.downloads.artifact.url).readBytes())
            }.also { debug("Downloaded ${library.name} in ${it.millisecondsLong}ms") }
            require(file.exists())
        }
        return file
    }

    suspend fun getMinecraftJarFileStore(version: String): VfsFile =
        (config.cacheDirectory / "minecraft-jars").also { it.mkdirs() } / "$version-client.jar"

    suspend fun getLibraryFileStore(library: Library): VfsFile =
        (config.cacheDirectory / "minecraft-jars/libraries/${library.downloads.artifact.path}").also { it.parent.mkdirs() }

    @Serializable
    data class VersionManifest(
        val versions: List<MinecraftVersonEntry>,
    )

    @Serializable
    data class MinecraftVersonEntry(
        val id: String,
        val url: String,
    )

    @Serializable
    data class VersionJson(
        val downloads: Downloads,
        val libraries: List<Library>,
    )

    @Serializable
    data class Downloads(
        val client: Download,
    )

    @Serializable
    data class Download(
        val url: String,
    )

    @Serializable
    data class Library(
        val name: String,
        val downloads: LibraryDownload,
    )

    @Serializable
    data class LibraryDownload(
        val artifact: LibraryArtifact,
    )

    @Serializable
    data class LibraryArtifact(
        val path: String,
        val url: String,
    )
}
