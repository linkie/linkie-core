package me.shedaniel.linkie

data class MappingsProvider(var version: String?, var cached: Boolean?, var mappingsContainer: (() -> MappingsContainer)?) {
    fun isEmpty(): Boolean = version == null || cached == null || mappingsContainer == null

    fun injectDefaultVersion(mappingsProvider: MappingsProvider) {
        if (isEmpty() && !mappingsProvider.isEmpty()) {
            version = mappingsProvider.version
            cached = mappingsProvider.cached
            mappingsContainer = mappingsProvider.mappingsContainer
        }
    }

    companion object {
        fun of(version: String, mappingsContainer: MappingsContainer?): MappingsProvider =
                if (mappingsContainer == null)
                    supply(version, null, null)
                else supply(version, true) { mappingsContainer }

        fun supply(version: String?, cached: Boolean?, mappingsContainer: (() -> MappingsContainer)?): MappingsProvider =
                MappingsProvider(version, cached, mappingsContainer)

        fun empty(): MappingsProvider =
                MappingsProvider(null, null, null)
    }
}