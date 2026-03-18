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

package org.axonframework.messaging.eventhandling.gateway;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.FutureUtils;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.messaging.core.MessageTypeResolver;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.EventSink;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Default implementation of the {@link EventGateway} interface using the {@link EventSink} to publish events.
 *
 * @author Bert laverman
 * @author Mitchell Herrijgers
 * @since 4.1.0
 */
public class DefaultEventGateway implements EventGateway {

    private final EventSink eventSink;
    private final MessageTypeResolver messageTypeResolver;

    /**
     * Creates a new {@link EventGateway} that uses the given {@code eventSink} to publish events. The
     * {@code messageTypeResolver} is used to resolve the type of the event if no {@link EventMessage} is provided but a
     * payload.
     *
     * @param eventSink           the {@link EventSink} to publish events to
     * @param messageTypeResolver the {@link MessageTypeResolver} to resolve the type of the event
     */
    public DefaultEventGateway(EventSink eventSink,
                               MessageTypeResolver messageTypeResolver) {
        this.eventSink = Objects.requireNonNull(eventSink, "EventSink may not be null");
        this.messageTypeResolver = Objects.requireNonNull(messageTypeResolver, "MessageTypeResolver may not be null");
    }

    @Override
    public CompletableFuture<Void> publish(@Nullable ProcessingContext context,
                                           List<?> events) {
        List<EventMessage> eventMessages =
                events.stream()
                      .map(event -> EventPublishingUtils.asEventMessage(event, messageTypeResolver))
                      .collect(Collectors.toList());
        return eventMessages.isEmpty()
                ? FutureUtils.emptyCompletedFuture()
                : eventSink.publish(context, eventMessages);
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeProperty("eventSink", eventSink);
        descriptor.describeProperty("messageTypeResolver", messageTypeResolver);
    }
}