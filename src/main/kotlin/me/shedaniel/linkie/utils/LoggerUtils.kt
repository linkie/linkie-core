@file:Suppress("unused")

package me.shedaniel.linkie.utils

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Linkie")

fun info(string: String) = logger.info(string)
fun info(string: String, vararg args: String) = logger.info(string, args)

fun warn(string: String) = logger.warn(string)
fun warn(string: String, vararg args: String) = logger.warn(string, args)

fun error(string: String) = logger.error(string)
fun error(string: String, vararg args: String) = logger.error(string, args)

fun debug(string: String) = logger.debug(string)
fun debug(string: String, vararg args: String) = logger.debug(string, args)

fun trace(string: String) = logger.trace(string)
fun trace(string: String, vararg args: String) = logger.trace(string, args)
