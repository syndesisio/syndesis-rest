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

import com.redhat.ipaas.api.EventBus;
import io.undertow.Handlers;
import io.undertow.server.handlers.sse.ServerSentEventConnection;
import io.undertow.server.handlers.sse.ServerSentEventConnectionCallback;
import io.undertow.server.handlers.sse.ServerSentEventHandler;
import io.undertow.servlet.api.DeploymentInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.stereotype.Component;

import static io.undertow.Handlers.serverSentEvents;

/**
 * Connects the the EventBus to an Undertow Sever Side Event handler at the "/api/v1/events/{:subscription}" path.
 */
@Component

public class SSEUndertowCustomizer implements UndertowDeploymentInfoCustomizer {

    private final EventBus bus;

    private String path = "/api/v1/events";

    @Autowired
    public SSEUndertowCustomizer(EventBus bus) {
        this.bus = bus;
    }

    public class EventBusHandler implements ServerSentEventConnectionCallback {

        String subscriptionId;

        @Override
        public void connected(ServerSentEventConnection connection, String lastEventId) {
            String uri = connection.getRequestURI();
            if( uri.indexOf(path+"/") != 0 ) {
                connection.send("Invalid path", "error", null, null);
                connection.shutdown();
                return;
            }

            subscriptionId = uri.substring(path.length()+1);
            if( subscriptionId.isEmpty() ) {
                connection.send("Invalid subscription id", "error", null, null);
                connection.shutdown();
                return;
            }

            connection.send("connected", "status", null, null);
            bus.subscribe(subscriptionId, (type, data)->{
                connection.send(data, type, null, null);
            });
        }

    }

    @Override
    public void customize(DeploymentInfo deploymentInfo) {
        final ServerSentEventHandler sseHandler = serverSentEvents(new EventBusHandler());
        deploymentInfo.addInitialHandlerChainWrapper(handler -> {
                return Handlers.path()
                    .addPrefixPath("/", handler)
                    .addPrefixPath(path, sseHandler);
            }
        );
    }
}
