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
import io.axoniq.axonserver.grpc.event.dcb.SourceEventsResponse;
import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.GlobalIndexConsistencyMarker;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.SimpleEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link MessageStream} implementation backed by a {@link ResultStream} of
 * {@link SourceEventsResponse SourceEventsResponses} from Axon Server, translating the {@code SourceEventsResponses}
 * into {@link EventMessage EventMessages} as it moves along.
 *
 * This {@code MessageStream} implementation will receive the {@link ConsistencyMarker} for the given {@code ResultStream} at the <b>end</b>. As such
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
class SourcingMessageStream implements MessageStream<EventMessage<?>> {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final ResultStream<SourceEventsResponse> stream;
    private final EventConverter converter;
    private final AtomicReference<ConsistencyMarker> consistencyMarker =
            new AtomicReference<>(new GlobalIndexConsistencyMarker(-1));

    /**
     * Constructs a {@code SourcingMessageStream} with the given {@code stream} and {@code converter}.
     *
     * @param stream    The {@code ResultStream} of {@code SourceEventsResponses} to convert into
     *                  {@link EventMessage EventMessages} for this {@link MessageStream} implementation.
     * @param converter The {@code EventConverter} used to convert {@code SourceEventsResponses} into
     *                  {@link EventMessage EventMessages} for this {@link MessageStream} implementation.
     */
    SourcingMessageStream(@Nonnull ResultStream<SourceEventsResponse> stream,
                          @Nonnull EventConverter converter) {
        this.stream = Objects.requireNonNull(stream, "The source result stream cannot be null.");
        this.converter = Objects.requireNonNull(converter, "The converter cannot be null.");
    }

    @Override
    public Optional<Entry<EventMessage<?>>> next() {
        SourceEventsResponse response = stream.nextIfAvailable();
        if (response == null) {
            logger.debug("There are no more events to source from the source result stream.");
            return Optional.empty();
        }
        if (response.hasConsistencyMarker()) {
            logger.debug("Reached consistency marker for the source result stream. Setting reference.");
            consistencyMarker.set(new GlobalIndexConsistencyMarker(response.getConsistencyMarker()));
            return Optional.empty();
        }
        return Optional.of(convertToEntry(response.getEvent()));
    }

    private SimpleEntry<EventMessage<?>> convertToEntry(SequencedEvent event) {
        EventMessage<byte[]> eventMessage = converter.convertEvent(event.getEvent());
        TrackingToken token = new GlobalSequenceTrackingToken(event.getSequence());
        Context context = Context.with(TrackingToken.RESOURCE_KEY, token);
        return new SimpleEntry<>(eventMessage, context);
    }

    /**
     * Returns an {@code AtomicReference} of the {@link ConsistencyMarker} from this stream.
     * <p>
     * Defaults to {@code -1}, switching to the actual consistency marker of the given {@link ResultStream} at the end.
     *
     * @return An {@code AtomicReference} of the {@link ConsistencyMarker} from this stream.
     */
    AtomicReference<ConsistencyMarker> consistencyMarker() {
        return consistencyMarker;
    }

    @Override
    public void onAvailable(@Nonnull Runnable callback) {
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
