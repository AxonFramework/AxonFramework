/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.sample.app.socket;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import org.apache.commons.io.IOUtils;
import org.axonframework.core.Event;
import org.axonframework.core.eventhandler.EventBus;
import org.axonframework.core.eventhandler.EventListener;
import org.axonframework.core.eventhandler.EventSequencingPolicy;
import org.axonframework.core.eventhandler.SequentialPolicy;
import org.joda.time.LocalDateTime;

import java.io.IOException;
import java.net.Socket;

/**
 * @author Allard Buijze
 */
public class SocketPrintingEventListener implements EventListener {

    private final Socket socket;
    private final XStream xStream;
    private final EventBus eventBus;

    public SocketPrintingEventListener(Socket socket, EventBus eventBus) {
        xStream = new XStream();
        xStream.registerConverter(new LocalDateTimeConverter());
        this.socket = socket;
        this.eventBus = eventBus;
        eventBus.subscribe(this);
    }

    @Override
    public boolean canHandle(Class<? extends Event> eventType) {
        return !socket.isClosed();
    }

    @Override
    public void handle(Event event) {
        System.out.println("Pushing event details through socket");
        try {
            String eventString = xStream.toXML(event);
            eventString = eventString.replaceAll("\n(\r)?", IOUtils.LINE_SEPARATOR);
            socket.getOutputStream().write(eventString.getBytes("UTF-8"));
        }
        catch (Exception e) {
            System.out.println("Exception occurred. Socket seems closed.");
            try {
                socket.close();
            } catch (IOException e1) {
                // we did our best
            }
        }
        if (socket.isClosed()) {
            eventBus.unsubscribe(this);
            System.out.println("Socket is closed. Unsubscribed event listener");
        }
    }

    @Override
    public EventSequencingPolicy getEventSequencingPolicy() {
        return new SequentialPolicy();
    }

    private static class LocalDateTimeConverter implements SingleValueConverter {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean canConvert(Class type) {
            return type.equals(LocalDateTime.class);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString(Object obj) {
            return obj.toString();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object fromString(String str) {
            return new LocalDateTime(str);
        }
    }

}
