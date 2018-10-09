/*
 * Copyright (c) 2010-2017. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.mongo.eventsourcing.eventstore;

import com.mongodb.DuplicateKeyException;
import com.mongodb.MongoBulkWriteException;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.BatchingEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.DomainEventData;
import org.axonframework.eventsourcing.eventstore.TrackedEventData;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.mongo.MongoTemplate;
import org.axonframework.mongo.eventsourcing.eventstore.documentperevent.DocumentPerEventStorageStrategy;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * EventStorageEngine implementation that uses Mongo to store and fetch events.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public class MongoEventStorageEngine extends BatchingEventStorageEngine {

    private final MongoTemplate template;
    private final StorageStrategy storageStrategy;

    /**
     * Instantiate a {@link MongoEventStorageEngine} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link MongoTemplate} is not {@code null}, and will throw an
     * {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link MongoEventStorageEngine} instance
     */
    protected MongoEventStorageEngine(Builder builder) {
        super(builder);
        this.template = builder.template;
        this.storageStrategy = builder.storageStrategy;
    }

    /**
     * Instantiate a Builder to be able to create a {@link MongoEventStorageEngine}.
     * <p>
     * The following configurable fields have defaults:
     * <ul>
     * <li>The snapshot {@link Serializer} defaults to {@link org.axonframework.serialization.xml.XStreamSerializer}.</li>
     * <li>The {@link EventUpcaster} defaults to an {@link org.axonframework.serialization.upcasting.event.NoOpEventUpcaster}.</li>
     * <li>The {@link PersistenceExceptionResolver} is defaulted to {@link MongoEventStorageEngine#isDuplicateKeyException(Exception)}</li>
     * <li>The event Serializer defaults to a {@link org.axonframework.serialization.xml.XStreamSerializer}.</li>
     * <li>The {@code snapshotFilter} defaults to a {@link Predicate} which returns {@code true} regardless.</li>
     * <li>The {@code batchSize} defaults to an integer of size {@code 100}.</li>
     * <li>The {@link StorageStrategy} defaults to a {@link DocumentPerEventStorageStrategy}.</li>
     * </ul>
     * <p>
     * The {@link MongoTemplate} is a <b>hard requirement</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link MongoEventStorageEngine}
     */
    public static Builder builder() {
        return new Builder();
    }

    private static boolean isDuplicateKeyException(Exception exception) {
        return exception instanceof DuplicateKeyException || (exception instanceof MongoBulkWriteException &&
                ((MongoBulkWriteException) exception).getWriteErrors().stream().anyMatch(e -> e.getCode() == 11000));
    }

    /**
     * Make sure an index is created on the collection that stores domain events.
     */
    @PostConstruct
    public void ensureIndexes() {
        storageStrategy.ensureIndexes(template.eventCollection(), template.snapshotCollection());
    }

    @Override
    protected void appendEvents(List<? extends EventMessage<?>> events, Serializer serializer) {
        if (!events.isEmpty()) {
            try {
                storageStrategy.appendEvents(template.eventCollection(), events, serializer);
            } catch (Exception e) {
                handlePersistenceException(e, events.get(0));
            }
        }
    }

    @Override
    protected void storeSnapshot(DomainEventMessage<?> snapshot, Serializer serializer) {
        try {
            storageStrategy.appendSnapshot(template.snapshotCollection(), snapshot, serializer);
            storageStrategy.deleteSnapshots(
                    template.snapshotCollection(), snapshot.getAggregateIdentifier(), snapshot.getSequenceNumber()
            );
        } catch (Exception e) {
            handlePersistenceException(e, snapshot);
        }
    }

    @Override
    protected Stream<? extends DomainEventData<?>> readSnapshotData(String aggregateIdentifier) {
        return storageStrategy.findSnapshots(template.snapshotCollection(), aggregateIdentifier);
    }

    @Override
    protected List<? extends DomainEventData<?>> fetchDomainEvents(String aggregateIdentifier, long firstSequenceNumber,
                                                                   int batchSize) {
        return storageStrategy
                .findDomainEvents(template.eventCollection(), aggregateIdentifier, firstSequenceNumber, batchSize);
    }

    @Override
    protected List<? extends TrackedEventData<?>> fetchTrackedEvents(TrackingToken lastToken, int batchSize) {
        return storageStrategy.findTrackedEvents(template.eventCollection(), lastToken, batchSize);
    }

    @Override
    public Optional<Long> lastSequenceNumberFor(String aggregateIdentifier) {
        return storageStrategy.lastSequenceNumberFor(template.eventCollection(), aggregateIdentifier);
    }

    @Override
    public TrackingToken createTailToken() {
        return storageStrategy.createTailToken(template.eventCollection());
    }

    @Override
    public TrackingToken createHeadToken() {
        return createTokenAt(Instant.now());
    }

    @Override
    public TrackingToken createTokenAt(Instant dateTime) {
        return MongoTrackingToken.of(dateTime, Collections.emptyMap());
    }

    /**
     * Builder class to instantiate a {@link MongoEventStorageEngine}.
     * <p>
     * The following configurable fields have defaults:
     * <ul>
     * <li>The snapshot {@link Serializer} defaults to {@link org.axonframework.serialization.xml.XStreamSerializer}.</li>
     * <li>The {@link EventUpcaster} defaults to an {@link org.axonframework.serialization.upcasting.event.NoOpEventUpcaster}.</li>
     * <li>The {@link PersistenceExceptionResolver} is defaulted to {@link MongoEventStorageEngine#isDuplicateKeyException(Exception)}</li>
     * <li>The event Serializer defaults to a {@link org.axonframework.serialization.xml.XStreamSerializer}.</li>
     * <li>The {@code snapshotFilter} defaults to a {@link Predicate} which returns {@code true} regardless.</li>
     * <li>The {@code batchSize} defaults to an integer of size {@code 100}.</li>
     * <li>The {@link StorageStrategy} defaults to a {@link DocumentPerEventStorageStrategy}.</li>
     * </ul>
     * <p>
     * The {@link MongoTemplate} is a <b>hard requirement</b> and as such should be provided.
     */
    public static class Builder extends BatchingEventStorageEngine.Builder {

        private MongoTemplate template;
        private StorageStrategy storageStrategy = new DocumentPerEventStorageStrategy();

        private Builder() {
            persistenceExceptionResolver(MongoEventStorageEngine::isDuplicateKeyException);
        }

        @Override
        public Builder snapshotSerializer(Serializer snapshotSerializer) {
            super.snapshotSerializer(snapshotSerializer);
            return this;
        }

        @Override
        public Builder upcasterChain(EventUpcaster upcasterChain) {
            super.upcasterChain(upcasterChain);
            return this;
        }

        @Override
        public Builder persistenceExceptionResolver(PersistenceExceptionResolver persistenceExceptionResolver) {
            super.persistenceExceptionResolver(persistenceExceptionResolver);
            return this;
        }

        @Override
        public Builder eventSerializer(Serializer eventSerializer) {
            super.eventSerializer(eventSerializer);
            return this;
        }

        @Override
        public Builder snapshotFilter(Predicate<? super DomainEventData<?>> snapshotFilter) {
            super.snapshotFilter(snapshotFilter);
            return this;
        }

        @Override
        public Builder batchSize(int batchSize) {
            super.batchSize(batchSize);
            return this;
        }

        /**
         * Sets the {@link MongoTemplate} used to obtain the database and the collections.
         *
         * @param template the {@link MongoTemplate} used to obtain the database and the collections
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder mongoTemplate(MongoTemplate template) {
            assertNonNull(template, "MongoTemplate may not be null");
            this.template = template;
            return this;
        }

        /**
         * Sets the {@link StorageStrategy} specifying how to store and retrieve events and snapshots from the
         * collections. Defaults to a {@link DocumentPerEventStorageStrategy}, causing every event and snapshot to be
         * stored in a separate Mongo Document.
         *
         * @param storageStrategy the {@link StorageStrategy} specifying how to store and retrieve events and snapshots
         *                        from the collections
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder storageStrategy(StorageStrategy storageStrategy) {
            assertNonNull(storageStrategy, "StorageStrategy may not be null");
            this.storageStrategy = storageStrategy;
            return this;
        }

        /**
         * Initializes a {@link MongoEventStorageEngine} as specified through this Builder.
         *
         * @return a {@link MongoEventStorageEngine} as specified through this Builder
         */
        public MongoEventStorageEngine build() {
            return new MongoEventStorageEngine(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        @Override
        protected void validate() throws AxonConfigurationException {
            super.validate();
            assertNonNull(template, "The MongoTemplate is a hard requirement and should be provided");
        }
    }
}
