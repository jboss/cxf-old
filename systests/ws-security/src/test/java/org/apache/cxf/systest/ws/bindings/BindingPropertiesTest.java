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

package org.apache.cxf.systest.ws.bindings;

import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.systest.ws.bindings.server.Server;
import org.apache.cxf.systest.ws.common.SecurityTestUtil;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;

import org.example.contract.doubleit.DoubleItPortType;

import org.junit.BeforeClass;

/**
 * This is a test for various properties associated with a security binding
 */
public class BindingPropertiesTest extends AbstractBusClientServerTestBase {
    static final String PORT = allocatePort(Server.class);
    
    private static final String NAMESPACE = "http://www.example.org/contract/DoubleIt";
    private static final QName SERVICE_QNAME = new QName(NAMESPACE, "DoubleItService");

    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue(
            "Server failed to launch",
            // run the server in the same process
            // set this to false to fork
            launchServer(Server.class, true)
        );
    }
    
    @org.junit.AfterClass
    public static void cleanup() throws Exception {
        SecurityTestUtil.cleanup();
        stopAllServers();
    }
    
    // Child of Body is signed which conflicts with the OnlySignEntireHeadersAndBody property
    @org.junit.Test
    public void testOnlySignEntireHeadersAndBody() throws Exception {

        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = BindingPropertiesTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);

        URL wsdl = BindingPropertiesTest.class.getResource("DoubleItBindings.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
       
        // This should work, as OnlySignEntireHeadersAndBody is not specified
        QName portQName = new QName(NAMESPACE, "DoubleItNotOnlySignPort");
        DoubleItPortType port = service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        port.doubleIt(25);
        
        // This should fail, as OnlySignEntireHeadersAndBody is specified
        portQName = new QName(NAMESPACE, "DoubleItOnlySignPort");
        port = service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        try {
            port.doubleIt(25);
            fail("Failure expected on OnlySignEntireHeadersAndBody property");
        } catch (javax.xml.ws.soap.SOAPFaultException ex) {
            String error = "OnlySignEntireHeadersAndBody does not match the requirements";
            assertTrue(ex.getMessage().contains(error));
        }
        
        ((java.io.Closeable)port).close();
        bus.shutdown(true);
    }
    
}
