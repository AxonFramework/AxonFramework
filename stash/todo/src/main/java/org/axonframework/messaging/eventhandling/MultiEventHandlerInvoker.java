/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.messaging.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.eventhandling.processing.streaming.segmenting.Segment;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of {@link EventHandlerInvoker} with capabilities to invoke several different invokers.
 *
 * @author Milan Savic
 * @since 3.3
 */
@Deprecated(since = "5.0.0", forRemoval = true)
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
    public MultiEventHandlerInvoker(@Nonnull List<EventHandlerInvoker> delegates) {
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

    @Nonnull
    public List<EventHandlerInvoker> delegates() {
        return Collections.unmodifiableList(delegates);
    }

    @Override
    public boolean canHandle(@Nonnull EventMessage eventMessage, @Nonnull ProcessingContext context, @Nonnull Segment segment) {
        return delegates.stream().anyMatch(i -> canHandle(i, eventMessage, context, segment));
    }

    private boolean canHandle(EventHandlerInvoker invoker, EventMessage eventMessage, ProcessingContext context, Segment segment) {
        return invoker.supportsReset() && invoker.canHandle(eventMessage, context, segment);
    }

    @Override
    public boolean canHandleType(@Nonnull Class<?> payloadType) {
        return delegates.stream().anyMatch(i -> i.canHandleType(payloadType));
    }

    @Override
    public void handle(@Nonnull EventMessage message, @Nonnull ProcessingContext context, @Nonnull Segment segment) throws Exception {
        for (EventHandlerInvoker i : delegates) {
            if (canHandle(i, message, context, segment)) {
                i.handle(message, context, segment);
            }
        }
    }

    @Override
    public boolean supportsReset() {
        return delegates.stream()
                        .anyMatch(EventHandlerInvoker::supportsReset);
    }

    @Override
    public void performReset(ProcessingContext context) {
        performReset(null, context);
    }

    @Override
    public <R> void performReset(R resetContext, ProcessingContext processingContext) {
        delegates.stream()
                 .filter(EventHandlerInvoker::supportsReset)
                 .forEach(eventHandlerInvoker -> eventHandlerInvoker.performReset(resetContext, processingContext));
    }

    @Override
    public Set<Class<?>> supportedEventTypes() {
        return delegates.stream()
                        .flatMap(invoker -> invoker.supportedEventTypes().stream())
                        .collect(Collectors.toSet());
    }
}
