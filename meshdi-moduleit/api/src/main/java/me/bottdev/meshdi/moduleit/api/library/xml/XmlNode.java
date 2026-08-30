package me.bottdev.meshdi.moduleit.api.library.xml;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Elegant XML Wrapper for fluent XML parsing without namespaces.
public record XmlNode(Element el) {

    public String text(String name) {
        XmlNode childNode = child(name);
        return childNode != null ? childNode.el().getTextContent().trim() : null;
    }

    public XmlNode child(String name) {
        if (el == null) return null;
        NodeList nodes = el.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(name)) {
                return new XmlNode((Element) n);
            }
        }
        return null;
    }

    public List<XmlNode> children(String name) {
        List<XmlNode> res = new ArrayList<>();
        if (el == null) return res;
        NodeList nodes = el.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(name)) {
                res.add(new XmlNode((Element) n));
            }
        }
        return res;
    }

    public Map<String, String> propertiesAsMap() {
        Map<String, String> props = new LinkedHashMap<>();
        if (el == null) return props;
        NodeList nodes = el.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                props.put(n.getNodeName(), n.getTextContent().trim());
            }
        }
        return props;
    }
}
