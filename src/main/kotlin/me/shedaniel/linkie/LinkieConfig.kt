package me.shedaniel.linkie

import java.io.File
import java.time.Duration

data class LinkieConfig(
    val cacheDirectory: File,
    val maximumLoadedVersions: Int,
    val namespaces: Iterable<Namespace>,
    val reloadCycleDuration: Duration,
) {
    companion object {
        @JvmStatic
        val DEFAULT = LinkieConfig(
            cacheDirectory = File(System.getProperty("user.dir"), ".linkie-cache"),
            maximumLoadedVersions = 2,
            namespaces = listOf(),
            reloadCycleDuration = Duration.ofMillis(1800000)
        )
    }
}
