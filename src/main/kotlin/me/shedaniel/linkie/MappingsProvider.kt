package me.shedaniel.linkie

data class MappingsProvider(var namespace: Namespace, var version: String?, var cached: Boolean?, var mappingsContainer: (suspend () -> MappingsContainer)?) {
    fun isEmpty(): Boolean = version == null || cached == null || mappingsContainer == null

    fun injectDefaultVersion(mappingsProvider: MappingsProvider) {
        if (isEmpty() && !mappingsProvider.isEmpty()) {
            if (namespace != mappingsProvider.namespace) {
                throw IllegalStateException("Could not inject default version as they are from the different namespace: ${namespace.id} and ${mappingsProvider.namespace.id}")
            }
            version = mappingsProvider.version
            cached = mappingsProvider.cached
            mappingsContainer = mappingsProvider.mappingsContainer
        }
    }

    suspend fun getOrNull(): MappingsContainer? = mappingsContainer?.invoke()
    suspend fun get(): MappingsContainer = getOrNull()!!

    companion object {
        fun of(namespace: Namespace, version: String, mappingsContainer: MappingsContainer?): MappingsProvider =
            if (mappingsContainer == null)
                supply(namespace, version, null, null)
            else supply(namespace, version, true) { mappingsContainer }

        fun supply(namespace: Namespace, version: String?, cached: Boolean?, mappingsContainer: (suspend () -> MappingsContainer)?): MappingsProvider =
            MappingsProvider(namespace, version, cached, mappingsContainer)

        fun empty(namespace: Namespace): MappingsProvider =
            MappingsProvider(namespace, null, null, null)
    }
}