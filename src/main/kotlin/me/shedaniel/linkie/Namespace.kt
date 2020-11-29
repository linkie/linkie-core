package me.shedaniel.linkie

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.shedaniel.linkie.namespaces.MappingsVersion
import me.shedaniel.linkie.namespaces.MappingsContainerBuilder
import me.shedaniel.linkie.namespaces.MappingsVersionBuilder
import me.shedaniel.linkie.namespaces.ofVersion
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

    protected inline fun buildSupplier(builder: MappingsSupplierBuilder.() -> Unit) {
        registerSupplier(MappingsSupplierBuilder().also(builder).toMappingsSupplier())
    }

    protected inner class MappingsSupplierBuilder(
        var cached: Boolean = false,
        var versions: MutableMap<MappingsVersionBuilder, () -> Iterable<String>> = mutableMapOf(),
    ) {
        fun cached() {
            cached = true
        }

        fun version(version: String, uuid: String, mappings: MappingsContainer) {
            version(version, uuid) { mappings }
        }

        fun version(version: String, uuid: (String) -> String, mappings: MappingsContainer) {
            version(version, uuid) { mappings }
        }

        fun version(version: String, uuid: String, mappings: MappingsContainerBuilder) {
            val list = listOf(version)
            versions(list, uuid, mappings)
        }

        fun version(version: String, uuid: (String) -> String, mappings: MappingsContainerBuilder) {
            val list = listOf(version)
            versions(list, uuid, mappings)
        }

        fun version(version: String, mappings: MappingsVersionBuilder) {
            val list = listOf(version)
            versions(list, mappings)
        }

        fun buildVersion(version: String, spec: VersionSpec.(String) -> Unit) {
            buildVersions {
                versions(version)
                spec(version)
            }
        }

        fun buildVersion(spec: VersionSpec.() -> Unit) {
            buildVersions(spec)
        }

        fun versions(vararg versions: String, uuid: String, mappings: MappingsContainer) {
            val list = versions.toList()
            versions(list, uuid, mappings)
        }

        fun versions(vararg versions: String, uuid: (String) -> String, mappings: MappingsContainer) {
            val list = versions.toList()
            versions(list, uuid, mappings)
        }

        fun versions(vararg versions: String, uuid: String, mappings: MappingsContainerBuilder) {
            val list = versions.toList()
            versions(list, uuid, mappings)
        }

        fun versions(vararg versions: String, uuid: (String) -> String, mappings: MappingsContainerBuilder) {
            val list = versions.toList()
            versions(list, uuid, mappings)
        }

        fun buildVersions(vararg versions: String, spec: VersionSpec.() -> Unit) {
            buildVersions {
                versions(*versions)
                spec()
            }
        }

        fun versions(vararg versions: String, mappings: MappingsVersionBuilder) {
            val list = versions.toList()
            versions(list, mappings)
        }

        fun versions(versions: Iterable<String>, uuid: String, mappings: MappingsContainer) {
            versions(versions, uuid) { mappings }
        }

        fun versions(versions: Iterable<String>, uuid: (String) -> String, mappings: MappingsContainer) {
            versions(versions, uuid) { mappings }
        }

        fun versions(versions: Iterable<String>, uuid: String, mappings: MappingsContainerBuilder) {
            versions(versions) { ofVersion(uuid, mappings) }
        }

        fun versions(versions: Iterable<String>, uuid: (String) -> String, mappings: MappingsContainerBuilder) {
            versions(versions) { ofVersion(uuid(it), mappings) }
        }

        fun versions(versions: Iterable<String>, mappings: MappingsVersion) {
            versions(versions) { mappings }
        }

        fun versions(versions: Iterable<String>, mappings: MappingsVersionBuilder) {
            versions({ versions }, mappings)
        }

        fun buildVersions(versions: Iterable<String>, spec: VersionSpec.() -> Unit) {
            buildVersions {
                versions(versions)
                spec()
            }
        }

        fun versions(versions: () -> Iterable<String>, mappings: MappingsVersionBuilder) {
            this.versions[mappings] = versions
        }

        fun buildVersions(versions: () -> Iterable<String>, spec: VersionSpec.() -> Unit) {
            buildVersions {
                versions(versions)
                spec()
            }
        }

        fun buildVersions(spec: VersionSpec.() -> Unit) {
            val versionSpec = VersionSpec()
            val mappings = versionSpec.accept(spec)
            versions(versionSpec.versions!!, mappings)
        }

        fun toMappingsSupplier(): MappingsSupplier {
            val suppliers = mutableListOf<MappingsSupplier>()
            this.versions.forEach { (builder, versions) ->
                var supplier = multipleSupplier(versions) {
                    builder.build(it).container.build(it)
                }
                if (cached) {
                    supplier = cachedSupplier({
                        builder.build(it).uuid
                    }, supplier)
                }
                suppliers.add(supplier)
            }
            return when (suppliers.size) {
                0 -> EmptyMappingsSupplier
                1 -> suppliers.first()
                else -> ConcatMappingsSupplier(suppliers)
            }
        }
    }

    inner class VersionSpec(
        private var uuidGetter: (String) -> String = { it },
        private var mappingsGetter: MappingsContainerBuilder? = null,
        internal var versions: (() -> Iterable<String>)? = null,
    ) {
        fun uuid(uuid: String) {
            uuid { uuid }
        }

        fun uuid(uuid: (String) -> String) {
            uuidGetter = uuid
        }

        fun mappings(mappings: MappingsContainer) {
            mappings { mappings }
        }

        fun mappings(mappings: MappingsContainerBuilder) {
            mappingsGetter = mappings
        }

        inline fun buildMappings(
            version: String,
            name: String,
            fillFieldDesc: Boolean = true,
            fillMethodDesc: Boolean = true,
            expendIntermediaryToMapped: Boolean = false,
            crossinline builder: me.shedaniel.linkie.MappingsContainerBuilder.() -> Unit,
        ) {
            mappings(me.shedaniel.linkie.buildMappings(version, name, fillFieldDesc, fillMethodDesc, expendIntermediaryToMapped, builder))
        }

        fun version(version: String) {
            val list = listOf(version)
            versions { list }
        }

        fun versions(vararg versions: String) {
            val list = versions.toList()
            versions { list }
        }

        fun versions(versions: Iterable<String>) {
            versions { versions }
        }

        fun versions(versions: () -> Iterable<String>) {
            this.versions = versions
        }

        fun accept(spec: VersionSpec.() -> Unit): MappingsVersionBuilder {
            also(spec)
            mappingsGetter!!

            return MappingsVersionBuilder {
                ofVersion(uuidGetter(it), mappingsGetter!!)
            }
        }
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