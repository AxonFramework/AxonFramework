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

package org.axonframework.eventhandling.deadletter.jpa;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.jpa.SimpleEntityManagerProvider;
import org.axonframework.common.transaction.NoOpTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.deadletter.DeadLetter;
import org.axonframework.messaging.deadletter.GenericDeadLetter;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueueTest;
import org.axonframework.messaging.deadletter.WrongDeadLetterTypeException;
import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JpaSequencedDeadLetterQueueTest extends SequencedDeadLetterQueueTest<EventMessage<?>> {
    private static final int MAX_SEQUENCES_AND_SEQUENCE_SIZE = 64;

    private final TransactionManager transactionManager = spy(new NoOpTransactionManager());
    private final EntityManagerFactory emf = Persistence.createEntityManagerFactory("dlq");
    private final EntityManager entityManager = emf.createEntityManager();
    private EntityTransaction transaction;

    @BeforeEach
    public void setUpJpa() throws SQLException {
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
    public DeadLetter<EventMessage<?>> generateInitialLetter() {
        return new GenericDeadLetter<>("sequenceIdentifier", generateEvent(), generateThrowable());
    }

    @Override
    protected DeadLetter<EventMessage<?>> generateFollowUpLetter() {
        return new GenericDeadLetter<>("sequenceIdentifier", generateEvent());
    }

    @Override
    protected DeadLetter<EventMessage<?>> mapToQueueImplementation(DeadLetter<EventMessage<?>> deadLetter) {
        if (deadLetter instanceof JpaDeadLetter) {
            return deadLetter;
        }
        if (deadLetter instanceof GenericDeadLetter) {
            return new JpaDeadLetter<>(IdentifierFactory.getInstance().generateIdentifier(),
                                       0L,
                                       ((GenericDeadLetter<EventMessage<?>>) deadLetter).getSequenceIdentifier()
                                                                                        .toString(),
                                       deadLetter.enqueuedAt(),
                                       deadLetter.lastTouched(),
                                       deadLetter.cause().orElse(null),
                                       deadLetter.diagnostics(),
                                       deadLetter.message());
        }
        throw new IllegalArgumentException("Can not map dead letter of type " + deadLetter.getClass().getName());
    }

    @Override
    protected DeadLetter<EventMessage<?>> generateRequeuedLetter(DeadLetter<EventMessage<?>> original,
                                                                 Instant lastTouched,
                                                                 Throwable requeueCause,
                                                                 MetaData diagnostics) {
        setAndGetTime(lastTouched);
        return original.withCause(requeueCause).withDiagnostics(diagnostics).markTouched();
    }

    @Override
    protected void assertLetter(DeadLetter<? extends EventMessage<?>> expected,
                                DeadLetter<? extends EventMessage<?>> actual) {

        assertEquals(expected.message().getPayload(), actual.message().getPayload());
        assertEquals(expected.message().getPayloadType(), actual.message().getPayloadType());
        assertEquals(expected.message().getMetaData(), actual.message().getMetaData());
        assertEquals(expected.message().getIdentifier(), actual.message().getIdentifier());
        assertEquals(expected.cause(), actual.cause());
        assertEquals(expected.enqueuedAt(), actual.enqueuedAt());
        assertEquals(expected.lastTouched(), actual.lastTouched());
        assertEquals(expected.diagnostics(), actual.diagnostics());
    }

    @Override
    public SequencedDeadLetterQueue<EventMessage<?>> buildTestSubject() {
        EntityManagerProvider entityManagerProvider = new SimpleEntityManagerProvider(entityManager);
        return JpaSequencedDeadLetterQueue
                .builder()
                .transactionManager(transactionManager)
                .entityManagerProvider(entityManagerProvider)
                .maxSequences(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                .maxSequenceSize(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                .processingGroup("my_processing_group")
                .serializer(TestSerializer.JACKSON.getSerializer())
                .build();
    }

    @Test
    void buildWithNegativeMaxQueuesThrowsAxonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequences(-1));
    }

    @Test
    void buildWithZeroMaxQueuesThrowsAxonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequences(0));
    }

    @Test
    void buildWithNegativeMaxQueueSizeThrowsAxonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequenceSize(-1));
    }

    @Test
    void buildWithZeroMaxQueueSizeThrowsAxonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.maxSequenceSize(0));
    }

    @Test
    void canNotSetNegativeQueryPageSize() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> {
            builder.queryPageSize(-1);
        });
    }

    @Test
    void canNotSetZeroQueryPageSize() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> {
            builder.queryPageSize(0);
        });
    }

    @Test
    void cannotRequeueGenericDeadLetter() {
        SequencedDeadLetterQueue<EventMessage<?>> queue = buildTestSubject();
        DeadLetter<EventMessage<?>> letter = generateInitialLetter();
        assertThrows(WrongDeadLetterTypeException.class, () -> {
            queue.requeue(letter, d -> d);
        });
    }

    @Test
    void cannotEvictGenericDeadLetter() {
        SequencedDeadLetterQueue<EventMessage<?>> queue = buildTestSubject();
        DeadLetter<EventMessage<?>> letter = generateInitialLetter();
        assertThrows(WrongDeadLetterTypeException.class, () -> {
            queue.evict(letter);
        });
    }

    @Test
    void canNotSetProcessingGroupToEmpty() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> {
            builder.processingGroup("");
        });
    }

    @Test
    void canNotSetProcessingGroupToNull() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue.builder();
        assertThrows(AxonConfigurationException.class, () -> {
            builder.processingGroup("");
        });
    }

    @Test
    void canNotBuildWithoutProcessingGroup() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue
                .builder()
                .transactionManager(transactionManager)
                .entityManagerProvider(() -> entityManager)
                .serializer(TestSerializer.JACKSON.getSerializer());
        assertThrows(AxonConfigurationException.class, builder::build);
    }

    @Test
    void canNotBuildWithoutTransactionManager() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue
                .builder()
                .processingGroup("my_processing_Group")
                .entityManagerProvider(() -> entityManager)
                .serializer(TestSerializer.JACKSON.getSerializer());

        assertThrows(AxonConfigurationException.class, builder::build);
    }

    @Test
    void canNotBuildWithoutEntityManagerProvider() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue
                .builder()
                .processingGroup("my_processing_Group")
                .transactionManager(transactionManager)
                .serializer(TestSerializer.JACKSON.getSerializer());

        assertThrows(AxonConfigurationException.class, builder::build);
    }
}
