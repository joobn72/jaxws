/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems Inc. All Rights Reserved
 */

package com.sun.tools.ws.wsdl.parser;

import com.sun.tools.ws.api.wsdl.TWSDLExtensible;
import com.sun.tools.ws.api.wsdl.TWSDLExtensionHandler;
import com.sun.tools.ws.resources.WsdlMessages;
import com.sun.tools.ws.util.xml.XmlUtil;
import com.sun.tools.ws.wscompile.ErrorReceiver;
import com.sun.tools.ws.wscompile.WsimportOptions;
import com.sun.tools.ws.wsdl.document.Binding;
import com.sun.tools.ws.wsdl.document.BindingFault;
import com.sun.tools.ws.wsdl.document.BindingInput;
import com.sun.tools.ws.wsdl.document.BindingOperation;
import com.sun.tools.ws.wsdl.document.BindingOutput;
import com.sun.tools.ws.wsdl.document.Definitions;
import com.sun.tools.ws.wsdl.document.Documentation;
import com.sun.tools.ws.wsdl.document.Fault;
import com.sun.tools.ws.wsdl.document.Import;
import com.sun.tools.ws.wsdl.document.Input;
import com.sun.tools.ws.wsdl.document.Message;
import com.sun.tools.ws.wsdl.document.MessagePart;
import com.sun.tools.ws.wsdl.document.Operation;
import com.sun.tools.ws.wsdl.document.OperationStyle;
import com.sun.tools.ws.wsdl.document.Output;
import com.sun.tools.ws.wsdl.document.Port;
import com.sun.tools.ws.wsdl.document.PortType;
import com.sun.tools.ws.wsdl.document.Service;
import com.sun.tools.ws.wsdl.document.WSDLConstants;
import com.sun.tools.ws.wsdl.document.WSDLDocument;
import com.sun.tools.ws.wsdl.document.jaxws.JAXWSBindingsConstants;
import com.sun.tools.ws.wsdl.document.schema.SchemaConstants;
import com.sun.tools.ws.wsdl.document.schema.SchemaKinds;
import com.sun.tools.ws.wsdl.framework.Entity;
import com.sun.tools.ws.wsdl.framework.ParserListener;
import com.sun.tools.ws.wsdl.framework.TWSDLParserContextImpl;
import com.sun.xml.ws.util.ServiceFinder;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A parser for WSDL documents.
 *
 * @author WS Development Team
 */
public class WSDLParser {
    private final ErrorReceiver errReceiver;
    private WsimportOptions options;
    private MetadataFinder forest;

    //wsdl extension handlers
    private final Map extensionHandlers;

    private ArrayList<ParserListener> listeners;

    public WSDLParser(WsimportOptions options, ErrorReceiver errReceiver) {
        this.extensionHandlers = new HashMap();
        this.options = options;
        this.errReceiver = errReceiver;

        // register handlers for default extensions
        register(new SOAPExtensionHandler(extensionHandlers));
        register(new HTTPExtensionHandler(extensionHandlers));
        register(new MIMEExtensionHandler(extensionHandlers));
        register(new JAXWSBindingExtensionHandler(extensionHandlers));
        register(new SOAP12ExtensionHandler(extensionHandlers));
        register(new MemberSubmissionAddressingExtensionHandler(extensionHandlers, errReceiver));
        register(new W3CAddressingExtensionHandler(extensionHandlers, errReceiver));

        for (TWSDLExtensionHandler te : ServiceFinder.find(TWSDLExtensionHandler.class)) {
            register(te);
        }

    }

    private void register(TWSDLExtensionHandler h) {
        extensionHandlers.put(h.getNamespaceURI(), h);
    }

    public void addParserListener(ParserListener l) {
        if (listeners == null) {
            listeners = new ArrayList<ParserListener>();
        }
        listeners.add(l);
    }

    public WSDLDocument parse() throws SAXException {
        forest = new MetadataFinder(new WSDLInternalizationLogic(), options, errReceiver);
        forest.parseWSDL();

        // parse external binding files
        for (InputSource value : options.getWSDLBindings()) {
            errReceiver.pollAbort();
            Document root = forest.parse(value, true); // TODO: I think this should be false - KK
            if(root==null)       continue;   // error must have been reported
            Element binding = root.getDocumentElement();
            if (!fixNull(binding.getNamespaceURI()).equals(JAXWSBindingsConstants.NS_JAXWS_BINDINGS)
                    || !binding.getLocalName().equals("bindings")){
                    errReceiver.error(forest.locatorTable.getStartLocation(binding), WsdlMessages.PARSER_NOT_A_BINDING_FILE(
                        binding.getNamespaceURI(),
                        binding.getLocalName()));
                continue;
            }

            NodeList nl = binding.getElementsByTagNameNS(
                "http://java.sun.com/xml/ns/javaee", "handler-chains");
            for(int i = 0; i < nl.getLength(); i++){
                options.addHandlerChainConfiguration((Element) nl.item(i));
            }

        }
        return buildWSDLDocument();
    }

    private String fixNull(String s) {
        if(s==null) return "";
        else        return s;
    }

    public MetadataFinder getDOMForest() {
        return forest;
    }

    private WSDLDocument buildWSDLDocument(){
        /**
         * Currently we are working off first WSDL document
         * TODO: add support of creating WSDLDocument from collection of WSDL documents
         */

        String location = forest.getRootWSDL();

        //It means that WSDL is not found, an error might have been reported, lets try to recover
        if(location == null)
            return null;

        Document root = forest.get(location);

        if(root == null)
            return null;

        WSDLDocument document = new WSDLDocument();
        document.setSystemId(location);
        TWSDLParserContextImpl context = new TWSDLParserContextImpl(forest, document, listeners);

        Definitions definitions = parseDefinitions(context, root);
        document.setDefinitions(definitions);
        return document;
    }

    private Definitions parseDefinitions(TWSDLParserContextImpl context, Document root) {
        context.pushWSDLLocation();
        context.setWSDLLocation(context.getDocument().getSystemId());

        new Internalizer(forest, options, errReceiver).transform();

        //print the wsdl
//        try{
//            forest.dump(System.out);
//        }catch(IOException e){
//            e.printStackTrace();
//        }

        Definitions definitions = parseDefinitionsNoImport(context, root);
        if(definitions == null){
            Locator locator = forest.locatorTable.getStartLocation(root.getDocumentElement());
            errReceiver.error(locator, WsdlMessages.PARSING_NOT_AWSDL(locator.getSystemId()));

        }
        processImports(context);
        context.popWSDLLocation();
        return definitions;
    }

    private void processMexDocs(TWSDLParserContextImpl context){
        for(String location : forest.listSystemIDs()){
            if (!context.getDocument().isImportedDocument(location)){
                Document doc = forest.get(location);
                if(doc == null)
                    continue;
                Definitions importedDefinitions = parseDefinitionsNoImport(context, doc);
                if(importedDefinitions == null)
                    continue;
                context.getDocument().addImportedEntity(importedDefinitions);
                context.getDocument().addImportedDocument(location);
            }
        }
    }
    private void processImports(TWSDLParserContextImpl context) {
        for(String location : forest.getExternalReferences()){
            if (!context.getDocument().isImportedDocument(location)){
                Document doc = forest.get(location);
                if(doc == null)
                    continue;
                Definitions importedDefinitions = parseDefinitionsNoImport(context, doc);
                if(importedDefinitions == null)
                    continue;
                context.getDocument().addImportedEntity(importedDefinitions);
                context.getDocument().addImportedDocument(location);
            }
        }
    }

    private Definitions parseDefinitionsNoImport(
        TWSDLParserContextImpl context,
        Document doc) {
        Element e = doc.getDocumentElement();
        //at this poinjt we expect a wsdl or schema document to be fully qualified
        if(e.getNamespaceURI() == null || (!e.getNamespaceURI().equals(WSDLConstants.NS_WSDL) || !e.getLocalName().equals("definitions"))){
            return null;
        }
        context.push();
        context.registerNamespaces(e);

        Definitions definitions = new Definitions(context.getDocument(), forest.locatorTable.getStartLocation(e));
        String name = XmlUtil.getAttributeOrNull(e, Constants.ATTR_NAME);
        definitions.setName(name);

        String targetNamespaceURI =
            XmlUtil.getAttributeOrNull(e, Constants.ATTR_TARGET_NAMESPACE);

        definitions.setTargetNamespaceURI(targetNamespaceURI);

        boolean gotDocumentation = false;
        boolean gotTypes = false;

        for (Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();) {
            Element e2 = Util.nextElement(iter);
            if (e2 == null)
                break;

            if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_DOCUMENTATION)) {
                if (gotDocumentation) {
                    errReceiver.error(forest.locatorTable.getStartLocation(e2), WsdlMessages.PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(e.getLocalName()));
                    return null;
                }
                gotDocumentation = true;
                if(definitions.getDocumentation() == null)
                    definitions.setDocumentation(getDocumentationFor(e2));
            } else if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_TYPES)) {
                if (gotTypes && !options.isExtensionMode()) {
                    errReceiver.error(forest.locatorTable.getStartLocation(e2), WsdlMessages.PARSING_ONLY_ONE_TYPES_ALLOWED(Constants.TAG_DEFINITIONS));
                    return null;
                }
                gotTypes = true;
                //add all the wsdl:type elements to latter make a list of all the schema elements
                // that will be needed to create jaxb model
                if(!options.isExtensionMode())
                    validateSchemaImports(e2);
            } else if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_MESSAGE)) {
                Message message = parseMessage(context, definitions, e2);
                definitions.add(message);
            } else if (
                XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_PORT_TYPE)) {
                PortType portType = parsePortType(context, definitions, e2);
                definitions.add(portType);
            } else if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_BINDING)) {
                Binding binding = parseBinding(context, definitions, e2);
                definitions.add(binding);
            } else if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_SERVICE)) {
                Service service = parseService(context, definitions, e2);
                definitions.add(service);
            } else if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_IMPORT)) {
                definitions.add(parseImport(context, definitions, e2));
            } else if (XmlUtil.matchesTagNS(e2, SchemaConstants.QNAME_IMPORT)) {
                errReceiver.warning(forest.locatorTable.getStartLocation(e2), WsdlMessages.WARNING_WSI_R_2003());
            } else {
                // possible extensibility element -- must live outside the WSDL namespace
                checkNotWsdlElement(e2);
                if (!handleExtension(context, definitions, e2)) {
                    checkNotWsdlRequired(e2);
                }
            }
        }

        context.pop();
        context.fireDoneParsingEntity(
            WSDLConstants.QNAME_DEFINITIONS,
            definitions);
        return definitions;
    }

    private Message parseMessage(
        TWSDLParserContextImpl context,
        Definitions definitions,
        Element e) {
        context.push();
        context.registerNamespaces(e);
        Message message = new Message(definitions, forest.locatorTable.getStartLocation(e), errReceiver);
        String name = Util.getRequiredAttribute(e, Constants.ATTR_NAME);
        message.setName(name);

        boolean gotDocumentation = false;

        for (Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();) {
            Element e2 = Util.nextElement(iter);
            if (e2 == null)
                break;

            if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_DOCUMENTATION)) {
                if (gotDocumentation) {
                    Util.fail(
                        "parsing.onlyOneDocumentationAllowed",
                        e.getLocalName());
                }
                gotDocumentation = true;
                message.setDocumentation(getDocumentationFor(e2));
            } else if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_PART)) {
                MessagePart part = parseMessagePart(context, e2);
                message.add(part);
            } else {
                Util.fail(
                    "parsing.invalidElement",
                    e2.getTagName(),
                    e2.getNamespaceURI());
            }
        }

        context.pop();
        context.fireDoneParsingEntity(WSDLConstants.QNAME_MESSAGE, message);
        return message;
    }

    private MessagePart parseMessagePart(TWSDLParserContextImpl context, Element e) {
        context.push();
        context.registerNamespaces(e);
        MessagePart part = new MessagePart(forest.locatorTable.getStartLocation(e));
        String partName = Util.getRequiredAttribute(e, Constants.ATTR_NAME);
        part.setName(partName);

        String elementAttr =
            XmlUtil.getAttributeOrNull(e, Constants.ATTR_ELEMENT);
        String typeAttr = XmlUtil.getAttributeOrNull(e, Constants.ATTR_TYPE);

        if (elementAttr != null) {
            if (typeAttr != null) {
                Util.fail("parsing.onlyOneOfElementOrTypeRequired", partName);
            }

            part.setDescriptor(context.translateQualifiedName(elementAttr));
            part.setDescriptorKind(SchemaKinds.XSD_ELEMENT);
        } else if (typeAttr != null) {
            part.setDescriptor(context.translateQualifiedName(typeAttr));
            part.setDescriptorKind(SchemaKinds.XSD_TYPE);
        } else {
            // XXX-NOTE - this is wrong; for extensibility purposes,
            // any attribute can be specified on a <part> element, so
            // we need to put an extensibility hook here
            errReceiver.warning(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_ELEMENT_OR_TYPE_REQUIRED(partName));
        }

        context.pop();
        context.fireDoneParsingEntity(WSDLConstants.QNAME_PART, part);
        return part;
    }

    private PortType parsePortType(
        TWSDLParserContextImpl context,
        Definitions definitions,
        Element e) {
        context.push();
        context.registerNamespaces(e);
        PortType portType = new PortType(definitions, forest.locatorTable.getStartLocation(e));
        String name = Util.getRequiredAttribute(e, Constants.ATTR_NAME);
        portType.setName(name);

        boolean gotDocumentation = false;

        for (Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();) {
            Element e2 = Util.nextElement(iter);
            if (e2 == null)
                break;

            if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_DOCUMENTATION)) {
                if (gotDocumentation) {
                    errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(e.getLocalName()));
                }
                gotDocumentation = true;
                if(portType.getDocumentation() == null)
                    portType.setDocumentation(getDocumentationFor(e2));
            } else if (
                XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_OPERATION)) {
                Operation op = parsePortTypeOperation(context, e2);
                op.setParent(portType);
                portType.add(op);
            } else {
                // possible extensibility element -- must live outside the WSDL namespace
                checkNotWsdlElement(e2);
                if (!handleExtension(context, portType, e2)) {
                    checkNotWsdlRequired(e2);
                }
            }/*else {
                Util.fail(
                    "parsing.invalidElement",
                    e2.getTagName(),
                    e2.getNamespaceURI());
            }*/
        }

        context.pop();
        context.fireDoneParsingEntity(WSDLConstants.QNAME_PORT_TYPE, portType);
        return portType;
    }

    private Operation parsePortTypeOperation(
        TWSDLParserContextImpl context,
        Element e) {
        context.push();
        context.registerNamespaces(e);

        Operation operation = new Operation(forest.locatorTable.getStartLocation(e));
        String name = Util.getRequiredAttribute(e, Constants.ATTR_NAME);
        operation.setName(name);
        String parameterOrderAttr =
            XmlUtil.getAttributeOrNull(e, Constants.ATTR_PARAMETER_ORDER);
        operation.setParameterOrder(parameterOrderAttr);

        boolean gotDocumentation = false;

        boolean gotInput = false;
        boolean gotOutput = false;
        boolean gotFault = false;
        boolean inputBeforeOutput = false;

        for (Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();) {
            Element e2 = Util.nextElement(iter);
            if (e2 == null)
                break;

            if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_DOCUMENTATION)) {
                if (gotDocumentation) {
                    errReceiver.error(forest.locatorTable.getStartLocation(e2), WsdlMessages.PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(e2.getLocalName()));
                }
                gotDocumentation = true;
                if(operation.getDocumentation() == null)
                    operation.setDocumentation(getDocumentationFor(e2));
            } else if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_INPUT)) {
                if (gotInput) {
                    errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_TOO_MANY_ELEMENTS(Constants.TAG_INPUT,
                            Constants.TAG_OPERATION,
                            name));
                }

                context.push();
                context.registerNamespaces(e2);
                Input input = new Input(forest.locatorTable.getStartLocation(e2), errReceiver);
                input.setParent(operation);
                String messageAttr =
                    Util.getRequiredAttribute(e2, Constants.ATTR_MESSAGE);
                input.setMessage(context.translateQualifiedName(messageAttr));
                String nameAttr =
                    XmlUtil.getAttributeOrNull(e2, Constants.ATTR_NAME);
                input.setName(nameAttr);
                operation.setInput(input);
                gotInput = true;
                if (gotOutput) {
                    inputBeforeOutput = false;
                }

                // check for extensiblity attributes
                for (Iterator iter2 = XmlUtil.getAllAttributes(e2);
                     iter2.hasNext();
                ) {
                    Attr e3 = (Attr)iter2.next();
                    if (e3.getLocalName().equals(Constants.ATTR_MESSAGE) ||
                        e3.getLocalName().equals(Constants.ATTR_NAME))
                        continue;

                    // possible extensibility element -- must live outside the WSDL namespace
                    checkNotWsdlAttribute(e3);
                    if (!handleExtension(context, input, e3, e2)) {
                        // ignore the extensiblity attribute
                        // TODO throw a WARNING
                    }
                }

                // verify that there is at most one child element and it is a documentation element
                boolean gotDocumentation2 = false;
                for (Iterator iter2 = XmlUtil.getAllChildren(e2);
                     iter2.hasNext();
                    ) {
                    Element e3 = Util.nextElement(iter2);
                    if (e3 == null)
                        break;

                    if (XmlUtil
                        .matchesTagNS(e3, WSDLConstants.QNAME_DOCUMENTATION)) {
                        if (gotDocumentation2) {
                            errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(e.getLocalName()));
                        }
                        gotDocumentation2 = true;
                        input.setDocumentation(getDocumentationFor(e3));
                    } else {
                        errReceiver.error(forest.locatorTable.getStartLocation(e3), WsdlMessages.PARSING_INVALID_ELEMENT(e3.getTagName(),
                            e3.getNamespaceURI()));
                    }
                }
                context.pop();
            } else if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_OUTPUT)) {
                if (gotOutput) {
                    errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_TOO_MANY_ELEMENTS(Constants.TAG_INPUT,
                            Constants.TAG_OPERATION,
                            name));
                }

                context.push();
                context.registerNamespaces(e2);
                Output output = new Output(forest.locatorTable.getStartLocation(e2), errReceiver);
                output.setParent(operation);
                String messageAttr =
                    Util.getRequiredAttribute(e2, Constants.ATTR_MESSAGE);
                output.setMessage(context.translateQualifiedName(messageAttr));
                String nameAttr =
                    XmlUtil.getAttributeOrNull(e2, Constants.ATTR_NAME);
                output.setName(nameAttr);
                operation.setOutput(output);
                gotOutput = true;
                if (gotInput) {
                    inputBeforeOutput = true;
                }

                // check for extensiblity attributes
                for (Iterator iter2 = XmlUtil.getAllAttributes(e2);
                     iter2.hasNext();
                ) {
                    Attr e3 = (Attr)iter2.next();
                    if (e3.getLocalName().equals(Constants.ATTR_MESSAGE) ||
                        e3.getLocalName().equals(Constants.ATTR_NAME))
                        continue;

                    // possible extensibility element -- must live outside the WSDL namespace
                    checkNotWsdlAttribute(e3);
                    if (!handleExtension(context, output, e3, e2)) {
                        // ignore the extensiblity attribute
                        // TODO throw a WARNING
                    }
                }

                // verify that there is at most one child element and it is a documentation element
                boolean gotDocumentation2 = false;
                for (Iterator iter2 = XmlUtil.getAllChildren(e2);
                     iter2.hasNext();
                    ) {
                    Element e3 = Util.nextElement(iter2);
                    if (e3 == null)
                        break;

                    if (XmlUtil
                        .matchesTagNS(e3, WSDLConstants.QNAME_DOCUMENTATION)) {
                        if (gotDocumentation2) {
                            errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(e.getLocalName()));
                        }
                        gotDocumentation2 = true;
                        output.setDocumentation(getDocumentationFor(e3));
                    } else {
                        errReceiver.error(forest.locatorTable.getStartLocation(e3), WsdlMessages.PARSING_INVALID_ELEMENT(e3.getTagName(),
                            e3.getNamespaceURI()));
                    }
                }
                context.pop();
            } else if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_FAULT)) {
                context.push();
                context.registerNamespaces(e2);
                Fault fault = new Fault(forest.locatorTable.getStartLocation(e2));
                fault.setParent(operation);
                String messageAttr =
                    Util.getRequiredAttribute(e2, Constants.ATTR_MESSAGE);
                fault.setMessage(context.translateQualifiedName(messageAttr));
                String nameAttr =
                    XmlUtil.getAttributeOrNull(e2, Constants.ATTR_NAME);
                fault.setName(nameAttr);
                operation.addFault(fault);
                gotFault = true;

                // check for extensiblity attributes
                for (Iterator iter2 = XmlUtil.getAllAttributes(e2);
                     iter2.hasNext();
                ) {
                    Attr e3 = (Attr)iter2.next();
                    if (e3.getLocalName().equals(Constants.ATTR_MESSAGE) ||
                        e3.getLocalName().equals(Constants.ATTR_NAME))
                        continue;

                    // possible extensibility element -- must live outside the WSDL namespace
                    checkNotWsdlAttribute(e3);
                    if (!handleExtension(context, fault, e3, e2)) {
                        // ignore the extensiblity attribute
                        // TODO throw a WARNING
                    }
                }

                // verify that there is at most one child element and it is a documentation element
                boolean gotDocumentation2 = false;
                for (Iterator iter2 = XmlUtil.getAllChildren(e2);
                     iter2.hasNext();
                    ) {
                    Element e3 = Util.nextElement(iter2);
                    if (e3 == null)
                        break;

                    if (XmlUtil
                        .matchesTagNS(e3, WSDLConstants.QNAME_DOCUMENTATION)) {
                        if (gotDocumentation2) {
                            errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(e.getLocalName()));
                        }
                        gotDocumentation2 = true;
                        if(fault.getDocumentation() == null)
                            fault.setDocumentation(getDocumentationFor(e3));
                    } else {
                        // possible extensibility element -- must live outside the WSDL namespace
                        checkNotWsdlElement(e3);
                        if (!handleExtension(context, fault, e3)) {
                            checkNotWsdlRequired(e3);
                        }
                    }/*else {
                        Util.fail(
                            "parsing.invalidElement",
                            e3.getTagName(),
                            e3.getNamespaceURI());
                    }*/
                }
                context.pop();
            } else {
                // possible extensibility element -- must live outside the WSDL namespace
                checkNotWsdlElement(e2);
                if (!handleExtension(context, operation, e2)) {
                    checkNotWsdlRequired(e2);
                }
            }/*else {
                Util.fail(
                    "parsing.invalidElement",
                    e2.getTagName(),
                    e2.getNamespaceURI());
            }*/
        }

        if (gotInput && !gotOutput && !gotFault) {
            operation.setStyle(OperationStyle.ONE_WAY);
        } else if (gotInput && gotOutput && inputBeforeOutput) {
            operation.setStyle(OperationStyle.REQUEST_RESPONSE);
        } else if (gotInput && gotOutput && !inputBeforeOutput) {
            operation.setStyle(OperationStyle.SOLICIT_RESPONSE);
        } else if (gotOutput && !gotInput && !gotFault) {
            operation.setStyle(OperationStyle.NOTIFICATION);
        } else {
            errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_INVALID_OPERATION_STYLE(name));
        }

        context.pop();
        context.fireDoneParsingEntity(WSDLConstants.QNAME_OPERATION, operation);
        return operation;
    }

    private Binding parseBinding(
        TWSDLParserContextImpl context,
        Definitions definitions,
        Element e) {
        context.push();
        context.registerNamespaces(e);
        Binding binding = new Binding(definitions, forest.locatorTable.getStartLocation(e));
        String name = Util.getRequiredAttribute(e, Constants.ATTR_NAME);
        binding.setName(name);
        String typeAttr = Util.getRequiredAttribute(e, Constants.ATTR_TYPE);
        binding.setPortType(context.translateQualifiedName(typeAttr));

        boolean gotDocumentation = false;

        for (Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();) {
            Element e2 = Util.nextElement(iter);
            if (e2 == null)
                break;

            if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_DOCUMENTATION)) {
                if (gotDocumentation) {
                    errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(e.getLocalName()));
                }
                gotDocumentation = true;
                binding.setDocumentation(getDocumentationFor(e2));
            } else if (
                XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_OPERATION)) {
                BindingOperation op = parseBindingOperation(context, e2);
                binding.add(op);
            } else {
                // possible extensibility element -- must live outside the WSDL namespace
                checkNotWsdlElement(e2);
                if (!handleExtension(context, binding, e2)) {
                    checkNotWsdlRequired(e2);
                }
            }
        }

        context.pop();
        context.fireDoneParsingEntity(WSDLConstants.QNAME_BINDING, binding);
        return binding;
    }

    private BindingOperation parseBindingOperation(
        TWSDLParserContextImpl context,
        Element e) {
        context.push();
        context.registerNamespaces(e);
        BindingOperation operation = new BindingOperation(forest.locatorTable.getStartLocation(e));
        String name = Util.getRequiredAttribute(e, Constants.ATTR_NAME);
        operation.setName(name);

        boolean gotDocumentation = false;

        boolean gotInput = false;
        boolean gotOutput = false;
        boolean gotFault = false;
        boolean inputBeforeOutput = false;

        for (Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();) {
            Element e2 = Util.nextElement(iter);
            if (e2 == null)
                break;
            if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_DOCUMENTATION)) {
                if (gotDocumentation) {
                    errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(e.getLocalName()));
                }
                gotDocumentation = true;
                operation.setDocumentation(getDocumentationFor(e2));
            } else if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_INPUT)) {
                if (gotInput) {
                    errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_TOO_MANY_ELEMENTS(Constants.TAG_INPUT,
                            Constants.TAG_OPERATION,
                            name));
                }

                /* Here we check for the use scenario */
                Iterator itere2 = XmlUtil.getAllChildren(e2);
                context.push();
                context.registerNamespaces(e2);
                BindingInput input = new BindingInput(forest.locatorTable.getStartLocation(e2));
                String nameAttr =
                    XmlUtil.getAttributeOrNull(e2, Constants.ATTR_NAME);
                input.setName(nameAttr);
                operation.setInput(input);
                gotInput = true;
                if (gotOutput) {
                    inputBeforeOutput = false;
                }

                // verify that there is at most one child element and it is a documentation element
                boolean gotDocumentation2 = false;
                for (Iterator iter2 = XmlUtil.getAllChildren(e2);
                     iter2.hasNext();
                    ) {
                    Element e3 = Util.nextElement(iter2);
                    if (e3 == null)
                        break;

                    if (XmlUtil
                        .matchesTagNS(e3, WSDLConstants.QNAME_DOCUMENTATION)) {
                        if (gotDocumentation2) {
                            errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(e.getLocalName()));
                        }
                        gotDocumentation2 = true;
                        input.setDocumentation(getDocumentationFor(e3));
                    } else {
                        // possible extensibility element -- must live outside the WSDL namespace
                        checkNotWsdlElement(e3);
                        if (!handleExtension(context, input, e3)) {
                            checkNotWsdlRequired(e3);
                        }
                    }
                }
                context.pop();
            } else if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_OUTPUT)) {
                if (gotOutput) {
                    errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_TOO_MANY_ELEMENTS(Constants.TAG_INPUT,
                            Constants.TAG_OPERATION,
                            name));
                }

                context.push();
                context.registerNamespaces(e2);
                BindingOutput output = new BindingOutput(forest.locatorTable.getStartLocation(e2));
                String nameAttr =
                    XmlUtil.getAttributeOrNull(e2, Constants.ATTR_NAME);
                output.setName(nameAttr);
                operation.setOutput(output);
                gotOutput = true;
                if (gotInput) {
                    inputBeforeOutput = true;
                }

                // verify that there is at most one child element and it is a documentation element
                boolean gotDocumentation2 = false;
                for (Iterator iter2 = XmlUtil.getAllChildren(e2);
                     iter2.hasNext();
                    ) {

                    Element e3 = Util.nextElement(iter2);
                    if (e3 == null)
                        break;

                    if (XmlUtil
                        .matchesTagNS(e3, WSDLConstants.QNAME_DOCUMENTATION)) {
                        if (gotDocumentation2) {
                            errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(e.getLocalName()));
                        }
                        gotDocumentation2 = true;
                        output.setDocumentation(getDocumentationFor(e3));
                    } else {
                        // possible extensibility element -- must live outside the WSDL namespace
                        checkNotWsdlElement(e3);
                        if (!handleExtension(context, output, e3)) {
                            checkNotWsdlRequired(e3);
                        }
                    }
                }
                context.pop();
            } else if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_FAULT)) {
                context.push();
                context.registerNamespaces(e2);
                BindingFault fault = new BindingFault(forest.locatorTable.getStartLocation(e2));
                String nameAttr =
                    Util.getRequiredAttribute(e2, Constants.ATTR_NAME);
                fault.setName(nameAttr);
                operation.addFault(fault);
                gotFault = true;

                // verify that there is at most one child element and it is a documentation element
                boolean gotDocumentation2 = false;
                for (Iterator iter2 = XmlUtil.getAllChildren(e2);
                     iter2.hasNext();
                    ) {
                    Element e3 = Util.nextElement(iter2);
                    if (e3 == null)
                        break;

                    if (XmlUtil
                        .matchesTagNS(e3, WSDLConstants.QNAME_DOCUMENTATION)) {
                        if (gotDocumentation2) {
                            errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(e.getLocalName()));
                        }
                        gotDocumentation2 = true;
                        if(fault.getDocumentation() == null)
                            fault.setDocumentation(getDocumentationFor(e3));
                    } else {
                        // possible extensibility element -- must live outside the WSDL namespace
                        checkNotWsdlElement(e3);
                        if (!handleExtension(context, fault, e3)) {
                            checkNotWsdlRequired(e3);
                        }
                    }
                }
                context.pop();
            } else {
                // possible extensibility element -- must live outside the WSDL namespace
                checkNotWsdlElement(e2);
                if (!handleExtension(context, operation, e2)) {
                    checkNotWsdlRequired(e2);
                }
            }
        }

        if (gotInput && !gotOutput && !gotFault) {
            operation.setStyle(OperationStyle.ONE_WAY);
        } else if (gotInput && gotOutput && inputBeforeOutput) {
            operation.setStyle(OperationStyle.REQUEST_RESPONSE);
        } else if (gotInput && gotOutput && !inputBeforeOutput) {
            operation.setStyle(OperationStyle.SOLICIT_RESPONSE);
        } else if (gotOutput && !gotInput && !gotFault) {
            operation.setStyle(OperationStyle.NOTIFICATION);
        } else {
            errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_INVALID_OPERATION_STYLE(name));
        }

        context.pop();
        context.fireDoneParsingEntity(WSDLConstants.QNAME_OPERATION, operation);
        return operation;
    }

    private Import parseImport(
        TWSDLParserContextImpl context,
        Definitions definitions,
        Element e) {
        context.push();
        context.registerNamespaces(e);
        Import anImport = new Import(forest.locatorTable.getStartLocation(e));
        String namespace =
            Util.getRequiredAttribute(e, Constants.ATTR_NAMESPACE);
        anImport.setNamespace(namespace);
        String location = Util.getRequiredAttribute(e, Constants.ATTR_LOCATION);
        anImport.setLocation(location);

        // according to the schema in the WSDL 1.1 spec, an import can have a documentation element
        boolean gotDocumentation = false;

        for (Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();) {
            Element e2 = Util.nextElement(iter);
            if (e2 == null)
                break;

            if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_DOCUMENTATION)) {
                if (gotDocumentation) {
                    errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(e.getLocalName()));
                }
                gotDocumentation = true;
                anImport.setDocumentation(getDocumentationFor(e2));
            } else {
                errReceiver.error(forest.locatorTable.getStartLocation(e2), WsdlMessages.PARSING_INVALID_ELEMENT(e2.getTagName(),
                    e2.getNamespaceURI()));
            }
        }
        context.pop();
        context.fireDoneParsingEntity(WSDLConstants.QNAME_IMPORT, anImport);
        return anImport;
    }

    private Service parseService(
        TWSDLParserContextImpl context,
        Definitions definitions,
        Element e) {
        context.push();
        context.registerNamespaces(e);
        Service service = new Service(definitions, forest.locatorTable.getStartLocation(e));
        String name = Util.getRequiredAttribute(e, Constants.ATTR_NAME);
        service.setName(name);

        boolean gotDocumentation = false;

        for (Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();) {
            Element e2 = Util.nextElement(iter);
            if (e2 == null)
                break;

            if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_DOCUMENTATION)) {
                if (gotDocumentation) {
                    errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(e.getLocalName()));
                }
                gotDocumentation = true;
                if(service.getDocumentation() == null)
                    service.setDocumentation(getDocumentationFor(e2));
            } else if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_PORT)) {
                Port port = parsePort(context, definitions, e2);
                service.add(port);
            } else {
                // possible extensibility element -- must live outside the WSDL namespace
                checkNotWsdlElement(e2);
                if (!handleExtension(context, service, e2)) {
                    checkNotWsdlRequired(e2);
                }
            }
        }

        context.pop();
        context.fireDoneParsingEntity(WSDLConstants.QNAME_SERVICE, service);
        return service;
    }

    private Port parsePort(
        TWSDLParserContextImpl context,
        Definitions definitions,
        Element e) {
        context.push();
        context.registerNamespaces(e);

        Port port = new Port(definitions, forest.locatorTable.getStartLocation(e));
        String name = Util.getRequiredAttribute(e, Constants.ATTR_NAME);
        port.setName(name);

        String bindingAttr =
            Util.getRequiredAttribute(e, Constants.ATTR_BINDING);
        port.setBinding(context.translateQualifiedName(bindingAttr));

        boolean gotDocumentation = false;

        for (Iterator iter = XmlUtil.getAllChildren(e); iter.hasNext();) {
            Element e2 = Util.nextElement(iter);
            if (e2 == null)
                break;

            if (XmlUtil.matchesTagNS(e2, WSDLConstants.QNAME_DOCUMENTATION)) {
                if (gotDocumentation) {
                    errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_ONLY_ONE_DOCUMENTATION_ALLOWED(e.getLocalName()));
                }
                gotDocumentation = true;
                if(port.getDocumentation() == null)
                    port.setDocumentation(getDocumentationFor(e2));
            } else {
                // possible extensibility element -- must live outside the WSDL namespace
                checkNotWsdlElement(e2);
                if (!handleExtension(context, port, e2)) {
                    checkNotWsdlRequired(e2);
                }
            }
        }

        context.pop();
        context.fireDoneParsingEntity(WSDLConstants.QNAME_PORT, port);
        return port;
    }

    private void validateSchemaImports(Element typesElement){
        for (Iterator iter = XmlUtil.getAllChildren(typesElement); iter.hasNext();) {
            Element e = Util.nextElement(iter);
            if (e == null)
                break;
            if (XmlUtil.matchesTagNS(e, SchemaConstants.QNAME_IMPORT)) {
                errReceiver.warning(forest.locatorTable.getStartLocation(e), WsdlMessages.WARNING_WSI_R_2003());
            }else{
                checkNotWsdlElement(e);
//                if (XmlUtil.matchesTagNS(e, SchemaConstants.QNAME_SCHEMA)) {
//                    forest.getInlinedSchemaElement().add(e);
//                }

            }
        }
    }


    private boolean handleExtension(
        TWSDLParserContextImpl context,
        TWSDLExtensible entity,
        Element e) {
        TWSDLExtensionHandler h =
             (TWSDLExtensionHandler) extensionHandlers.get(e.getNamespaceURI());
        if (h == null) {
            context.fireIgnoringExtension(e, (Entity) entity);
            return false;
        } else {
            return h.doHandleExtension(context, entity, e);
        }
    }

    private boolean handleExtension(
        TWSDLParserContextImpl context,
        TWSDLExtensible entity,
        Node n,
        Element e) {
        TWSDLExtensionHandler h =
            (TWSDLExtensionHandler) extensionHandlers.get(n.getNamespaceURI());
        if (h == null) {
            context.fireIgnoringExtension(e, (Entity) entity);
            return false;
        } else {
            return h.doHandleExtension(context, entity, e);
        }
    }

    private void checkNotWsdlElement(Element e) {
        // possible extensibility element -- must live outside the WSDL namespace
        if (e.getNamespaceURI().equals(Constants.NS_WSDL))
            errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_INVALID_WSDL_ELEMENT(e.getTagName()));
    }

    private void checkNotWsdlAttribute(Attr a) {
        // possible extensibility element -- must live outside the WSDL namespace
        if (a.getNamespaceURI().equals(Constants.NS_WSDL))
            errReceiver.error(forest.locatorTable.getStartLocation(a.getOwnerElement()), WsdlMessages.PARSING_INVALID_WSDL_ELEMENT(a.getLocalName()));
    }

    private void checkNotWsdlRequired(Element e) {
        // check the wsdl:required attribute, fail if set to "true"
        String required =
            XmlUtil.getAttributeNSOrNull(
                e,
                Constants.ATTR_REQUIRED,
                Constants.NS_WSDL);
        if (required != null && required.equals(Constants.TRUE)) {
            errReceiver.error(forest.locatorTable.getStartLocation(e), WsdlMessages.PARSING_REQUIRED_EXTENSIBILITY_ELEMENT(e.getTagName(),
                e.getNamespaceURI()));
        }
    }

    private Documentation getDocumentationFor(Element e) {
        String s = XmlUtil.getTextForNode(e);
        if (s == null) {
            return null;
        } else {
            return new Documentation(s);
        }
    }
}
