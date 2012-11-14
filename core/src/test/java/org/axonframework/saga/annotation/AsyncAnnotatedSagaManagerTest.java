/*
 * Copyright (c) 2010-2012. Axon Framework
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

package org.axonframework.saga.annotation;

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.saga.Saga;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.junit.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.domain.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AsyncAnnotatedSagaManagerTest {

    private AsyncAnnotatedSagaManager testSubject;
    private EventBus eventBus;
    private AsyncAnnotatedSagaManagerTest.StubInMemorySagaRepository sagaRepository;
    private ExecutorService executorService;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        eventBus = mock(EventBus.class);
        testSubject = new AsyncAnnotatedSagaManager(eventBus, StubAsyncSaga.class);
        sagaRepository = new StubInMemorySagaRepository();
        testSubject.setSagaRepository(sagaRepository);
        executorService = Executors.newCachedThreadPool();
        testSubject.setExecutor(executorService);
        testSubject.setProcessorCount(3);
        testSubject.setBufferSize(64);
    }

    @Test
    public void testSingleSagaLifeCycle() throws InterruptedException {
        testSubject.start();
        assertEquals(0, sagaRepository.getKnownSagas());
        for (EventMessage message : createSimpleLifeCycle("one", "two", true)) {
            testSubject.handle(message);
        }
        testSubject.stop();
        executorService.shutdown();
        assertTrue("Service refused to stop in 1 second", executorService.awaitTermination(1, TimeUnit.SECONDS));
        assertEquals("Incorrect known saga count", 1, sagaRepository.getKnownSagas());
        assertEquals("Incorrect live saga count", 0, sagaRepository.getLiveSagas());
    }

    @Test
    public void testMultipleDisconnectedSagaLifeCycle() throws InterruptedException {
        testSubject.start();
        assertEquals(0, sagaRepository.getKnownSagas());
        for (int t = 0; t < 1000; t++) {
            for (EventMessage message : createSimpleLifeCycle("association-" + t, "newAssociation-" + t,
                                                              (t & 1) == 0)) {
                testSubject.handle(message);
            }
        }
        testSubject.stop();
        executorService.shutdown();
        assertTrue("Service refused to stop in 1 second", executorService.awaitTermination(1, TimeUnit.SECONDS));
        assertEquals("Incorrect known saga count", 1000, sagaRepository.getKnownSagas());
        assertEquals("Incorrect live saga count", 0, sagaRepository.getLiveSagas());
    }

    @Test
    public void testMultipleDisconnectedSagaLifeCycle_WithOptionalStart() throws InterruptedException {
        testSubject = new AsyncAnnotatedSagaManager(eventBus, StubAsyncSaga.class, AnotherStubAsyncSaga.class,
                                                    ThirdStubAsyncSaga.class);
        testSubject.setSagaRepository(sagaRepository);
        executorService = Executors.newCachedThreadPool();
        testSubject.setExecutor(executorService);
        testSubject.setProcessorCount(3);
        testSubject.setBufferSize(64);

        testSubject.start();
        assertEquals(0, sagaRepository.getKnownSagas());
        for (int t = 0; t < 500; t++) {
            for (EventMessage message : createSimpleLifeCycle("association-" + t, "newAssociation-" + t, false)) {
                testSubject.handle(message);
            }
        }
        testSubject.stop();
        executorService.shutdown();
        assertTrue("Service refused to stop in 1 second", executorService.awaitTermination(1, TimeUnit.SECONDS));
        assertEquals("Incorrect known saga count", 1500, sagaRepository.getKnownSagas());
        assertEquals("Incorrect live saga count", 0, sagaRepository.getLiveSagas());
    }

    private List<EventMessage> createSimpleLifeCycle(String firstAssociation, String newAssociation,
                                                     boolean includeForceStart) {
        List<EventMessage> publicationList = new ArrayList<EventMessage>();
        if (includeForceStart) {
            publicationList.add(asEventMessage(new ForceCreateNewEvent(firstAssociation)));
        }
        publicationList.add(asEventMessage(new OptionallyCreateNewEvent(firstAssociation)));
        publicationList.add(asEventMessage(new UpdateEvent(firstAssociation)));
        publicationList.add(asEventMessage(new AddAssociationEvent(firstAssociation, newAssociation)));
        publicationList.add(asEventMessage(new OptionallyCreateNewEvent(newAssociation)));
        publicationList.add(asEventMessage(new DeleteEvent(newAssociation)));
        // this exception should never be thrown, as the previous event ends the saga
        publicationList.add(asEventMessage(new GenerateErrorOnHandlingEvent(firstAssociation)));
        return publicationList;
    }

    @After
    public void tearDown() {
        testSubject.stop();
    }

    public static class ThirdStubAsyncSaga extends AnotherStubAsyncSaga {

    }

    public static class AnotherStubAsyncSaga extends StubAsyncSaga {

    }

    public static class StubAsyncSaga extends AbstractAnnotatedSaga {

        private int optionallyNewInvocations;
        private int forceNewInvocations;
        private int updateInvocations;
        private int deleteInvocations;

        @StartSaga(forceNew = false)
        @SagaEventHandler(associationProperty = "association")
        public void handleOptionallyCreateNew(OptionallyCreateNewEvent event) {
//            optionallyNewInvocations++;
        }

        @StartSaga(forceNew = true)
        @SagaEventHandler(associationProperty = "association")
        public void handleOptionallyCreateNew(ForceCreateNewEvent event) {
//            optionallyNewInvocations++;
        }

        @SagaEventHandler(associationProperty = "association")
        public void handleAddAssociation(AddAssociationEvent event) {
//            updateInvocations++;
            associateWith("association", event.getNewAssociation());
        }

        @SagaEventHandler(associationProperty = "association")
        public void handleUpdate(UpdateEvent event) {
//            updateInvocations++;
        }

        @SagaEventHandler(associationProperty = "association")
        public void createError(GenerateErrorOnHandlingEvent event) {
            throw new RuntimeException("Handled an event that should cause an exception");
        }

        @EndSaga
        @SagaEventHandler(associationProperty = "association")
        public void handleDelete(DeleteEvent event) {
//            deleteInvocations++;
        }
    }

    private static class OptionallyCreateNewEvent extends AbstractSagaTestEvent {

        private OptionallyCreateNewEvent(String association) {
            super(association);
        }
    }

    private static class ForceCreateNewEvent extends AbstractSagaTestEvent {

        private ForceCreateNewEvent(String association) {
            super(association);
        }
    }

    private static class UpdateEvent extends AbstractSagaTestEvent {

        private UpdateEvent(String association) {
            super(association);
        }
    }

    private static class DeleteEvent extends AbstractSagaTestEvent {

        private DeleteEvent(String association) {
            super(association);
        }
    }

    private static class GenerateErrorOnHandlingEvent extends AbstractSagaTestEvent {

        private GenerateErrorOnHandlingEvent(String association) {
            super(association);
        }
    }

    private static class AddAssociationEvent extends AbstractSagaTestEvent {

        private final String newAssociation;

        private AddAssociationEvent(String association, String newAssociation) {
            super(association);
            this.newAssociation = newAssociation;
        }

        public String getNewAssociation() {
            return newAssociation;
        }
    }

    private static class AbstractSagaTestEvent {

        private final String association;

        private AbstractSagaTestEvent(String association) {
            this.association = association;
        }

        public String getAssociation() {
            return association;
        }
    }

    private class StubInMemorySagaRepository extends InMemorySagaRepository {

        private AtomicInteger knownSagas = new AtomicInteger();
        private AtomicInteger liveSagas = new AtomicInteger();

        @Override
        public void commit(Saga saga) {
            if (!saga.isActive()) {
                liveSagas.decrementAndGet();
            }
            super.commit(saga);
        }

        @Override
        public void add(Saga saga) {
            knownSagas.incrementAndGet();
            liveSagas.incrementAndGet();
            super.add(saga);
        }

        public int getKnownSagas() {
            return knownSagas.get();
        }

        public int getLiveSagas() {
            return liveSagas.get();
        }
    }
}
