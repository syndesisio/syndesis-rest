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
package com.redhat.ipaas.runtime;

import org.jboss.resteasy.spi.CorsHeaders;
import org.jboss.resteasy.spi.DefaultOptionsMethodException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.*;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Arrays;
import java.util.List;

@Service
@EnableConfigurationProperties
@ConfigurationProperties("cors")
@Provider
public class CorsOptionsFeature implements Feature {
    private List<String> allowedOrigins = Arrays.asList("*");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public class DefaultOptionsExceptionMapper implements ExceptionMapper<DefaultOptionsMethodException> {

        @Context HttpHeaders httpHeaders;

        @Override
        public Response toResponse(DefaultOptionsMethodException exception) {

            String origin = httpHeaders.getHeaderString(CorsHeaders.ORIGIN);
            if (!allowedOrigins.contains("*") && !allowedOrigins.contains(origin)) {
                return exception.getResponse();
            }

            final Response.ResponseBuilder response = Response.fromResponse(exception.getResponse());
            if( origin!=null )
                response.header(CorsHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);

            String requestHeaders = httpHeaders.getHeaderString(CorsHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
            if (requestHeaders != null)
                response.header(CorsHeaders.ACCESS_CONTROL_ALLOW_HEADERS, requestHeaders);

            String requestMethods = httpHeaders.getHeaderString(CorsHeaders.ACCESS_CONTROL_REQUEST_METHOD);
            if (requestMethods != null)
                response.header(CorsHeaders.ACCESS_CONTROL_ALLOW_METHODS, requestMethods);

            return response.build();
        }
    }

    @Override
    public boolean configure(FeatureContext context) {
        context.register(new DefaultOptionsExceptionMapper());
        return true;
    }
}
