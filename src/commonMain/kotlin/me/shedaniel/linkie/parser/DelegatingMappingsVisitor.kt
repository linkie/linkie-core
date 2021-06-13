package me.shedaniel.linkie.parser

open class DelegatingMappingsVisitor(override var parent: MappingsVisitor?) : MappingsVisitor

open class DelegatingMappingsEntryComplex(override var parent: MappingsEntryComplex?) : MappingsEntryComplex

fun MappingsEntryComplex?.delegate(complex: (namespace: String?, base: (namespace: String?) -> String?) -> String?): DelegatingMappingsEntryComplex =
    object : DelegatingMappingsEntryComplex(this) {
        override fun get(namespace: String?): String? {
            val base by lazy { super.get(namespace) }
            return complex(namespace) { base } ?: base
        }
    }

fun entryComplex(complex: (namespace: String?, base: () -> String?) -> String?): DelegatingMappingsEntryComplex =
    object : DelegatingMappingsEntryComplex(null) {
        override fun get(namespace: String?): String? {
            val base by lazy { super.get(namespace) }
            return complex(namespace) { base } ?: base
        }
    }

fun entryComplex(complex: (namespace: String?) -> String?): DelegatingMappingsEntryComplex =
    object : DelegatingMappingsEntryComplex(null) {
        override fun get(namespace: String?): String? {
            return complex(namespace) ?: super.get(namespace)
        }
    }
