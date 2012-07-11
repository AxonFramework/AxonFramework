package org.axonframework.saga.annotation;

import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.saga.Saga;
import org.axonframework.saga.repository.inmemory.InMemorySagaRepository;
import org.junit.*;

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
public class AsyncAnnotatedSagaManagerRunner {

    private static final int LIFECYCLE_COUNT = 100000;
    private AsyncAnnotatedSagaManager sagaManager;
    private AsyncAnnotatedSagaManagerRunner.StubInMemorySagaRepository sagaRepository;
    private ExecutorService executorService;

    public static void main(String[] args) throws InterruptedException {
        int lifecycleCount = LIFECYCLE_COUNT;
        if (args.length > 0) {
            lifecycleCount = Integer.parseInt(args[0]);
        }
        AsyncAnnotatedSagaManagerRunner runner = new AsyncAnnotatedSagaManagerRunner();
        runner.setUp();
        try {
            runner.testMultipleDisconnectedSagaLifeCycle(lifecycleCount);
        } finally {
            runner.tearDown();
        }
    }

    public void setUp() {
        sagaManager = new AsyncAnnotatedSagaManager(mock(EventBus.class), StubAsyncSaga.class);
        sagaRepository = new StubInMemorySagaRepository();
        sagaManager.setSagaRepository(sagaRepository);
        executorService = Executors.newCachedThreadPool();
        sagaManager.setExecutor(executorService);
        sagaManager.setProcessorCount(3);
        sagaManager.setBufferSize(1024);
    }

    public void testMultipleDisconnectedSagaLifeCycle(int lifecycleCount) throws InterruptedException {
        sagaManager.start();
        assertEquals(0, sagaRepository.getKnownSagas());
        long t0 = System.currentTimeMillis();
        for (int t = 0; t < lifecycleCount; t++) {
            for (EventMessage message : createSimpleLifeCycle("association-" + t, "newAssociation-" + t)) {
                sagaManager.handle(message);
            }
        }
        sagaManager.stop();
        long t1 = System.currentTimeMillis();
        System.out.println("It took " + (t1 - t0) + " ms to process " + lifecycleCount + " Saga life cycles.");
        System.out.println("That is " + (lifecycleCount * 6 * 1000) / (t1 - t0) + " events per second.");
        executorService.shutdown();
        assertTrue("Service refused to stop in 1 second", executorService.awaitTermination(1, TimeUnit.SECONDS));
        assertEquals("Incorrect known saga count", lifecycleCount, sagaRepository.getKnownSagas());
        assertEquals("Incorrect live saga count", 0, sagaRepository.getLiveSagas());
    }

    private EventMessage[] createSimpleLifeCycle(String firstAssociation, String newAssociation) {
        EventMessage[] messages = new EventMessage[6];
        messages[0] = asEventMessage(new ForceCreateNewEvent(firstAssociation));
        messages[1] = asEventMessage(new OptionallyCreateNewEvent(firstAssociation));
        messages[2] = asEventMessage(new UpdateEvent(firstAssociation));
        messages[3] = asEventMessage(new AddAssociationEvent(firstAssociation, newAssociation));
        messages[4] = asEventMessage(new OptionallyCreateNewEvent(newAssociation));
        messages[5] = asEventMessage(new DeleteEvent(firstAssociation));
        return messages;
    }

    @After
    public void tearDown() {
        sagaManager.stop();
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

        @EndSaga
        @SagaEventHandler(associationProperty = "association")
        public void handleDelete(DeleteEvent event) {
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
