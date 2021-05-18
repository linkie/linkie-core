package me.shedaniel.linkie

import com.soywiz.klock.TimeSpan
import com.soywiz.klock.milliseconds
import com.soywiz.korio.file.VfsFile
import com.soywiz.korio.file.std.localCurrentDirVfs
import me.shedaniel.linkie.utils.div

data class LinkieConfig(
    val cacheDirectory: VfsFile,
    val maximumLoadedVersions: Int,
    val namespaces: Iterable<Namespace>,
    val reloadCycleDuration: TimeSpan,
) {
    companion object {
        val DEFAULT = LinkieConfig(
            cacheDirectory = (localCurrentDirVfs / ".linkie-cache").jail(),
            maximumLoadedVersions = 2,
            namespaces = listOf(),
            reloadCycleDuration = 1800000.milliseconds
        )
    }
}
