package me.shedaniel.linkie.utils

class StringPool {
    private val pool = mutableMapOf<String, String>()

    operator fun get(string: String): String = pool.getOrPut(string) { string }
    @JvmName("getNullable")
    operator fun get(string: String?): String? = string?.let(::get)
}
