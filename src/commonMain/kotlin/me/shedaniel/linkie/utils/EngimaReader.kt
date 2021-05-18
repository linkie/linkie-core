package me.shedaniel.linkie.utils

import me.shedaniel.linkie.Class
import me.shedaniel.linkie.Mappings
import me.shedaniel.linkie.MappingsEntryType
import me.shedaniel.linkie.Method
import me.shedaniel.linkie.utils.io.ZipFile
import me.shedaniel.linkie.utils.io.bytes
import me.shedaniel.linkie.utils.io.forEachEntry
import me.shedaniel.linkie.utils.io.isDirectory

suspend fun Mappings.loadNamedFromEngimaStream(zip: ZipFile, showError: Boolean = true, ignoreError: Boolean = false) {
    zip.forEachEntry { path, entry ->
        if (!entry.isDirectory && path.endsWith(".mapping")) {
            val lines = entry.bytes.lines()
                .filterNotBlank()
                .map { line -> EngimaLine(line, line.count { it == '\t' }, getTypeByString(line.replace("\t", "").split(" ")[0])) }
            val levels = mutableListOf<Class?>()
            repeat(lines.filter { it.type != null }.map { it.indent }.maxOrNull()!! + 1) { levels.add(null) }
            lines.forEach { line ->
                if (line.type == MappingsEntryType.CLASS) {
                    readClass(line, levels, showError, ignoreError)
                } else if (line.type != null) {
                    readMember(line, levels, showError, ignoreError)
                }
            }
        }
    }
}

private fun Mappings.readClass(line: EngimaLine, levels: MutableList<Class?>, showError: Boolean, ignoreError: Boolean) {
    var className = line[1]
    for (i in 0 until line.indent) {
        className = "${levels[i]!!.intermediaryName}\$$className"
    }

    levels[line.indent] = (if (ignoreError) getOrCreateClass(className) else getClass(className))?.apply {
        mappedName = if (line.size >= 3) line[2] else null
    }

    if (levels[line.indent] == null && showError) {
        warn("Class $className does not have intermediary name! Skipping!")
    }
}

private fun readMember(line: EngimaLine, levels: MutableList<Class?>, showError: Boolean, ignoreError: Boolean) {
    if (levels[line.indent - 1] == null) {
        if (showError) warn("Class of ${line[1]} does not have intermediary name! Skipping!")
    } else {
        levels[line.indent - 1]!!.apply {
            if (line.type == MappingsEntryType.METHOD) {
                val method = when {
                    ignoreError || line[1] == "<init>" -> getOrCreateMethod(line[1], line.last())
                    else -> getMethod(line[1], line.last())
                }
                if (method == null && showError) {
                    warn("Method ${line[1]} in ${levels[line.indent - 1]!!.intermediaryName} does not have intermediary name! Skipping!")
                }
                if (line.size == 4) {
                    method?.mappedName = line[2]
                }
            } else if (line.type == MappingsEntryType.FIELD) {
                val field = when {
                    ignoreError -> getOrCreateField(line[1], line.last())
                    else -> getField(line[1])
                }
                if (field == null && showError) {
                    warn("Field ${line[1]} in ${levels[line.indent - 1]!!.intermediaryName} does not have intermediary name! Skipping!")
                }
                if (line.size == 4) {
                    field?.mappedName = line[2]
                }
            }
        }
    }
}

private data class EngimaLine(
    val text: String,
    val indent: Int,
    val type: MappingsEntryType?,
) {
    private val split: List<String> by lazy { text.trimStart('\t').split(" ") }

    operator fun get(index: Int): String = split[index]
    fun first(): String = split.first()
    fun last(): String = split.last()

    val size: Int
        get() = split.size
}

private fun getTypeByString(string: String): MappingsEntryType? =
    MappingsEntryType.values().firstOrNull { it.name.equals(string, true) }
