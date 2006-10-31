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

package org.apache.cxf.common.util;

import java.util.List;

import junit.framework.TestCase;

public class StringUtilsTest extends TestCase {
    public void testTrim() throws Exception {
        String target = "////soapport///";
        assertEquals("soapport", StringUtils.trim(target, "/"));
    }
    
    public void testDiff() throws Exception {
        String str1 = "http://local/SoapContext/SoapPort/greetMe/me/CXF";
        String str2 = "http://local/SoapContext/SoapPort";
        String str3 = "http://local/SoapContext/SoapPort/";
        assertEquals("/greetMe/me/CXF", StringUtils.diff(str1, str2));
        assertEquals("greetMe/me/CXF", StringUtils.diff(str1, str3));
        assertEquals("http://local/SoapContext/SoapPort/", StringUtils.diff(str3, str1));
    }
    
    public void testGetFirstNotEmpty() throws Exception {        
        assertEquals("greetMe", StringUtils.getFirstNotEmpty("/greetMe/me/CXF", "/"));
        assertEquals("greetMe", StringUtils.getFirstNotEmpty("greetMe/me/CXF", "/"));
    }
    
    public void testGetParts() throws Exception {
        String str = "/greetMe/me/CXF";
        List<String> parts = StringUtils.getParts(str, "/");
        assertEquals(3, parts.size());
        assertEquals("greetMe", parts.get(0));
        assertEquals("me", parts.get(1));
        assertEquals("CXF", parts.get(2));
    }
}
