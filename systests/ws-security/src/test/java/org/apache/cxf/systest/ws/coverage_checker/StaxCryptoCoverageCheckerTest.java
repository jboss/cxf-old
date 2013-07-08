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

package org.apache.cxf.systest.ws.coverage_checker;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.systest.ws.common.SecurityTestUtil;
import org.apache.cxf.systest.ws.coverage_checker.server.StaxServer;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.cxf.ws.security.wss4j.WSS4JStaxOutInterceptor;
import org.example.contract.doubleit.DoubleItPortType;
import org.junit.BeforeClass;

/**
 * A set of tests for the StaxCryptoCoverageChecker. It tests both DOM + StAX 
 * clients against the StAX server.
 */
public class StaxCryptoCoverageCheckerTest extends AbstractBusClientServerTestBase {
    public static final String PORT = allocatePort(StaxServer.class);

    private static final String NAMESPACE = "http://www.example.org/contract/DoubleIt";
    private static final QName SERVICE_QNAME = new QName(NAMESPACE, "DoubleItService");

    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue(
                "Server failed to launch",
                // run the server in the same process
                // set this to false to fork
                launchServer(StaxServer.class, true)
        );
    }
    
    @org.junit.AfterClass
    public static void cleanup() throws Exception {
        SecurityTestUtil.cleanup();
        stopAllServers();
    }
    
    @org.junit.Test
    public void testSignedBodyTimestamp() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = StaxCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = StaxCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItBodyTimestampPort");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("user", "alice");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.common.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://schemas.xmlsoap.org/soap/envelope/}Body;"
                     + "{}{http://docs.oasis-open.org/wss/2004/01/oasis-"
                     + "200401-wss-wssecurity-utility-1.0.xsd}Timestamp;");
        
        // DOM
        WSS4JOutInterceptor outInterceptor = new WSS4JOutInterceptor(outProps);
        bus.getOutInterceptors().add(outInterceptor);
        port.doubleIt(25);
        bus.getOutInterceptors().remove(outInterceptor);
        
        // Streaming
        WSS4JStaxOutInterceptor staxOutInterceptor = new WSS4JStaxOutInterceptor(outProps);
        bus.getOutInterceptors().add(staxOutInterceptor);
        port.doubleIt(25);
        
        ((java.io.Closeable)port).close();
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testSignedBodyOnly() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = StaxCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = StaxCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItBodyTimestampPort");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("user", "alice");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.common.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://schemas.xmlsoap.org/soap/envelope/}Body;");
        
        // DOM
        WSS4JOutInterceptor outInterceptor = new WSS4JOutInterceptor(outProps);
        bus.getOutInterceptors().add(outInterceptor);
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not signing the Timestamp");
        } catch (Exception ex) {
            // expected
        }
        bus.getOutInterceptors().remove(outInterceptor);
        
        // Streaming
        WSS4JStaxOutInterceptor staxOutInterceptor = new WSS4JStaxOutInterceptor(outProps);
        bus.getOutInterceptors().add(staxOutInterceptor);
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not signing the Timestamp");
        } catch (Exception ex) {
            // expected
        }
        
        ((java.io.Closeable)port).close();
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testSignedTimestampOnly() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = StaxCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = StaxCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItBodyTimestampPort");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("user", "alice");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.common.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://docs.oasis-open.org/wss/2004/01/oasis-"
                     + "200401-wss-wssecurity-utility-1.0.xsd}Timestamp;");
        
        // DOM
        WSS4JOutInterceptor outInterceptor = new WSS4JOutInterceptor(outProps);
        bus.getOutInterceptors().add(outInterceptor);
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not signing the Timestamp");
        } catch (Exception ex) {
            // expected
        }
        bus.getOutInterceptors().remove(outInterceptor);
        
        // Streaming
        WSS4JStaxOutInterceptor staxOutInterceptor = new WSS4JStaxOutInterceptor(outProps);
        bus.getOutInterceptors().add(staxOutInterceptor);
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not signing the Timestamp");
        } catch (Exception ex) {
            // expected
        }
        
        ((java.io.Closeable)port).close();
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testSignedBodyTimestampSoap12() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = StaxCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = StaxCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItBodyTimestampSoap12Port");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("user", "alice");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.common.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://www.w3.org/2003/05/soap-envelope}Body;"
                     + "{}{http://docs.oasis-open.org/wss/2004/01/oasis-"
                     + "200401-wss-wssecurity-utility-1.0.xsd}Timestamp;");
        
        // DOM
        WSS4JOutInterceptor outInterceptor = new WSS4JOutInterceptor(outProps);
        bus.getOutInterceptors().add(outInterceptor);
        port.doubleIt(25);
        bus.getOutInterceptors().remove(outInterceptor);
        
        // Streaming
        WSS4JStaxOutInterceptor staxOutInterceptor = new WSS4JStaxOutInterceptor(outProps);
        bus.getOutInterceptors().add(staxOutInterceptor);
        port.doubleIt(25);
        
        ((java.io.Closeable)port).close();
        bus.shutdown(true);
    }
   
    @org.junit.Test
    public void testSignedBodyOnlySoap12() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = StaxCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = StaxCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItBodyTimestampSoap12Port");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("user", "alice");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.common.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://www.w3.org/2003/05/soap-envelope}Body;");
        
        // DOM
        WSS4JOutInterceptor outInterceptor = new WSS4JOutInterceptor(outProps);
        bus.getOutInterceptors().add(outInterceptor);
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not signing the Timestamp");
        } catch (Exception ex) {
            // expected
        }
        bus.getOutInterceptors().remove(outInterceptor);
        
        // Streaming
        WSS4JStaxOutInterceptor staxOutInterceptor = new WSS4JStaxOutInterceptor(outProps);
        bus.getOutInterceptors().add(staxOutInterceptor);
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not signing the Timestamp");
        } catch (Exception ex) {
            // expected
        }
        
        ((java.io.Closeable)port).close();
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testSignedTimestampOnlySoap12() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = StaxCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = StaxCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItBodyTimestampSoap12Port");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("user", "alice");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.common.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://docs.oasis-open.org/wss/2004/01/oasis-"
                     + "200401-wss-wssecurity-utility-1.0.xsd}Timestamp;");
        
        // DOM
        WSS4JOutInterceptor outInterceptor = new WSS4JOutInterceptor(outProps);
        bus.getOutInterceptors().add(outInterceptor);
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not signing the Timestamp");
        } catch (Exception ex) {
            // expected
        }
        bus.getOutInterceptors().remove(outInterceptor);
        
        // Streaming
        WSS4JStaxOutInterceptor staxOutInterceptor = new WSS4JStaxOutInterceptor(outProps);
        bus.getOutInterceptors().add(staxOutInterceptor);
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not signing the Timestamp");
        } catch (Exception ex) {
            // expected
        }
        
        ((java.io.Closeable)port).close();
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testSignedEncryptedBody() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = StaxCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = StaxCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItSignedEncryptedBodyPort");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature Encrypt");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("encryptionPropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/bob.properties");
        outProps.put("user", "alice");
        outProps.put("encryptionUser", "bob");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.common.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://schemas.xmlsoap.org/soap/envelope/}Body;");
        outProps.put("encryptionParts",
                     "{}{http://schemas.xmlsoap.org/soap/envelope/}Body;");

        // DOM
        WSS4JOutInterceptor outInterceptor = new WSS4JOutInterceptor(outProps);
        bus.getOutInterceptors().add(outInterceptor);
        port.doubleIt(25);
        bus.getOutInterceptors().remove(outInterceptor);
        
        // Streaming
        WSS4JStaxOutInterceptor staxOutInterceptor = new WSS4JStaxOutInterceptor(outProps);
        bus.getOutInterceptors().add(staxOutInterceptor);
        port.doubleIt(25);
        
        ((java.io.Closeable)port).close();
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testSignedNotEncryptedBody() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = StaxCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = StaxCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItSignedEncryptedBodyPort");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature Encrypt");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("encryptionPropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/bob.properties");
        outProps.put("user", "alice");
        outProps.put("encryptionUser", "bob");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.common.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://schemas.xmlsoap.org/soap/envelope/}Body;");
        outProps.put("encryptionParts",
                     "{}{http://docs.oasis-open.org/wss/2004/01/oasis-"
                     + "200401-wss-wssecurity-utility-1.0.xsd}Timestamp;");
        
        // DOM
        WSS4JOutInterceptor outInterceptor = new WSS4JOutInterceptor(outProps);
        bus.getOutInterceptors().add(outInterceptor);
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not encrypting the SOAP Body");
        } catch (Exception ex) {
            // expected
        }
        bus.getOutInterceptors().remove(outInterceptor);
        
        // Streaming
        WSS4JStaxOutInterceptor staxOutInterceptor = new WSS4JStaxOutInterceptor(outProps);
        bus.getOutInterceptors().add(staxOutInterceptor);
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not encrypting the SOAP Body");
        } catch (Exception ex) {
            // expected
        }
        
        ((java.io.Closeable)port).close();
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testWSAddressing() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = StaxCryptoCoverageCheckerTest.class.getResource("client/client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = StaxCryptoCoverageCheckerTest.class.getResource("DoubleItCoverageChecker.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItWSAPort");
        DoubleItPortType port = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(port, PORT);
        
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "Timestamp Signature");
        outProps.put("signaturePropFile", 
                     "org/apache/cxf/systest/ws/wssec10/client/alice.properties");
        outProps.put("user", "alice");
        outProps.put("passwordCallbackClass", 
                     "org.apache.cxf.systest.ws.common.KeystorePasswordCallback");
        outProps.put("signatureParts",
                     "{}{http://schemas.xmlsoap.org/soap/envelope/}Body;"
                     + "{}{http://docs.oasis-open.org/wss/2004/01/oasis-"
                     + "200401-wss-wssecurity-utility-1.0.xsd}Timestamp;");

        // DOM
        WSS4JOutInterceptor outInterceptor = new WSS4JOutInterceptor(outProps);
        bus.getOutInterceptors().add(outInterceptor);
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not signing the WS-Addressing headers");
        } catch (Exception ex) {
            // expected
        }
        bus.getOutInterceptors().remove(outInterceptor);
        
        // Streaming
        WSS4JStaxOutInterceptor staxOutInterceptor = new WSS4JStaxOutInterceptor(outProps);
        bus.getOutInterceptors().add(staxOutInterceptor);
        
        try {
            port.doubleIt(25);
            fail("Failure expected on not signing the WS-Addressing headers");
        } catch (Exception ex) {
            // expected
        }
        bus.getOutInterceptors().remove(staxOutInterceptor);
        
        // Now sign the WS-Addressing headers
        outProps.put("signatureParts",
                "{}{http://schemas.xmlsoap.org/soap/envelope/}Body;"
                + "{}{http://docs.oasis-open.org/wss/2004/01/oasis-"
                + "200401-wss-wssecurity-utility-1.0.xsd}Timestamp;"
                + "{}{http://www.w3.org/2005/08/addressing}ReplyTo;");
        
        // DOM
        outInterceptor = new WSS4JOutInterceptor(outProps);
        bus.getOutInterceptors().add(outInterceptor);
        
        port.doubleIt(25);
        bus.getOutInterceptors().remove(outInterceptor);
        
        // Streaming
        staxOutInterceptor = new WSS4JStaxOutInterceptor(outProps);
        bus.getOutInterceptors().add(staxOutInterceptor);
        
        port.doubleIt(25);
        
        ((java.io.Closeable)port).close();
        bus.shutdown(true);
    }
    
}
