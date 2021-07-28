package me.shedaniel.linkie

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.shedaniel.linkie.namespaces.MappingsContainerBuilder
import me.shedaniel.linkie.namespaces.MappingsVersion
import me.shedaniel.linkie.namespaces.MappingsVersionBuilder
import me.shedaniel.linkie.namespaces.ofVersion
import me.shedaniel.linkie.namespaces.toBuilder
import me.shedaniel.linkie.utils.tryToVersion

abstract class Namespace(val id: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false
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
                GlobalScope.launch {
                    val provider = getProvider(it)
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

    open fun getAvailableMappingChannels(): List<String> = listOf("release")
    open fun getDefaultMappingChannel(): String = getAvailableMappingChannels().first()

    abstract fun getDefaultLoadedVersions(): List<String>
    abstract fun getAllVersions(): Sequence<String>
    abstract suspend fun reloadData()
    open fun getDefaultVersion(channel: () -> String = this::getDefaultMappingChannel): String =
        getAllVersions().maxWithOrNull(nullsFirst(compareBy(String::tryToVersion)))!!

    fun getAllSortedVersions(): List<String> =
        getAllVersions().sortedWith(nullsFirst(compareBy { it.tryToVersion() })).toList().asReversed()

    protected fun registerSupplier(mappingsSupplier: MappingsSupplier) {
        mappingsSuppliers.add(namespacedSupplier(loggingSupplier(mappingsSupplier)))
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

        fun version(version: String, uuid: String, mappings: suspend (String) -> MappingsContainer) {
            version(version, uuid, toBuilder(mappings))
        }

        fun version(version: String, uuid: (String) -> String, mappings: suspend (String) -> MappingsContainer) {
            version(version, uuid, toBuilder(mappings))
        }

        fun version(version: String, mappings: MappingsVersionBuilder) {
            val list = listOf(version)
            versions(list, mappings)
        }

        fun buildVersion(version: String, spec: VersionSpec.(String) -> Unit) {
            buildVersions {
                version(version)
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

        fun versions(vararg versions: String, uuid: String, mappings: suspend (String) -> MappingsContainer) {
            val list = versions.toList()
            versions(list, uuid, toBuilder(mappings))
        }

        fun versions(vararg versions: String, uuid: (String) -> String, mappings: suspend (String) -> MappingsContainer) {
            val list = versions.toList()
            versions(list, uuid, toBuilder(mappings))
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

        fun versions(versions: Iterable<String>, uuid: String, mappings: suspend (String) -> MappingsContainer) {
            versions(versions, uuid, toBuilder(mappings))
        }

        fun versions(versions: Iterable<String>, uuid: (String) -> String, mappings: suspend (String) -> MappingsContainer) {
            versions(versions, uuid, toBuilder(mappings))
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
        private var uuidGetter: suspend (String) -> String = { it },
        private var mappingsGetter: MappingsContainerBuilder? = null,
        internal var versions: (() -> Iterable<String>)? = null,
    ) {
        fun uuid(uuid: String) {
            uuid { uuid }
        }

        fun uuid(uuid: suspend (String) -> String) {
            uuidGetter = uuid
        }

        fun mappings(mappings: MappingsContainer) {
            mappings { mappings }
        }

        fun mappings(mappings: MappingsContainerBuilder) {
            mappingsGetter = mappings
        }

        fun mappings(mappings: suspend (String) -> MappingsContainer) {
            mappingsGetter = toBuilder(mappings)
        }

        fun buildMappings(
            version: String,
            name: String,
            fillFieldDesc: Boolean = true,
            fillMethodDesc: Boolean = true,
            builder: suspend MappingsBuilder.() -> Unit,
        ) {
            mappings { me.shedaniel.linkie.buildMappings(version, name, fillFieldDesc, fillMethodDesc, builder) }
        }

        fun buildMappings(
            name: String,
            fillFieldDesc: Boolean = true,
            fillMethodDesc: Boolean = true,
            builder: suspend MappingsBuilder.(version: String) -> Unit,
        ) {
            mappings { me.shedaniel.linkie.buildMappings(it, name, fillFieldDesc, fillMethodDesc) { builder(it) } }
        }

        fun buildMappings(
            name: suspend (version: String) -> String,
            fillFieldDesc: Boolean = true,
            fillMethodDesc: Boolean = true,
            builder: suspend MappingsBuilder.(version: String) -> Unit,
        ) {
            mappings { me.shedaniel.linkie.buildMappings(it, name(it), fillFieldDesc, fillMethodDesc) { builder(it) } }
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

        fun versionsSeq(versions: () -> Sequence<String>) {
            this.versions = {
                val sequence = versions()
                Iterable { sequence.iterator() }
            }
        }

        fun accept(spec: VersionSpec.() -> Unit): MappingsVersionBuilder {
            also(spec)
            mappingsGetter!!

            return MappingsVersionBuilder {
                ofVersion(runBlocking { uuidGetter(it) }, mappingsGetter!!)
            }
        }
    }

    suspend fun getProvider(version: String): MappingsProvider {
        val entry = mappingsSuppliers.firstOrNull { it.isApplicable(version) } ?: return MappingsProvider.empty(this)
        return MappingsProvider.supply(this, version, entry.isCached(version)) { entry.applyVersion(version) }
    }

    suspend fun getDefaultProvider(channel: () -> String = this::getDefaultMappingChannel): MappingsProvider {
        return getProvider(getDefaultVersion(channel))
    }

    open fun supportsMixin(): Boolean = false
    open fun supportsAT(): Boolean = false
    open fun supportsAW(): Boolean = false
    open fun supportsFieldDescription(): Boolean = true
}