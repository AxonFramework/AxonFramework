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

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Decorator that extends {@link DelegatingEventHandlingComponent} to add reset capability
 * to an existing event handling component.
 * <p>
 * This implementation:
 * <ul>
 *   <li>Delegates all event handling to the wrapped {@link EventHandlingComponent}</li>
 *   <li>Stores reset handlers in a thread-safe list via {@link ResetHandlerRegistry}</li>
 *   <li>Invokes all subscribed reset handlers sequentially during reset</li>
 *   <li>Returns {@code true} from {@link #supportsReset()} when at least one handler is registered</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create base event component
 * EventHandlingComponent events = new SimpleEventHandlingComponent();
 * events.subscribe(new QualifiedName("OrderPlaced"), orderHandler);
 *
 * // Wrap with reset capability
 * SimpleResetEventHandlingComponent resetable = new SimpleResetEventHandlingComponent(events);
 *
 * // Subscribe reset handlers (just like event handlers!)
 * resetable.subscribe((resetContext, context) -> {
 *     repository.deleteAll();
 *     return MessageStream.empty();
 * });
 *
 * resetable.subscribe((resetContext, context) -> {
 *     cache.clear();
 *     return MessageStream.empty();
 * });
 * }</pre>
 *
 * @author Mateusz Nowak
 * @see DelegatingEventHandlingComponent
 * @see ResetHandlerRegistry
 * @since 5.0.0
 */
public class SimpleResetEventHandlingComponent
        extends DelegatingEventHandlingComponent
        implements ResetHandlerRegistry {

    private final List<ResetHandler> resetHandlers = new CopyOnWriteArrayList<>();

    /**
     * Creates a reset-capable event handling component that wraps the given delegate.
     * <p>
     * All event handling operations are delegated to the wrapped component. Reset handlers
     * can be added via {@link #subscribe(ResetHandler)}.
     *
     * @param delegate the event handling component to wrap, must not be {@code null}
     */
    public SimpleResetEventHandlingComponent(@Nonnull EventHandlingComponent delegate) {
        super(delegate);
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
