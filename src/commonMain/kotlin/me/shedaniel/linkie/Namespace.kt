package me.shedaniel.linkie

import com.soywiz.korio.async.runBlockingNoJs
import kotlinx.coroutines.GlobalScope
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
        get() = selfReloading || dependencies.any { it.reloading }
    private var selfReloading = false

    suspend fun reset() {
        selfReloading = true
        try {
            reloadData()
            val jobs = getDefaultLoadedVersions().map {
                GlobalScope.launch {
                    val provider = this@Namespace[it]
                    if (provider.isEmpty().not() && provider.cached != true)
                        provider.get().also {
                            Namespaces.cachedMappings.remove(it)
                        }
                }
            }
            jobs.forEach { it.join() }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        selfReloading = false
    }

    abstract fun getDefaultLoadedVersions(): List<String>
    abstract fun getAllVersions(): Sequence<String>
    abstract suspend fun reloadData()
    abstract val defaultVersion: String
    fun getAllSortedVersions(): List<String> =
        getAllVersions().sortedWith(nullsFirst(compareBy { it.tryToVersion() })).toList().asReversed()

    fun registerSupplier(mappingsSupplier: MappingsSupplier) {
        mappingsSuppliers.add(namespacedSupplier(loggingSupplier(mappingsSupplier)))
    }

    @Deprecated("Use the operator instead.", replaceWith = ReplaceWith("this[version]", imports = ["me.shedaniel.linkie.get"]))
    fun getProvider(version: String): MappingsProvider = this[version]

    @Deprecated("Use getDefault instead.", replaceWith = ReplaceWith("this.getDefault()", imports = ["me.shedaniel.linkie.getDefault"]))
    fun getDefaultProvider(channel: (() -> String)? = null): MappingsProvider = getDefault()

    abstract val metadata: NamespaceMetadata
}

fun Namespace.buildSupplier(builder: MappingsSupplierBuilder.() -> Unit) {
    registerSupplier(MappingsSupplierBuilder().also(builder).toMappingsSupplier())
}

operator fun Namespace.get(version: String): MappingsProvider {
    return runBlockingNoJs {
        val entry = mappingsSuppliers.firstOrNull { it.isApplicable(version) } ?: return@runBlockingNoJs MappingsProvider.empty(this@get)
        return@runBlockingNoJs MappingsProvider.supply(this@get, version, entry.isCached(version)) { entry.applyVersion(version) }
    }
}

fun Namespace.getDefault(): MappingsProvider = this[defaultVersion]
