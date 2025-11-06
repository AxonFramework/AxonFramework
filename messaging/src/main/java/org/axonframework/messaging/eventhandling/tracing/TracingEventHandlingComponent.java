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

package org.axonframework.messaging.eventhandling.tracing;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.eventhandling.DelegatingEventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.tracing.Span;
import org.axonframework.messaging.tracing.SpanScope;

import java.util.Objects;
import java.util.function.Function;

/**
 * An {@link EventHandlingComponent} that tracks the handling of events using a {@link Span} supplier.
 * <p>
 * It delegates the actual event handling to another {@link EventHandlingComponent} while tracking the events
 * processed.
 *
 * @author Allard Buijze
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 * @since 5.0.0
 */
// TODO #3594 - Revisit as part of Tracing Integration
public class TracingEventHandlingComponent extends DelegatingEventHandlingComponent {

    private final Function<EventMessage, Span> spanProvider;

    /**
     * Constructs the component with given {@code delegate} to receive calls.
     *
     * @param delegate     The instance to delegate calls to.
     * @param spanProvider The provider of {@link Span} to track the event handling.
     */
    public TracingEventHandlingComponent(
            @Nonnull Function<EventMessage, Span> spanProvider,
            @Nonnull EventHandlingComponent delegate
    ) {
        super(delegate);
        this.spanProvider = Objects.requireNonNull(spanProvider, "Span provider may not be null");
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message> handle(@Nonnull EventMessage event,
                                               @Nonnull ProcessingContext context) {
        Span span = spanProvider.apply(event);
        span.start();
        try (SpanScope ignored = span.makeCurrent()) { // works as long as the MessageStream doesn't change threads
            return delegate.handle(event, context)
                           .onComplete(span::end)
                           .onErrorContinue(ex -> {
                               span.recordException(ex);
                               span.end();
                               return MessageStream.failed(ex);
                           }).ignoreEntries().cast();
        } catch (Exception e) {
            span.recordException(e);
            span.end();
            return MessageStream.failed(e);
        }
    }
}
