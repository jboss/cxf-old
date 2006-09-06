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

package org.apache.cxf.xjc.dv;

import javax.xml.bind.DatatypeConverter;

import junit.framework.TestCase;

import org.apache.cxf.configuration.foo.Foo;
import org.apache.ws.jaxme.impl.DatatypeConverterImpl;



public class DefaultValueTest extends TestCase {

    public void testFooDefaultValues() throws Exception {

        DatatypeConverter.setDatatypeConverter(new DatatypeConverterImpl());
        
        Foo foo = new org.apache.cxf.configuration.foo.ObjectFactory().createFoo();

        // verify default attribute values

        assertEquals("hello", foo.getStringAttr());
        assertEquals(3, foo.getBase64BinaryAttr().length);

        // verify default element values
 
         
    }
    
    
}
