package me.shedaniel.linkie.utils.io

import com.soywiz.korio.async.use
import com.soywiz.korio.file.std.openAsZip
import com.soywiz.korio.stream.openAsync
import kotlinx.coroutines.runBlocking

actual suspend fun ZipFile.forEachEntry(action: suspend (path: String, entry: ZipEntry) -> Unit) {
    bytes.openAsync().use {
        openAsZip {
            action(it.path, ZipEntry(it.path) { runBlocking { it.readBytes() } })
        }
    }
}
