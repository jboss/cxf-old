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

package org.apache.cxf.message;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.interceptor.InterceptorChain;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.Destination;

public class MessageImpl extends StringMapImpl implements Message {
    private Collection<Attachment> attachments;
    private Conduit conduit;
    private Destination destination;
    private Exchange exchange;
    private String id;
    private InterceptorChain interceptorChain;
    private Map<Class<?>, Object> contents = new HashMap<Class<?>, Object>();
    
    public Collection<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(Collection<Attachment> attachments) {
        this.attachments = attachments;
    }

    public String getAttachmentMimeType() {
        //for sub class overriding
        return null;
    }
    
    public Conduit getConduit() {
        return conduit;
    }

    public Destination getDestination() {
        return destination;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public String getId() {
        return id;
    }

    public InterceptorChain getInterceptorChain() {
        return this.interceptorChain;
    }

    public <T> T getContent(Class<T> format) {
        return format.cast(contents.get(format));
    }

    public <T> void setContent(Class<T> format, Object content) {
        contents.put(format, content);
    }

    public Set<Class<?>> getContentFormats() {
        return contents.keySet();
    }

    public void setConduit(Conduit c) {
        this.conduit = c;
    }

    public void setDestination(Destination d) {
        this.destination = d;
    }

    public void setExchange(Exchange e) {
        this.exchange = e;
    }

    public void setId(String i) {
        this.id = i;
    }

    public void setInterceptorChain(InterceptorChain ic) {
        this.interceptorChain = ic;
    }

    public Object getContextualProperty(String key) {
        Object val = get(key);
        
        if (val == null) {
            val = getExchange().get(key);
        }
        
        if (val == null) {
            OperationInfo ep = get(OperationInfo.class); 
            if (ep != null) {
                val = ep.getProperty(key);
            }
        }
        
        if (val == null) {
            Endpoint ep = getExchange().get(Endpoint.class); 
            if (ep != null) {
                val = ep.get(key);
                
                if (val == null) {
                    val = ep.getEndpointInfo().getProperty(key);
                }

                if (val == null) {
                    val = ep.getEndpointInfo().getBinding().getProperty(key);
                }

            }
        }
        
        if (val == null) {
            Service ep = getExchange().get(Service.class); 
            if (ep != null) {
                val = ep.get(key);
            }
        }
        
        return val;
    }
    
    public static void copyContent(Message m1, Message m2) {
        for (Class<?> c : m1.getContentFormats()) {
            m2.setContent(c, m1.getContent(c));
        }
    }
}
