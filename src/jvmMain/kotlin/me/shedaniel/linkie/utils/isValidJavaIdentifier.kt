package me.shedaniel.linkie.utils

/**
 * Determines if the specified string is permissible as a Java identifier.
 */
actual fun String.isValidJavaIdentifier(): Boolean {
    return isNotEmpty() && allIndexed { index, c ->
        if (index == 0) {
            Character.isJavaIdentifierStart(c)
        } else {
            Character.isJavaIdentifierPart(c)
        }
    }
}
