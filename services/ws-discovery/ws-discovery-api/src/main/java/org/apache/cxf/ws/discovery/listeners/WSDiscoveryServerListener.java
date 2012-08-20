/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.ws.discovery.listeners;

import javax.xml.namespace.QName;

import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.endpoint.ServerLifeCycleListener;
import org.apache.cxf.ws.discovery.WSDiscoveryService;

/**
 * 
 */
public class WSDiscoveryServerListener implements ServerLifeCycleListener {
    WSDiscoveryService service;
    
    public WSDiscoveryServerListener(Bus bus) {
        service = bus.getExtension(WSDiscoveryService.class);
        if (service == null) {
            service = new WSDiscoveryService(bus);
            bus.setExtension(service, WSDiscoveryService.class);
        }
    }

    public void startServer(Server server) {
        QName sn = server.getEndpoint().getEndpointInfo().getInterface().getName();
        System.out.println(sn);
        if ("http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01".equals(sn.getNamespaceURI())) {
            return;
        }
        service.serverStarted(server);
    }

    public void stopServer(Server server) {
        QName sn = server.getEndpoint().getEndpointInfo().getInterface().getName();
        if ("http://docs.oasis-open.org/ws-dd/ns/discovery/2009/01".equals(sn.getNamespaceURI())) {
            return;
        }
        service.serverStopped(server);
    }
}
