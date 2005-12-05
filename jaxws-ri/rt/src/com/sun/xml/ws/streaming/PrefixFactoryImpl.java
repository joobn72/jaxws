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

package com.sun.xml.ws.streaming;

import java.util.HashMap;
import java.util.Map;

/**
 * <p> A prefix factory that caches the prefixes it creates. </p>
 *
 * @author WS Development Team
 */
public class PrefixFactoryImpl implements PrefixFactory {

    public PrefixFactoryImpl(String base) {
        _base = base;
        _next = 1;
    }

    public String getPrefix(String uri) {
        String prefix = null;

        if (_cachedUriToPrefixMap == null) {
            _cachedUriToPrefixMap = new HashMap();
        } else {
            prefix = (String) _cachedUriToPrefixMap.get(uri);
        }

        if (prefix == null) {
            prefix = _base + Integer.toString(_next++);
            _cachedUriToPrefixMap.put(uri, prefix);
        }

        return prefix;
    }

    private String _base;
    private int _next;
    private Map _cachedUriToPrefixMap;
}
