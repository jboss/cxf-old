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
package org.apache.cxf.transport.http.blueprint;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;

import org.apache.cxf.common.jaxb.JAXBContextCache;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.util.PackageUtils;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;


public class HTTPClientPolicyHolder extends HTTPClientPolicy {
    private static final Logger LOG = LogUtils.getL7dLogger(HTTPClientPolicyHolder.class);

    private String parsedElement;
    private HTTPClientPolicy clientPolicy;

    private JAXBContext jaxbContext;
    private Set<Class<?>> jaxbClasses;

    public HTTPClientPolicyHolder() {
    }

    public void init() {
        try {

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            docFactory.setNamespaceAware(true);

            Element element = docFactory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(parsedElement.getBytes())).getDocumentElement();

            clientPolicy = (HTTPClientPolicy)getJaxbObject(element, HTTPClientPolicy.class);
            
            this.setAccept(clientPolicy.getAccept());
            this.setAcceptEncoding(clientPolicy.getAcceptEncoding());
            this.setAcceptLanguage(clientPolicy.getAcceptLanguage());
            this.setAllowChunking(clientPolicy.isAllowChunking());
            this.setAsyncExecuteTimeout(clientPolicy.getAsyncExecuteTimeout());
            this.setAsyncExecuteTimeoutRejection(clientPolicy.isAsyncExecuteTimeoutRejection());
            this.setAutoRedirect(clientPolicy.isAutoRedirect());
            this.setBrowserType(clientPolicy.getBrowserType());
            this.setCacheControl(clientPolicy.getCacheControl());
            this.setChunkingThreshold(clientPolicy.getChunkingThreshold());
            this.setConnection(clientPolicy.getConnection());
            this.setConnectionTimeout(clientPolicy.getConnectionTimeout());
            this.setContentType(clientPolicy.getContentType());
            this.setCookie(clientPolicy.getCookie());
            this.setDecoupledEndpoint(clientPolicy.getDecoupledEndpoint());
            this.setHost(clientPolicy.getHost());
            this.setMaxRetransmits(clientPolicy.getMaxRetransmits());
            this.setNonProxyHosts(clientPolicy.getNonProxyHosts());
            this.setProxyServer(clientPolicy.getProxyServer());
            this.setProxyServerPort(clientPolicy.getProxyServerPort());
            this.setProxyServerType(clientPolicy.getProxyServerType());
            this.setReceiveTimeout(clientPolicy.getReceiveTimeout());
            this.setReferer(clientPolicy.getReferer());
            
        } catch (Exception e) {
            throw new RuntimeException("Could not process configuration.", e);
        }
    }

    public void destroy() {
        
    }

    public String getParsedElement() {
        return parsedElement;
    }

    public void setParsedElement(String parsedElement) {
        this.parsedElement = parsedElement;
    }

    protected Object getJaxbObject(Element parent, Class<?> c) {

        try {
            Unmarshaller umr = getContext(c).createUnmarshaller();
            JAXBElement<?> ele = (JAXBElement<?>) umr.unmarshal(parent);

            return ele.getValue();
        } catch (JAXBException e) {
            LOG.warning("Unable to parse property due to " + e);
            return null;
        }
    }

    protected synchronized JAXBContext getContext(Class<?> cls) {
        if (jaxbContext == null || jaxbClasses == null || !jaxbClasses.contains(cls)) {
            try {
                Set<Class<?>> tmp = new HashSet<Class<?>>();
                if (jaxbClasses != null) {
                    tmp.addAll(jaxbClasses);
                }
                JAXBContextCache.addPackage(tmp, PackageUtils.getPackageName(cls), 
                                            cls == null ? getClass().getClassLoader() : cls.getClassLoader());
                if (cls != null) {
                    boolean hasOf = false;
                    for (Class<?> c : tmp) {
                        if (c.getPackage() == cls.getPackage() && "ObjectFactory".equals(c.getSimpleName())) {
                            hasOf = true;
                        }
                    }
                    if (!hasOf) {
                        tmp.add(cls);
                    }
                }
                JAXBContextCache.scanPackages(tmp);
                JAXBContextCache.CachedContextAndSchemas ccs 
                    = JAXBContextCache.getCachedContextAndSchemas(tmp, null, null, null, false);
                jaxbClasses = ccs.getClasses();
                jaxbContext = ccs.getContext();
            } catch (JAXBException e) {
                throw new RuntimeException(e);
            }
        }
        return jaxbContext;
    }
    
}
