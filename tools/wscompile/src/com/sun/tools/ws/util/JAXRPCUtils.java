/*
 * $Id: JAXRPCUtils.java,v 1.1 2005-05-24 13:49:43 bbissett Exp $
 */

/*
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.sun.tools.ws.util;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.UUID;

/**
 * @author Vivek Pandey
 *
 * Wrapper utility class to be used from the generated code or run time.
 */
public final class JAXRPCUtils {
    public static String getUUID(){
         return UUID.randomUUID().toString();
    }



    public static String getFileOrURLName(String fileOrURL) {
        try{
            try {
                return escapeSpace(new URL(fileOrURL).toExternalForm());
            } catch (MalformedURLException e) {
                return new File(fileOrURL).getCanonicalFile().toURL().toExternalForm();
            }
        } catch (Exception e) {
            // try it as an URL
            return fileOrURL;
        }
    }

    private static String escapeSpace( String url ) {
        // URLEncoder didn't work.
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < url.length(); i++) {
            // TODO: not sure if this is the only character that needs to be escaped.
            if (url.charAt(i) == ' ')
                buf.append("%20");
            else
                buf.append(url.charAt(i));
        }
        return buf.toString();
    }

    public static String absolutize(String name) {
        // absolutize all the system IDs in the input,
        // so that we can map system IDs to DOM trees.
        try {
            URL baseURL = new File(".").getCanonicalFile().toURL();
            return new URL(baseURL, name).toExternalForm();
        } catch( IOException e ) {
            ; // ignore
        }
        return name;
    }

    /**
     * Checks if the system ID is absolute.
     */
    public static  void checkAbsoluteness(String systemId) {
        // we need to be able to handle system IDs like "urn:foo", which java.net.URL can't process,
        // but OTOH we also need to be able to process system IDs like "file://a b c/def.xsd",
        // which java.net.URI can't process. So for now, let's fail only if both of them fail.
        // eventually we need a proper URI class that works for us.
        try {
            new URL(systemId);
        } catch( MalformedURLException _ ) {
            try {
                new URI(systemId);
            } catch (URISyntaxException e ) {
                throw new IllegalArgumentException("system ID '"+systemId+"' isn't absolute",e);
            }
        }
    }


}
