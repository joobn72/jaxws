/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.xml.ws.server.sei;

import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.Messages;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.model.MEP;
import com.sun.xml.ws.api.model.SEIModel;
import com.sun.xml.ws.api.model.wsdl.WSDLOperation;
import com.sun.xml.ws.model.AbstractSEIModelImpl;
import com.sun.xml.ws.model.CheckedExceptionImpl;
import com.sun.xml.ws.model.JavaMethodImpl;

/**
 * An {@link EndpointMethodDispatcher} that uses
 * WS-Addressing Action Message Addressing Property, <code>wsa:Action</code>,
 * as the key for dispatching.
 * <p/>
 * A map of all wsa:Actions on the port and the corresponding {@link EndpointMethodHandler}
 * is initialized in the constructor. The wsa:Action value is extracted from
 * the request {@link Packet} and used as the key to return the correct
 * handler.
 *
 * @author Arun Gupta
 */
final class ResponseActionBasedDispatcher extends ActionBasedDispatcher {
    private final Map<String, EndpointMethodHandler> actionMethodHandlers;
	private String defaultFaultAction;

    public ResponseActionBasedDispatcher(SEIModel model, WSBinding binding, InvokerSource invoker, EndpointMethodDispatcherGetter owner, EndpointMethodHandlerFactory factory) {
    	super(model, binding, invoker, true, owner, factory);
		defaultFaultAction = av != null ? av.getDefaultFaultAction() : null;
        actionMethodHandlers = new HashMap<String, EndpointMethodHandler>();

        Map<QName, JavaMethodImpl> syncMethods = new HashMap<QName, JavaMethodImpl>();
        for( JavaMethodImpl m : ((AbstractSEIModelImpl)model).getJavaMethods() ) {
        	if (!MEP.REQUEST_RESPONSE.equals(m.getMEP())) 
        		continue;
        	syncMethods.put(m.getOperation().getName(), m);
        }
        
        for( JavaMethodImpl m : ((AbstractSEIModelImpl)model).getJavaMethods() ) {
        	if (!MEP.ASYNC_CALLBACK.equals(m.getMEP())) 
        		continue;
            EndpointMethodHandler handler = factory.create(invoker,m,binding, owner);
            String action = m.getInputAction();
            //first look at annotations and then in wsdlmodel
            if(action != null && !action.equals("")) {
                actionMethodHandlers.put(action, handler);
            } else {
            	WSDLOperation op = m.getOperation().getOperation();
                action = op.getInput().getAction();
                if (action != null)
                    actionMethodHandlers.put(action, handler);
            }
            action = m.getOutputAction();
            //first look at annotations and then in wsdlmodel
            if(action != null && !action.equals("")) {
                actionMethodHandlers.put(action, handler);
            } else {
            	WSDLOperation op = m.getOperation().getOperation();
                action = op.getOutput() != null ? op.getOutput().getAction() : null;
                if (action != null)
                    actionMethodHandlers.put(action, handler);
            }    
            JavaMethodImpl s = syncMethods.get(m.getOperation().getName());
            if (s != null)
                for (CheckedExceptionImpl ce : s.getCheckedExceptions()) {
                	action = ce.getFaultAction();
                    //first look at annotations and then in wsdlmodel
                    if(action != null && !action.equals("")) {
                        actionMethodHandlers.put(action, handler);
                    } else {
                    	WSDLOperation op = m.getOperation().getOperation();
                    	String opNS = op.getName().getNamespaceURI();
                        action = opNS + "/" + op.getPortTypeName().getLocalPart() + "/" + op.getName().getLocalPart() + "Fault:" + ce.getDetailType().tagName.getLocalPart();
                        actionMethodHandlers.put(action, handler);
                        action = opNS + "/" + op.getPortTypeName().getLocalPart() + "/" + op.getName().getLocalPart() + "/Fault/" + ce.getDetailType().tagName.getLocalPart();
                        actionMethodHandlers.put(action, handler);
                        if(opNS.endsWith("/"))
                        {
                        	//To support this format "opNS/localPart/..." rather than "opNS//localPart/..." 
                        	action = opNS + op.getPortTypeName().getLocalPart() + "/" + op.getName().getLocalPart() + "Fault:" + ce.getDetailType().tagName.getLocalPart();
                            actionMethodHandlers.put(action, handler);
                            action = opNS + op.getPortTypeName().getLocalPart() + "/" + op.getName().getLocalPart() + "/Fault/" + ce.getDetailType().tagName.getLocalPart();
                            actionMethodHandlers.put(action, handler);
                        }
                        
                    } 
                  //workaround put a Fake Action "OperationFault" for handle fault in case of no @Addressing set on server side  
                  actionMethodHandlers.put(m.getOperation().getOperation().getName().toString()+"/Fault", handler);
                }
        }
    }

    public EndpointMethodHandler getEndpointMethodHandler(Packet request) throws DispatchException {

        String action = CorrelationPropertySet.
          getSOAPAction(request, av, binding.getSOAPVersion());

        if (action == null){
            // this message doesn't contain addressing headers, which is legal.
            // this happens when the server is capable of processing addressing but the client didn't send them
        	
        	//add process if message is fault
        	if (request.getMessage().isFault()){
        		QName opName = (QName)request.invocationProperties.get(javax.xml.ws.handler.MessageContext.WSDL_OPERATION);
        		if(opName == null){
        			//bad, no way to send the fault to client side
        			return null;
        		}
        		return actionMethodHandlers.get(opName.toString()+"/Fault");
        	}
        	
            return null;
        }

        EndpointMethodHandler h = actionMethodHandlers.get(action);
        if (h != null)
            return h;

        if (defaultFaultAction != null && defaultFaultAction.equals(action) &&
            request.supports(CorrelationPropertySet.REQUEST_SOAPACTION)) {
        	return getEndpointMethodHandler((String) request.get(CorrelationPropertySet.REQUEST_SOAPACTION));
        }
        
        // invalid action header
        Message result = Messages.create(action, av, binding.getSOAPVersion());

        throw new DispatchException(result);
    }
}
