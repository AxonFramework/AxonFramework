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

package org.axonframework.eventhandling.gateway;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Default implementation of the {@link EventGateway} interface. Events are published using the {@link EventSink} in a
 * new {@link UnitOfWork}.
 *
 * @author Bert laverman
 * @author Mitchell Herrijgers
 * @since 4.1
 */
public class DefaultEventGateway implements EventGateway {

    private final EventSink eventSink;
    private final MessageTypeResolver messageTypeResolver;

    /**
     * Creates a new {@link EventGateway} that uses the given {@code eventSink} to publish events. The
     * {@code messageTypeResolver} is used to resolve the type of the event if no {@link EventMessage} is provided but a
     * payload.
     *
     * @param eventSink           The {@link EventSink} to publish events to.
     * @param messageTypeResolver The {@link MessageTypeResolver} to resolve the type of the event.
     */
    public DefaultEventGateway(@Nonnull EventSink eventSink, @Nonnull MessageTypeResolver messageTypeResolver) {
        this.eventSink = Objects.requireNonNull(eventSink, "EventSink may not be null");
        this.messageTypeResolver = Objects.requireNonNull(messageTypeResolver, "MessageTypeResolver may not be null");
    }

    @Override
    public void publish(@Nonnull List<?> events) {
        UnitOfWork unitOfWork = new UnitOfWork();
        unitOfWork.onInvocation(context -> {
            doPublish(events, context);
            return CompletableFuture.completedFuture(null);
        });
        unitOfWork.execute().join();
    }

    private void doPublish(List<?> events, ProcessingContext context) {
        Objects.requireNonNull(events, "Events may not be null");
        Objects.requireNonNull(context, "Context may not be null");
        List<EventMessage<?>> eventMessages = events
                .stream()
                .map(e -> EventPublishingUtils.asEventMessage(e, messageTypeResolver))
                .collect(Collectors.toList());
        this.eventSink.publish(context, eventMessages);
    }
}