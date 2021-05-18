package me.shedaniel.linkie.utils

import io.ktor.client.*
import io.ktor.client.engine.curl.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlin.system.getTimeMillis

val client = HttpClient(Curl)

actual suspend fun URL.readBytes(): ByteArray =
    client.get<HttpResponse>(url).readBytes()

actual fun getMillis(): Long = getTimeMillis()
actual fun gc() = Unit
