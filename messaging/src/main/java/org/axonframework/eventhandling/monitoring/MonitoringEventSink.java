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

package org.axonframework.eventhandling.monitoring;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.DecoratorDefinition;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingLifecycle;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.MessageMonitorUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * A {@link EventSink} wrapper that supports a {@link org.axonframework.monitoring.MessageMonitor}. Actual publication
 * of events is done by a delegate.
 * <p>
 * This {@link MonitoringEventSink} is typically registered as a
 * {@link org.axonframework.configuration.ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} and
 * automatically kicks in whenever an {@link EventMessage} specific {@link MessageMonitor} is present.
 */
public class MonitoringEventSink implements EventSink {

    /**
     * The order in which the {@link MonitoringEventSink} is applied as a
     * {@link org.axonframework.configuration.ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} to the
     * {@link EventSink}.
     * <p>
     * As such, any decorator with a lower value will be applied to the delegate, and any higher value will be applied
     * to the {@code MonitoringEventSink} itself. Using the same value can either lead to application of the decorator
     * to the delegate or the {@code MonitoringEventSink}, depending on the order of registration.
     * <p>
     * The order of the {@code MonitoringEventSink} is set to {@code Integer.MIN_VALUE + 100} to ensure it is applied
     * very early in the configuration process, but not the earliest to allow for other decorators to be applied.
     */
    public static final int DECORATION_ORDER = Integer.MIN_VALUE + 100;

    private final EventSink delegate;
    private final MessageMonitor<? super EventMessage> messageMonitor;

    /**
     * Constructs a {@code MonitoringEventSink}, decorating the given {@code delegate}.
     * <p>
     * The {@link MessageMonitor.MonitorCallback}s for the given {@code messageMonitor} are registered on the
     * {@link ProcessingContext#onAfterCommit(Function) onAfterCommit} and
     * {@link ProcessingContext#onError(ProcessingLifecycle.ErrorHandler) onError} hooks and invoked after the
     * {@link EventSink#publish(ProcessingContext, List) delegate.publish} method returned.
     *
     * @param delegate       The delegate {@code EventSink} that will handle all publishing.
     * @param messageMonitor the {@link MessageMonitor} to use.
     */
    public MonitoringEventSink(@Nonnull final EventSink delegate,
                               @Nonnull final MessageMonitor<Message> messageMonitor) {
        this.delegate = requireNonNull(delegate, "eventSink cannot be null");
        this.messageMonitor = requireNonNull(messageMonitor, "messageMonitor may not be null");
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context, @Nonnull List<EventMessage> events) {
        // TODO: JG? - I noticed that when this gets called, the context is not null. But the SimpleEventStore explicitly deals with null.
        // TODO: JG? - in other words: do we ever have to wonder if it is null/not started?
        MessageMonitorUtils.registerMonitorCallbacks(context, messageMonitor, events);

        return delegate.publish(context, events);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("messageMonitor", messageMonitor);
    }
}
