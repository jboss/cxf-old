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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.Message;

public abstract class AbstractPropertiesImpl {

    private static final String PROPERTY_KEY = "jaxrs.filter.properties";
    
    protected Message m;
    private Map<String, Object> props;
    public AbstractPropertiesImpl(Message message) {
        this.m = message;
        this.props = CastUtils.cast((Map<?, ?>)message.get(PROPERTY_KEY));
    }
    
    public Object getProperty(String name) {
        return props == null ? null : props.get(name);
    }

    public void removeProperty(String name) {
        if (props != null) {
            props.remove(name);    
        }
    }


    public void setProperty(String name, Object value) {
        if (props == null) {
            props = new HashMap<String, Object>();
            m.put(PROPERTY_KEY, props);
        }    
        props.put(name, value);    
        
    }

    public Collection<String> getPropertyNames() {
        return props == null ? Collections.<String>emptyList() 
            : Collections.unmodifiableSet(props.keySet());
    }
}
