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
import org.axonframework.common.Registration;
import org.axonframework.common.annotations.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.DecoratorDefinition;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Decorator around the {@link EventBus} intercepting all {@link EventMessage events} before they are
 * {@link #publish(ProcessingContext, List) published} with {@link MessageDispatchInterceptor dispatch interceptors}.
 * <p>
 * This {@code InterceptingEventBus} is typically registered as a
 * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} and automatically kicks in whenever
 * {@code MessageDispatchInterceptors} are present.
 *
 * @author Mateusz Nowak
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class InterceptingEventBus implements EventBus {

    /**
     * The order in which the {@link InterceptingEventBus} is applied as a
     * {@link ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} to the {@link EventBus}.
     * <p>
     * As such, any decorator with a lower value will be applied to the delegate, and any higher value will be applied
     * to the {@code InterceptingEventBus} itself. Using the same value can either lead to application of the
     * decorator to the delegate or the {@code InterceptingEventBus}, depending on the order of registration.
     * <p>
     * The order of the {@code InterceptingEventBus} is set to {@code Integer.MIN_VALUE + 100} to ensure it is applied
     * very early in the configuration process, but not the earliest to allow for other decorators to be applied.
     */
    public static final int DECORATION_ORDER = Integer.MIN_VALUE + 100;

    private final EventBus delegate;
    private final List<MessageDispatchInterceptor<? super EventMessage>> interceptors;
    private final InterceptingEventSink delegateSink;

    /**
     * Constructs a {@code InterceptingEventBus}, delegating all operations to the given {@code delegate}.
     * <p>
     * The given {@code interceptors} are invoked before {@link #publish(ProcessingContext, List) publishing} is done
     * by the given {@code delegate}.
     *
     * @param delegate     The delegate {@code EventBus} that will handle all dispatching and handling logic.
     * @param interceptors The interceptors to invoke before publishing an event.
     */
    @Internal
    public InterceptingEventBus(@Nonnull EventBus delegate,
                                @Nonnull List<MessageDispatchInterceptor<? super EventMessage>> interceptors) {
        this.delegate = Objects.requireNonNull(delegate, "The EventBus may not be null.");
        this.interceptors = Objects.requireNonNull(interceptors, "The dispatch interceptors must not be null.");
        this.delegateSink = new InterceptingEventSink(delegate, interceptors);
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                           @Nonnull List<EventMessage> events) {
        return delegateSink.publish(context, events);
    }

    @Override
    public Registration subscribe(@Nonnull BiConsumer<List<? extends EventMessage>, ProcessingContext> eventsBatchConsumer) {
        return delegate.subscribe(eventsBatchConsumer);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("dispatchInterceptors", interceptors);
        descriptor.describeProperty("delegateSink", delegateSink);
    }
}
