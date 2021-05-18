package me.shedaniel.linkie.utils.xml

import org.dom4j.Element
import org.dom4j.io.SAXReader

actual fun parseXml(content: String): XmlNode = XmlNodeImpl(SAXReader().read(content.reader()).rootElement)

@JvmInline
value class XmlNodeImpl(val element: Element) : XmlNode {
    override val text: String
        get() = element.text

    override operator fun get(key: String): XmlNode = XmlNodeImpl(element.element(key))
    override fun getAll(key: String): Sequence<XmlNode> = element.elements(key).asSequence().map(::XmlNodeImpl)
}
