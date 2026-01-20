/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.eventhandling.replay;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.DelegatingEventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An {@link EventHandlingComponent} that blocks all event handling during replay.
 * <p>
 * This component wraps another {@link EventHandlingComponent} and prevents any events from being processed when the
 * current {@link TrackingToken} indicates a replay is in progress (i.e., when the token is a {@link ReplayToken}).
 * <p>
 * The {@link #supportsReset()} method returns {@code true} only if at least one {@link ResetHandler} has been
 * registered through {@link #subscribe(ResetHandler)}.
 *
 * @param <E> the delegate {@link EventHandlingComponent} and {@link ResetHandlerRegistry} implementation
 * @author Mateusz Nowak
 * @see ReplayToken#isReplay(TrackingToken)
 * @since 5.1.0
 */
public class ReplayBlockingEventHandlingComponent<E extends EventHandlingComponent & ResetHandlerRegistry<?>>
        extends DelegatingEventHandlingComponent
        implements ResetHandlerRegistry<ReplayBlockingEventHandlingComponent<E>> {

    private final AtomicBoolean hasResetHandler = new AtomicBoolean(false);

    private final E delegateComponentAndRegistry;

    /**
     * Constructs a {@code ReplayBlockingEventHandlingComponent} that wraps the given {@code delegate}.
     *
     * @param delegate The {@link EventHandlingComponent} to delegate calls to.
     */
    public ReplayBlockingEventHandlingComponent(@Nonnull E delegate) {
        super(delegate);
        this.delegateComponentAndRegistry = delegate;
    }

    /**
     * Handles the given {@code event} unless a replay is in progress.
     * <p>
     * If the {@link TrackingToken} in the {@code context} indicates a replay (i.e., is a {@link ReplayToken}), this
     * method returns an empty {@link MessageStream} without delegating to the wrapped component.
     *
     * @param event   The event message to handle.
     * @param context The processing context containing the tracking token.
     * @return An empty {@link MessageStream} if replaying, otherwise the result from the delegate.
     */
    @Nonnull
    @Override
    public MessageStream.Empty<Message> handle(@Nonnull EventMessage event, @Nonnull ProcessingContext context) {
        Optional<TrackingToken> token = TrackingToken.fromContext(context);
        if (token.isPresent() && ReplayToken.isReplay(token.get())) {
            return MessageStream.empty();
        }
        return super.handle(event, context);
    }

    /**
     * Subscribes a {@link ResetHandler} and tracks that at least one reset handler has been registered.
     * <p>
     * This tracking is used by {@link #supportsReset()} to determine if the component supports reset operations.
     *
     * @param resetHandler The reset handler to subscribe.
     * @return A {@link ResetHandlerRegistry} for managing the subscription.
     */
    @Nonnull
    @Override
    public ReplayBlockingEventHandlingComponent<E> subscribe(@Nonnull ResetHandler resetHandler) {
        hasResetHandler.set(true);
        delegateComponentAndRegistry.subscribe(resetHandler);
        return this;
    }

    /**
     * Returns whether this component supports reset operations.
     * <p>
     * This implementation returns {@code true} only if at least one {@link ResetHandler} has been registered through
     * {@link #subscribe(ResetHandler)}.
     *
     * @return {@code true} if a reset handler has been registered, {@code false} otherwise.
     */
    @Override
    public boolean supportsReset() {
        return hasResetHandler.get();
    }
}
