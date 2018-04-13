/*
 * Copyright (c) 2010-2018. Axon Framework
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

import java.util.Arrays;
import java.util.List;

/**
 * Invoker with capabilities to invoke several different invokers.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class MultiEventHandlerInvoker implements EventHandlerInvoker {

    private final List<EventHandlerInvoker> invokers;

    /**
     * Initializes multi invoker with different invokers.
     *
     * @param invokers which will be used to do the actual event handling
     */
    public MultiEventHandlerInvoker(EventHandlerInvoker... invokers) {
        this(Arrays.asList(invokers));
    }

    /**
     * Initializes multi invoker with different invokers.
     *
     * @param invokers which will be used to do the actual event handling
     */
    public MultiEventHandlerInvoker(List<EventHandlerInvoker> invokers) {
        this.invokers = invokers;
    }

    @Override
    public boolean canHandle(EventMessage<?> eventMessage, Segment segment) {
        return invokers.stream().anyMatch(i -> i.canHandle(eventMessage, segment));
    }

    @Override
    public void handle(EventMessage<?> message, Segment segment) throws Exception {
        invokers.stream().filter(i -> i.canHandle(message, segment)).forEach(i -> {
            try {
                i.handle(message, segment);
            } catch (Exception e) {
                // do nothing, each handler invoker should invoke its own ListenerInvocationErrorHandler
            }
        });
    }
}
