package me.shedaniel.linkie.utils

import com.soywiz.klock.TimeSpan
import com.soywiz.klock.minutes
import com.soywiz.korio.async.runBlockingNoJs
import java.util.*
import kotlin.concurrent.timerTask
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

@Suppress("MemberVisibilityCanBePrivate", "unused")
class ValueKeeper<T> constructor(val timeToKeep: TimeSpan, var valueBackend: Optional<T>, val getter: suspend () -> T) : Lazy<T> {
    companion object {
        private val timer = Timer()
    }

    private var task: TimerTask? = null

    constructor(timeToKeep: TimeSpan, value: T, getter: suspend () -> T) : this(timeToKeep, Optional.of(value), getter)
    constructor(timeToKeep: TimeSpan, getter: suspend () -> T) : this(timeToKeep, runBlockingNoJs { getter() }, getter)

    init {
        runBlockingNoJs {
            schedule()
        }
    }

    suspend fun get(): T = valueBackend.orElse(null) ?: getter().also { valueBackend = Optional.of(it); schedule() }

    fun clear() {
        valueBackend = Optional.empty()
    }

    suspend fun schedule() {
        task?.cancel()
        task = timerTask { runBlockingNoJs { clear() } }
        timer.schedule(task, timeToKeep.millisecondsLong)
    }

    override val value: T
        get() = runBlockingNoJs { get() }

    override fun isInitialized(): Boolean = valueBackend.isPresent
}

fun <T> valueKeeper(timeToKeep: TimeSpan = 2.minutes, getter: suspend () -> T): ValueKeeperProperty<T> =
    ValueKeeperProperty(timeToKeep, getter)

class ValueKeeperProperty<T>(
    timeToKeep: TimeSpan,
    getter: suspend () -> T,
) : ReadOnlyProperty<Any?, T>, Lazy<T> {
    val keeperLazy = lazy { ValueKeeper(timeToKeep, getter) }
    val keeper by keeperLazy
    val property = ReadOnlyProperty<Any?, T> { _, _ -> runBlockingNoJs { keeper.get() } }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return this.property.getValue(thisRef, property)
    }

    override fun isInitialized(): Boolean = keeperLazy.isInitialized() && keeper.isInitialized()
    override val value: T
        get() = runBlockingNoJs { keeper.get() }
}
