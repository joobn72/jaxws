/*
 * $Id: Extension.java,v 1.1 2005-05-24 14:04:12 bbissett Exp $
 */

/*
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package com.sun.tools.ws.wsdl.framework;

/**
 * An entity extending another entity.
 *
 * @author JAX-RPC Development Team
 */
public abstract class Extension extends Entity {

    public Extension() {
    }

    public Extensible getParent() {
        return _parent;
    }

    public void setParent(Extensible parent) {
        _parent = parent;
    }

    public void accept(ExtensionVisitor visitor) throws Exception {
        visitor.preVisit(this);
        visitor.postVisit(this);
    }

    private Extensible _parent;
}
