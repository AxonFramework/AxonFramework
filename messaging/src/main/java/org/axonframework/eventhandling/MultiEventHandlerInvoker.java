/*
 * Copyright (c) 2010-2018. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.Collections;
import java.util.List;

/**
 * Implementation of {@link EventHandlerInvoker} with capabilities to invoke several different invokers.
 *
 * @author Milan Savic
 * @since 3.3
 */
public class MultiEventHandlerInvoker implements EventHandlerInvoker {

    private final List<EventHandlerInvoker> delegates;

    /**
     * Initializes multi invoker with different invokers. Invokers of instance {@link MultiEventHandlerInvoker} will be
     * flattened.
     *
     * @param delegates which will be used to do the actual event handling
     */
    public MultiEventHandlerInvoker(EventHandlerInvoker... delegates) {
        this(Arrays.asList(delegates));
    }

    /**
     * Initializes multi invoker with different invokers. Invokers of instance {@link MultiEventHandlerInvoker} will be
     * flattened.
     *
     * @param delegates which will be used to do the actual event handling
     */
    public MultiEventHandlerInvoker(List<EventHandlerInvoker> delegates) {
        this.delegates = flatten(delegates);
    }

    private List<EventHandlerInvoker> flatten(List<EventHandlerInvoker> invokers) {
        List<EventHandlerInvoker> flattened = new ArrayList<>();
        for (EventHandlerInvoker invoker : invokers) {
            if (invoker instanceof MultiEventHandlerInvoker) {
                flattened.addAll(((MultiEventHandlerInvoker) invoker).delegates());
            } else {
                flattened.add(invoker);
            }
        }
        return flattened;
    }

    public List<EventHandlerInvoker> delegates() {
        return Collections.unmodifiableList(delegates);
    }

    @Override
    public boolean canHandle(EventMessage<?> eventMessage, Segment segment) {
        return delegates.stream().anyMatch(i -> i.canHandle(eventMessage, segment));
    }

    @Override
    public void handle(EventMessage<?> message, Segment segment) throws Exception {
        for (EventHandlerInvoker i : delegates) {
            if (i.canHandle(message, segment)) {
                i.handle(message, segment);
            }
        }
    }

    @Override
    public boolean supportsReset() {
        return delegates.stream()
                        .anyMatch(EventHandlerInvoker::supportsReset);
    }

    @Override
    public void performReset() {
        delegates.stream()
                 .filter(EventHandlerInvoker::supportsReset)
                 .forEach(EventHandlerInvoker::performReset);
    }
}
