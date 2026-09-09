package io.collectra.api.importing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

final class XmlInputParser implements InputParser {
    private final ObjectMapper json;

    XmlInputParser(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public ParsedInput parse(byte[] content, Collection<String> sourcePaths) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(
                    new org.xml.sax.helpers.DefaultHandler() {
                        @Override
                        public void error(org.xml.sax.SAXParseException exception)
                                throws org.xml.sax.SAXException {
                            throw exception;
                        }

                        @Override
                        public void fatalError(org.xml.sax.SAXParseException exception)
                                throws org.xml.sax.SAXException {
                            throw exception;
                        }
                    });
            var document = builder.parse(new ByteArrayInputStream(content));
            var xpath = XPathFactory.newInstance().newXPath();
            Map<String, List<JsonNode>> result = new LinkedHashMap<>();
            for (String path : sourcePaths) {
                NodeList nodes = (NodeList) xpath.evaluate(path, document, XPathConstants.NODESET);
                List<JsonNode> values = new ArrayList<>();
                for (int index = 0; index < nodes.getLength(); index++) {
                    String value = nodes.item(index).getTextContent().trim();
                    if (!value.isEmpty()) values.add(json.getNodeFactory().textNode(value));
                }
                result.put(path, List.copyOf(values));
            }
            return new ParsedInput(result);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid XML document or XPath", ex);
        }
    }
}
