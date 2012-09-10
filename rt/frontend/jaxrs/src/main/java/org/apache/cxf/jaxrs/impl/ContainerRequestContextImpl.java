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
package org.apache.cxf.jaxrs.impl;

import java.io.InputStream;
import java.net.URI;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.Message;

public class ContainerRequestContextImpl implements ContainerRequestContext {

    private static final String PROPERTY_KEY = "jaxrs.filter.properties";
    private static final String ENDPOINT_ADDRESS_PROPERTY = "org.apache.cxf.transport.endpoint.address";
    
    private HttpHeaders h;
    private Message m;
    private Map<String, Object> props;
    private boolean preMatch;
    private boolean responseContext;
    public ContainerRequestContextImpl(Message message, boolean preMatch, boolean responseContext) {
        this.m = message;
        this.props = CastUtils.cast((Map<?, ?>)message.get(PROPERTY_KEY));
        this.h = new HttpHeadersImpl(message);
        this.preMatch = preMatch;
        this.responseContext = responseContext;
    }
    
    @Override
    public void abortWith(Response response) {
        checkContext();
        m.getExchange().put(Response.class, response);
    }

    @Override
    public List<Locale> getAcceptableLanguages() {
        return getHttpHeaders().getAcceptableLanguages();
    }

    @Override
    public List<MediaType> getAcceptableMediaTypes() {
        return getHttpHeaders().getAcceptableMediaTypes();
    }

    @Override
    public Map<String, Cookie> getCookies() {
        return getHttpHeaders().getCookies();
    }

    @Override
    public Date getDate() {
        return getHttpHeaders().getDate();
    }

    @Override
    public InputStream getEntityStream() {
        return m.get(InputStream.class);
    }

    @Override
    public String getHeaderString(String name) {
        return getHttpHeaders().getHeaderString(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public MultivaluedMap<String, String> getHeaders() {
        h = null;
        return new MetadataMap<String, String>(
            (Map<String, List<String>>)m.get(Message.PROTOCOL_HEADERS), false, true, true);
    }

    @Override
    public Locale getLanguage() {
        return getHttpHeaders().getLanguage();
    }

    @Override
    public int getLength() {
        return getHttpHeaders().getLength();
    }

    @Override
    public MediaType getMediaType() {
        return getHttpHeaders().getMediaType();
    }

    @Override
    public String getMethod() {
        return (String)getProperty(Message.HTTP_REQUEST_METHOD);
    }

    @Override
    public Object getProperty(String name) {
        return props == null ? null : props.get(name);
    }

    @Override
    public Enumeration<String> getPropertyNames() {
        final Iterator<String> it = props.keySet().iterator();
        return new Enumeration<String>() {

            @Override
            public boolean hasMoreElements() {
                return it.hasNext();
            }

            @Override
            public String nextElement() {
                return it.next();
            }
            
        };
    }

    @Override
    public Request getRequest() {
        return new RequestImpl(m);
    }

    @Override
    public SecurityContext getSecurityContext() {
        return new SecurityContextImpl(m);
    }

    @Override
    public UriInfo getUriInfo() {
        return new UriInfoImpl(m);
    }

    @Override
    public boolean hasEntity() {
        return getEntityStream() != null;
    }

    @Override
    public void removeProperty(String name) {
        if (props != null) {
            props.remove(name);    
        }
    }

    @Override
    public void setEntityStream(InputStream is) {
        checkContext();
        m.put(InputStream.class, is);
    }

    @Override
    public void setMethod(String method) throws IllegalStateException {
        checkContext();
        m.put(Message.HTTP_REQUEST_METHOD, method);

    }

    @Override
    public void setProperty(String name, Object value) {
        if (props == null) {
            props = new HashMap<String, Object>();
            m.put(PROPERTY_KEY, props);
        }    
        props.put(name, value);    
        
    }

    @Override
    public void setRequestUri(URI requestUri) throws IllegalStateException {
        if (!preMatch) {
            throw new IllegalStateException();
        }
        m.put(Message.REQUEST_URI, requestUri.toString());
    }

    @Override
    public void setRequestUri(URI baseUri, URI requestUri) throws IllegalStateException {
        setRequestUri(requestUri);
        Object servletRequest = m.get("HTTP.REQUEST");
        if (servletRequest != null) {
            ((javax.servlet.http.HttpServletRequest)servletRequest)
                .setAttribute(ENDPOINT_ADDRESS_PROPERTY, baseUri.toString());
        }
    }

    @Override
    public void setSecurityContext(SecurityContext sc) {
        m.put(SecurityContext.class, sc);
    }

    private HttpHeaders getHttpHeaders() {
        return h != null ? h : new HttpHeadersImpl(m);
    }
    
    private void checkContext() {
        if (responseContext) {
            throw new IllegalStateException();
        }
    }
}
