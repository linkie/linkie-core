package me.shedaniel.linkie

import com.soywiz.korio.async.runBlockingNoJs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.shedaniel.linkie.namespace.MappingsSupplierBuilder
import me.shedaniel.linkie.namespace.NamespaceMetadata
import me.shedaniel.linkie.utils.tryToVersion

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

abstract class Namespace(val id: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false
        return id == (other as Namespace).id
    }

    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id

    open val dependencies: Set<Namespace> = setOf()
    internal val mappingsSuppliers = mutableListOf<MappingsSupplier>()

    val reloading: Boolean
        get() = _reloading || dependencies.any { it.reloading }
    private var _reloading = false

    suspend fun reset() = coroutineScope {
        _reloading = true
        runCatching {
            reloadData()
            val jobs = getDefaultLoadedVersions().map {
                launch {
                    val provider = this@Namespace[it]
                    if (provider.isEmpty().not() && provider.cached != true)
                        provider.get().also {
                            Namespaces.cachedMappings.remove(it)
                        }
                }
            }
            jobs.forEach { it.join() }
        }.exceptionOrNull()?.printStackTrace()
        _reloading = false
    }

    abstract fun getDefaultLoadedVersions(): List<String>
    abstract fun getAllVersions(): Sequence<String>
    abstract suspend fun reloadData()
    abstract val defaultVersion: String

    abstract val metadata: NamespaceMetadata
}

internal fun Namespace.registerSupplier(mappingsSupplier: MappingsSupplier) {
    mappingsSuppliers.add(namespacedSupplier(loggingSupplier(mappingsSupplier)))
}

fun Namespace.buildSupplier(
    cached: Boolean = false,
    builder: MappingsSupplierBuilder.() -> Unit,
) {
    registerSupplier(MappingsSupplierBuilder(cached).also(builder).toMappingsSupplier(this))
}

operator fun Namespace.get(version: String): MappingsProvider {
    return runBlockingNoJs {
        val entry = mappingsSuppliers.firstOrNull { it.isApplicable(version) } ?: return@runBlockingNoJs MappingsProvider.empty(this@get)
        return@runBlockingNoJs MappingsProvider.supply(this@get, version, entry.isCached(version)) { entry.applyVersion(version) }
    }
}

val Namespace.default: MappingsProvider
    get() = this[defaultVersion]

val Namespace.sortedVersions: List<String>
    get() = getAllVersions().sortedWith(nullsFirst<String>(compareBy { it.tryToVersion() }).reversed()).toList()
