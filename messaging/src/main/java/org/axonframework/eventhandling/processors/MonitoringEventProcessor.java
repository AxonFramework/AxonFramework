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

package org.axonframework.eventhandling.processors;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

/**
 * An {@link EventProcessor} wrapper that supports a {@link org.axonframework.monitoring.MessageMonitor}. Actual
 * processing is done by a delegate.
 * <p>
 * This {@link MonitoringEventProcessor} is typically registered as a
 * {@link org.axonframework.configuration.ComponentRegistry#registerDecorator(org.axonframework.configuration.DecoratorDefinition)
 * decorator} and automatically kicks in whenever an {@link EventMessage} specific
 * {@link org.axonframework.monitoring.MessageMonitor} is present.
 */
public class MonitoringEventProcessor implements EventProcessor {

    private final EventProcessor delegate;
    private final MessageMonitor<? super EventMessage> messageMonitor;

    public MonitoringEventProcessor(@Nonnull final EventProcessor delegate,
                                    @Nullable final MessageMonitor<? super EventMessage> messageMonitor) {
        this.delegate = requireNonNull(delegate, "delegate cannot be null");
        this.messageMonitor = messageMonitor != null ? messageMonitor : NoOpMessageMonitor.INSTANCE;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public CompletableFuture<Void> start() {
        return delegate.start();
    }

    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    @Override
    public boolean isError() {
        return delegate.isError();
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return delegate.shutdown();
    }

    @Override
    public void describeTo(@Nonnull final ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("messageMonitor", messageMonitor);
    }
}
