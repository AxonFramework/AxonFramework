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

package org.axonframework.messaging.eventhandling.replay;

import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.DelegatingEventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventHandlingComponent;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.ReplayToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Decorator that blocks all event handling during replay while still allowing reset handling.
 * <p>
 * This component checks if the current tracking token indicates replay (via {@link ReplayToken})
 * and prevents all event handling in that case. Reset handlers can still be subscribed and
 * will be invoked during reset operations.
 * <p>
 * Implements {@link ResetEventHandlingComponent} to ensure it participates in the processor's
 * event handling chain during replay (components without reset support are skipped entirely).
 * <p>
 * This is the non-annotated, component-level equivalent of the {@code @DisallowReplay}
 * annotation.
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create notification component
 * EventHandlingComponent notifications = new SimpleEventHandlingComponent();
 * notifications.subscribe(new QualifiedName("OrderShipped"), emailHandler);
 *
 * // Wrap to block during replay
 * ReplayBlockingEventHandlingComponent blocking =
 *     new ReplayBlockingEventHandlingComponent(notifications);
 *
 * // Optionally subscribe reset handler for logging
 * blocking.subscribe((resetContext, context) -> {
 *     log.info("Reset triggered, notifications will be blocked during replay");
 *     return MessageStream.empty();
 * });
 * }</pre>
 *
 * @author Mateusz Nowak
 * @see SimpleResetEventHandlingComponent
 * @see ReplayToken
 * @see org.axonframework.messaging.eventhandling.replay.annotation.DisallowReplay
 * @since 5.0.0
 */
public class ReplayBlockingEventHandlingComponent
        extends DelegatingEventHandlingComponent
        implements ResetEventHandlingComponent {

    private final List<ResetHandler> resetHandlers = new CopyOnWriteArrayList<>();

    /**
     * Creates a replay-blocking component that wraps the given event handling component.
     * <p>
     * The wrapped component will not receive any events during replay. Reset handlers
     * can be added via {@link #subscribe(ResetHandler)}.
     *
     * @param delegate the event handling component to wrap, must not be {@code null}
     */
    public ReplayBlockingEventHandlingComponent(@Nonnull EventHandlingComponent delegate) {
        super(delegate);
    }

    @Override
    public MessageStream.Empty<Message> handle(@Nonnull EventMessage event,
                                               @Nonnull ProcessingContext context) {
        // Block all events during replay
        Optional<TrackingToken> token = TrackingToken.fromContext(context);
        if (token.isPresent() && ReplayToken.isReplay(token.get())) {
            return MessageStream.empty();
        }

        return super.handle(event, context);
    }

    @Override
    public MessageStream.Empty<Message> handle(@Nonnull ResetContext resetContext,
                                               @Nonnull ProcessingContext context) {
        MessageStream<Message> result = MessageStream.empty();

        for (ResetHandler handler : resetHandlers) {
            MessageStream<Message> handlerResult = handler.handle(resetContext, context);
            result = result.concatWith(handlerResult);
        }

        return result.ignoreEntries().cast();
    }

    @Override
    public ResetHandlerRegistry subscribe(@Nonnull ResetHandler resetHandler) {
        resetHandlers.add(resetHandler);
        return this;
    }

    @Override
    public boolean supportsReset() {
        return !resetHandlers.isEmpty();
    }
}
