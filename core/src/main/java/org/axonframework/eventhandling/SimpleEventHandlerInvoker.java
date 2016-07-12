/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.eventhandling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * @author Rene de Waele
 */
public class SimpleEventHandlerInvoker implements EventHandlerInvoker {

    private final List<EventListener> eventListeners;
    private final ListenerErrorHandler listenerErrorHandler;

    public SimpleEventHandlerInvoker(Object... eventListeners) {
        this(Arrays.asList(eventListeners), new LoggingListenerErrorHandler());
    }

    public SimpleEventHandlerInvoker(List<?> eventListeners, ListenerErrorHandler listenerErrorHandler) {
        this.eventListeners = new ArrayList<>(eventListeners.stream()
                                                      .map(listener -> listener instanceof EventListener ?
                                                              (EventListener) listener :
                                                              new AnnotationEventListenerAdapter(listener))
                                                      .collect(toList()));
        this.listenerErrorHandler = listenerErrorHandler;
    }

    @Override
    public Object handle(EventMessage<?> message) throws Exception {
        for (EventListener listener : eventListeners) {
            try {
                listener.handle(message);
            } catch (Exception e) {
                listenerErrorHandler.onError(e, message, listener);
            }
        }
        return null;
    }

    @Override
    public boolean hasHandler(EventMessage<?> eventMessage) {
        return true;
    }
}
