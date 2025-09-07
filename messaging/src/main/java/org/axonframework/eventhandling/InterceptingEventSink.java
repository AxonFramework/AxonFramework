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
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.DecoratorDefinition;
import org.axonframework.messaging.DefaultMessageDispatchInterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Decorator around the {@link EventSink} intercepting all {@link EventMessage events} before they are
 * {@link #publish(ProcessingContext, List) published} with {@link MessageDispatchInterceptor dispatch interceptors}.
 * <p>
 * This {@code InterceptingEventSink} is typically registered as a
 * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} and automatically kicks in whenever
 * {@code MessageDispatchInterceptors} are present.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class InterceptingEventSink implements EventSink, DescribableComponent {

    /**
     * The order in which the {@link InterceptingEventSink} is applied as a
     * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} to the {@link EventSink}.
     * <p>
     * As such, any decorator with a lower value will be applied to the delegate, and any higher value will be applied
     * to the {@code InterceptingEventSink} itself. Using the same value can either lead to application of the decorator
     * to the delegate or the {@code InterceptingEventSink}, depending on the order of registration.
     * <p>
     * The order of the {@code InterceptingEventSink} is set to {@code Integer.MIN_VALUE + 100} to ensure it is applied
     * very early in the configuration process, but not the earliest to allow for other decorators to be applied.
     */
    public static final int DECORATION_ORDER = Integer.MIN_VALUE + 100;

    private final EventSink delegate;
    private final List<MessageDispatchInterceptor<? super Message>> interceptors;
    private final InterceptingPublisher interceptingPublisher;

    /**
     * Constructs a {@code InterceptingEventSink}, delegating publishing to the given {@code delegate}.
     * <p>
     * The given {@code interceptors} are invoked before {@link #publish(ProcessingContext, List) publishing} is done by
     * the given {@code delegate}.
     *
     * @param delegate     The delegate {@code EventSink} that will handle all dispatching and handling logic.
     * @param interceptors The interceptors to invoke before dispatching a command and on the command result.
     */
    public InterceptingEventSink(@Nonnull EventSink delegate,
                                 @Nonnull List<MessageDispatchInterceptor<? super Message>> interceptors) {
        this.delegate = Objects.requireNonNull(delegate, "The EventSink may not be null.");
        this.interceptors = Objects.requireNonNull(interceptors, "The dispatch interceptors must not be null.");
        this.interceptingPublisher = new InterceptingPublisher(interceptors, this::publishEvent);
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                           @Nonnull List<EventMessage> events) {
        return interceptingPublisher.interceptAndPublish(events, context);
    }

    private MessageStream.Empty<Message> publishEvent(@Nonnull Message message,
                                                      @Nullable ProcessingContext context) {
        if (!(message instanceof EventMessage event)) {
            // The compiler should avoid this from happening.
            throw new IllegalArgumentException("Unsupported message implementation: " + message);
        }
        return MessageStream.fromFuture(delegate.publish(context, event)
                                                .thenApply(v -> null))
                            .ignoreEntries();
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("dispatchInterceptors", interceptors);
    }

    private static class InterceptingPublisher {

        private final DefaultMessageDispatchInterceptorChain<Message> interceptorChain;

        private InterceptingPublisher(
                List<MessageDispatchInterceptor<? super Message>> interceptors,
                BiFunction<? super Message, ProcessingContext, MessageStream<?>> publisher
        ) {
            this.interceptorChain = new DefaultMessageDispatchInterceptorChain<>(interceptors, publisher);
        }

        private CompletableFuture<Void> interceptAndPublish(
                @Nonnull List<EventMessage> events,
                @Nullable ProcessingContext context
        ) {

            MessageStream<Message> resultStream = MessageStream.empty();
            for (EventMessage event : events) {
                resultStream = resultStream.concatWith(interceptorChain.proceed(event, context)
                                                                       .cast());
            }
            return resultStream.ignoreEntries()
                               .asCompletableFuture()
                               .thenApply(v -> null);
        }
    }
}
