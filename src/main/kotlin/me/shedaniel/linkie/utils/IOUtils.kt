package me.shedaniel.linkie.utils

import java.util.zip.ZipInputStream

suspend fun URL.readBytes() = java.net.URL(toString()).readBytes()
suspend fun URL.readText() = readBytes().decodeToString()
suspend fun URL.readLines() = readText().lineSequence()
suspend fun ByteArray.lines() = decodeToString().lineSequence()
suspend fun URL.toAsyncZip(): ZipFile = ZipFile(readBytes())

fun getMillis(): Long = System.currentTimeMillis()

@JvmInline
value class URL(val path: String) {
    override fun toString(): String = path
}

class ZipFile(val bytes: ByteArray) {
    suspend fun forEachEntry(action: suspend (path: String, entry: ZipEntry) -> Unit) {
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
}

data class ZipEntry(
    val path: String,
    private val bytesProvider: () -> ByteArray,
) {
    val isDirectory: Boolean
        get() = path.endsWith("/")
    val bytes: ByteArray
        get() = bytesProvider()
}