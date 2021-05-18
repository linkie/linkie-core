package me.shedaniel.linkie.utils.io

import me.shedaniel.linkie.utils.URL
import me.shedaniel.linkie.utils.readBytes
import kotlin.jvm.JvmInline

@JvmInline
value class ZipFile(val bytes: ByteArray)

expect suspend fun ZipFile.forEachEntry(action: suspend (path: String, entry: ZipEntry) -> Unit)

data class ZipEntry(
    val path: String,
    val bytesProvider: () -> ByteArray,
)

inline val ZipEntry.isDirectory: Boolean
    get() = path.endsWith("/")
inline val ZipEntry.bytes: ByteArray
    get() = bytesProvider()

suspend fun URL.readZip(): ZipFile = ZipFile(readBytes())
suspend fun URL.forEachZipEntry(action: suspend (path: String, entry: ZipEntry) -> Unit) = readZip().forEachEntry(action)
