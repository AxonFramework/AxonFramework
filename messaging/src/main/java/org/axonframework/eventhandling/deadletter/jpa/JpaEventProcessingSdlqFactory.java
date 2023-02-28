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

package org.axonframework.eventhandling.deadletter.jpa;

import jakarta.persistence.EntityManager;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.EventProcessingSdlqFactory;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.serialization.Serializer;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.BuilderUtils.assertStrictPositive;

/**
 * JPA-backed implementation of the {@link EventProcessingSdlqFactory}, used for creating instances of
 * {@link JpaSequencedDeadLetterQueue queues} for storing dead letters containing {@link EventMessage Eventmessages}
 * durably as a {@link DeadLetterEntry}.
 * <p>
 *
 * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead-letters} within this queue.
 * @author Gerard Klijs
 * @since 4.8.0
 */
public class JpaEventProcessingSdlqFactory<M extends EventMessage<?>> implements EventProcessingSdlqFactory<M> {

    private final Supplier<JpaSequencedDeadLetterQueue.Builder<M>> supplier;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public JpaEventProcessingSdlqFactory(Builder<M> builder) {
        builder.validate();
        supplier = () -> {
            JpaSequencedDeadLetterQueue.Builder queueBuilder = JpaSequencedDeadLetterQueue
                    .builder()
                    .maxSequences(builder.maxSequences)
                    .maxSequenceSize(builder.maxSequenceSize)
                    .entityManagerProvider(builder.entityManagerProvider)
                    .transactionManager(builder.transactionManager)
                    .eventSerializer(builder.eventSerializer)
                    .genericSerializer(builder.genericSerializer)
                    .claimDuration(builder.claimDuration)
                    .queryPageSize(builder.queryPageSize)
                    .clearConverters();
            builder.converters.forEach(queueBuilder::addConverter);
            return queueBuilder;
        };
    }

    /**
     * Creates a new builder, capable of building a {@link JpaEventProcessingSdlqFactory} according to the provided
     * configuration.
     * <p>
     * Note that the
     * {@link {@link Builder#transactionManager}, {@link Builder#serializer(Serializer)} and {@link
     * Builder#entityManagerProvider} are mandatory for the queue to be constructed.
     * <p>
     *
     * @param <M> An implementation of {@link Message} contained in the {@link DeadLetter dead-letters} within this
     *            factory.
     * @return The builder
     */
    public static <M extends EventMessage<?>> Builder<M> builder() {
        return new Builder<>();
    }

    @Override
    public SequencedDeadLetterQueue<M> getSdlq(String processingGroup) {
        return supplier.get()
                       .processingGroup(processingGroup)
                       .build();
    }

    /**
     * Builder class to instantiate an {@link JpaSequencedDeadLetterQueue}.
     * <p>
     * The maximum number of unique sequences defaults to {@code 1024}, the maximum amount of dead letters inside a
     * unique sequence to {@code 1024}, the claim duration defaults to {@code 30} seconds, the query page size defaults
     * to {@code 100}, and the converters default to containing a single {@link EventMessageDeadLetterJpaConverter}.
     * <p>
     * If you have custom {@link EventMessage} to use with this queue, replace the current (or add a second) converter.
     * <p>
     * The {@link EntityManagerProvider}, {@link TransactionManager} and {@link Serializer} have to be configured for
     * the {@link JpaEventProcessingSdlqFactory} to be constructed.
     *
     * @param <T> The type of {@link Message} maintained in this {@link JpaEventProcessingSdlqFactory}.
     */
    public static class Builder<T extends EventMessage<?>> {

        private final List<DeadLetterJpaConverter<EventMessage<?>>> converters = new LinkedList<>();
        private int maxSequences = 1024;
        private int maxSequenceSize = 1024;
        private int queryPageSize = 100;
        private EntityManagerProvider entityManagerProvider;
        private TransactionManager transactionManager;
        private Serializer eventSerializer;
        private Serializer genericSerializer;
        private Duration claimDuration = Duration.ofSeconds(30);

        public Builder() {
            converters.add(new EventMessageDeadLetterJpaConverter());
        }

        /**
         * Sets the maximum number of unique sequences this {@link SequencedDeadLetterQueue} may contain.
         * <p>
         * The given {@code maxSequences} is required to be a positive number, higher or equal to {@code 128}. It
         * defaults to {@code 1024}.
         *
         * @param maxSequences The maximum amount of unique sequences for the queue under construction.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<T> maxSequences(int maxSequences) {
            assertStrictPositive(maxSequences,
                                 "The maximum number of sequences should be larger or equal to 0");
            this.maxSequences = maxSequences;
            return this;
        }

        /**
         * Sets the maximum amount of {@link DeadLetter letters} per unique sequences this
         * {@link SequencedDeadLetterQueue} can store.
         * <p>
         * The given {@code maxSequenceSize} is required to be a positive number, higher or equal to {@code 128}. It
         * defaults to {@code 1024}.
         *
         * @param maxSequenceSize The maximum amount of {@link DeadLetter letters} per unique  sequence.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<T> maxSequenceSize(int maxSequenceSize) {
            assertStrictPositive(maxSequenceSize,
                                 "The maximum number of entries in a sequence should be larger or equal to 128");
            this.maxSequenceSize = maxSequenceSize;
            return this;
        }

        /**
         * Sets the {@link EntityManagerProvider} which provides the {@link EntityManager} used to access the underlying
         * database for this {@link JpaSequencedDeadLetterQueue} implementation.
         *
         * @param entityManagerProvider a {@link EntityManagerProvider} which provides the {@link EntityManager} used to
         *                              access the underlying database
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> entityManagerProvider(
                EntityManagerProvider entityManagerProvider) {
            assertNonNull(entityManagerProvider, "EntityManagerProvider may not be null");
            this.entityManagerProvider = entityManagerProvider;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to manage transaction around fetching event data. Required by
         * certain databases for reading blob data.
         *
         * @param transactionManager a {@link TransactionManager} used to manage transaction around fetching event data
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@link Serializer} to (de)serialize the event payload, event metadata, tracking token, and
         * diagnostics of the {@link DeadLetter} when storing it to the database.
         *
         * @param serializer The serializer to use
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> serializer(Serializer serializer) {
            assertNonNull(serializer, "The serializer may not be null");
            this.eventSerializer = serializer;
            this.genericSerializer = serializer;
            return this;
        }

        /**
         * Sets the {@link Serializer} to (de)serialize the event payload, event metadata, and diagnostics of the
         * {@link DeadLetter} when storing it to the database.
         *
         * @param serializer The serializer to use
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> eventSerializer(Serializer serializer) {
            assertNonNull(serializer, "The eventSerializer may not be null");
            this.eventSerializer = serializer;
            return this;
        }

        /**
         * Sets the {@link Serializer} to (de)serialize the tracking token of the event in the {@link DeadLetter} when
         * storing it to the database.
         *
         * @param serializer The serializer to use
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> genericSerializer(Serializer serializer) {
            assertNonNull(serializer, "The genericSerializer may not be null");
            this.genericSerializer = serializer;
            return this;
        }

        /**
         * Removes all current converters currently configured, including the default
         * {@link EventMessageDeadLetterJpaConverter}.
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> clearConverters() {
            this.converters.clear();
            return this;
        }

        /**
         * Adds a {@link DeadLetterJpaConverter} to the configuration, which is used to deserialize dead-letter entries
         * from the database.
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> addConverter(DeadLetterJpaConverter<EventMessage<?>> converter) {
            assertNonNull(converter, "Can not add a null DeadLetterJpaConverter.");
            this.converters.add(converter);
            return this;
        }

        /**
         * Sets the claim duration, which is the time a message gets locked when processing and waiting for it to
         * complete. Other invocations of the {@link JpaSequencedDeadLetterQueue#process(Predicate, Function)} method
         * will be unable to process a sequence while the claim is active. Its default is 30 seconds.
         * <p>
         * Claims are automatically released once the item is requeued, the claim time is a backup policy in case of
         * unforeseen trouble such as down database connections.
         *
         * @param claimDuration The longest claim duration allowed.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> claimDuration(Duration claimDuration) {
            assertNonNull(claimDuration, "Claim duration can not be set to null.");
            this.claimDuration = claimDuration;
            return this;
        }

        /**
         * Modifies the page size used when retrieving a sequence of dead letters. Defaults to {@code 100} items in a
         * page.
         *
         * @param queryPageSize The page size
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder<T> queryPageSize(int queryPageSize) {
            assertStrictPositive(queryPageSize, "The query page size must be at least 1.");
            this.queryPageSize = queryPageSize;
            return this;
        }

        /**
         * Initializes a {@link JpaSequencedDeadLetterQueue} as specified through this Builder.
         *
         * @return A {@link JpaSequencedDeadLetterQueue} as specified through this Builder.
         */
        public JpaEventProcessingSdlqFactory<T> build() {
            return new JpaEventProcessingSdlqFactory<>(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException When one field asserts to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonNull(transactionManager,
                          "Must supply a TransactionManager when constructing a JpaSequencedDeadLetterQueue");
            assertNonNull(entityManagerProvider,
                          "Must supply a EntityManagerProvider when constructing a JpaSequencedDeadLetterQueue");
            assertNonNull(eventSerializer,
                          "Must supply an eventSerializer when constructing a JpaSequencedDeadLetterQueue");
            assertNonNull(genericSerializer,
                          "Must supply an genericSerializer when constructing a JpaSequencedDeadLetterQueue");
        }
    }
}
