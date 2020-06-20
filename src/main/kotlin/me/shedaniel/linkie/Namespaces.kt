package me.shedaniel.linkie

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch

object Namespaces {
    val namespaces = mutableMapOf<String, Namespace>()

    private fun registerNamespace(namespace: Namespace) =
            namespace.also { namespaces[it.id] = it }

    operator fun get(id: String) = namespaces[id]!!

    fun init(
            vararg namespaces: Namespace,
            cycleMs: Long = 1800000
    ) {
        namespaces.forEach { registerNamespace(it) }
        val tickerChannel = ticker(delayMillis = cycleMs, initialDelayMillis = 0)
        CoroutineScope(Dispatchers.Default).launch {
            for (event in tickerChannel) {
                Namespaces.namespaces.map { (_, namespace) ->
                    launch { namespace.reset() }
                }.forEach { it.join() }
                System.gc()
            }
        }
    }
}