@file:Suppress("unused")

package me.shedaniel.linkie

import me.shedaniel.linkie.utils.error
import me.shedaniel.linkie.utils.info
import java.io.File

interface MappingsSupplier {
    fun isApplicable(version: String): Boolean
    fun isCached(version: String): Boolean = false
    fun applyVersion(version: String): MappingsContainer
}

fun Namespace.namespacedSupplier(mappingsSupplier: MappingsSupplier): MappingsSupplier =
    NamespacedMappingsSupplier(this, mappingsSupplier)

fun Namespace.loggedSupplier(mappingsSupplier: MappingsSupplier): MappingsSupplier =
    LoggedMappingsSupplier(this, mappingsSupplier)

fun Namespace.cachedSupplier(uuidGetter: (String) -> String, mappingsSupplier: MappingsSupplier): MappingsSupplier =
    CachedMappingsSupplier(this, uuidGetter, mappingsSupplier)

fun Namespace.simpleSupplier(version: String, supplier: (String) -> MappingsContainer): MappingsSupplier =
    SimpleMappingsSupplier(version) { supplier(version) }

fun Namespace.simpleCachedSupplier(version: String, uuid: String = version, supplier: (String) -> MappingsContainer): MappingsSupplier =
    cachedSupplier({ uuid }, simpleSupplier(version, supplier))

fun Namespace.simpleCachedSupplier(version: String, uuidGetter: (String) -> String, supplier: (String) -> MappingsContainer): MappingsSupplier =
    cachedSupplier(uuidGetter, simpleSupplier(version, supplier))

fun Namespace.multipleSupplier(versions: Iterable<String>, supplier: (String) -> MappingsContainer): MappingsSupplier =
    multipleSupplier({ versions }, supplier)

fun Namespace.multipleSupplier(versions: () -> Iterable<String>, supplier: (String) -> MappingsContainer): MappingsSupplier =
    MultiMappingsSupplier(versions, supplier)

fun Namespace.multipleCachedSupplier(versions: Iterable<String>, uuidGetter: (String) -> String, supplier: (String) -> MappingsContainer): MappingsSupplier =
    cachedSupplier(uuidGetter, multipleSupplier(versions, supplier))

fun Namespace.multipleCachedSupplier(versions: () -> Iterable<String>, uuidGetter: (String) -> String, supplier: (String) -> MappingsContainer): MappingsSupplier =
    cachedSupplier(uuidGetter, multipleSupplier(versions, supplier))

private class NamespacedMappingsSupplier(val namespace: Namespace, mappingsSupplier: MappingsSupplier) : DelegateMappingsSupplier(mappingsSupplier) {
    override fun applyVersion(version: String): MappingsContainer = super.applyVersion(version).also {
        it.namespace = namespace.id
    }
}

private class LoggedMappingsSupplier(val namespace: Namespace, mappingsSupplier: MappingsSupplier) : DelegateMappingsSupplier(mappingsSupplier) {
    override fun applyVersion(version: String): MappingsContainer {
        info("Loading $version in $namespace")
        val start = System.currentTimeMillis()
        return super.applyVersion(version).also { info("Loaded $version in $namespace within ${System.currentTimeMillis() - start}ms") }
    }
}

private open class DelegateMappingsSupplier(val mappingsSupplier: MappingsSupplier) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = mappingsSupplier.isApplicable(version)

    override fun applyVersion(version: String): MappingsContainer = mappingsSupplier.applyVersion(version)

    override fun isCached(version: String): Boolean = mappingsSupplier.isCached(version)
}

private class CachedMappingsSupplier(val namespace: Namespace, val uuidGetter: (String) -> String, val mappingsSupplier: MappingsSupplier) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = mappingsSupplier.isApplicable(version)

    override fun isCached(version: String): Boolean {
        val cacheFolder = File(Namespaces.cacheFolder, "mappings").also { it.mkdirs() }
        val uuid = uuidGetter(version)
        val cachedFile = File(cacheFolder, "${namespace.id}-$uuid.linkie3")
        return cachedFile.exists()
    }

    override fun applyVersion(version: String): MappingsContainer {
        val cacheFolder = File(Namespaces.cacheFolder, "mappings").also { it.mkdirs() }
        val uuid = uuidGetter(version)
        val cachedFile = File(cacheFolder, "${namespace.id}-$uuid.linkie3")
        if (cachedFile.exists()) {
            val mappingsContainer = loadFromCachedFile(cachedFile)
            if (mappingsContainer != null) return mappingsContainer
            error("Cache for version $version has failed.")
            cachedFile.delete()
        }
        val mappingsContainer = mappingsSupplier.applyVersion(version)
        mappingsContainer.saveToCachedFile(cachedFile)
        return mappingsContainer
    }

    private fun loadFromCachedFile(cachedFile: File): MappingsContainer? =
        outputBuffer(cachedFile.readBytes()).readMappingsContainer()

    private fun MappingsContainer.saveToCachedFile(cachedFile: File) =
        cachedFile.writeBytes(inputBuffer().also { it.writeMappingsContainer(this) }.toByteArray())
}

private class SimpleMappingsSupplier(val version: String, val supplier: () -> MappingsContainer) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = version == this.version

    override fun applyVersion(version: String): MappingsContainer = supplier()
}

private class MultiMappingsSupplier(val versions: () -> Iterable<String>, val supplier: (String) -> MappingsContainer) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = version in versions()

    override fun applyVersion(version: String): MappingsContainer = supplier(version)
}

object EmptyMappingsSupplier : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = false
    override fun isCached(version: String): Boolean = false
    override fun applyVersion(version: String): MappingsContainer = throw UnsupportedOperationException()
}

class ConcatMappingsSupplier(val suppliers: List<MappingsSupplier>) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = suppliers.any { it.isApplicable(version) }
    override fun isCached(version: String): Boolean {
        for (supplier in suppliers) {
            if (supplier.isApplicable(version)) {
                return supplier.isCached(version)
            }
        }
        
        return false
    }
    override fun applyVersion(version: String): MappingsContainer {
        for (supplier in suppliers) {
            if (supplier.isApplicable(version)) {
                return supplier.applyVersion(version)
            }
        }

        throw IllegalStateException()
    }
}