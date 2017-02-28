/**
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.ipaas.rest.v1.controller.handler;

import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * A endpoint which will issue a redirect.
 */
@Service
@Path("/redirect")
public class RedirectHandler {

    @Path("{target:.*}")
    @GET
    public Response get(@Context HttpServletRequest request, @PathParam("target") String path) throws URISyntaxException {
        String queryString = request.getQueryString();
        if( queryString!=null ) {
            path += "?"+queryString;
        }
        URI uri = new URI(path);
        return Response.seeOther(uri).build();
    }

}
