package me.shedaniel.linkie.utils.xml

import com.soywiz.korio.serialization.xml.Xml
import com.soywiz.korio.serialization.xml.toXml

actual fun parseXml(content: String): XmlNode =
    XmlNodeImpl(content.toXml())

value class XmlNodeImpl(val element: Xml) : XmlNode {
    override val text: String
        get() = element.text

    override operator fun get(key: String): XmlNode = XmlNodeImpl(element.child(key)!!)
    override fun getAll(key: String): Sequence<XmlNode> = element[key].asSequence().map(::XmlNodeImpl)
}
