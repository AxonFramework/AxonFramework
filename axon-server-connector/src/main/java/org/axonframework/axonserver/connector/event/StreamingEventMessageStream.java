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

package org.axonframework.axonserver.connector.event;

import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.grpc.event.dcb.SequencedEvent;
import io.axoniq.axonserver.grpc.event.dcb.StreamEventsResponse;
import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.SimpleEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link MessageStream} implementation backed by a {@link ResultStream} of
 * {@link StreamEventsResponse StreamEventsResponses} from Axon Server, translating the {@code StreamEventsResponses}
 * into {@link EventMessage EventMessages} as it moves along.
 * <p>
 * Note that Axon Server regards the {@code ResultStream} as infinite. As such, even if {@link #next()} returns an empty
 * {@link Optional}, the stream will remain open.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class StreamingEventMessageStream implements MessageStream<EventMessage> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ResultStream<StreamEventsResponse> stream;
    private final TaggedEventConverter converter;

    /**
     * Constructs a {@code StreamingMessageStream} with the given {@code stream} and {@code converter}.
     *
     * @param stream    The {@code ResultStream} of {@code StreamEventsResponses} to convert into
     *                  {@link EventMessage EventMessages} for this {@link MessageStream} implementation.
     * @param converter The {@code EventConverter} used to convert {@code StreamEventsResponses} into
     *                  {@link EventMessage EventMessages} for this {@link MessageStream} implementation.
     */
    public StreamingEventMessageStream(@Nonnull ResultStream<StreamEventsResponse> stream,
                                       @Nonnull TaggedEventConverter converter) {
        this.stream = Objects.requireNonNull(stream, "The result stream cannot be null.");
        this.converter = Objects.requireNonNull(converter, "The converter cannot be null.");
    }

    @Override
    public Optional<Entry<EventMessage>> next() {
        StreamEventsResponse response = stream.nextIfAvailable();
        if (response == null) {
            logger.debug("There are no more events to stream at this moment in time.");
            return Optional.empty();
        }
        return Optional.of(convertToEntry(response.getEvent()));
    }

    private SimpleEntry<EventMessage> convertToEntry(SequencedEvent event) {
        EventMessage eventMessage = converter.convertEvent(event.getEvent());
        TrackingToken token = new GlobalSequenceTrackingToken(event.getSequence() + 1);
        Context context = Context.with(TrackingToken.RESOURCE_KEY, token);
        return new SimpleEntry<>(eventMessage, context);
    }

    @Override
    public Optional<Entry<EventMessage>> peek() {
        StreamEventsResponse response = stream.peek();
        if (response == null) {
            logger.debug("There are no more events to peek at this moment in time.");
            return Optional.empty();
        }
        return Optional.of(convertToEntry(response.getEvent()));
    }

    @Override
    public void setCallback(@Nonnull Runnable callback) {
        stream.onAvailable(callback);
    }

    @Override
    public Optional<Throwable> error() {
        return stream.getError();
    }

    @Override
    public boolean isCompleted() {
        return stream.isClosed();
    }

    @Override
    public boolean hasNextAvailable() {
        return stream.peek() != null;
    }

    @Override
    public void close() {
        stream.close();
    }
}
