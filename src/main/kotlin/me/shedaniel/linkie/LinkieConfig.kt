package me.shedaniel.linkie

import com.soywiz.klock.TimeSpan
import com.soywiz.klock.milliseconds
import com.soywiz.korio.file.VfsFile
import com.soywiz.korio.file.std.localCurrentDirVfs
import me.shedaniel.linkie.jar.GameJarDownloadingProvider
import me.shedaniel.linkie.jar.GameJarProvider
import me.shedaniel.linkie.utils.div

data class LinkieConfig(
    val cacheDirectory: VfsFile,
    val maximumLoadedVersions: Int,
    val namespaces: Iterable<Namespace>,
    val reloadCycleDuration: TimeSpan,
    val gameJarProvider: ((LinkieConfig) -> GameJarProvider)?,
    val remapSourceDaemonDuration: TimeSpan?,
) {
    companion object {
        @JvmStatic
        val DEFAULT = LinkieConfig(
            cacheDirectory = localCurrentDirVfs / ".linkie-cache",
            maximumLoadedVersions = 2,
            namespaces = listOf(),
            reloadCycleDuration = 1800000.milliseconds,
            gameJarProvider = ::GameJarDownloadingProvider,
            remapSourceDaemonDuration = null,
        )
    }
}
