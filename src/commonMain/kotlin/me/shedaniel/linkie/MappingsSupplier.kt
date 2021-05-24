@file:Suppress("unused")

package me.shedaniel.linkie

import com.soywiz.korio.file.VfsFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.shedaniel.linkie.buffer.readMappingsContainer
import me.shedaniel.linkie.buffer.swimmingPoolReader
import me.shedaniel.linkie.buffer.swimmingPoolWriter
import me.shedaniel.linkie.buffer.writeMappingsContainer
import me.shedaniel.linkie.namespaces.MappingsBuilder
import me.shedaniel.linkie.namespaces.UuidGetter
import me.shedaniel.linkie.utils.div
import me.shedaniel.linkie.utils.error
import me.shedaniel.linkie.utils.info
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

interface MappingsSupplier {
    fun isApplicable(version: String): Boolean
    suspend fun isCached(version: String): Boolean = false
    suspend fun applyVersion(version: String): Mappings
}

fun Namespace.namespacedSupplier(mappingsSupplier: MappingsSupplier): MappingsSupplier =
    NamespacedMappingsSupplier(this, mappingsSupplier)

fun Namespace.loggingSupplier(mappingsSupplier: MappingsSupplier): MappingsSupplier =
    LoggingMappingsSupplier(this, mappingsSupplier)

fun Namespace.cachedSupplier(
    uuidGetter: UuidGetter,
    mappingsSupplier: MappingsSupplier,
): MappingsSupplier =
    CachedMappingsSupplier(this, uuidGetter, mappingsSupplier)

fun simpleSupplier(
    version: String,
    supplier: MappingsBuilder,
): MappingsSupplier =
    SimpleMappingsSupplier(version, supplier)

fun Namespace.simpleCachedSupplier(
    version: String,
    uuid: String = version,
    supplier: MappingsBuilder,
): MappingsSupplier =
    cachedSupplier({ uuid }, simpleSupplier(version, supplier))

fun Namespace.simpleCachedSupplier(
    version: String,
    uuidGetter: UuidGetter,
    supplier: MappingsBuilder,
): MappingsSupplier =
    cachedSupplier(uuidGetter, simpleSupplier(version, supplier))

fun multipleSupplier(
    versions: Iterable<String>,
    supplier: MappingsBuilder,
): MappingsSupplier =
    multipleSupplier({ versions }, supplier)

fun multipleSupplier(
    versions: () -> Iterable<String>,
    supplier: MappingsBuilder,
): MappingsSupplier =
    MultiMappingsSupplier(versions, supplier)

fun Namespace.multipleCachedSupplier(
    versions: Iterable<String>,
    uuidGetter: UuidGetter,
    supplier: MappingsBuilder,
): MappingsSupplier =
    cachedSupplier(uuidGetter, multipleSupplier(versions, supplier))

fun Namespace.multipleCachedSupplier(
    versions: () -> Iterable<String>,
    uuidGetter: UuidGetter,
    supplier: MappingsBuilder,
): MappingsSupplier =
    cachedSupplier(uuidGetter, multipleSupplier(versions, supplier))

private class NamespacedMappingsSupplier(val namespace: Namespace, mappingsSupplier: MappingsSupplier) : DelegateMappingsSupplier(mappingsSupplier) {
    val mutex = Mutex()
    override suspend fun applyVersion(version: String): Mappings {
        mutex.withLock {
            return getCachedVersion(version) ?: super.applyVersion(version).also {
                it.namespace = namespace.id
                Namespaces.addMappingsContainer(it)
            }
        }
    }

    override suspend fun isCached(version: String): Boolean {
        return getCachedVersion(version) != null || super.isCached(version)
    }
    
    private fun getCachedVersion(version: String): Mappings? =
        Namespaces.cachedMappings.firstOrNull { it.namespace == namespace.id && it.version == version.lowercase() }
}

private class LoggingMappingsSupplier(val namespace: Namespace, mappingsSupplier: MappingsSupplier) : DelegateMappingsSupplier(mappingsSupplier) {
    @OptIn(ExperimentalTime::class)
    override suspend fun applyVersion(version: String): Mappings {
        info("Loading $version in $namespace")
        return measureTimedValue {
            super.applyVersion(version)
        }.also { (mappings, duration) ->
            info("Loaded $version [${mappings.name}] in $namespace within $duration")
        }.value
    }
}

private open class DelegateMappingsSupplier(val mappingsSupplier: MappingsSupplier) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = mappingsSupplier.isApplicable(version)

    override suspend fun applyVersion(version: String): Mappings = mappingsSupplier.applyVersion(version)

    override suspend fun isCached(version: String): Boolean = mappingsSupplier.isCached(version)
}

private class CachedMappingsSupplier(
    val namespace: Namespace,
    val uuidGetter: UuidGetter,
    val mappingsSupplier: MappingsSupplier,
) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = mappingsSupplier.isApplicable(version)

    override suspend fun isCached(version: String): Boolean {
        val cacheFolder = (Namespaces.cacheFolder / "mappings").also { it.mkdir() }
        val uuid = uuidGetter.get(version)
        val cachedFile = cacheFolder / "${namespace.id}-$uuid.linkie5"
        return cachedFile.exists()
    }

    override suspend fun applyVersion(version: String): Mappings {
        val cacheFolder = (Namespaces.cacheFolder / "mappings").also { it.mkdir() }
        val uuid = uuidGetter.get(version)
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

    private suspend fun loadFromCachedFile(cachedFile: VfsFile): Mappings =
        cachedFile.readBytes().swimmingPoolReader().readMappingsContainer()

    private suspend fun Mappings.saveToCachedFile(cachedFile: VfsFile) =
        cachedFile.writeBytes(swimmingPoolWriter().also { it.writeMappingsContainer(this) }.writeTo())
}

private class SimpleMappingsSupplier(val version: String, val supplier: MappingsBuilder) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = version == this.version

    override suspend fun applyVersion(version: String): Mappings = supplier.build(version)
}

private class MultiMappingsSupplier(val versions: () -> Iterable<String>, val supplier: MappingsBuilder) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = version in versions()

    override suspend fun applyVersion(version: String): Mappings = supplier.build(version)
}

object EmptyMappingsSupplier : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = false
    override suspend fun isCached(version: String): Boolean = false
    override suspend fun applyVersion(version: String): Mappings = throw UnsupportedOperationException()
}

fun Collection<MappingsSupplier>.concat(): MappingsSupplier {
    return when (size) {
        0 -> EmptyMappingsSupplier
        1 -> first()
        else -> ConcatMappingsSupplier(this)
    }
}

class ConcatMappingsSupplier(val suppliers: Collection<MappingsSupplier>) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = suppliers.any { it.isApplicable(version) }
    override suspend fun isCached(version: String): Boolean {
        for (supplier in suppliers) {
            if (supplier.isApplicable(version)) {
                return supplier.isCached(version)
            }
        }

        return false
    }

    override suspend fun applyVersion(version: String): Mappings {
        for (supplier in suppliers) {
            if (supplier.isApplicable(version)) {
                return supplier.applyVersion(version)
            }
        }

        throw IllegalStateException("Invalid state, no supplier is able to supply for version $version")
    }
}
