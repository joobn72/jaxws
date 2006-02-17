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

package com.sun.xml.ws.sandbox.handler;

import com.sun.xml.ws.api.message.Packet;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import javax.activation.DataHandler;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.handler.MessageContext;

/**
 *
 * @author WS Development Team
 */

public class MessageContextImpl implements MessageContext {
    
    Map<String,Object> internalMap = new HashMap<String,Object>(); 
    Set<String> appScopeProps;
    /** Creates a new instance of MessageContextImpl */
    public MessageContextImpl(Packet packet) {       
       
       internalMap.putAll(packet.createMapView());
       internalMap.putAll(packet.invocationProperties);
       internalMap.putAll(packet.otherProperties);
       appScopeProps =  packet.getApplicationScopePropertyNames(false);
              
    }
    
    public void setScope(String name, Scope scope) {
        //TODO: check in intrenalMap
        if(scope == MessageContext.Scope.APPLICATION) {
            appScopeProps.add(name);
        } else {
            appScopeProps.remove(name);
                
        }   
    }

    public Scope getScope(String name) {
        if(appScopeProps.contains(name)) {
            return MessageContext.Scope.APPLICATION;
        } else {
            return MessageContext.Scope.HANDLER;
        }    
    }
    
    public int size() {
        return internalMap.size();
    }

    public boolean isEmpty() {
        return internalMap.isEmpty();
    }

    public boolean containsKey(Object key) {
        return internalMap.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return internalMap.containsValue(value);
    }
    
    public Object put(String key, Object value) {
        return internalMap.put(key,value);
    }
    public Object get(Object key) {
        return internalMap.get(key);
    }

    public void putAll(Map<? extends String, ? extends Object> t) {
        internalMap.putAll(t);
    }
    
    public void clear() {
        internalMap.clear();
    }
    public Object remove(Object key){
        return internalMap.remove(key);
    }
    public Set<String> keySet() {
        return internalMap.keySet();
    }
    public Set<Map.Entry<String, Object>> entrySet(){
        return internalMap.entrySet();
    }
    public Collection<Object> values() {
        return internalMap.values();
    }
    
    /**
     * Fill a {@link Packet} with values of this {@link MessageContext}.
     */
    public void fill(Packet packet) {
        //Remove properties which are removed by user.
        for (String key : packet.createMapView().keySet()) {
            if(!internalMap.containsKey(key)) {
                packet.remove(key);
                appScopeProps.remove(key);
            } else {
                Object value = internalMap.get(key);
                packet.put(key,value);
                internalMap.remove(key);
            }
        }
        
        //Remove properties which are removed by user.
        for (String key : packet.otherProperties.keySet()) {
            if(!internalMap.containsKey(key)) {
                packet.otherProperties.remove(key);
                appScopeProps.remove(key);
            } else {
                packet.otherProperties.put(key,internalMap.get(key));
                internalMap.remove(key);
            }
        }
        
        //Remove properties which are removed by user.
        for (String key : packet.invocationProperties.keySet()) {
            if(!internalMap.containsKey(key)) {
                packet.invocationProperties.remove(key);
                appScopeProps.remove(key);
            } else {
                packet.invocationProperties.put(key,internalMap.get(key));
                internalMap.remove(key);
            }
        }
        
        packet.invocationProperties.putAll(internalMap);
        /*
        for (Entry<String,Object> entry : internalMap.entrySet()) {
                String key = entry.getKey();
                if(packet.supports(key)) {
                    packet.put(key,entry.getValue());
                } else if(packet.otherProperties.containsKey(key)) {
                    packet.otherProperties.put(key,entry.getValue());                        
                } else {   
                    packet.invocationProperties.put(key,entry.getValue());
                }
        }
               
        */
        
    }
    
}