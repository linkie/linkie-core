@file:Suppress("unused")

package me.shedaniel.linkie

import com.soywiz.korio.file.VfsFile
import me.shedaniel.linkie.utils.div
import me.shedaniel.linkie.utils.error
import me.shedaniel.linkie.utils.info
import java.util.concurrent.locks.ReentrantLock

interface MappingsSupplier {
    fun isApplicable(version: String): Boolean
    suspend fun isCached(version: String): Boolean = false
    suspend fun applyVersion(version: String): MappingsContainer
}

fun Namespace.namespacedSupplier(mappingsSupplier: MappingsSupplier): MappingsSupplier =
    NamespacedMappingsSupplier(this, mappingsSupplier)

fun Namespace.loggingSupplier(mappingsSupplier: MappingsSupplier): MappingsSupplier =
    LoggingMappingsSupplier(this, mappingsSupplier)

fun Namespace.cachedSupplier(
    uuidGetter: suspend (String) -> String,
    mappingsSupplier: MappingsSupplier,
): MappingsSupplier =
    CachedMappingsSupplier(this, uuidGetter, mappingsSupplier)

fun Namespace.simpleSupplier(
    version: String,
    supplier: suspend (String) -> MappingsContainer,
): MappingsSupplier =
    SimpleMappingsSupplier(version) { supplier(version) }

fun Namespace.simpleCachedSupplier(
    version: String,
    uuid: String = version,
    supplier: suspend (String) -> MappingsContainer,
): MappingsSupplier =
    cachedSupplier({ uuid }, simpleSupplier(version, supplier))

fun Namespace.simpleCachedSupplier(
    version: String,
    uuidGetter: suspend (String) -> String,
    supplier: suspend (String) -> MappingsContainer,
): MappingsSupplier =
    cachedSupplier(uuidGetter, simpleSupplier(version, supplier))

fun Namespace.multipleSupplier(
    versions: Iterable<String>,
    supplier: suspend (String) -> MappingsContainer,
): MappingsSupplier =
    multipleSupplier({ versions }, supplier)

fun Namespace.multipleSupplier(
    versions: () -> Iterable<String>,
    supplier: suspend (String) -> MappingsContainer,
): MappingsSupplier =
    MultiMappingsSupplier(versions, supplier)

fun Namespace.multipleCachedSupplier(
    versions: Iterable<String>,
    uuidGetter: suspend (String) -> String,
    supplier: suspend (String) -> MappingsContainer,
): MappingsSupplier =
    cachedSupplier(uuidGetter, multipleSupplier(versions, supplier))

fun Namespace.multipleCachedSupplier(
    versions: () -> Iterable<String>,
    uuidGetter: suspend (String) -> String,
    supplier: suspend (String) -> MappingsContainer,
): MappingsSupplier =
    cachedSupplier(uuidGetter, multipleSupplier(versions, supplier))

private class NamespacedMappingsSupplier(val namespace: Namespace, mappingsSupplier: MappingsSupplier) : DelegateMappingsSupplier(mappingsSupplier) {
    private val lock = ReentrantLock()
    override suspend fun applyVersion(version: String): MappingsContainer {
        lock.lock()
        try {
            return getCachedVersion(version) ?: super.applyVersion(version).also {
                it.namespace = namespace.id
                Namespaces.addMappingsContainer(it)
            }
        } finally {
            lock.unlock()
        }
    }

    override suspend fun isCached(version: String): Boolean {
        return getCachedVersion(version) != null || super.isCached(version)
    }
    
    private fun getCachedVersion(version: String): MappingsContainer? =
        Namespaces.cachedMappings.firstOrNull { it.namespace == namespace.id && it.version == version.toLowerCase() }
}

private class LoggingMappingsSupplier(val namespace: Namespace, mappingsSupplier: MappingsSupplier) : DelegateMappingsSupplier(mappingsSupplier) {
    override suspend fun applyVersion(version: String): MappingsContainer {
        info("Loading $version in $namespace")
        val start = System.currentTimeMillis()
        return super.applyVersion(version).also {
            info("Loaded $version [${it.name}] in $namespace within ${System.currentTimeMillis() - start}ms")
        }
    }
}

private open class DelegateMappingsSupplier(val mappingsSupplier: MappingsSupplier) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = mappingsSupplier.isApplicable(version)

    override suspend fun applyVersion(version: String): MappingsContainer = mappingsSupplier.applyVersion(version)

    override suspend fun isCached(version: String): Boolean = mappingsSupplier.isCached(version)
}

private class CachedMappingsSupplier(
    val namespace: Namespace,
    val uuidGetter: suspend (String) -> String,
    val mappingsSupplier: MappingsSupplier,
) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = mappingsSupplier.isApplicable(version)

    override suspend fun isCached(version: String): Boolean {
        val cacheFolder = (Namespaces.cacheFolder / "mappings").also { it.mkdir() }
        val uuid = uuidGetter(version)
        val cachedFile = cacheFolder / "${namespace.id}-$uuid.linkie5"
        return cachedFile.exists()
    }

    override suspend fun applyVersion(version: String): MappingsContainer {
        val cacheFolder = (Namespaces.cacheFolder / "mappings").also { it.mkdir() }
        val uuid = uuidGetter(version)
        val cachedFile = cacheFolder / "${namespace.id}-$uuid.linkie5"
        if (cachedFile.exists()) {
            try {
                return loadFromCachedFile(cachedFile)
            } catch (t: Throwable) {
                error("Cache for version $version has failed.")
                t.printStackTrace()
                cachedFile.delete()
            }
        }
        val mappingsContainer = mappingsSupplier.applyVersion(version)
        mappingsContainer.saveToCachedFile(cachedFile)
        return mappingsContainer
    }

    private suspend fun loadFromCachedFile(cachedFile: VfsFile): MappingsContainer =
        cachedFile.readBytes().swimmingPoolReader().readMappingsContainer()

    private suspend fun MappingsContainer.saveToCachedFile(cachedFile: VfsFile) =
        cachedFile.writeBytes(swimmingPoolWriter().also { it.writeMappingsContainer(this) }.writeTo())
}

private class SimpleMappingsSupplier(val version: String, val supplier: suspend () -> MappingsContainer) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = version == this.version

    override suspend fun applyVersion(version: String): MappingsContainer = supplier()
}

private class MultiMappingsSupplier(val versions: () -> Iterable<String>, val supplier: suspend (String) -> MappingsContainer) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = version in versions()

    override suspend fun applyVersion(version: String): MappingsContainer = supplier(version)
}

object EmptyMappingsSupplier : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = false
    override suspend fun isCached(version: String): Boolean = false
    override suspend fun applyVersion(version: String): MappingsContainer = throw UnsupportedOperationException()
}

class ConcatMappingsSupplier(val suppliers: List<MappingsSupplier>) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = suppliers.any { it.isApplicable(version) }
    override suspend fun isCached(version: String): Boolean {
        for (supplier in suppliers) {
            if (supplier.isApplicable(version)) {
                return supplier.isCached(version)
            }
        }

        return false
    }

    override suspend fun applyVersion(version: String): MappingsContainer {
        for (supplier in suppliers) {
            if (supplier.isApplicable(version)) {
                return supplier.applyVersion(version)
            }
        }

        throw IllegalStateException()
    }
}
