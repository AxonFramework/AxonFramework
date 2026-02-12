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

package org.axonframework.messaging.eventhandling.deadletter.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.jpa.EntityManagerExecutor;
import org.axonframework.eventsourcing.eventstore.jpa.JpaTransactionalExecutorProvider;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.DeadLetterWithContext;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueueTest;
import org.axonframework.messaging.deadletter.WrongDeadLetterTypeException;
import org.axonframework.conversion.json.JacksonConverter;
import org.axonframework.messaging.core.Context;
import org.axonframework.messaging.core.LegacyResources;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.eventhandling.processing.streaming.token.GlobalSequenceTrackingToken;
import org.axonframework.messaging.eventhandling.processing.streaming.token.TrackingToken;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class JpaSequencedDeadLetterQueueTest extends SequencedDeadLetterQueueTest<EventMessage> {

    private static final int MAX_SEQUENCES_AND_SEQUENCE_SIZE = 64;
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    private final EntityManagerFactory emf = Persistence.createEntityManagerFactory("dlq");
    private final EntityManager entityManager = emf.createEntityManager();
    private EntityTransaction transaction;

    @BeforeEach
    public void setUpJpa() {
        transaction = entityManager.getTransaction();
        transaction.begin();
    }

    @AfterEach
    public void rollback() {
        transaction.rollback();
    }

    @Override
    protected void setClock(Clock clock) {
        GenericDeadLetter.clock = clock;
    }

    @Override
    protected long maxSequences() {
        return MAX_SEQUENCES_AND_SEQUENCE_SIZE;
    }

    @Override
    protected long maxSequenceSize() {
        return MAX_SEQUENCES_AND_SEQUENCE_SIZE;
    }

    @Override
    public DeadLetterWithContext<EventMessage> generateInitialLetter() {
        return new DeadLetterWithContext<>(
                new GenericDeadLetter<>("sequenceIdentifier", generateEvent(), generateThrowable()),
                buildTestContext()
        );
    }

    @Override
    protected DeadLetterWithContext<EventMessage> generateFollowUpLetter() {
        return new DeadLetterWithContext<>(
                new GenericDeadLetter<>("sequenceIdentifier", generateEvent()),
                buildTestContext()
        );
    }

    private Context buildTestContext() {
        long seqNo = sequenceCounter.getAndIncrement();
        return Context.with(TrackingToken.RESOURCE_KEY, new GlobalSequenceTrackingToken(seqNo))
                      .withResource(LegacyResources.AGGREGATE_TYPE_KEY, "TestAggregate")
                      .withResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY, "aggregate-" + seqNo)
                      .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY, seqNo);
    }

    @Override
    protected DeadLetter<EventMessage> mapToQueueImplementation(DeadLetterWithContext<EventMessage> letterWithContext) {
        DeadLetter<EventMessage> deadLetter = letterWithContext.letter();
        if (deadLetter instanceof JpaDeadLetter) {
            return deadLetter;
        }
        if (deadLetter instanceof GenericDeadLetter) {
            Context context = letterWithContext.context() != null ? letterWithContext.context() : Context.empty();
            return new JpaDeadLetter<>(
                    IdentifierFactory.getInstance().generateIdentifier(),
                    0L,
                    ((GenericDeadLetter<EventMessage>) deadLetter).getSequenceIdentifier().toString(),
                    deadLetter.enqueuedAt(),
                    deadLetter.lastTouched(),
                    deadLetter.cause().orElse(null),
                    deadLetter.diagnostics(),
                    deadLetter.message(),
                    context
            );
        }
        throw new IllegalArgumentException("Can not map dead letter of type " + deadLetter.getClass().getName());
    }

    @Override
    protected DeadLetter<EventMessage> generateRequeuedLetter(DeadLetter<EventMessage> original,
                                                              Instant lastTouched,
                                                              Throwable requeueCause,
                                                              Metadata diagnostics) {
        setAndGetTime(lastTouched);
        return original.withCause(requeueCause).withDiagnostics(diagnostics).markTouched();
    }

    @Override
    protected void assertLetter(DeadLetter<? extends EventMessage> expected,
                                DeadLetter<? extends EventMessage> actual) {

        assertEquals(expected.message().payload(), actual.message().payload());
        assertEquals(expected.message().payloadType(), actual.message().payloadType());
        assertEquals(expected.message().metadata(), actual.message().metadata());
        assertEquals(expected.message().identifier(), actual.message().identifier());
        assertEquals(expected.cause(), actual.cause());
        assertEquals(expected.enqueuedAt(), actual.enqueuedAt());
        assertEquals(expected.lastTouched(), actual.lastTouched());
        assertEquals(expected.diagnostics(), actual.diagnostics());
    }

    @Override
    protected Context extractContext(DeadLetter<? extends EventMessage> deadLetter) {
        if (deadLetter instanceof JpaDeadLetter<? extends EventMessage> jpaDeadLetter) {
            return jpaDeadLetter.context();
        }
        return super.extractContext(deadLetter);
    }

    @Override
    public SequencedDeadLetterQueue<EventMessage> buildTestSubject() {
        JacksonConverter jacksonConverter = new JacksonConverter();
        return JpaSequencedDeadLetterQueue
                .builder()
                // TODO #3517 - why it doesn't work with: .transactionalExecutorProvider(new JpaTransactionalExecutorProvider(emf))
                .transactionalExecutorProvider(pc -> new EntityManagerExecutor(() -> entityManager))
                .maxSequences(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                .maxSequenceSize(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                .processingGroup("my_processing_group")
                .eventConverter(new DelegatingEventConverter(jacksonConverter))
                .genericConverter(jacksonConverter)
                .serializableResources(Set.of(
                        TrackingToken.RESOURCE_KEY,
                        LegacyResources.AGGREGATE_TYPE_KEY,
                        LegacyResources.AGGREGATE_IDENTIFIER_KEY,
                        LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY
                ))
                .build();
    }

    @Test
    void buildWithNegativeMaxQueuesThrowsAxonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequences(-1));
    }

    @Test
    void buildWithZeroMaxQueuesThrowsAxonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequences(0));
    }

    @Test
    void buildWithNegativeMaxQueueSizeThrowsAxonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequenceSize(-1));
    }

    @Test
    void buildWithZeroMaxQueueSizeThrowsAxonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequenceSize(0));
    }

    @Test
    void canNotSetNegativeQueryPageSize() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage> builder = JpaSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> builder.queryPageSize(-1));
    }

    @Test
    void canNotSetZeroQueryPageSize() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage> builder = JpaSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> builder.queryPageSize(0));
    }

    @Test
    void enqueuedLetterWithoutAggregateResourcesPreservesTrackingToken() {
        // given
        SequencedDeadLetterQueue<EventMessage> queue = buildTestSubject();
        Object sequenceId = generateId();
        GlobalSequenceTrackingToken expectedToken = new GlobalSequenceTrackingToken(42L);
        Context contextWithTokenOnly = Context.with(TrackingToken.RESOURCE_KEY, expectedToken);
        DeadLetter<EventMessage> letter = new GenericDeadLetter<>(
                "sequenceIdentifier", generateEvent(), generateThrowable()
        );

        // when
        queue.enqueue(sequenceId, letter, StubProcessingContext.fromContext(contextWithTokenOnly)).join();

        // then
        Iterator<DeadLetter<? extends EventMessage>> result =
                queue.deadLetterSequence(sequenceId, null).join().iterator();
        assertThat(result.hasNext()).isTrue();
        JpaDeadLetter<? extends EventMessage> retrieved = (JpaDeadLetter<? extends EventMessage>) result.next();

        assertThat(retrieved.context().getResource(TrackingToken.RESOURCE_KEY))
                .isEqualTo(expectedToken);
        assertThat(retrieved.context().containsResource(LegacyResources.AGGREGATE_TYPE_KEY))
                .isFalse();
        assertThat(retrieved.context().containsResource(LegacyResources.AGGREGATE_IDENTIFIER_KEY))
                .isFalse();
        assertThat(retrieved.context().containsResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY))
                .isFalse();
    }

    @Test
    void cannotRequeueGenericDeadLetter() {
        SequencedDeadLetterQueue<EventMessage> queue = buildTestSubject();
        DeadLetter<EventMessage> letter = generateInitialLetter().letter();
        CompletionException exception =
                assertThrows(CompletionException.class, () -> queue.requeue(letter, d -> d, null).join());
        assertThat(exception.getCause()).isInstanceOf(WrongDeadLetterTypeException.class);
    }

    @Test
    void cannotEvictGenericDeadLetter() {
        SequencedDeadLetterQueue<EventMessage> queue = buildTestSubject();
        DeadLetter<EventMessage> letter = generateInitialLetter().letter();
        CompletionException exception =
                assertThrows(CompletionException.class, () -> queue.evict(letter, null).join());
        assertThat(exception.getCause()).isInstanceOf(WrongDeadLetterTypeException.class);
    }

    @Test
    void canNotSetProcessingGroupToEmpty() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage> builder = JpaSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> builder.processingGroup(""));
    }

    @Test
    void canNotSetProcessingGroupToNull() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage> builder = JpaSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> builder.processingGroup(""));
    }

    @Test
    void canNotBuildWithoutProcessingGroup() {
        JacksonConverter jacksonConverter = new JacksonConverter();
        JpaSequencedDeadLetterQueue.Builder<EventMessage> builder = JpaSequencedDeadLetterQueue
                .builder()
                .transactionalExecutorProvider(new JpaTransactionalExecutorProvider(emf))
                .eventConverter(new DelegatingEventConverter(jacksonConverter))
                .genericConverter(jacksonConverter);
        assertThrows(AxonConfigurationException.class, builder::build);
    }

    @Test
    void canNotBuildWithoutTransactionalExecutorProvider() {
        JacksonConverter jacksonConverter = new JacksonConverter();
        JpaSequencedDeadLetterQueue.Builder<EventMessage> builder = JpaSequencedDeadLetterQueue
                .builder()
                .processingGroup("my_processing_Group")
                .eventConverter(new DelegatingEventConverter(jacksonConverter))
                .genericConverter(jacksonConverter);

        assertThrows(AxonConfigurationException.class, builder::build);
    }

    @Test
    void canNotSetNullConverter() {
        assertThrows(AxonConfigurationException.class, () -> JpaSequencedDeadLetterQueue.builder().converter(null));
    }
}
