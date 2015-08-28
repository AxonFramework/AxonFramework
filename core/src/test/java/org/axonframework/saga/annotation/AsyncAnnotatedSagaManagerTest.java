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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessingMonitor;
import org.axonframework.saga.Saga;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.internal.stubbing.answers.CallsRealMethods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * @author Allard Buijze
 */
public class AsyncAnnotatedSagaManagerTest {

    private static final Logger logger = Logger.getLogger(AsyncSagaEventProcessor.class);
    private static Level oldLevel;

    private AsyncAnnotatedSagaManager testSubject;
    private AsyncAnnotatedSagaManagerTest.StubInMemorySagaRepository sagaRepository;
    private ExecutorService executorService;
    private EventProcessingMonitor mockMonitor;
    private List<EventMessage> ackedMessages;
    private List<EventMessage> failedMessages;

    @BeforeClass
    public static void disableLogging() {
        oldLevel = logger.getLevel();
        logger.setLevel(Level.OFF);
    }

    @AfterClass
    public static void enableLogging() {
        logger.setLevel(oldLevel);
    }

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        testSubject = new AsyncAnnotatedSagaManager(StubAsyncSaga.class);
        sagaRepository = new StubInMemorySagaRepository();
        testSubject.setSagaRepository(sagaRepository);
        executorService = Executors.newCachedThreadPool();
        testSubject.setExecutor(executorService);
        testSubject.setProcessorCount(3);
        testSubject.setBufferSize(64);

        mockMonitor = mock(EventProcessingMonitor.class);
        ackedMessages = Collections.synchronizedList(new ArrayList());
        doAnswer(invocationOnMock -> {
            ackedMessages.addAll(((List) invocationOnMock.getArguments()[0]));
            return null;
        }).when(mockMonitor).onEventProcessingCompleted(isA(List.class));
    }

    @After
    public void tearDown() {
        testSubject.stop();
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

    @Test(timeout = 10000, expected = AxonConfigurationException.class)
    public void testThreadPoolExecutorHasTooSmallCorePoolSize() throws InterruptedException {
        testSubject.setStartTimeout(100);
        executorService = new ThreadPoolExecutor(1, 3, 5, TimeUnit.SECONDS, new ArrayBlockingQueue<>(25));
        testSubject.setExecutor(executorService);

        testSubject.start();
    }

    @Test
    public void testSingleSagaLifeCycle_FailedPersistence() throws InterruptedException {
        final StubInMemorySagaRepository spy = spy(sagaRepository);
        testSubject.setSagaRepository(spy);
        Exception failure = new RuntimeException("Mockexception");
        doThrow(failure).doAnswer(new CallsRealMethods()).when(spy).add(isA(Saga.class));
        doThrow(failure).doAnswer(new CallsRealMethods()).when(spy).commit(isA(Saga.class));
        testSubject.start();
        assertEquals(0, sagaRepository.getKnownSagas());
        for (EventMessage message : createSimpleLifeCycle("one", "two", true)) {
            testSubject.handle(message);
        }
        Thread.sleep(100);
        testSubject.stop();
        executorService.shutdown();
        assertTrue("Service refused to stop in 10 seconds", executorService.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals("Incorrect known saga count", 1, sagaRepository.getKnownSagas());
        assertEquals("Incorrect live saga count", 0, sagaRepository.getLiveSagas());
    }

   @Test
    public void testSingleSagaLifeCycle_NonTransientFailure() throws InterruptedException {
        final StubInMemorySagaRepository spy = spy(sagaRepository);
        testSubject.setSagaRepository(spy);
        Exception failure = new RuntimeException("Mockexception",
                                                 new AxonConfigurationException("Faking a wrong config"));
        doThrow(failure).when(spy).add(isA(Saga.class));
        doThrow(failure).when(spy).commit(isA(Saga.class));
        testSubject.start();
        assertEquals(0, sagaRepository.getKnownSagas());
        for (EventMessage message : createSimpleLifeCycle("one", "two", true)) {
            testSubject.handle(message);
        }
        testSubject.stop();
        executorService.shutdown();
        assertTrue("Service refused to stop in 1 seconds", executorService.awaitTermination(1, TimeUnit.SECONDS));
        assertEquals("Incorrect known saga count", 0, sagaRepository.getKnownSagas());
        assertEquals("Incorrect live saga count", 0, sagaRepository.getLiveSagas());
    }

    @Test
    public void testSingleSagaLifeCycle_FinalAttemptOnClose() throws InterruptedException {
        testSubject.subscribeEventProcessingMonitor(mockMonitor);
        final StubInMemorySagaRepository spy = spy(sagaRepository);
        testSubject.setSagaRepository(spy);
        Exception failure = new RuntimeException("Mock Exception");
        doThrow(failure).when(spy).commit(isA(Saga.class));
        doThrow(failure).when(spy).add(isA(Saga.class));
        testSubject.start();
        assertEquals(0, sagaRepository.getKnownSagas());


        final List<EventMessage> simpleLifeCycle = createSimpleLifeCycle("one", "two", true);
        for (EventMessage message : simpleLifeCycle) {
            testSubject.handle(message);
        }
        Thread.sleep(500);
        // to make sure at least one failed call was made...
        verify(spy, atLeastOnce()).add(isA(Saga.class));
        doCallRealMethod().when(spy).commit(isA(Saga.class));
        doCallRealMethod().when(spy).add(isA(Saga.class));
        testSubject.stop();
        executorService.shutdown();
        assertTrue("Service refused to stop in 1 seconds", executorService.awaitTermination(1, TimeUnit.SECONDS));
        assertEquals("Incorrect known saga count", 1, sagaRepository.getKnownSagas());
        assertEquals("Incorrect live saga count", 0, sagaRepository.getLiveSagas());

        assertEquals(simpleLifeCycle, ackedMessages);
    }

    @Test
    public void testMultipleDisconnectedSagaLifeCycle() throws InterruptedException {
        testSubject.subscribeEventProcessingMonitor(mockMonitor);
        testSubject.start();
        assertEquals(0, sagaRepository.getKnownSagas());
        int expectedMessageCount = 0;
        for (int t = 0; t < 1000; t++) {
            final List<EventMessage> lifeCycle = createSimpleLifeCycle("association-" + t, "newAssociation-" + t,
                                                                       (t & 1) == 0);
            expectedMessageCount += lifeCycle.size();
            for (EventMessage message : lifeCycle) {
                testSubject.handle(message);
            }
        }
        testSubject.stop();
        executorService.shutdown();
        assertTrue("Service refused to stop in 1 second", executorService.awaitTermination(1, TimeUnit.SECONDS));
        assertEquals("Incorrect known saga count", 1000, sagaRepository.getKnownSagas());
        assertEquals("Incorrect live saga count", 0, sagaRepository.getLiveSagas());

        assertEquals(expectedMessageCount, ackedMessages.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNullAssociationIgnoresEvent() throws InterruptedException {
        testSubject = new AsyncAnnotatedSagaManager(StubAsyncSaga.class, AnotherStubAsyncSaga.class,
                                                    ThirdStubAsyncSaga.class);
        testSubject.setSagaRepository(sagaRepository);
        executorService = Executors.newCachedThreadPool();
        testSubject.setExecutor(executorService);
        testSubject.setProcessorCount(3);
        testSubject.setBufferSize(64);

        testSubject.start();

        testSubject.handle(asEventMessage(new ForceCreateNewEvent(null)));
        testSubject.handle(asEventMessage(new OptionallyCreateNewEvent(null)));

        testSubject.stop();
        executorService.shutdown();
        assertTrue("Service refused to stop in 1 second", executorService.awaitTermination(1, TimeUnit.SECONDS));
        assertEquals("Incorrect known saga count", 0, sagaRepository.getKnownSagas());
        assertEquals("Incorrect live saga count", 0, sagaRepository.getLiveSagas());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExceptionsFromHandlerAreIgnored() throws InterruptedException {
        testSubject = new AsyncAnnotatedSagaManager(StubAsyncSaga.class);
        testSubject.setSagaRepository(sagaRepository);
        executorService = Executors.newCachedThreadPool();
        testSubject.setExecutor(executorService);
        testSubject.setProcessorCount(3);
        testSubject.setBufferSize(64);

        testSubject.start();

        testSubject.handle(asEventMessage(new ForceCreateNewEvent("test")));
        testSubject.handle(asEventMessage(new GenerateErrorOnHandlingEvent("test")));

        testSubject.stop();
        executorService.shutdown();
        assertTrue("Service refused to stop in 1 second", executorService.awaitTermination(1, TimeUnit.SECONDS));
        assertEquals("Incorrect known saga count", 1, sagaRepository.getKnownSagas());
        assertEquals("Incorrect live saga count", 1, sagaRepository.getLiveSagas());
        logger.setLevel(oldLevel);
    }

    @Test
    public void testMultipleDisconnectedSagaLifeCycle_WithOptionalStart() throws InterruptedException {
        testSubject = new AsyncAnnotatedSagaManager(StubAsyncSaga.class, AnotherStubAsyncSaga.class,
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
        List<EventMessage> publicationList = new ArrayList<>();
        if (includeForceStart) {
            publicationList.add(asEventMessage(new ForceCreateNewEvent(firstAssociation)));
        }
        publicationList.add(asEventMessage(new OptionallyCreateNewEvent(firstAssociation)));
        publicationList.add(asEventMessage(new UpdateEvent(firstAssociation)));
        publicationList.add(asEventMessage(new AddAssociationEvent(firstAssociation, newAssociation)));
        publicationList.add(asEventMessage(new OptionallyCreateNewEvent(newAssociation)));
        publicationList.add(asEventMessage(new DeleteEvent(newAssociation)));
        // this exception should never be thrown, as the previous event ends the saga
        return publicationList;
    }

    public static class ThirdStubAsyncSaga extends AnotherStubAsyncSaga {

    }

    public static class AnotherStubAsyncSaga extends StubAsyncSaga {

    }

    public static class StubAsyncSaga extends AbstractAnnotatedSaga {

        @StartSaga(forceNew = false)
        @SagaEventHandler(associationProperty = "association")
        public void handleOptionallyCreateNew(OptionallyCreateNewEvent event) {
        }

        @StartSaga(forceNew = true)
        @SagaEventHandler(associationProperty = "association")
        public void handleOptionallyCreateNew(ForceCreateNewEvent event) {
        }

        @SagaEventHandler(associationProperty = "association")
        public void handleAddAssociation(AddAssociationEvent event) {
            associateWith("association", event.getNewAssociation());
        }

        @SagaEventHandler(associationProperty = "association")
        public void handleUpdate(UpdateEvent event) {
        }

        @SagaEventHandler(associationProperty = "association")
        public void createError(GenerateErrorOnHandlingEvent event) {
            throw new RuntimeException("Stub");
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

    public static class StubInMemorySagaRepository extends InMemorySagaRepository {

        private Set<String> knownSagas = new ConcurrentSkipListSet<>();
        private Set<String> liveSagas = new ConcurrentSkipListSet<>();

        @Override
        public void commit(Saga saga) {
            assertTrue(knownSagas.contains(saga.getSagaIdentifier()));
            if (!saga.isActive()) {
                liveSagas.remove(saga.getSagaIdentifier());
            }
            super.commit(saga);
        }

        @Override
        public void add(Saga saga) {
            knownSagas.add(saga.getSagaIdentifier());
            liveSagas.add(saga.getSagaIdentifier());
            super.add(saga);
        }

        public int getKnownSagas() {
            return knownSagas.size();
        }

        public int getLiveSagas() {
            return liveSagas.size();
        }
    }
}
