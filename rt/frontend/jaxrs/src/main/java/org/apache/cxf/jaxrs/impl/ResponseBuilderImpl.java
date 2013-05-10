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

import java.lang.annotation.Annotation;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Variant;

import org.apache.cxf.jaxrs.utils.HttpUtils;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;

public final class ResponseBuilderImpl extends ResponseBuilder implements Cloneable {
    private int status = 200;
    private boolean statusSet;
    private Object entity;
    private MultivaluedMap<String, Object> metadata = new MetadataMap<String, Object>();
    private Annotation[] annotations;

    public ResponseBuilderImpl() {
    }

    private ResponseBuilderImpl(ResponseBuilderImpl copy) {
        status = copy.status;
        statusSet = copy.statusSet;
        metadata.putAll(copy.metadata);
        entity = copy.entity;
    }
       
    public Response build() {
        if (entity == null && !statusSet) {
            status = 204;
        }
        ResponseImpl r = new ResponseImpl(status);
        MetadataMap<String, Object> m = 
            new MetadataMap<String, Object>(metadata, false, true);
        r.addMetadata(m);
        r.setEntity(entity, annotations);
        reset();
        return r;
    }

    public ResponseBuilder status(int s) {
        if (s < 100 || s > 599) {
            throw new IllegalArgumentException("Illegal status value : " + s);
        }
        status = s;
        statusSet = true;
        return this;
    }

    public ResponseBuilder entity(Object e) {
        entity = e;
        return this;
    }

    public ResponseBuilder type(MediaType type) {
        return type(type == null ? null : JAXRSUtils.mediaTypeToString(type));
    }

    public ResponseBuilder type(String type) {
        return setHeader(HttpHeaders.CONTENT_TYPE, type);
    }

    @Override
    public ResponseBuilder language(Locale locale) {
        return language(locale == null ? null : locale.toString());
    }
    
    public ResponseBuilder language(String language) {
        return setHeader(HttpHeaders.CONTENT_LANGUAGE, language);
    }

    public ResponseBuilder location(URI loc) {
        if (!loc.isAbsolute()) {
            Message currentMessage = PhaseInterceptorChain.getCurrentMessage();
            if (currentMessage != null) {
                
                UriInfo ui = new UriInfoImpl(currentMessage.getExchange().getInMessage(), null);
                loc = ui.getBaseUriBuilder()
                        .path(loc.getRawPath())
                        .replaceQuery(loc.getRawQuery())
                        .fragment(loc.getRawFragment()).buildFromEncoded();
            }
        }
        return setHeader(HttpHeaders.LOCATION, loc);
    }

    public ResponseBuilder contentLocation(URI location) {
        return setHeader(HttpHeaders.CONTENT_LOCATION, location);
    }

    public ResponseBuilder tag(EntityTag tag) {
        return setHeader(HttpHeaders.ETAG, tag == null ? null : tag.toString());
    }

    public ResponseBuilder tag(String tag) {
        // String tag value needs to be parsed as it may 
        // contain parameters indicating it's a weak tag, etc
        return tag(tag == null ? null : EntityTag.valueOf(tag));
    }

    public ResponseBuilder lastModified(Date date) {
        return setHeader(HttpHeaders.LAST_MODIFIED, date == null ? null : toHttpDate(date));
    }

    public ResponseBuilder cacheControl(CacheControl cacheControl) {
        return setHeader(HttpHeaders.CACHE_CONTROL, cacheControl);
    }

    @Override
    public ResponseBuilder expires(Date date) {
        return setHeader(HttpHeaders.EXPIRES, date == null ? null : toHttpDate(date));
    }

    @Override
    public ResponseBuilder cookie(NewCookie... cookies) {
        return addHeader(HttpHeaders.SET_COOKIE, (Object[])cookies);
    }
    
    public ResponseBuilder header(String name, Object value) {
        if (HttpUtils.isDateRelatedHeader(name)) {
            Object theValue = value instanceof Date ? toHttpDate((Date)value) : value;  
            return setHeader(name, theValue);
        } else if (HttpHeaders.LOCATION.equals(name)) {
            return location(URI.create(value.toString()));
        } else {
            return addHeader(name, value);
        }
    }

    
    @Override
    public ResponseBuilder variant(Variant variant) {
        type(variant == null ? null : variant.getMediaType());
        language(variant == null ? null : variant.getLanguage());
        setHeader(HttpHeaders.CONTENT_ENCODING, variant == null ? null : variant.getEncoding());
        return this;
    }


    @Override
    public ResponseBuilder variants(List<Variant> variants) {
        if (variants == null) {
            metadata.remove(HttpHeaders.VARY);
            return this;
        }
        String acceptVary = null;
        String acceptLangVary = null;
        String acceptEncVary = null;
        for (Variant v : variants) {
            MediaType mt = v.getMediaType();
            if (mt != null) {
                acceptVary = HttpHeaders.ACCEPT;
                addHeader(HttpHeaders.CONTENT_TYPE, mt);
            }
            Locale l = v.getLanguage();
            if (l != null) {
                acceptLangVary = HttpHeaders.ACCEPT_LANGUAGE;
                addHeader(HttpHeaders.CONTENT_LANGUAGE, l);
            }
            String enc = v.getEncoding();
            if (enc != null) {
                acceptEncVary = HttpHeaders.ACCEPT_ENCODING;
                addHeader(HttpHeaders.CONTENT_ENCODING, enc);
            }
        }
        handleVaryValue(acceptVary, acceptLangVary, acceptEncVary);
        return this;
    }
    
    private void handleVaryValue(String ...values) {
        List<Object> varyValues = metadata.get(HttpHeaders.VARY);
        for (String v : values) {
            if (v == null) {
                metadata.remove(null);
                if (varyValues != null) {
                    varyValues.remove(null);
                }
            } else {
                addHeader(HttpHeaders.VARY, v);
            }
        }
    }
    
//  CHECKSTYLE:OFF
    @Override
    public ResponseBuilder clone() {
        return new ResponseBuilderImpl(this);
    }
//  CHECKSTYLE:ON

    
    private void reset() {
        metadata.clear();
        entity = null;
        annotations = null;
        status = 200;
    }
    
    private String toHttpDate(Date date) {
        SimpleDateFormat format = HttpUtils.getHttpDateFormat();
        return format.format(date);
    }
    
    private ResponseBuilder setHeader(String name, Object value) {
        if (value == null) {
            metadata.remove(name);
        } else {
            metadata.putSingle(name, value.toString());
        }
        return this;
    }
    
    private ResponseBuilder addHeader(String name, Object... values) {
        if (values != null && values.length >= 1 && values[0] != null) {
            for (Object value : values) {
                if (!valueExists(name, value)) {
                    metadata.add(name, value.toString());
                }
            }
        } else {
            metadata.remove(name);
        }    
        return this;
    }
    
    private boolean valueExists(String key, Object value) {
        List<Object> values = metadata.get(key);
        return values == null ? false : values.contains(value.toString());
    }

    @Override
    public ResponseBuilder allow(String... methods) {
        return addHeader(HttpHeaders.ALLOW, (Object[])methods);
    }

    @Override
    public ResponseBuilder allow(Set<String> methods) {
        if (methods == null) {
            return allow();
        } else {
            return allow(methods.toArray(new String[methods.size()]));
        }
    }

    @Override
    public ResponseBuilder encoding(String encoding) {
        return setHeader(HttpHeaders.CONTENT_ENCODING, encoding);
    }

    @Override
    public ResponseBuilder entity(Object ent, Annotation[] anns) {
        this.annotations = anns;
        this.entity = ent;
        return this;
    }

    @Override
    public ResponseBuilder link(URI href, String rel) {
        Link.Builder linkBuilder = new LinkBuilderImpl();
        return links(linkBuilder.uri(href).rel(rel).build());
    }

    @Override
    public ResponseBuilder link(String href, String rel) {
        Link.Builder linkBuilder = new LinkBuilderImpl();
        return links(linkBuilder.uri(href).rel(rel).build());
    }

    @Override
    public ResponseBuilder links(Link... links) {
        return addHeader(HttpHeaders.LINK, (Object[])links);
    }

    @Override
    public ResponseBuilder replaceAll(MultivaluedMap<String, Object> map) {
        metadata.clear();
        if (map != null) {
            metadata.putAll(map);
        }
        return this;
    }

    @Override
    public ResponseBuilder variants(Variant... variants) {
        if (variants == null) {
            return variants((List<Variant>)null);
        }
        return variants(Arrays.asList(variants));
    }
}
