/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling;

import org.axonframework.common.Registration;
import org.axonframework.messaging.MessageHandlerInterceptor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static java.util.Objects.requireNonNull;

/**
 * @author Rene de Waele
 */
public abstract class AbstractEventProcessor implements EventProcessor {
    private final String name;
    private final EventProcessingStrategy processingStrategy;
    private final Set<MessageHandlerInterceptor<EventMessage<?>>> interceptors = new CopyOnWriteArraySet<>();

    public AbstractEventProcessor(String name, EventProcessingStrategy processingStrategy) {
        this.name = requireNonNull(name);
        this.processingStrategy = requireNonNull(processingStrategy);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void accept(List<? extends EventMessage<?>> events) {
        processingStrategy.handle(events, this::doHandle);
    }

    @Override
    public Registration registerInterceptor(MessageHandlerInterceptor<EventMessage<?>> interceptor) {
        interceptors.add(interceptor);
        return () -> interceptors.remove(interceptor);
    }

    protected abstract void doHandle(List<? extends EventMessage<?>> eventMessages);

    protected Set<MessageHandlerInterceptor<EventMessage<?>>> interceptors() {
        return interceptors;
    }
}
