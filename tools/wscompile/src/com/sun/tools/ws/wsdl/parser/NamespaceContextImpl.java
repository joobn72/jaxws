/**
 * $Id: NamespaceContextImpl.java,v 1.1 2005-05-24 14:07:29 bbissett Exp $
 */

/*
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.sun.tools.ws.wsdl.parser;

import java.util.Iterator;

import javax.xml.namespace.NamespaceContext;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.sun.xml.bind.v2.WellKnownNamespace;

public class NamespaceContextImpl implements NamespaceContext {

    private final Element e;

    public NamespaceContextImpl(Element e) {
        this.e = e;
    }

    /*
     * Copyright 1999-2004 The Apache Software Foundation.
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *     http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
    public String getNamespaceURI(String prefix) {
        Node parent = e;
        String namespace = null;

        if (prefix.equals("xml")) {
            namespace = WellKnownNamespace.XML_NAMESPACE_URI;
        } else {
            int type;

            while ((null != parent) && (null == namespace)
                    && (((type = parent.getNodeType()) == Node.ELEMENT_NODE)
                    || (type == Node.ENTITY_REFERENCE_NODE))) {
                if (type == Node.ELEMENT_NODE) {
                    if (parent.getNodeName().indexOf(prefix + ':') == 0)
                        return parent.getNamespaceURI();
                    NamedNodeMap nnm = parent.getAttributes();

                    for (int i = 0; i < nnm.getLength(); i++) {
                        Node attr = nnm.item(i);
                        String aname = attr.getNodeName();
                        boolean isPrefix = aname.startsWith("xmlns:");

                        if (isPrefix || aname.equals("xmlns")) {
                            int index = aname.indexOf(':');
                            String p = isPrefix ? aname.substring(index + 1) : "";

                            if (p.equals(prefix)) {
                                namespace = attr.getNodeValue();

                                break;
                            }
                        }
                    }
                }

                parent = parent.getParentNode();
            }
        }

        return namespace;
    }

    public String getPrefix(String namespaceURI) {
        throw new UnsupportedOperationException();
    }

    public Iterator getPrefixes(String namespaceURI) {
        throw new UnsupportedOperationException();
    }
}
