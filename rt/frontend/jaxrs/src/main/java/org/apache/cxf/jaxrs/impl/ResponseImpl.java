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

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.ResponseProcessingException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.ext.ReaderInterceptor;

import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.cxf.jaxrs.utils.HttpUtils;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;

public final class ResponseImpl extends Response {
    private int status;
    private Object entity;
    private Annotation[] entityAnnotations; 
    private MultivaluedMap<String, Object> metadata;
    
    private Message responseMessage;
    private boolean entityClosed;    
    private boolean entityBufferred;
    
    ResponseImpl(int s) {
        this.status = s;
    }
    
    ResponseImpl(int s, Object e) {
        this.status = s;
        this.entity = e;
    }
    
    void addMetadata(MultivaluedMap<String, Object> meta) { 
        this.metadata = meta;
    }
    
    public void setStatus(int s) { 
        this.status = s;
    }
    
    public void setEntity(Object e, Annotation[] anns) { 
        this.entity = e;
        this.entityAnnotations = anns;
    }
    
    public Annotation[] getEntityAnnotations() {
        return entityAnnotations;
    }

    //TODO: This method is needed because on the client side the
    // Response processing is done after the chain completes, thus
    // PhaseInterceptorChain.getCurrentMessage() returns null.
    // The refactoring will be required
    public void setMessage(Message message) {
        this.responseMessage = message;
    }
    
    public int getStatus() {
        return status;
    }

    public StatusType getStatusInfo() {
        final Response.Status responseStatus = Response.Status.fromStatusCode(status);
        return new Response.StatusType() {

            public Family getFamily() {
                return responseStatus.getFamily();
            }

            public String getReasonPhrase() {
                return responseStatus.getReasonPhrase();
            }

            public int getStatusCode() {
                return responseStatus.getStatusCode();
            } 
            
        };
    }
    
    public Object getEntity() {
        return entity;
    }

    public boolean hasEntity() {
        return entity != null;
    }
    
    public MultivaluedMap<String, Object> getMetadata() {
        return getHeaders();
    }
    
    public MultivaluedMap<String, Object> getHeaders() {
        return metadata;
    }
    
    public MultivaluedMap<String, String> getStringHeaders() {
        MetadataMap<String, String> headers = new MetadataMap<String, String>(metadata.size());
        for (Map.Entry<String, List<Object>> entry : metadata.entrySet()) {
            headers.put(entry.getKey(), toListOfStrings(entry.getValue()));
        }
        return headers;
    }

    private String getHeader(String header) {
        Object value = metadata.getFirst(header);
        return value == null ? null : value.toString();
    }
    
    public String getHeaderString(String header) {
        List<Object> methodValues = metadata.get(header);
        return HttpUtils.getHeaderString(toListOfStrings(methodValues));
    }
    
    // This conversion is needed as some values may not be Strings
    private List<String> toListOfStrings(List<Object> values) {
        if (values == null) {
            return null; 
        } else {
            List<String> stringValues = new ArrayList<String>(values.size());
            for (Object value : values) {
                stringValues.add(value.toString());
            }
            return stringValues;
        }
    }
    
    public Set<String> getAllowedMethods() {
        List<Object> methodValues = metadata.get(HttpHeaders.ALLOW);
        if (methodValues == null) {
            return Collections.emptySet();
        } else {
            Set<String> methods = new HashSet<String>();
            for (Object o : methodValues) {
                methods.add(o.toString());
            }
            return methods;
        }
    }

    
    
    public Map<String, NewCookie> getCookies() {
        List<Object> cookieValues = metadata.get(HttpHeaders.SET_COOKIE);
        if (cookieValues == null) {
            return Collections.emptyMap();
        } else {
            Map<String, NewCookie> cookies = new HashMap<String, NewCookie>();
            for (Object o : cookieValues) {
                NewCookie newCookie = NewCookie.valueOf(o.toString());
                cookies.put(newCookie.getName(), newCookie);
            }
            return cookies;
        }
    }

    public Date getDate() {
        return doGetDate(HttpHeaders.DATE);
    }

    private Date doGetDate(String dateHeader) {
        return HttpUtils.getHttpDate(getHeader(dateHeader));
    }
    
    public EntityTag getEntityTag() {
        String header = getHeader(HttpHeaders.ETAG);
        return header == null ? null : EntityTag.valueOf(header);
    }

    public Locale getLanguage() {
        return HttpUtils.getLocale(getHeader(HttpHeaders.CONTENT_LANGUAGE));
    }

    public Date getLastModified() {
        return doGetDate(HttpHeaders.LAST_MODIFIED);
    }

    public int getLength() {
        return HttpUtils.getContentLength(getHeader(HttpHeaders.CONTENT_LENGTH));
    }

    public URI getLocation() {
        String header = getHeader(HttpHeaders.LOCATION);
        return header == null ? null : URI.create(header);
    }

    public MediaType getMediaType() {
        String header = getHeader(HttpHeaders.CONTENT_TYPE);
        return header == null ? null : MediaType.valueOf(header);
    }
    
    public boolean hasLink(String relation) {
        return getLink(relation) != null;
    }
    
    public Link getLink(String relation) {
        Set<Map.Entry<String, Link>> entries = getAllLinks().entrySet();
        for (Map.Entry<String, Link> entry : entries) {
            if (entry.getKey().contains(relation)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public Link.Builder getLinkBuilder(String relation) {
        return Link.fromLink(getLink(relation));
    }

    public Set<Link> getLinks() {
        return new HashSet<Link>(getAllLinks().values());
    }

    private Map<String, Link> getAllLinks() {
        List<Object> linkValues = metadata.get(HttpHeaders.LINK);
        if (linkValues == null) {
            return Collections.emptyMap();
        } else {
            Map<String, Link> links = new HashMap<String, Link>();
            for (Object o : linkValues) {
                Link link = Link.valueOf(o.toString());
                links.put(link.getRel(), link);
            }
            return links;
        }
    }
    
    public <T> T readEntity(Class<T> cls) throws ProcessingException, IllegalStateException {
        return readEntity(cls, new Annotation[]{});
    }

    public <T> T readEntity(GenericType<T> genType) throws ProcessingException, IllegalStateException {
        return readEntity(genType, new Annotation[]{});
    }

    public <T> T readEntity(Class<T> cls, Annotation[] anns) throws ProcessingException,
        IllegalStateException {
        
        return doReadEntity(cls, cls, anns);
    }

    @SuppressWarnings("unchecked")
    public <T> T readEntity(GenericType<T> genType, Annotation[] anns) throws ProcessingException,
        IllegalStateException {
        return doReadEntity((Class<T>)genType.getRawType(), 
                            genType.getType(), anns);
    }
    
    public <T> T doReadEntity(Class<T> cls, Type t, Annotation[] anns) throws ProcessingException,
        IllegalStateException {
        
        checkEntityIsClosed();
        
        if (!hasEntity()) {
            return null;
        }
        
        if (cls.isAssignableFrom(entity.getClass())) {
            T response = cls.cast(entity);
            closeIfNotBufferred(cls);
            return response;
        }
        
        if (responseMessage != null && entity instanceof InputStream) {
            MediaType mediaType = getMediaType();
            if (mediaType == null) {
                mediaType = MediaType.WILDCARD_TYPE;
            }
            
            List<ReaderInterceptor> readers = ProviderFactory.getInstance(responseMessage)
                .createMessageBodyReaderInterceptor(cls, t, anns, mediaType, 
                                                    responseMessage.getExchange().getOutMessage());
            if (readers != null) {
                try {
                    responseMessage.put(Message.PROTOCOL_HEADERS, this.getMetadata());
                    return cls.cast(JAXRSUtils.readFromMessageBodyReader(readers, cls, t, 
                                                                anns, 
                                                                InputStream.class.cast(entity), 
                                                                mediaType, 
                                                                responseMessage));
                } catch (Exception ex) {
                    throw new ResponseProcessingException(this, ex);
                } finally {
                    closeIfNotBufferred(cls);
                }
            }
        }
        
        throw new ResponseProcessingException(this, "No Message Body reader is available");
    }
    
    private void closeIfNotBufferred(Class<?> responseCls) {
        if (!entityBufferred && !InputStream.class.isAssignableFrom(responseCls)) {
            close();
        }
    }
    
    public boolean bufferEntity() throws ProcessingException {
        if (entityClosed) {
            throw new IllegalStateException();
        }
        if (!entityBufferred && entity instanceof InputStream) {
            try {
                InputStream oldEntity = (InputStream)entity;
                entity = IOUtils.loadIntoBAIS(oldEntity);
                entityBufferred = true;
            } catch (IOException ex) {
                throw new ResponseProcessingException(this, ex);
            }
        }
        return entityBufferred;
    }

    public void close() throws ProcessingException {
        if (!entityClosed) {
            if (entity instanceof InputStream) {
                try {
                    ((InputStream)entity).close();
                    entity = null;
                } catch (IOException ex) {
                    throw new ResponseProcessingException(this, ex);
                }
            }
            entityClosed = true;
            
        }
        
    }
    
    private void checkEntityIsClosed() {
        if (entityClosed) {
            throw new IllegalStateException("Entity is not available");
        }
    }
}
