package me.shedaniel.linkie.parser.srg

import me.shedaniel.linkie.parser.AbstractParser
import me.shedaniel.linkie.parser.ProvidedEntryComplex

abstract class AbstractTsrgParser : AbstractParser<ProvidedEntryComplex>(::ProvidedEntryComplex) {
    protected fun readClass(split: List<String>) {
        lastClass.split = split::get
        lastClassVisitor = visitor.visitClass(lastClass)
    }

    protected fun readSecondary(line: String, split: List<String>) {
        if (lastClassVisitor != null) {
            when (split.size) {
                namespaces.size -> readField(split)
                namespaces.size + 1 -> readMethod(split)
                else -> throw IllegalArgumentException("Invalid secondary line ${line + 1}: $line")
            }
        }
    }

    protected fun readField(split: List<String>) {
        lastField.split = split::get
        lastClassVisitor!!.visitField(lastField, null)
    }

    protected fun readMethod(split: List<String>) {
        lastMethod.split = { namespaceIndex ->
            if (namespaceIndex == 0) split[namespaceIndex]
            else split[namespaceIndex + 1]
        }
        lastMethodVisitor = lastClassVisitor!!.visitMethod(lastMethod, split[1])
    }
}
