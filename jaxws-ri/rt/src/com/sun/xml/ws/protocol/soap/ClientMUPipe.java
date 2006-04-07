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
package com.sun.xml.ws.protocol.soap;

import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.pipe.Pipe;
import com.sun.xml.ws.api.pipe.PipeCloner;
import com.sun.xml.ws.client.HandlerConfiguration;

import javax.xml.namespace.QName;
import javax.xml.ws.soap.SOAPFaultException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Rama Pulavarthi
 */

public class ClientMUPipe extends MUPipe {

    public ClientMUPipe(WSBinding binding, Pipe next) {
        super(binding, next);
    }

    protected ClientMUPipe(MUPipe that, PipeCloner cloner) {
        super(that,cloner);
    }

    /**
     * Do MU Header Processing on incoming message (repsonse)
     * @return
     *         if all the headers in the packet are understood, returns the packet.
     * @throws SOAPFaultException
     *         if all the headers in the packet are not understood, throws SOAPFaultException
     */

    @Override
    public Packet process(Packet packet) {
        Packet reply = next.process(packet);
        HandlerConfiguration handlerConfig = packet.handlerConfig;
        Set<QName> misUnderstoodHeaders = getMisUnderstoodHeaders(reply.getMessage().getHeaders(),
                handlerConfig.getRoles(),handlerConfig.getKnownHeaders());
        if(misUnderstoodHeaders.isEmpty()) {
            return reply;
        }
        throw createMUSOAPFaultException(misUnderstoodHeaders);

    }

    public Pipe copy(PipeCloner cloner) {
        return new ClientMUPipe(this,cloner);
    }

}
