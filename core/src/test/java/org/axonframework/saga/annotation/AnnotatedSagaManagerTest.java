/*
 * Copyright (c) 2010-2014. Axon Framework
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

import org.axonframework.common.annotation.MetaData;
import org.axonframework.correlation.CorrelationDataHolder;
import org.axonframework.correlation.CorrelationDataProvider;
import org.axonframework.domain.EventMessage;
import org.axonframework.domain.GenericEventMessage;
import org.axonframework.domain.Message;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.saga.AssociationValue;
import org.axonframework.saga.Saga;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.junit.*;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.axonframework.domain.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class AnnotatedSagaManagerTest {

    private InMemorySagaRepository sagaRepository;
    private AnnotatedSagaManager manager;

    @Before
    public void setUp() throws Exception {
        CorrelationDataHolder.clear();
        sagaRepository = spy(new InMemorySagaRepository());
        manager = new AnnotatedSagaManager(sagaRepository, new SimpleEventBus(), MyTestSaga.class);
    }

    @After
    public void tearDown() throws Exception {
        CorrelationDataHolder.clear();
    }

    @Test
    public void testCreationPolicy_NoneExists() {
        manager.handle(new GenericEventMessage<StartingEvent>(new StartingEvent("123")));
        assertEquals(1, repositoryContents("123", MyTestSaga.class).size());
    }

    @Test
    public void testCreationPolicy_OneAlreadyExists() {
        manager.handle(new GenericEventMessage<StartingEvent>(new StartingEvent("123")));
        manager.handle(new GenericEventMessage<StartingEvent>(new StartingEvent("123")));
        assertEquals(1, repositoryContents("123", MyTestSaga.class).size());
    }

    @Test
    public void testHandleUnrelatedEvent() {
        manager.handle(new GenericEventMessage("Unrelated"));
        verify(sagaRepository, never()).find(any(Class.class), (AssociationValue) isNull());
    }

    @Test
    public void testCreationPolicy_CreationForced() {
        StartingEvent startingEvent = new StartingEvent("123");
        manager.handle(new GenericEventMessage<StartingEvent>(startingEvent));
        manager.handle(new GenericEventMessage<ForcingStartEvent>(new ForcingStartEvent("123")));
        Set<MyTestSaga> sagas = repositoryContents("123", MyTestSaga.class);
        assertEquals(2, sagas.size());
        for (MyTestSaga saga : sagas) {
            if (saga.getCapturedEvents().contains(startingEvent)) {
                assertEquals(2, saga.getCapturedEvents().size());
            }
            assertTrue(saga.getCapturedEvents().size() >= 1);
        }
    }

    @Test
    public void testCreationPolicy_SagaNotCreated() {
        manager.handle(new GenericEventMessage<MiddleEvent>(new MiddleEvent("123")));
        assertEquals(0, repositoryContents("123", MyTestSaga.class).size());
    }

    @Test
    public void testMostSpecificHandlerEvaluatedFirst() {
        manager.handle(new GenericEventMessage<StartingEvent>(new StartingEvent("12")));
        manager.handle(new GenericEventMessage<StartingEvent>(new StartingEvent("23")));
        assertEquals(1, repositoryContents("12", MyTestSaga.class).size());
        assertEquals(1, repositoryContents("23", MyTestSaga.class).size());

        manager.handle(new GenericEventMessage<MiddleEvent>(new MiddleEvent("12")));
        manager.handle(new GenericEventMessage<MiddleEvent>(new MiddleEvent("23"), singletonMap("catA", "value")));
        assertEquals(0, repositoryContents("12", MyTestSaga.class).iterator().next().getSpecificHandlerInvocations());
        assertEquals(1, repositoryContents("23", MyTestSaga.class).iterator().next().getSpecificHandlerInvocations());
    }

    @Test
    public void testLifecycle_DestroyedOnEnd() {
        manager.handle(new GenericEventMessage<StartingEvent>(new StartingEvent("12")));
        manager.handle(new GenericEventMessage<StartingEvent>(new StartingEvent("23")));
        manager.handle(new GenericEventMessage<MiddleEvent>(new MiddleEvent("12")));
        manager.handle(new GenericEventMessage<MiddleEvent>(new MiddleEvent("23"), singletonMap("catA",
                                                                                                "value")));
        assertEquals(1, repositoryContents("12", MyTestSaga.class).size());
        assertEquals(1, repositoryContents("23", MyTestSaga.class).size());
        assertEquals(0, repositoryContents("12", MyTestSaga.class).iterator().next().getSpecificHandlerInvocations());
        assertEquals(1, repositoryContents("23", MyTestSaga.class).iterator().next().getSpecificHandlerInvocations());
        manager.handle(new GenericEventMessage<EndingEvent>(new EndingEvent("12")));
        assertEquals(1, repositoryContents("23", MyTestSaga.class).size());
        assertEquals(0, repositoryContents("12", MyTestSaga.class).size());
        manager.handle(new GenericEventMessage<EndingEvent>(new EndingEvent("23")));
        assertEquals(0, repositoryContents("23", MyTestSaga.class).size());
        assertEquals(0, repositoryContents("12", MyTestSaga.class).size());
    }

    @Test
    public void testNullAssociationValueDoesNotThrowNullPointer() {
        manager.handle(asEventMessage(new StartingEvent(null)));
    }

    @Test
    public void testLifeCycle_ExistingInstanceIgnoresEvent() {
        manager.handle(new GenericEventMessage<StartingEvent>(new StartingEvent("12")));
        manager.handle(new GenericEventMessage<StubDomainEvent>(new StubDomainEvent()));
        assertEquals(1, repositoryContents("12", MyTestSaga.class).size());
        assertEquals(1, repositoryContents("12", MyTestSaga.class).iterator().next().getCapturedEvents().size());
    }

    @Test
    public void testLifeCycle_IgnoredEventDoesNotCreateInstance() {
        manager.handle(new GenericEventMessage<StubDomainEvent>(new StubDomainEvent()));
        assertEquals(0, repositoryContents("12", MyTestSaga.class).size());
    }

    @Test
    public void testSagaTypeTakenIntoConsiderationWhenCheckingForSagasInCreation() throws InterruptedException {
        manager = new AnnotatedSagaManager(sagaRepository, new SimpleEventBus(),
                                           MyOtherTestSaga.class, MyTestSaga.class);

        ExecutorService executorService = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 100; i++) {
            executorService.execute(new HandleEventTask(
                    GenericEventMessage.asEventMessage(new StartingEvent("id" + i))));
            executorService.execute(new HandleEventTask(
                    GenericEventMessage.asEventMessage(new OtherStartingEvent("id" + i))));
        }
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        for (int i = 0; i < 100; i++) {
            assertEquals("MyTestSaga missing for id" + i, 1, repositoryContents("id" + i, MyTestSaga.class).size());
            assertEquals("MyOtherTestSaga missing for id" + i, 1, repositoryContents("id" + i, MyOtherTestSaga.class).size());
        }
    }

    @Test
    public void testCorrelationDataReadFromProvider() throws Exception {
        CorrelationDataProvider<? super EventMessage> correlationDataProvider = mock(CorrelationDataProvider.class);
        manager.setCorrelationDataProvider(correlationDataProvider);

        manager.handle(asEventMessage(new StartingEvent("12")).withMetaData(singletonMap("key", "val")));

        verify(correlationDataProvider).correlationDataFor(isA(EventMessage.class));
    }

    @Test
    public void testCorrelationDataReadFromProviders() throws Exception {
        CorrelationDataProvider<Message> correlationDataProvider1 = mock(CorrelationDataProvider.class);
        CorrelationDataProvider<Message> correlationDataProvider2 = mock(CorrelationDataProvider.class);
        manager.setCorrelationDataProviders(asList(correlationDataProvider1, correlationDataProvider2));

        manager.handle(asEventMessage(new StartingEvent("12")).withMetaData(singletonMap("key", "val")));

        verify(correlationDataProvider1).correlationDataFor(isA(EventMessage.class));
        verify(correlationDataProvider2).correlationDataFor(isA(EventMessage.class));
    }

    @Test(timeout = 5000)
    public void testEventForSagaIsHandledWhenSagaIsBeingCreated() throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        final CountDownLatch awaitStart = new CountDownLatch(1);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                manager.handle(new GenericEventMessage<StartingEvent>(new SlowStartingEvent("12", awaitStart, 100)));
            }
        });
        awaitStart.await();
        manager.handle(asEventMessage(new MiddleEvent("12")));
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        assertEquals(1, repositoryContents("12", MyTestSaga.class).size());
        assertEquals(2, repositoryContents("12", MyTestSaga.class).iterator().next().getCapturedEvents().size());
    }

    private <T extends Saga> Set<T> repositoryContents(String lookupValue, Class<T> sagaType) {
        final Set<String> identifiers = sagaRepository.find(sagaType, new AssociationValue("myIdentifier",
                                                                                           lookupValue));
        Set<T> sagas = new HashSet<T>();
        for (String identifier : identifiers) {
            sagas.add((T) sagaRepository.load(identifier));
        }
        return sagas;
    }

    public static class MyOtherTestSaga extends AbstractAnnotatedSaga {

        @StartSaga
        @SagaEventHandler(associationProperty = "myIdentifier")
        public void handleSomeEvent(OtherStartingEvent event) throws InterruptedException {
        }
    }

    public static class MyTestSaga extends AbstractAnnotatedSaga {

        private static final long serialVersionUID = -1562911263884220240L;
        private List<Object> capturedEvents = new LinkedList<Object>();
        private int specificHandlerInvocations = 0;

        @StartSaga
        @SagaEventHandler(associationProperty = "myIdentifier")
        public void handleSomeEvent(StartingEvent event) throws InterruptedException {
            capturedEvents.add(event);
        }

        @StartSaga
        @SagaEventHandler(associationProperty = "myIdentifier")
        public void handleSomeEvent(SlowStartingEvent event) throws InterruptedException {
            event.getStartCdl().countDown();
            capturedEvents.add(event);
            Thread.sleep(event.getDuration());
        }

        @StartSaga(forceNew = true)
        @SagaEventHandler(associationProperty = "myIdentifier")
        public void handleSomeEvent(ForcingStartEvent event) {
            capturedEvents.add(event);
        }

        @EndSaga
        @SagaEventHandler(associationProperty = "myIdentifier")
        public void handleSomeEvent(EndingEvent event) {
            capturedEvents.add(event);
        }

        @SagaEventHandler(associationProperty = "myIdentifier")
        public void handleMiddleEvent(MiddleEvent event) {
            capturedEvents.add(event);
        }

        @SagaEventHandler(associationProperty = "myIdentifier")
        public void handleSpecificMiddleEvent(MiddleEvent event, @MetaData(value = "catA", required = true) String category) {
            // this handler is more specific, but requires meta data that not all events might have
            capturedEvents.add(event);
            specificHandlerInvocations++;
        }

        public List<Object> getCapturedEvents() {
            return capturedEvents;
        }

        public int getSpecificHandlerInvocations() {
            return specificHandlerInvocations;
        }
    }

    public static abstract class MyIdentifierEvent {

        private String myIdentifier;

        protected MyIdentifierEvent(String myIdentifier) {
            this.myIdentifier = myIdentifier;
        }

        public String getMyIdentifier() {
            return myIdentifier;
        }
    }

    public static class StartingEvent extends MyIdentifierEvent {

        protected StartingEvent(String myIdentifier) {
            super(myIdentifier);
        }
    }

    public static class OtherStartingEvent extends MyIdentifierEvent {

        private final CountDownLatch countDownLatch;

        protected OtherStartingEvent(String myIdentifier) {
            this(myIdentifier, null);
        }

        public OtherStartingEvent(String id, CountDownLatch countDownLatch) {
            super(id);
            this.countDownLatch = countDownLatch;
        }
    }

    public static class SlowStartingEvent extends StartingEvent {


        private final CountDownLatch startCdl;
        private final long duration;

        protected SlowStartingEvent(String myIdentifier, CountDownLatch startCdl, long duration) {
            super(myIdentifier);
            this.startCdl = startCdl;
            this.duration = duration;
        }

        public long getDuration() {
            return duration;
        }

        public CountDownLatch getStartCdl() {
            return startCdl;
        }
    }

    public static class ForcingStartEvent extends MyIdentifierEvent {

        protected ForcingStartEvent(String myIdentifier) {
            super(myIdentifier);
        }
    }

    public static class EndingEvent extends MyIdentifierEvent {

        protected EndingEvent(String myIdentifier) {
            super(myIdentifier);
        }
    }

    public static class MiddleEvent extends MyIdentifierEvent {

        protected MiddleEvent(String myIdentifier) {
            super(myIdentifier);
        }
    }

    private class HandleEventTask implements Runnable {

        private final EventMessage<?> eventMessage;

        public HandleEventTask(EventMessage<?> eventMessage) {
            this.eventMessage = eventMessage;
        }

        @Override
        public void run() {
            manager.handle(eventMessage);
        }
    }
}
