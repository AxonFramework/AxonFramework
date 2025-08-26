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

package org.axonframework.eventhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.unitofwork.UnitOfWorkTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Test utilities when dealing with events.
 *
 * @author Mateusz Nowak
 * @author Steven van Beelen
 */
public abstract class EventTestUtils {

    public static final String PAYLOAD = "payload";
    public static final String AGGREGATE = "aggregate";

    private EventTestUtils() {
        // Utility class
    }

    /**
     * Constructs a {@link List} of {@link EventMessage EventMessages} with a size equalling the given {@code number}.
     * <p>
     * The {@link EventMessage#payload() payload} of the events equals it's position within the sequence.
     *
     * @param number The number of events to construct.
     * @return A {@link List} of {@link EventMessage EventMessages} with a size equalling the given {@code number}.
     */
    public static List<EventMessage> createEvents(int number) {
        return IntStream.range(0, number)
                        .mapToObj(EventTestUtils::createEvent)
                        .toList();
    }

    /**
     * Constructs an {@link EventMessage} with the given {@code seq} as the {@link EventMessage#payload() payload}.
     *
     * @param seq The payload for the message to construct.
     * @return An {@link EventMessage} with the given {@code seq} as the {@link EventMessage#payload() payload}.
     */
    public static EventMessage createEvent(int seq) {
        return EventTestUtils.asEventMessage(seq);
    }

    /**
     * Returns the given {@code event} wrapped in an {@link EventMessage}.
     * <p>
     * If {@code event} already implements {@code EventMessage}, it is returned as-is. If it is a {@link Message}, a new
     * {@code EventMessage} will be created using the payload and metadata of the given message. Otherwise, the given
     * {@code event} is wrapped into a {@link GenericEventMessage} as its payload.
     *
     * @param event The event to wrap as {@link EventMessage}.
     * @return An {@link EventMessage} containing given {@code event} as payload, or {@code event} if it already
     * implements {@code EventMessage}.
     */
    public static EventMessage asEventMessage(@Nonnull Object event) {
        if (event instanceof EventMessage e) {
            return e;
        }
        if (event instanceof Message message) {
            return new GenericEventMessage(message, GenericEventMessage.clock.instant());
        }
        return new GenericEventMessage(
                new GenericMessage(new MessageType(event.getClass()), event),
                GenericEventMessage.clock.instant()
        );
    }

    /**
     * Handles the event in a {@link org.axonframework.messaging.unitofwork.UnitOfWork} using the provided
     * {@link EventHandler}. Useful if the {@link org.axonframework.messaging.unitofwork.ProcessingContext} is required
     * in your tests.
     * <p>
     *
     * @param handler The {@link EventHandler} to handle the events.
     * @param event   The {@link EventMessage} to handle.
     */
    public static void handleEventInUnitOfWork(EventHandler handler, EventMessage event) {
        handleEventsInUnitOfWork(handler, List.of(event));
    }

    /**
     * Handles a batch of events in a {@link org.axonframework.messaging.unitofwork.UnitOfWork} using the provided
     * {@link EventHandler}. Useful if the {@link org.axonframework.messaging.unitofwork.ProcessingContext} is required
     * in your tests.
     * <p>
     *
     * @param handler     The {@link EventHandler} to handle the events.
     * @param eventsBatch The batch of {@link EventMessage EventMessages} to handle.
     */
    public static void handleEventsInUnitOfWork(EventHandler handler, List<EventMessage> eventsBatch) {
        var unitOfWork = UnitOfWorkTestUtils.aUnitOfWork();
        unitOfWork.onInvocation(context -> {
            MessageStream<Message> batchResult = MessageStream.empty().cast();
            for (var event : eventsBatch) {
                var eventResult = handler.handle(event, context);
                batchResult = batchResult.concatWith(eventResult.cast());
            }
            return batchResult.ignoreEntries().asCompletableFuture();
        });
        try {
            unitOfWork.execute().get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
