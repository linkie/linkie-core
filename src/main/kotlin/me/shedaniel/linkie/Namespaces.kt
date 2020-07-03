package me.shedaniel.linkie

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import me.shedaniel.linkie.utils.info
import java.util.concurrent.CopyOnWriteArrayList

object Namespaces {
    val namespaces = mutableMapOf<String, Namespace>()
    val cachedMappings = CopyOnWriteArrayList<MappingsContainer>()

    private fun registerNamespace(namespace: Namespace) =
            namespace.also { namespaces[it.id] = it }

    operator fun get(id: String) = namespaces[id]!!

    fun getMaximumCachedVersion(): Int = 2

    fun limitCachedData() {
        val list = mutableListOf<String>()
        while (cachedMappings.size > getMaximumCachedVersion()) {
            val first = cachedMappings.first()
            cachedMappings.remove(first)
            list.add(first.let { "${it.namespace}-${it.version}" })
        }
        System.gc()
        info("Removed ${list.size} Mapping(s): " + list.joinToString(", "))
    }

    fun addMappingsContainer(mappingsContainer: MappingsContainer) {
        cachedMappings.add(mappingsContainer)
        limitCachedData()
        info("Currently Loaded ${cachedMappings.size} Mapping(s): " + cachedMappings.joinToString(", ") { "${it.namespace}-${it.version}" })
    }

    fun init(
            vararg namespaces: Namespace,
            cycleMs: Long = 1800000
    ) {
        namespaces.forEach { registerNamespace(it) }
        val tickerChannel = ticker(delayMillis = cycleMs, initialDelayMillis = 0)
        CoroutineScope(Dispatchers.Default).launch {
            for (event in tickerChannel) {
                cachedMappings.clear()
                Namespaces.namespaces.map { (_, namespace) ->
                    launch {
                        namespace.reset()
                    }
                }.forEach { it.join() }
                System.gc()
            }
        }
    }
}