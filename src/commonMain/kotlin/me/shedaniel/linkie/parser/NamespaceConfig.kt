package me.shedaniel.linkie.parser

data class NamespaceConfig(
    val obfClient: String? = null,
    val obfServer: String? = null,
    val obfMerged: String? = null,
    val intermediary: String,
    val named: String? = null,
) {
    companion object {
        val DEFAULT = NamespaceConfig(
            obfMerged = Parser.NS_OBF,
            intermediary = Parser.NS_INTERMEDIARY,
            named = Parser.NS_MAPPED
        )
    }
}
