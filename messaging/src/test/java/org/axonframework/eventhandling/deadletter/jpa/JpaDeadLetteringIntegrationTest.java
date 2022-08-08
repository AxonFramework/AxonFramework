///*
// * Copyright (c) 2010-2022. Axon Framework
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *    http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package org.axonframework.eventhandling.deadletter.jpa;
//
//import org.axonframework.common.jpa.EntityManagerProvider;
//import org.axonframework.common.jpa.SimpleEntityManagerProvider;
//import org.axonframework.common.transaction.Transaction;
//import org.axonframework.common.transaction.TransactionManager;
//import org.axonframework.eventhandling.EventMessage;
//import org.axonframework.eventhandling.GenericEventMessage;
//import org.axonframework.eventhandling.deadletter.DeadLetteringEventHandlerInvoker;
//import org.axonframework.eventhandling.deadletter.DeadLetteringEventIntegrationTest;
//import org.axonframework.eventhandling.deadletter.EventHandlingQueueIdentifier;
//import org.axonframework.messaging.MetaData;
//import org.axonframework.messaging.deadletter.DeadLetter;
//import org.axonframework.messaging.deadletter.DeadLetterQueue;
//import org.axonframework.messaging.deadletter.GenericDeadLetter;
//import org.axonframework.messaging.deadletter.QueueIdentifier;
//import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue;
//import org.axonframework.messaging.deadletter.SequencedDeadLetterQueueTest;
//import org.axonframework.serialization.upcasting.event.EventUpcaster;
//import org.junit.jupiter.api.*;
//
//import java.sql.SQLException;
//import java.time.Duration;
//import java.time.Instant;
//import java.util.UUID;
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//import java.util.stream.Stream;
//import javax.persistence.EntityManager;
//import javax.persistence.EntityManagerFactory;
//import javax.persistence.EntityTransaction;
//import javax.persistence.Persistence;
//
//import static org.axonframework.utils.AssertUtils.assertWithin;
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.*;
//
///**
// * An implementation of the {@link DeadLetteringEventIntegrationTest} validating the {@link OldJpaDeadLetterQueue} with
// * an {@link org.axonframework.eventhandling.EventProcessor} and {@link DeadLetteringEventHandlerInvoker}.
// *
// * @author Mitchell Herrijgers
// */
//class JpaDeadLetteringIntegrationTest extends SequencedDeadLetterQueueTest<EventMessage<?>> {
//
//    EntityManagerFactory emf = Persistence.createEntityManagerFactory("dlq");
//    EntityManager entityManager = emf.createEntityManager();
//    private final TransactionManager transactionManager = spy(new NoOpTransactionManager());
//    private EntityTransaction transaction;
//    private EventUpcaster eventUpcaster = mock(EventUpcaster.class);
//
//    @BeforeEach
//    public void setUpJpa() throws SQLException {
//        transaction = entityManager.getTransaction();
//        transaction.begin();
//
//        // By default, do not upcast
//        when(eventUpcaster.upcast(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
//    }
//
//    @AfterEach
//    public void rollback() {
//        entityManager.createQuery("DELETE FROM DeadLetterEntry dl").executeUpdate();
//        entityManager.flush();
//        entityManager.clear();
//        transaction.commit();
//    }
//
//    @Override
//    protected SequencedDeadLetterQueue<EventMessage<?>> buildTestSubject() {
//        EntityManagerProvider entityManagerProvider = new SimpleEntityManagerProvider(entityManager);
//        return JpaSequencedDeadLetterQueue.builder()
//                                          .group("some_group")
//                                          .transactionManager(transactionManager)
//                                          .entityManagerProvider(entityManagerProvider)
//                                          .upcasterChain(eventUpcaster)
//                                          .build();
//    }
//
//
//    @Override
//    protected DeadLetter<EventMessage<?>> generateInitialLetter() {
//        return new GenericDeadLetter<>("sequenceIdentifier", generateEvent(), generateThrowable());
//    }
//
//    @Override
//    protected DeadLetter<EventMessage<?>> generateFollowUpLetter() {
//        return new GenericDeadLetter<>("sequenceIdentifier", generateEvent());
//    }
//
//    @Override
//    protected DeadLetter<EventMessage<?>> generateRequeuedLetter(DeadLetter<EventMessage<?>> original,
//                                                                 Instant lastTouched,
//                                                                 Throwable requeueCause,
//                                                                 MetaData diagnostics) {
//        setAndGetTime(lastTouched);
//        return original.withCause(requeueCause).andDiagnostics(diagnostics).markTouched();
//    }
//
////    @Override
////    protected DeadLetterQueue<EventMessage<?>> buildDeadLetterQueue() {
////        EntityManagerProvider entityManagerProvider = new SimpleEntityManagerProvider(entityManager);
////        return OldJpaDeadLetterQueue.<EventMessage<?>>builder()
////                                    .transactionManager(transactionManager)
////                                    .entityManagerProvider(entityManagerProvider)
////                                    .expireThreshold(Duration.ofMillis(50))
////                                    .scheduledExecutorService(Executors.newSingleThreadScheduledExecutor())
////                                    .upcasterChain(eventUpcaster)
////                                    .build();
////    }
////
////    @Test
////    void testUpcasterChainReturningNullWorksCorrectly() {
////        String aggregateId = UUID.randomUUID().toString();
////        QueueIdentifier queueId = new EventHandlingQueueIdentifier(aggregateId, PROCESSING_GROUP);
////        eventSource.publishMessage(GenericEventMessage.asEventMessage(new DeadLetterableEvent(aggregateId,
////                                                                                              FAIL,
////                                                                                              1)));
////
////        when(eventUpcaster.upcast(any())).thenAnswer(invocationOnMock -> Stream.empty());
////
////        startProcessingEvent();
////
////        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(1, streamingProcessor.processingStatus().size()));
////        //noinspection OptionalGetWithoutIsPresent
////        assertWithin(
////                1, TimeUnit.SECONDS,
////                () -> assertTrue(streamingProcessor.processingStatus().get(0).getCurrentPosition().getAsLong() >= 1)
////        );
////
////        assertTrue(eventHandlingComponent.unsuccessfullyHandled(aggregateId));
////        assertEquals(1, eventHandlingComponent.unsuccessfulHandlingCount(aggregateId));
////
////        assertTrue(deadLetterQueue.contains(queueId));
////
////        startDeadLetterEvaluation();
////
////        // Should not contain the messages (since they were upcasted to null), and also not have been executed
////        assertWithin(1, TimeUnit.SECONDS, () -> assertFalse(deadLetterQueue.contains(queueId)));
////        assertWithin(1, TimeUnit.SECONDS, () -> assertEquals(
////                0,
////                eventHandlingComponent.successfulHandlingCount(aggregateId)
////        ));
////    }
//
//    /**
//     * A non-final {@link TransactionManager} implementation, so that it can be spied upon through Mockito.
//     */
//    private static class NoOpTransactionManager implements TransactionManager {
//
//        @Override
//        public Transaction startTransaction() {
//            return new Transaction() {
//                @Override
//                public void commit() {
//                    // No-op
//                }
//
//                @Override
//                public void rollback() {
//                    // No-op
//                }
//            };
//        }
//    }
//}
