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

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.redhat.ipaas.core.EventBus;

import org.springframework.stereotype.Component;

/**
 * A simple event bus to abstract registering/sending Server Sent Events to browser clients
 * which have a subscribed to events.  This could potentially be implemented using a messaging broker.
 */
@Component
public class SimpleAsyncEventBus implements EventBus {

    //FIFO Queue
    private static Queue<Event> queue = new LinkedList<Event>();
    
    public static enum Type {BroadCast, Send};
    
    private static final ExecutorService threadpool = Executors.newFixedThreadPool(1);
    
    private final ConcurrentHashMap<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public Subscription subscribe(String subscriberId, Subscription handler) {
        return subscriptions.put(subscriberId, handler);
    }
    public Subscription unsubscribe(String subscriberId) {
        return subscriptions.remove(subscriberId);
    }

    public void broadcast(String event, String data) {
        Event e = new Event(Type.BroadCast, event, data);
        queue.add(e);
        threadpool.submit(new EventTask());
    }

    public void send(String subscriberId, String event, String data) {
        Event e = new Event(Type.Send, subscriberId, event, data);
        queue.add(e);
        threadpool.submit(new EventTask());
    }
    
    private class EventTask implements Callable<Void> {
        
        @Override
        public Void call() throws Exception {
            Event event = queue.poll();
            while (event != null) {
                if (Type.BroadCast.equals(event.getType())) {
                    for (Map.Entry<String, Subscription> entry : subscriptions.entrySet()) {
                        entry.getValue().onEvent(event.getEvent(), event.getData());
                    }
                } else if (Type.Send.equals(event.getType())) {
                    Subscription sub = subscriptions.get(event.getSubscriberId());
                    if( sub!=null ) {
                        sub.onEvent(event.getEvent(), event.getData());
                    }
                }
                event = queue.poll();
            }
            return null;
        }
    }
    
    private class Event {

        private final Type type;
        private final String subscriberId;
        private final String event;
        private final String data;
        
        public Event(Type type, String subscriberId, String event, String data) {
            super();
            this.type = type;
            this.subscriberId = subscriberId;
            this.event = event;
            this.data = data;
        }
        
        public Event(Type type, String event, String data) {
            super();
            this.type = type;
            this.subscriberId = null;
            this.event = event;
            this.data = data;
        }

        public Type getType() {
            return type;
        }

        public String getEvent() {
            return event;
        }

        public String getData() {
            return data;
        }

        public String getSubscriberId() {
            return subscriberId;
        }

    }


}
