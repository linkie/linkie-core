package me.shedaniel.linkie.utils

import com.soywiz.korio.net.http.HttpClient
import com.soywiz.korio.net.http.createHttpClient
import com.soywiz.korio.stream.AsyncInputStream
import com.soywiz.korio.stream.AsyncStream
import com.soywiz.korio.stream.openAsync
import com.soywiz.korio.stream.readAvailable
import okio.BufferedSource
import java.util.zip.ZipInputStream

val httpClient = createHttpClient()
val defaultRequestConfig = HttpClient.RequestConfig(
    throwErrors = true
)

suspend fun AsyncInputStream.readText(): String = readBytes().decodeToString()
suspend fun AsyncInputStream.lines(): Sequence<String> = readText().lineSequence()
suspend fun AsyncInputStream.readBytes() = readAvailable()
suspend fun URL.readBytes() = httpClient.readBytes(this.toString(), defaultRequestConfig)
suspend fun URL.readText() = readBytes().decodeToString()
suspend fun URL.readLines() = readText().lineSequence()
suspend fun ByteArray.lines() = decodeToString().lineSequence()
suspend fun URL.toAsyncZip(): ZipFile = toAsyncStream().zip()
suspend fun URL.toAsyncStream(): AsyncStream = readBytes().openAsync()
suspend fun AsyncStream.zip(): ZipFile = ZipFile(readBytes())

inline fun BufferedSource.forEachLine(consumer: (String) -> Unit) = readUtf8().lineSequence().forEach(consumer)
fun BufferedSource.lines() = readUtf8().lines()

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