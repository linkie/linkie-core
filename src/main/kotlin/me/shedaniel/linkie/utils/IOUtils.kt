package me.shedaniel.linkie.utils

import com.soywiz.korio.net.http.HttpClient
import com.soywiz.korio.net.http.createHttpClient
import com.soywiz.korio.stream.AsyncInputStream
import com.soywiz.korio.stream.AsyncStream
import com.soywiz.korio.stream.openAsync
import com.soywiz.korio.stream.readAvailable
import java.util.zip.ZipInputStream

val httpClient = createHttpClient()
val defaultRequestConfig = HttpClient.RequestConfig(
    followRedirects = false,
    throwErrors = true
)

data class URL(val url: String)

suspend fun AsyncInputStream.readText(): String = readBytes().decodeToString()
suspend fun AsyncInputStream.lines(): Sequence<String> = readText().lineSequence()
suspend fun AsyncInputStream.readBytes() = readAvailable()
suspend fun URL.readText() = readBytes().decodeToString()
suspend fun URL.readLines() = readBytes().lines()
suspend fun ByteArray.lines() = decodeToString().lineSequence()
suspend fun URL.toAsyncZip(): ZipFile = toAsyncStream().zip()
suspend fun URL.forEachZipEntry(action: suspend (path: String, entry: ZipEntry) -> Unit) = toAsyncZip().forEachEntry(action)
suspend fun URL.readBytes(): ByteArray = runCatching { java.net.URL(url).readBytes() }.getOrThrow()
suspend fun URL.toAsyncStream(): AsyncStream = readBytes().openAsync()
suspend fun AsyncStream.zip(): ZipFile = ZipFile(readBytes())

fun getMillis(): Long = System.currentTimeMillis()

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
    val bytesProvider: () -> ByteArray,
)

inline val ZipEntry.isDirectory: Boolean
    get() = path.endsWith("/")
inline val ZipEntry.bytes: ByteArray
    get() = bytesProvider()
