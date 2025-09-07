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

package org.axonframework.eventsourcing.eventstore;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.DecoratorDefinition;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.processors.streaming.token.TrackingToken;
import org.axonframework.eventstreaming.StreamingCondition;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.DefaultMessageDispatchInterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Decorator around the {@link EventStore} intercepting all {@link EventMessage events} before they are
 * {@link EventStoreTransaction#appendEvent(EventMessage) appended} or
 * {@link #publish(ProcessingContext, List) published} with {@link MessageDispatchInterceptor dispatch interceptors}.
 * <p>
 * This {@code InterceptingEventStore} is typically registered as a
 * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} and automatically kicks in whenever
 * {@code MessageDispatchInterceptors} are present.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class InterceptingEventStore implements EventStore {

    /**
     * The order in which the {@link InterceptingEventStore} is applied as a
     * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} to the {@link EventStore}.
     * <p>
     * As such, any decorator with a lower value will be applied to the delegate, and any higher value will be applied
     * to the {@code InterceptingEventStore} itself. Using the same value can either lead to application of the
     * decorator to the delegate or the {@code InterceptingEventStore}, depending on the order of registration.
     * <p>
     * The order of the {@code InterceptingEventStore} is set to {@code Integer.MIN_VALUE + 50} to ensure it is applied
     * very early in the configuration process, but not the earliest to allow for other decorators to be applied.
     */
    public static final int DECORATION_ORDER = Integer.MIN_VALUE + 50;

    private final EventStore delegate;
    private final List<MessageDispatchInterceptor<? super Message>> interceptors;
    private final InterceptingAppender interceptingAppender;
    private final InterceptingPublisher interceptingPublisher;

    private final Context.ResourceKey<EventStoreTransaction> delegateTransactionKey;
    private final Context.ResourceKey<EventStoreTransaction> interceptingTransactionKey;

    /**
     * Constructs a {@code InterceptingEventStore}, delegating all operation to the given {@code delegate}.
     * <p>
     * The given {@code interceptors} are invoked before {@link EventStoreTransaction#appendEvent(EventMessage)} or
     * {@link #publish(ProcessingContext, List) publishing} is done by the given {@code delegate}.
     *
     * @param delegate     The delegate {@code EventSink} that will handle all dispatching and handling logic.
     * @param interceptors The interceptors to invoke before dispatching a command and on the command result.
     */
    @Internal
    public InterceptingEventStore(@Nonnull EventStore delegate,
                                  @Nonnull List<MessageDispatchInterceptor<? super Message>> interceptors) {
        this.delegateTransactionKey = Context.ResourceKey.withLabel("delegateTransaction");
        this.interceptingTransactionKey = Context.ResourceKey.withLabel("interceptingTransaction");

        this.delegate = Objects.requireNonNull(delegate, "The EventStore may not be null.");
        this.interceptors = Objects.requireNonNull(interceptors, "The dispatch interceptors must not be null.");
        this.interceptingAppender = new InterceptingAppender(interceptors,
                                                             context -> context.getResource(delegateTransactionKey));
        this.interceptingPublisher = new InterceptingPublisher(interceptors, this::publishEvent);
    }

    @Override
    public EventStoreTransaction transaction(@Nonnull ProcessingContext processingContext) {
        // Set the delegate transaction to ensure the InterceptingAppender can reach the correct EventStoreTransaction.
        EventStoreTransaction delegateTransaction = processingContext.computeResourceIfAbsent(
                delegateTransactionKey,
                () -> delegate.transaction(processingContext)
        );
        // Set the intercepting transaction to ensure subsequent transaction operation receive the same intercepting transaction.
        return processingContext.computeResourceIfAbsent(
                interceptingTransactionKey,
                () -> new InterceptingEventStoreTransaction(processingContext, delegateTransaction)
        );
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
    public MessageStream<EventMessage> open(@Nonnull StreamingCondition condition) {
        return delegate.open(condition);
    }

    @Override
    public CompletableFuture<TrackingToken> firstToken() {
        return delegate.firstToken();
    }

    @Override
    public CompletableFuture<TrackingToken> latestToken() {
        return delegate.latestToken();
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        return delegate.tokenAt(at);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("dispatchInterceptors", interceptors);
    }

    private class InterceptingEventStoreTransaction implements EventStoreTransaction {

        private final ProcessingContext context;
        private final EventStoreTransaction delegate;

        private InterceptingEventStoreTransaction(@Nonnull ProcessingContext context,
                                                  @Nonnull EventStoreTransaction delegate) {
            this.delegate = delegate;
            this.context = context;
        }

        @Override
        public MessageStream<? extends EventMessage> source(@Nonnull SourcingCondition condition) {
            return delegate.source(condition);
        }

        @Override
        public void appendEvent(@Nonnull EventMessage eventMessage) {
            interceptingAppender.interceptAndAppend(eventMessage, context);
        }

        @Override
        public void onAppend(@Nonnull Consumer<EventMessage> callback) {
            delegate.onAppend(callback);
        }

        @Override
        public ConsistencyMarker appendPosition() {
            return delegate.appendPosition();
        }
    }

    private static class InterceptingAppender {

        private final DefaultMessageDispatchInterceptorChain<Message> interceptorChain;

        private InterceptingAppender(List<MessageDispatchInterceptor<? super Message>> interceptors,
                                     Function<ProcessingContext, EventStoreTransaction> transactionProvider) {
            this.interceptorChain = new DefaultMessageDispatchInterceptorChain<>(interceptors, (message, context) -> {
                if (!(message instanceof EventMessage event)) {
                    // The compiler should avoid this from happening.
                    throw new IllegalArgumentException("Unsupported message implementation: " + message);
                }
                transactionProvider.apply(context)
                                   .appendEvent(event);
                return MessageStream.empty();
            });
        }

        private void interceptAndAppend(@Nonnull EventMessage event,
                                        @Nullable ProcessingContext context) {
            interceptorChain.proceed(event, context)
                            .ignoreEntries()
                            .asCompletableFuture()
                            .join();
        }
    }

    private static class InterceptingPublisher {

        private final DefaultMessageDispatchInterceptorChain<Message> interceptorChain;

        private InterceptingPublisher(List<MessageDispatchInterceptor<? super Message>> interceptors,
                                      BiFunction<? super Message, ProcessingContext, MessageStream<?>> publisher) {
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
