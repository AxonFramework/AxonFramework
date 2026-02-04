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

package org.axonframework.messaging.commandhandling.distributed;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link CommandBusConnector} implementation that wraps another {@link CommandBusConnector} and delegates all calls
 * to it. This can be used to add additional functionality through decoration to a {@link CommandBusConnector} without
 * having to implement all methods again.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public abstract class DelegatingCommandBusConnector implements CommandBusConnector {

    protected final CommandBusConnector delegate;

    /**
     * Initialize the WrappedConnector to delegate all calls to the given {@code delegate}.
     *
     * @param delegate The {@link CommandBusConnector} to delegate all calls to.
     */
    protected DelegatingCommandBusConnector(@Nonnull CommandBusConnector delegate) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate must not be null.");
    }

    @Nonnull
    @Override
    public CompletableFuture<CommandResultMessage> dispatch(@Nonnull CommandMessage command,
                                                            @Nullable ProcessingContext processingContext) {
        return delegate.dispatch(command, processingContext);
    }

    // region [Connector]
    @Override
    public CompletableFuture<Void> subscribe(@Nonnull QualifiedName commandName, int loadFactor) {
        return delegate.subscribe(commandName, loadFactor);
    }

    @Override
    public boolean unsubscribe(@Nonnull QualifiedName commandName) {
        return delegate.unsubscribe(commandName);
    }

    @Override
    public void onIncomingCommand(@Nonnull Handler handler) {
        delegate.onIncomingCommand(handler);
    }

    // endregion

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }
}