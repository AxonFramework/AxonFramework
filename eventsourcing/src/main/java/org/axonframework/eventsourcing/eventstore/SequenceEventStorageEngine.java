/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nonnull;

/**
 * EventStorageEngine implementation that combines the streams of two event storage engines. The first event storage
 * engine contains historic events while the second is used for active event storage. If a stream of events is opened
 * this storage engine concatenates the stream of the historic and active storage.
 * <p>
 * New events and snapshots are stored in the active storage.
 * <p>
 * When fetching snapshots, if a snapshot cannot be found in the active storage it will be obtained from the historic
 * storage.
 * <p>
 * No mechanism is provided to move events from the active storage to the historic storage engine so clients need to
 * take care of this themselves.
 *
 * @author Rene de Waele
 * @author Allard Buijze
 * @since 3.0
 */
public class SequenceEventStorageEngine implements EventStorageEngine {

    private final EventStorageEngine historicStorage, activeStorage;

    /**
     * Initializes a new {@link SequenceEventStorageEngine} using given {@code historicStorage} and {@code
     * activeStorage}.
     *
     * @param historicStorage the event storage engine that contains historic events. This can be backed by a read-only
     *                        database
     * @param activeStorage   the event storage engine that contains 'new' events and to which new events and snapshots
     *                        will be written
     */
    public SequenceEventStorageEngine(EventStorageEngine historicStorage, EventStorageEngine activeStorage) {
        this.historicStorage = historicStorage;
        this.activeStorage = activeStorage;
    }

    @Override
    public void appendEvents(@Nonnull List<? extends EventMessage<?>> events) {
        activeStorage.appendEvents(events);
    }

    @Override
    public void storeSnapshot(@Nonnull DomainEventMessage<?> snapshot) {
        activeStorage.storeSnapshot(snapshot);
    }

    @Override
    public Stream<? extends TrackedEventMessage<?>> readEvents(TrackingToken trackingToken, boolean mayBlock) {
        Spliterator<? extends TrackedEventMessage<?>> historicSpliterator =
                historicStorage.readEvents(trackingToken, mayBlock).spliterator();
        Spliterator<? extends TrackedEventMessage<?>> merged = new ConcatenatingSpliterator(
                trackingToken,
                historicSpliterator,
                mayBlock,
                token -> activeStorage.readEvents(token, mayBlock).spliterator()
        );
        return StreamSupport.stream(merged, false);
    }

    @Override
    public DomainEventStream readEvents(@Nonnull String aggregateIdentifier, long firstSequenceNumber) {
        DomainEventStream historic = historicStorage.readEvents(aggregateIdentifier, firstSequenceNumber);
        return new ConcatenatingDomainEventStream(historic, aggregateIdentifier, firstSequenceNumber,
                                                  (id, seq) -> activeStorage.readEvents(aggregateIdentifier, seq));
    }

    @Override
    public Optional<DomainEventMessage<?>> readSnapshot(@Nonnull String aggregateIdentifier) {
        Optional<DomainEventMessage<?>> optionalDomainEventMessage = activeStorage.readSnapshot(aggregateIdentifier);
        return optionalDomainEventMessage.isPresent()
                ? optionalDomainEventMessage
                : historicStorage.readSnapshot(aggregateIdentifier);
    }

    @Override
    public Optional<Long> lastSequenceNumberFor(@Nonnull String aggregateIdentifier) {
        Optional<Long> result = activeStorage.lastSequenceNumberFor(aggregateIdentifier);
        if (result.isPresent()) {
            return result;
        }
        return historicStorage.lastSequenceNumberFor(aggregateIdentifier);
    }

    @Override
    public TrackingToken createTailToken() {
        return historicStorage.createTailToken();
    }

    @Override
    public TrackingToken createHeadToken() {
        return activeStorage.createHeadToken();
    }

    @Override
    public TrackingToken createTokenAt(@Nonnull Instant dateTime) {
        TrackingToken tokenFromActiveStorage = activeStorage.createTokenAt(dateTime);
        if (tokenFromActiveStorage == null) {
            return historicStorage.createTokenAt(dateTime);
        }
        return tokenFromActiveStorage;
    }

    private static class ConcatenatingSpliterator extends Spliterators.AbstractSpliterator<TrackedEventMessage<?>> {

        private final Spliterator<? extends TrackedEventMessage<?>> historicSpliterator;
        private final boolean mayBlock;
        private final Function<TrackingToken, Spliterator<? extends TrackedEventMessage<?>>> nextProvider;

        private TrackingToken lastToken;
        private Spliterator<? extends TrackedEventMessage<?>> active;

        public ConcatenatingSpliterator(TrackingToken initialToken,
                                        Spliterator<? extends TrackedEventMessage<?>> historicSpliterator,
                                        boolean mayBlock,
                                        Function<TrackingToken, Spliterator<? extends TrackedEventMessage<?>>> nextProvider) {
            super(Long.MAX_VALUE, Spliterator.ORDERED);
            this.lastToken = initialToken;
            this.historicSpliterator = historicSpliterator;
            this.mayBlock = mayBlock;
            this.nextProvider = nextProvider;
        }

        @Override
        public boolean tryAdvance(Consumer<? super TrackedEventMessage<?>> action) {
            if (active == null && historicSpliterator.tryAdvance((Consumer<TrackedEventMessage<?>>) message -> {
                lastToken = message.trackingToken();
                action.accept(message);
            })) {
                return true;
            } else if (active == null) {
                active = nextProvider.apply(lastToken);
            }
            return active.tryAdvance(message -> {
                lastToken = message.trackingToken();
                action.accept(message);
            });
        }
    }

    private static class ConcatenatingDomainEventStream implements DomainEventStream {

        private final DomainEventStream historic;
        private final String aggregateIdentifier;
        private final long firstSequenceNumber;
        private final BiFunction<String, Long, DomainEventStream> domainEventStream;

        private DomainEventStream actual;

        public ConcatenatingDomainEventStream(DomainEventStream historic,
                                              String aggregateIdentifier,
                                              long firstSequenceNumber,
                                              BiFunction<String, Long, DomainEventStream> domainEventStream) {
            this.historic = historic;
            this.aggregateIdentifier = aggregateIdentifier;
            this.firstSequenceNumber = firstSequenceNumber;
            this.domainEventStream = domainEventStream;
        }

        @Override
        public boolean hasNext() {
            initActiveIfRequired();
            if (actual == null) {
                return historic.hasNext();
            }
            return actual.hasNext();
        }

        private void initActiveIfRequired() {
            if (actual == null && !historic.hasNext()) {
                actual = domainEventStream.apply(aggregateIdentifier, nextSequenceNumber());
            }
        }

        private long nextSequenceNumber() {
            Long lastSequenceNumber = historic.getLastSequenceNumber();
            return lastSequenceNumber == null ? firstSequenceNumber : lastSequenceNumber + 1;
        }

        @Override
        public DomainEventMessage<?> next() {
            initActiveIfRequired();
            if (actual == null) {
                return historic.next();
            } else {
                return actual.next();
            }
        }

        @Override
        public DomainEventMessage<?> peek() {
            initActiveIfRequired();
            if (actual == null) {
                return historic.peek();
            } else {
                return actual.peek();
            }
        }

        @Override
        public Long getLastSequenceNumber() {
            initActiveIfRequired();
            if (actual == null) {
                return historic.getLastSequenceNumber();
            } else {
                Long actualLastSequenceNumber = actual.getLastSequenceNumber();
                return actualLastSequenceNumber != null ? actualLastSequenceNumber : historic.getLastSequenceNumber();
            }
        }
    }
}
