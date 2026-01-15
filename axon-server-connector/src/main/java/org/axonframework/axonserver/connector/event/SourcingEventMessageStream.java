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

package org.axonframework.axonserver.connector.event;

import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.grpc.event.dcb.SequencedEvent;
import io.axoniq.axonserver.grpc.event.dcb.SourceEventsResponse;
import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.TerminalEventMessage;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.GlobalIndexConsistencyMarker;
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
 * {@link SourceEventsResponse SourceEventsResponses} from Axon Server, translating the {@code SourceEventsResponses}
 * into {@link EventMessage EventMessages} as it moves along.
 * <p>
 * Note that Axon Server regards the {@code ResultStream} as finite. At the end, this {@code MessageStream}
 * implementation will receive the {@link ConsistencyMarker} from the given {@code ResultStream}. Due to this, the
 * {@link ConsistencyMarker#RESOURCE_KEY resource} will be empty when requested early, but present once this stream
 * {@link #isCompleted() completed}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class SourcingEventMessageStream implements MessageStream<EventMessage> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ResultStream<SourceEventsResponse> stream;
    private final TaggedEventConverter converter;

    /**
     * Constructs a {@code SourcingMessageStream} with the given {@code stream} and {@code converter}.
     *
     * @param stream    The {@code ResultStream} of {@code SourceEventsResponses} to convert into
     *                  {@link EventMessage EventMessages} for this {@link MessageStream} implementation.
     * @param converter The {@code EventConverter} used to convert {@code SourceEventsResponses} into
     *                  {@link EventMessage EventMessages} for this {@link MessageStream} implementation.
     */
    public SourcingEventMessageStream(@Nonnull ResultStream<SourceEventsResponse> stream,
                                      @Nonnull TaggedEventConverter converter) {
        this.stream = Objects.requireNonNull(stream, "The source result stream cannot be null.");
        this.converter = Objects.requireNonNull(converter, "The converter cannot be null.");
    }

    @Override
    public Optional<Entry<EventMessage>> next() {
        SourceEventsResponse next = stream.nextIfAvailable();
        if (next == null) {
            logger.debug("Reached the end of the source result stream.");
            return Optional.empty();
        } else if (next.hasConsistencyMarker()) {
            logger.debug("Reached the consistency marker message of the source result stream.");
            return convertToMarkerEntry(next.getConsistencyMarker());
        }
        return convertToEventEntry(next.getEvent());
    }

    private Optional<Entry<EventMessage>> convertToEventEntry(SequencedEvent event) {
        EventMessage eventMessage = converter.convertEvent(event.getEvent());
        TrackingToken token = new GlobalSequenceTrackingToken(event.getSequence() + 1);
        Context context = Context.with(TrackingToken.RESOURCE_KEY, token);
        return Optional.of(new SimpleEntry<>(eventMessage, context));
    }

    private static Optional<Entry<EventMessage>> convertToMarkerEntry(long marker) {
        Context context = ConsistencyMarker.addToContext(
                Context.empty(), new GlobalIndexConsistencyMarker(marker)
        );
        return Optional.of(new SimpleEntry<>(TerminalEventMessage.INSTANCE, context));
    }

    @Override
    public Optional<Entry<EventMessage>> peek() {
        SourceEventsResponse peeked = stream.peek();
        if (peeked == null) {
            logger.debug("Peeked the end of the source result stream.");
            return Optional.empty();
        } else if (peeked.hasConsistencyMarker()) {
            logger.debug("Peeked the consistency marker message of the source result stream.");
            return convertToMarkerEntry(peeked.getConsistencyMarker());
        }
        return convertToEventEntry(peeked.getEvent());
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
