@file:Suppress("unused")

package me.shedaniel.linkie.utils

import com.soywiz.klogger.Logger
import com.soywiz.korio.lang.format

private val logger = Logger("Linkie")

fun info(string: String) = logger.info { string }
fun info(string: String, vararg args: String) = logger.info { string.format(args) }

fun warn(string: String) = logger.warn { string }
fun warn(string: String, vararg args: String) = logger.warn { string.format(args) }

fun error(string: String) = logger.error { string }
fun error(string: String, vararg args: String) = logger.error { string.format(args) }

fun debug(string: String) = logger.debug { string }
fun debug(string: String, vararg args: String) = logger.debug { string.format(args) }

fun trace(string: String) = logger.trace { string }
fun trace(string: String, vararg args: String) = logger.trace { string.format(args) }
