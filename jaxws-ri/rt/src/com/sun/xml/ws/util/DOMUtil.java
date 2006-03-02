/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 * 
 * You can obtain a copy of the license at
 * https://jwsdp.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * https://jwsdp.dev.java.net/CDDLv1.0.html  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 */

package com.sun.xml.ws.util;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.XMLConstants;
import java.io.IOException;
import java.io.InputStream;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;

/**
 * $author: JAXWS Development Team
 */
public class DOMUtil {

    private static DocumentBuilder db;

    /**
     * Creates a new DOM document.
     */
    public static Document createDom() {
        synchronized (DOMUtil.class) {
            if (db == null) {
                try {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setNamespaceAware(true);
                    db = dbf.newDocumentBuilder();
                } catch (ParserConfigurationException e) {
                    throw new FactoryConfigurationError(e);
                }
            }
            return db.newDocument();
        }
    }

    public static Node createDOMNode(InputStream inputStream) {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setValidating(false);
        try {
            DocumentBuilder builder = dbf.newDocumentBuilder();
            try {
                return builder.parse(inputStream);
            } catch (SAXException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        } catch (ParserConfigurationException pce) {
            IllegalArgumentException iae = new IllegalArgumentException(pce.getMessage());
            iae.initCause(pce);
            throw iae;
        }
        return null;
    }

    /**
     * Traverses a DOM node and writes out on a streaming writer.
     * @param node
     * @param writer
     */
    public static void serializeNode(Node node, XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement(
            fixNull(node.getPrefix()),
            node.getLocalName(),
            fixNull(node.getNamespaceURI()));

        if (node.hasAttributes()){
            NamedNodeMap attrs = node.getAttributes();
            int numOfAttributes = attrs.getLength();
            // write namespace declarations first.
            // if we interleave this with attribue writing,
            // Zephyr will try to fix it and we end up getting inconsistent namespace bindings.
            for(int i = 0; i < numOfAttributes; i++){
                Node attr = attrs.item(i);
                String nsUri = fixNull(attr.getNamespaceURI());
                if(nsUri.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
                    // this is a namespace declaration, not an attribute
                    writer.writeNamespace(attr.getLocalName(),attr.getNodeValue());
                }
            }
            for(int i = 0; i < numOfAttributes; i++){
                Node attr = attrs.item(i);
                String nsUri = fixNull(attr.getNamespaceURI());
                if(!nsUri.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
                    writer.writeAttribute(
                        fixNull(attr.getPrefix()),
                        nsUri,
                        attr.getLocalName(),
                        attr.getNodeValue());
                }
            }
        }

        if(node.hasChildNodes()){
            NodeList children = node.getChildNodes();
            for(int i = 0; i< children.getLength(); i++){
                Node child = children.item(i);
                switch(child.getNodeType()){
                    case Node.PROCESSING_INSTRUCTION_NODE:
                        writer.writeProcessingInstruction(child.getNodeValue());
                    case Node.DOCUMENT_TYPE_NODE:
                        break;
                    case Node.CDATA_SECTION_NODE:
                        writer.writeCData(child.getNodeValue());
                        break;
                    case Node.COMMENT_NODE:
                        writer.writeComment(child.getNodeValue());
                        break;
                    case Node.TEXT_NODE:
                        writer.writeCharacters(child.getNodeValue());
                        break;
                    default:
                        serializeNode(child, writer);
                        break;
                }
            }
        }
        writer.writeEndElement();
    }

    /**
     * Gets the first child of the given name, or null.
     */
    public static Element getFirstChild(Element e, String nsUri, String local) {
        for( Node n=e.getFirstChild(); n!=null; n=n.getNextSibling() ) {
            if(n.getNodeType()==Node.ELEMENT_NODE) {
                Element c = (Element)n;
                if(c.getLocalName().equals(local) && c.getNamespaceURI().equals(nsUri))
                    return c;
            }
        }
        return null;
    }

    private static @NotNull String fixNull(@Nullable String s) {
        if(s==null)     return "";
        else            return s;
    }
}
