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
    public ParsedInput parse(byte[] content, Collection<String> sourcePaths, String recordPath) {
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
            NodeList records;
            if (recordPath == null || recordPath.isBlank()) {
                records = new SingleNodeList(document.getDocumentElement());
            } else {
                records = (NodeList) xpath.evaluate(recordPath, document, XPathConstants.NODESET);
                if (records.getLength() == 0)
                    throw new IllegalArgumentException("XML record path has no records");
            }
            List<ParsedInput.ParsedRow> rows = new ArrayList<>();
            for (int row = 0; row < records.getLength(); row++) {
                Map<String, JsonNode> values = new LinkedHashMap<>();
                for (String path : sourcePaths) {
                    NodeList nodes = (NodeList) xpath.evaluate(path, records.item(row), XPathConstants.NODESET);
                    if (nodes.getLength() == 0) values.put(path, json.nullNode());
                    else if (nodes.getLength() == 1) values.put(path,
                            text(nodes.item(0).getTextContent()));
                    else {
                        var array = json.createArrayNode();
                        for (int index = 0; index < nodes.getLength(); index++)
                            array.add(text(nodes.item(index).getTextContent()));
                        values.put(path, array);
                    }
                }
                rows.add(new ParsedInput.ParsedRow(row + 1,
                        (recordPath == null ? "/" : recordPath) + "[" + (row + 1) + "]", values));
            }
            return new ParsedInput(rows);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid XML document or XPath", ex);
        }
    }

    private JsonNode text(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? json.nullNode() : json.getNodeFactory().textNode(normalized);
    }

    private record SingleNodeList(org.w3c.dom.Node node) implements NodeList {
        @Override public org.w3c.dom.Node item(int index) { return index == 0 ? node : null; }
        @Override public int getLength() { return 1; }
    }
}
