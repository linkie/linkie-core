package me.shedaniel.linkie.utils.xml

expect fun parseXml(content: String): XmlNode

interface XmlNode {
    val text: String

    operator fun get(key: String): XmlNode
    fun getAll(key: String): Sequence<XmlNode>
}
