package me.shedaniel.linkie

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import me.shedaniel.linkie.utils.tryToVersion
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

abstract class Namespace(val id: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return id == (other as Namespace).id
    }

    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id

    private val cachedMappings = CopyOnWriteArrayList<MappingsContainer>()
    private val mappingsSuppliers = mutableListOf<MappingsSupplier>()
    val json = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true, isLenient = true))
    var reloading = false

    suspend fun reset() {
        reloading = true
        cachedMappings.clear()
        try {
            reloadData()
            val jobs = getDefaultLoadedVersions().map {
                GlobalScope.launch(Dispatchers.IO) {
                    createAndAdd(it)
                }
            }
            jobs.forEach { it.join() }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        reloading = false
    }

    abstract fun getDefaultLoadedVersions(): List<String>
    abstract fun getAllVersions(): List<String>
    abstract fun reloadData()
    open fun getMaximumCachedVersion(): Int = 3
    abstract fun getDefaultVersion(command: String?, channelId: Long?): String
    fun getAllSortedVersions(): List<String> =
            getAllVersions().sortedWith(Comparator.nullsFirst(compareBy { it.tryToVersion() })).asReversed()

    protected fun registerSupplier(mappingsSupplier: MappingsSupplier) {
        mappingsSuppliers.add(loggedSupplier(mappingsSupplier))
    }

    operator fun get(version: String): MappingsContainer? = cachedMappings.firstOrNull { it.version == version.toLowerCase(Locale.ROOT) }

    fun create(version: String): MappingsContainer? {
        return mappingsSuppliers.firstOrNull { it.isApplicable(version) }?.applyVersion(version)
    }

    fun createAndAdd(version: String): MappingsContainer? =
            create(version)?.also { cachedMappings.add(it).limitCachedData() }

    private fun <T> T.limitCachedData(): T {
        if (cachedMappings.size > getMaximumCachedVersion()) {
            val defaultLoadedVersions = getDefaultLoadedVersions()
            cachedMappings.firstOrNull { it.version !in defaultLoadedVersions }?.let { cachedMappings.remove(it) }
        }
        System.gc()
        return this
    }

    fun getOrCreate(version: String): MappingsContainer? =
            get(version) ?: createAndAdd(version)

    fun getProvider(version: String): MappingsProvider {
        val container = get(version)
        if (container != null) {
            return MappingsProvider.of(version, container)
        }
        val entry = mappingsSuppliers.firstOrNull { it.isApplicable(version) } ?: return MappingsProvider.empty()
        return MappingsProvider.supply(version, false) { entry.applyVersion(version).also { cachedMappings.add(it).limitCachedData() } }
    }

    fun getDefaultProvider(command: String?, channelId: Long?): MappingsProvider {
        val version = getDefaultVersion(command, channelId)
        return getProvider(version)
    }

    open fun supportsMixin(): Boolean = false
    open fun supportsAT(): Boolean = false
    open fun supportsAW(): Boolean = false
    open fun supportsFieldDescription(): Boolean = true
}