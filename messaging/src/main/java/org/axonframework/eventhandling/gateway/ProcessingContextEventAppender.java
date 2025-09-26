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
import org.axonframework.common.annotations.Internal;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.messaging.MessageTypeResolver;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Component that publishes events to an {@link EventSink} in the context of a {@link ProcessingContext}. The events
 * will be published in the context this appender was created for. You can construct one through the
 * {@link EventAppender#forContext(ProcessingContext, Configuration)} method.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public class ProcessingContextEventAppender implements EventAppender {

    private final ProcessingContext processingContext;
    private final EventSink eventSink;
    private final MessageTypeResolver messageTypeResolver;

    /**
     * Creates a new {@link EventAppender} that uses the given {@code eventSink} to publish events. The
     * {@code messageTypeResolver} is used to resolve the type of the event if no {@link EventMessage} is provided but a
     * payload.
     *
     * @param processingContext   The {@link ProcessingContext} to publish events to.
     * @param eventSink           The {@link EventSink} to publish events to.
     * @param messageTypeResolver The {@link MessageTypeResolver} to resolve the type of the event.
     */
    ProcessingContextEventAppender(
            ProcessingContext processingContext,
            EventSink eventSink,
            MessageTypeResolver messageTypeResolver
    ) {
        this.processingContext = Objects.requireNonNull(processingContext, "ProcessingContext may not be null");
        this.eventSink = Objects.requireNonNull(eventSink, "EventSink may not be null");
        this.messageTypeResolver = Objects.requireNonNull(messageTypeResolver, "MessageTypeResolver may not be null");
    }

    @Override
    public void append(@Nonnull List<?> events) {
        Objects.requireNonNull(events, "Events may not be null");
        List<EventMessage> eventMessages = events
                .stream()
                .map(e -> EventPublishingUtils.asEventMessage(e, messageTypeResolver))
                .collect(Collectors.toList());
        eventSink.publish(processingContext, eventMessages)
                 .join();
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("processingContext", processingContext);
        descriptor.describeProperty("eventSink", eventSink);
        descriptor.describeProperty("messageTypeResolver", messageTypeResolver);
    }
}
