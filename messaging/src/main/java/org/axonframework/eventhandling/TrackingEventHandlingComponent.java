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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.tracing.Span;

import java.util.function.Supplier;

/**
 * An {@link EventHandlingComponent} that tracks the handling of events using a {@link Span} supplier.
 * <p>
 * It delegates the actual event handling to another {@link EventHandlingComponent} while tracking the events
 * processed.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class TrackingEventHandlingComponent extends DelegatingEventHandlingComponent {

    private final Supplier<Span> spanSupplier;

    /**
     * Constructs the DelegatingEventHandlingComponent with given {@code delegate} to receive calls.
     *
     * @param delegate     The instance to delegate calls to.
     * @param spanSupplier The {@link Supplier} of {@link Span} to track the event handling.
     */
    public TrackingEventHandlingComponent(@Nonnull EventHandlingComponent delegate,
                                          @Nonnull Supplier<Span> spanSupplier) {
        super(delegate);
        this.spanSupplier = spanSupplier;
    }

    @Nonnull
    @Override
    public MessageStream.Empty<Message<Void>> handle(@Nonnull EventMessage<?> event,
                                                     @Nonnull ProcessingContext context) {
        return trackMessageStream(spanSupplier.get(), () -> {
            try {
                return delegate.handle(event, context).cast();
            } catch (Exception e) {
                return MessageStream.failed(e);
            }
        });
    }

    // todo: I'm not sure about that, it had some thread local used inside runSupplierAsync. Maybe we need to take the parent from ProcessingContext?
    private MessageStream.Empty<Message<Void>> trackMessageStream(
            Span span,
            Supplier<MessageStream<Message<?>>> messageStreamSupplier
    ) {
        span.start();
        var messageStream = messageStreamSupplier.get();
        return messageStream
                .whenComplete(span::end)
                .onErrorContinue(ex -> {
                    span.recordException(ex);
                    span.end();
                    return MessageStream.failed(ex);
                }).ignoreEntries().cast();
    }
}
