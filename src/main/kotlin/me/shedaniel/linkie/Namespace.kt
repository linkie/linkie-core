package me.shedaniel.linkie

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.shedaniel.linkie.utils.tryToVersion
import java.util.*

abstract class Namespace(val id: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return id == (other as Namespace).id
    }

    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id

    open fun getDependencies(): Set<Namespace> = setOf()

    private val mappingsSuppliers = mutableListOf<MappingsSupplier>()
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    val reloading: Boolean
        get() = selfReloading || getDependencies().any { it.reloading }
    private var selfReloading = false

    suspend fun reset() {
        selfReloading = true
        try {
            reloadData()
            val jobs = getDefaultLoadedVersions().map {
                GlobalScope.launch(Dispatchers.IO) {
                    val provider = getProvider(it)
                    if (provider.isEmpty().not() && provider.cached != true)
                        provider.mappingsContainer!!.invoke().also {
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

    open fun getAvailableMappingChannels(): List<String> = listOf("release")
    open fun getDefaultMappingChannel(): String = getAvailableMappingChannels().first()

    abstract fun getDefaultLoadedVersions(): List<String>
    abstract fun getAllVersions(): List<String>
    abstract fun reloadData()
    abstract fun getDefaultVersion(channel: () -> String = this::getDefaultMappingChannel): String
    fun getAllSortedVersions(): List<String> =
        getAllVersions().sortedWith(Comparator.nullsFirst(compareBy { it.tryToVersion() })).asReversed()

    protected fun registerSupplier(mappingsSupplier: MappingsSupplier) {
        mappingsSuppliers.add(namespacedSupplier(loggedSupplier(mappingsSupplier)))
    }

    operator fun get(version: String): MappingsContainer? = Namespaces.cachedMappings.firstOrNull { it.namespace == id && it.version == version.toLowerCase(Locale.ROOT) }

    fun create(version: String): MappingsContainer? {
        return mappingsSuppliers.firstOrNull { it.isApplicable(version) }?.applyVersion(version)
    }

    fun createAndAdd(version: String): MappingsContainer? =
        create(version)?.also { Namespaces.addMappingsContainer(it) }

    fun getOrCreate(version: String): MappingsContainer? =
        get(version) ?: createAndAdd(version)

    fun getProvider(version: String): MappingsProvider {
        val container = get(version)
        if (container != null) {
            return MappingsProvider.of(this, version, container)
        }
        val entry = mappingsSuppliers.firstOrNull { it.isApplicable(version) } ?: return MappingsProvider.empty(this)
        return MappingsProvider.supply(this, version, entry.isCached(version)) { entry.applyVersion(version).also { Namespaces.addMappingsContainer(it) } }
    }

    fun getDefaultProvider(channel: () -> String = this::getDefaultMappingChannel): MappingsProvider {
        val version = getDefaultVersion()
        return getProvider(version)
    }

    open fun supportsMixin(): Boolean = false
    open fun supportsAT(): Boolean = false
    open fun supportsAW(): Boolean = false
    open fun supportsFieldDescription(): Boolean = true
}