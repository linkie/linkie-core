package me.shedaniel.linkie.utils

import com.soywiz.korio.compression.zip.ZipFile
import com.soywiz.korio.net.URL
import com.soywiz.korio.net.http.Http
import com.soywiz.korio.net.http.HttpClient
import com.soywiz.korio.net.http.createHttpClient
import com.soywiz.korio.stream.AsyncInputStream
import com.soywiz.korio.stream.AsyncStream
import com.soywiz.korio.stream.openAsync
import com.soywiz.korio.stream.readAvailable
import okio.BufferedSource

val httpClient = createHttpClient()
val defaultRequestConfig = HttpClient.RequestConfig(
    followRedirects = false,
    throwErrors = true
)

suspend fun AsyncInputStream.readText(): String = readBytes().decodeToString()
suspend fun AsyncInputStream.lines(): Sequence<String> = readText().lineSequence()
suspend fun AsyncInputStream.readBytes() = readAvailable()
suspend fun URL.readBytes() = httpClient.requestAsBytes(Http.Method.GET, fullUrl, config = defaultRequestConfig).content
suspend fun URL.readText() = readBytes().decodeToString()
suspend fun URL.readLines() = readText().lineSequence()
suspend fun URL.toAsyncZip(): ZipFile = toAsyncStream().zip()
suspend fun URL.toAsyncStream(): AsyncStream = readBytes().openAsync()
suspend fun AsyncStream.zip(): ZipFile = ZipFile(this)

inline fun BufferedSource.forEachLine(consumer: (String) -> Unit) = readUtf8().lineSequence().forEach(consumer)
fun BufferedSource.lines() = readUtf8().lines()

fun getMillis(): Long = System.currentTimeMillis()
