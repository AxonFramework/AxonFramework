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

package org.axonframework.commandhandling;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.DecoratorDefinition;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

/**
 * A {@link CommandBus} wrapper that supports a {@link org.axonframework.monitoring.MessageMonitor}. Actual dispatching
 * and handling of commands is done by a delegate.
 * <p>
 * This {@link MonitoringCommandBus} is typically registered as a
 * {@link org.axonframework.configuration.ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} and
 * automatically kicks whenever a {@link CommandMessage} specific {@link org.axonframework.monitoring.MessageMonitor} is
 * present.
 */
public class MonitoringCommandBus implements CommandBus {

    /**
     * The order in which the {@link MonitoringCommandBus} is applied as a
     * {@link org.axonframework.configuration.ComponentRegistry#registerDecorator(DecoratorDefinition) decorator} to the
     * {@link CommandBus}.
     * <p>
     * As such, any decorator with a lower value will be applied to the delegate, and any higher value will be applied
     * to the {@code MonitoringCommandBus} itself. Using the same value can either lead to application of the decorator
     * to the delegate or the {@code MonitoringCommandBus}, depending on the order of registration.
     * <p>
     * The order of the {@code MonitoringCommandBus} is set to {@code Integer.MIN_VALUE + 100} to ensure it is applied
     * very early in the configuration process, but not the earliest to allow for other decorators to be applied.
     */
    public static final int DECORATION_ORDER = Integer.MIN_VALUE + 100;

    private final CommandBus delegate;
    private final MessageMonitor<? super CommandMessage> messageMonitor;

    public MonitoringCommandBus(@Nonnull final CommandBus delegate,
                                @Nullable final MessageMonitor<? super CommandMessage> messageMonitor) {
        this.delegate = requireNonNull(delegate, "delegate cannot be null");
        this.messageMonitor = messageMonitor != null ? messageMonitor : NoOpMessageMonitor.INSTANCE;
    }

    @Override
    public CompletableFuture<CommandResultMessage> dispatch(@Nonnull CommandMessage command,
                                                            @Nullable ProcessingContext processingContext) {
        // TODO: Is there actually anything to do here except calling the delegate? Seems like the monittoring is done in the commandHandler used to subscribe.
        return delegate.dispatch(command, processingContext);
    }

    @Override
    public CommandBus subscribe(@Nonnull QualifiedName name, @Nonnull CommandHandler commandHandler) {
        final CommandHandler monitoringCommandHandler = (command, context) -> {
            if (context.isStarted()) {
                final var monitorCallback = messageMonitor.onMessageIngested(command);
                context.onAfterCommit(processingContext -> CompletableFuture.runAsync(monitorCallback::reportSuccess));
                context.onError((processingContext, phase, error) -> monitorCallback.reportFailure(error));
            } else {
                // TODO: what do we do when the context is not started?
                throw new IllegalStateException("context is not started.");
            }
            return commandHandler.handle(command, context);
        };

        delegate.subscribe(name, monitoringCommandHandler);
        return this;
    }

    @Override
    public void describeTo(@Nonnull final ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("messageMonitor", messageMonitor);
    }
}
