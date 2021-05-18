package me.shedaniel.linkie.utils.io

import java.util.zip.ZipInputStream

actual suspend fun ZipFile.forEachEntry(action: suspend (path: String, entry: ZipEntry) -> Unit) {
    ZipInputStream(bytes.inputStream()).use { stream ->
        while (true) {
            val entry = stream.nextEntry ?: return@use
            var bytes: ByteArray? = null
            action(entry.name, ZipEntry(entry.name) {
                bytes ?: stream.readBytes().apply { bytes = this }
            })
        }
    }
}
