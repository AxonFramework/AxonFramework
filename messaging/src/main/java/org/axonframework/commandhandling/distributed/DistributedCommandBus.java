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

package org.axonframework.commandhandling.distributed;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of a {@code CommandBus} that is aware of multiple instances of a {@code CommandBus} working together
 * to spread load.
 * <p>
 * Each "physical" {@code CommandBus} instance is considered a "segment" of a conceptual distributed
 * {@code CommandBus}.
 * <p>
 * The {@code DistributedCommandBus} relies on a {@link Connector} to dispatch commands and replies to different
 * segments of the {@code CommandBus}. Depending on the implementation used, each segment may run in a different JVM.
 *
 * @author Allard Buijze
 * @since 2.0.0
 */
public class DistributedCommandBus implements CommandBus {

    private final CommandBus delegate;
    private final Connector connector;

    /**
     * Constructs a {@code DistributedCommandBus} using the given {@code delegate} for
     * {@link #subscribe(QualifiedName, CommandHandler) subscribing} handlers and the given {@code connector} to
     * dispatch commands and replies to different segments of the {@code CommandBus}.
     *
     * @param delegate  The delegate {@code CommandBus} used to subscribe handlers too.
     * @param connector The {@code Connector} to use to reschedule failed commands.
     */
    public DistributedCommandBus(@Nonnull CommandBus delegate,
                                 @Nonnull Connector connector) {
        this.delegate = Objects.requireNonNull(delegate, "Given CommandBus delegate cannot be null.");
        this.connector = Objects.requireNonNull(connector, "Given Connector cannot be null.");
        connector.onIncomingCommand((command, callback) -> delegate.dispatch(command, ProcessingContext.NONE)
                                                                   .whenComplete((chr, e) -> {
                                                                       if (e == null) {
                                                                           callback.success(chr);
                                                                       } else {
                                                                           callback.error(e);
                                                                       }
                                                                   }));
    }

    @Override
    public DistributedCommandBus subscribe(@Nonnull QualifiedName name,
                                           @Nonnull CommandHandler handler) {
        CommandHandler commandHandler = Objects.requireNonNull(handler, "Given handler cannot be null.");
        delegate.subscribe(name, commandHandler);
        connector.subscribe(name.toString(), 100);
        return this;
    }

    @Override
    public CompletableFuture<? extends Message<?>> dispatch(@Nonnull CommandMessage<?> command,
                                                            @Nullable ProcessingContext processingContext) {
        return connector.dispatch(command, processingContext);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
        descriptor.describeProperty("connector", connector);
    }
}
