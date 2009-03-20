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

package org.apache.cxf.systest.jaxrs;

import javax.ws.rs.ConsumeMime;
import javax.ws.rs.CookieParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.ProduceMime;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.cxf.customer.book.BookNotFoundFault;

public interface BookSubresource {
    
    @GET
    @Path("/subresource")
    @ProduceMime("application/xml")
    Book getTheBook() throws BookNotFoundFault;
    
    @POST
    @Path("/subresource2/{n1:.*}")
    @ConsumeMime("text/plain")
    @ProduceMime("application/xml")
    Book getTheBook2(@PathParam("n1") String name1,
                     @QueryParam("n2") String name2,
                     @QueryParam("n3") String name3,
                     @HeaderParam("N4") String name4,
                     @CookieParam("n5") String name5,
                     String name6) throws BookNotFoundFault;
    
    @POST
    @Path("/subresource3")
    Book getTheBook3(MultivaluedMap<String, String> form) throws BookNotFoundFault;
    
}

