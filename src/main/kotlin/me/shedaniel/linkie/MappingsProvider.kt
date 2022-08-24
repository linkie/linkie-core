package me.shedaniel.linkie

import com.soywiz.korio.file.VfsFile

data class MappingsProvider(
    var namespace: Namespace,
    var version: String?,
    var cached: Boolean?,
    private var mappingsContainer: (suspend () -> MappingsContainer)?,
) {
    fun isEmpty(): Boolean = version == null || cached == null || mappingsContainer == null

    fun injectDefaultVersion(other: MappingsProvider) = injectDefaultVersion { other }

    fun injectDefaultVersion(other: () -> MappingsProvider) {
        if (isEmpty()) {
            val otherProvider = other()
            if (!otherProvider.isEmpty()) {
                if (namespace != otherProvider.namespace) {
                    throw IllegalStateException("Could not inject default version as they are from the different namespace: ${namespace.id} and ${otherProvider.namespace.id}")
                }
                version = otherProvider.version
                cached = otherProvider.cached
                mappingsContainer = otherProvider.mappingsContainer
            }
        }
    }

    suspend fun getOrNull(): MappingsContainer? = mappingsContainer?.invoke()
    suspend fun get(): MappingsContainer = getOrNull()!!

    suspend fun getSources(className: String): VfsFile = namespace.getSource(get(), version!!, className)

    companion object {
        fun of(namespace: Namespace, version: String, mappingsContainer: MappingsContainer?): MappingsProvider =
            if (mappingsContainer == null)
                supply(namespace, version, null, null)
            else supply(namespace, version, true) { mappingsContainer }

        fun supply(
            namespace: Namespace,
            version: String?,
            cached: Boolean?,
            mappingsContainer: (suspend () -> MappingsContainer)?,
        ): MappingsProvider =
            MappingsProvider(namespace, version, cached, mappingsContainer)

        fun empty(namespace: Namespace): MappingsProvider =
            MappingsProvider(namespace, null, null, null)
    }
}
