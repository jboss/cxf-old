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

package org.apache.cxf.ws.discovery;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.xml.ws.Endpoint;

import org.apache.cxf.ws.discovery.internal.WSDiscoveryServiceImpl;
import org.apache.cxf.ws.discovery.wsdl.HelloType;
import org.apache.cxf.ws.discovery.wsdl.ProbeMatchType;
import org.apache.cxf.ws.discovery.wsdl.ProbeMatchesType;
import org.apache.cxf.ws.discovery.wsdl.ProbeType;


/**
 * 
 */
public final class WSDiscoveryClientTest {

    private WSDiscoveryClientTest() {
        
    }
    
    
    public static void main(String[] arg) throws Exception {
        try {
            Endpoint ep = Endpoint.publish("http://localhost:51919/Foo/Snarf", new FooImpl());
            WSDiscoveryServiceImpl service = new WSDiscoveryServiceImpl(null);
            service.startup();
            
            
            WSDiscoveryClient c = new WSDiscoveryClient();
            HelloType h = service.register(ep.getEndpointReference());
            
            
            System.out.println("1");
            ProbeMatchesType pmts = c.probe(new ProbeType());
            System.out.println("2");
            if  (pmts != null) {
                for (ProbeMatchType pmt : pmts.getProbeMatch()) {
                    System.out.println("Found " + pmt.getEndpointReference());
                    System.out.println(pmt.getTypes());
                    System.out.println(pmt.getXAddrs());
                }
            }
            pmts = c.probe(new ProbeType());
            System.out.println("3");
            
            if  (pmts != null) {
                for (ProbeMatchType pmt : pmts.getProbeMatch()) {
                    System.out.println("Found " + pmt.getEndpointReference());
                    System.out.println(pmt.getTypes());
                    System.out.println(pmt.getXAddrs());
                }
            }
            service.unregister(h);
            System.out.println("4");
            c.close();
            
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }
    

    @WebService
    public static class FooImpl {
        @WebMethod
        public int echo(int i) {
            return i;
        }
    }

    
}
