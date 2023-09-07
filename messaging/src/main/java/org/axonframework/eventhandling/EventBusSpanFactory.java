/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.tracing.Span;

/**
 * Span factory that creates spans for the {@link EventBus}. You can customize the spans of the bus by creating your
 * own implementation.
 *
 * @author Mitchell Herrijgers
 * @since 4.9.0
 */
public interface EventBusSpanFactory {

    /**
     * Creates a span for the publishing of an event. This span is created when the event is published on the {@link
     * EventBus}. This span does not include the actual commit of the event, this is represented by the {@link
     * #createCommitEventsSpan()} and may be later.
     *
     * @param eventMessage The event message to create a span for.
     * @return The created span.
     */
    Span createPublishEventSpan(EventMessage<?> eventMessage);

    /**
     * Creates a span for the committing of events. This is usually batched and done in the commit phase of a UnitOfWork.
     * If no UnitOfWork is active, the commit is done immediately.
     * @return The created span.
     */
    Span createCommitEventsSpan();

    /**
     * Propagates the context of the current span to the given event message.
     *
     * @param eventMessage The event message to propagate the context to.
     * @param <T>            The type of the payload of the event message.
     * @return The event message with the propagated context.
     */
    <T> EventMessage<T> propagateContext(EventMessage<T> eventMessage);
}
