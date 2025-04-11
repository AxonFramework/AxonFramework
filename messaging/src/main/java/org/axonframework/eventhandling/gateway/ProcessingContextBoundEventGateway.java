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

package org.axonframework.eventhandling.gateway;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;

/**
 * An {@link EventGateway} implementation that is bound to a {@link ProcessingContext}. It delegates any
 * {@link EventGateway} calls to the {@link EventGateway} it wraps, but ensures that the provided
 * {@link ProcessingContext} is used for publication unless a different one is provided.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ProcessingContextBoundEventGateway implements EventGateway {

    private final EventGateway delegate;
    private final ProcessingContext context;

    /**
     * Creates a new {@link EventGateway} that delegates to the given {@code delegate} and uses the given
     * {@code processingContext} for publication.
     *
     * @param delegate          The {@link EventGateway} to delegate to.
     * @param processingContext The {@link ProcessingContext} to use for publication.
     */
    public ProcessingContextBoundEventGateway(EventGateway delegate, ProcessingContext processingContext) {
        this.delegate = delegate;
        this.context = processingContext;
    }

    @Override
    public void publish(@Nonnull List<?> events) {
        delegate.publish(context, events);
    }

    @Override
    public void publish(@Nonnull ProcessingContext context, @Nonnull List<?> events) {
        delegate.publish(context, events);
    }

    @Override
    public EventGateway forProcessingContext(ProcessingContext processingContext) {
        if (processingContext == context) {
            return this;
        }
        return new ProcessingContextBoundEventGateway(delegate, processingContext);
    }
}
