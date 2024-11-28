/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.modelling.command;

import org.axonframework.common.lock.Lock;
import org.axonframework.common.lock.LockFactory;
import org.axonframework.common.lock.PessimisticLockFactory;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.QualifiedNameUtils;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.axonframework.messaging.QualifiedNameUtils.fromDottedName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link LockingRepository}.
 *
 * @author Allard Buijze
 */
class LockingRepositoryTest {

    private static final Message<?> MESSAGE = new GenericMessage<Object>(QualifiedNameUtils.fromDottedName("test.message"), "test");

    private EventBus eventBus;
    private LockFactory lockFactory;
    private Lock lock;
    private TestSpanFactory spanFactory;

    private InMemoryLockingRepository testSubject;

    @BeforeEach
    void setUp() {
        spanFactory = new TestSpanFactory();
        eventBus = spy(SimpleEventBus.builder().build());
        lockFactory = spy(PessimisticLockFactory.usingDefaults());
        when(lockFactory.obtainLock(anyString()))
                .thenAnswer(invocation -> lock = spy((Lock) invocation.callRealMethod()));

        testSubject = InMemoryLockingRepository.builder()
                                               .lockFactory(lockFactory)
                                               .eventBus(eventBus)
                                               .spanFactory(DefaultRepositorySpanFactory.builder()
                                                                                        .spanFactory(spanFactory)
                                                                                        .build())
                                               .build();
        testSubject = spy(testSubject);

        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @AfterEach
    void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void storeNewAggregate() throws Exception {
        startAndGetUnitOfWork();
        StubAggregate aggregate = new StubAggregate();
        testSubject.newInstance(() -> aggregate).execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        //noinspection resource
        verify(lockFactory).obtainLock(aggregate.getIdentifier());
        verify(eventBus).publish(isA(EventMessage.class));
    }

    @Test
    void lockingIsTracedDuringCreation() throws Exception {
        startAndGetUnitOfWork();
        StubAggregate aggregate = new StubAggregate();
        when(lockFactory.obtainLock(anyString()))
                .thenAnswer(invocation -> {
                    spanFactory.verifySpanActive("Repository.obtainLock");
                    return lock = spy((Lock) invocation.callRealMethod());
                });
        testSubject.newInstance(() -> aggregate).execute(StubAggregate::doSomething);
        spanFactory.verifySpanCompleted("Repository.obtainLock");
        CurrentUnitOfWork.commit();
    }

    @Test
    void lockingIsTracedDuringLoad() throws Exception {
        startAndGetUnitOfWork();
        StubAggregate aggregate = new StubAggregate();
        testSubject.newInstance(() -> aggregate).execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();
        reset(lockFactory);
        spanFactory.reset();

        // Now, for the real work
        when(lockFactory.obtainLock(anyString()))
                .thenAnswer(invocation -> {
                    spanFactory.verifySpanActive("Repository.obtainLock");
                    return lock = spy((Lock) invocation.callRealMethod());
                });
        startAndGetUnitOfWork();
        Aggregate<StubAggregate> loadedAggregate = testSubject.load(aggregate.getIdentifier(), 0L);
        spanFactory.verifySpanCompleted("Repository.obtainLock");
        spanFactory.verifySpanCompleted("Repository.load");
        spanFactory.verifySpanHasAttributeValue("Repository.load", "axon.aggregateId", aggregate.getIdentifier());
        //noinspection resource
        verify(lockFactory).obtainLock(aggregate.getIdentifier());

        loadedAggregate.execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();
    }


    /**
     * The aggregate identifier could not be set because command and event handling decided not to store the event. In
     * that case, there's no way a lock could be acquired for the aggregate identifier, as this isn't there.
     * <p>
     * Note that this ensures the aggregate state is not saved too, as there's no aggregate identifier to save the
     * aggregate with.
     */
    @Test
    void storingAggregateWithoutSettingAggregateIdentifierDoesNotInvokeLockFactory() throws Exception {
        UnitOfWork<?> uow = startAndGetUnitOfWork();
        Aggregate<StubAggregate> result = testSubject.newInstance(
                () -> new StubAggregate(null),
                aggregate -> aggregate.execute(StubAggregate::doSomething)
        );
        uow.commit();

        verifyNoInteractions(lockFactory);
        verify(eventBus).publish(isA(EventMessage.class));
        assertNull(result.identifier());
        assertEquals(0, testSubject.getSaveCount());
    }

    @Test
    void loadOrCreateAggregate() {
        startAndGetUnitOfWork();
        Aggregate<StubAggregate> createdAggregate = testSubject.loadOrCreate("newAggregate", StubAggregate::new);
        //noinspection resource
        verify(lockFactory).obtainLock("newAggregate");

        Aggregate<StubAggregate> loadedAggregate = testSubject.loadOrCreate("newAggregate", StubAggregate::new);
        assertEquals(createdAggregate.identifier(), loadedAggregate.identifier());
        CurrentUnitOfWork.commit();
        verify(lock).release();
    }

    @Test
    void loadAndStoreAggregate() throws Exception {
        startAndGetUnitOfWork();
        StubAggregate aggregate = new StubAggregate();
        testSubject.newInstance(() -> aggregate).execute(StubAggregate::doSomething);
        //noinspection resource
        verify(lockFactory).obtainLock(aggregate.getIdentifier());
        CurrentUnitOfWork.commit();
        verify(lock).release();
        reset(lockFactory);

        startAndGetUnitOfWork();
        Aggregate<StubAggregate> loadedAggregate = testSubject.load(aggregate.getIdentifier(), 0L);
        //noinspection resource
        verify(lockFactory).obtainLock(aggregate.getIdentifier());

        loadedAggregate.execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        verify(eventBus, times(2)).publish(any(EventMessage.class));
        verify(lock).release();
    }

    @Test
    void loadAndStoreAggregate_LockReleasedOnException() throws Exception {
        startAndGetUnitOfWork();
        StubAggregate aggregate = new StubAggregate();

        testSubject.newInstance(() -> aggregate).execute(StubAggregate::doSomething);
        //noinspection resource
        verify(lockFactory).obtainLock(aggregate.getIdentifier());
        CurrentUnitOfWork.commit();
        verify(lock).release();
        reset(lockFactory);

        startAndGetUnitOfWork();
        testSubject.load(aggregate.getIdentifier(), 0L);
        //noinspection resource
        verify(lockFactory).obtainLock(aggregate.getIdentifier());

        CurrentUnitOfWork.get().onPrepareCommit(u -> {
            throw new RuntimeException("Mock Exception");
        });
        try {
            CurrentUnitOfWork.commit();
            fail("Expected exception to be thrown");
        } catch (RuntimeException e) {
            assertEquals("Mock Exception", e.getMessage());
        }

        // make sure the lock is released
        verify(lock).release();
    }

    @Test
    void loadAndStoreAggregate_PessimisticLockReleasedOnException() throws Exception {
        lockFactory = spy(PessimisticLockFactory.usingDefaults());
        testSubject = InMemoryLockingRepository.builder().lockFactory(lockFactory).eventBus(eventBus).build();
        testSubject = spy(testSubject);

        // we do the same test, but with a pessimistic lock, which has a different way of "re-acquiring" a lost lock
        startAndGetUnitOfWork();
        StubAggregate aggregate = new StubAggregate();
        when(lockFactory.obtainLock(aggregate.getIdentifier()))
                .thenAnswer(invocation -> lock = spy((Lock) invocation.callRealMethod()));
        testSubject.newInstance(() -> aggregate).execute(StubAggregate::doSomething);
        //noinspection resource
        verify(lockFactory).obtainLock(aggregate.getIdentifier());
        CurrentUnitOfWork.commit();
        verify(lock).release();
        reset(lockFactory);

        startAndGetUnitOfWork();
        testSubject.load(aggregate.getIdentifier(), 0L);
        //noinspection resource
        verify(lockFactory).obtainLock(aggregate.getIdentifier());

        CurrentUnitOfWork.get().onPrepareCommit(u -> {
            throw new RuntimeException("Mock Exception");
        });

        try {
            CurrentUnitOfWork.commit();
            fail("Expected exception to be thrown");
        } catch (RuntimeException e) {
            assertEquals("Mock Exception", e.getMessage());
        }

        // make sure the lock is released
        verify(lock).release();
    }

    private UnitOfWork<?> startAndGetUnitOfWork() {
        return DefaultUnitOfWork.startAndGet(MESSAGE);
    }

    private static class InMemoryLockingRepository extends LockingRepository<StubAggregate, Aggregate<StubAggregate>> {

        private final EventBus eventBus;
        private final AggregateModel<StubAggregate> aggregateModel;

        private final Map<Object, Aggregate<StubAggregate>> store;
        private int saveCount;

        private InMemoryLockingRepository(Builder builder) {
            super(builder);
            this.eventBus = builder.eventBus;
            this.aggregateModel = builder.buildAggregateModel();

            store = new HashMap<>();
            this.saveCount = 0;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        protected void doSaveWithLock(Aggregate<StubAggregate> aggregate) {
            store.put(aggregate.identifierAsString(), aggregate);
            saveCount++;
        }

        @Override
        protected void doDeleteWithLock(Aggregate<StubAggregate> aggregate) {
            store.remove(aggregate.identifierAsString());
            saveCount++;
        }

        @Override
        protected Aggregate<StubAggregate> doLoadWithLock(String aggregateIdentifier, Long expectedVersion) {
            if (!store.containsKey(aggregateIdentifier)) {
                throw new AggregateNotFoundException(aggregateIdentifier,
                                                     "Aggregate not found");
            }
            return store.get(aggregateIdentifier);
        }

        @Override
        protected Aggregate<StubAggregate> doCreateNewForLock(Callable<StubAggregate> factoryMethod) throws Exception {
            return AnnotatedAggregate.initialize(factoryMethod, aggregateModel, eventBus);
        }

        public int getSaveCount() {
            return saveCount;
        }

        private static class Builder extends LockingRepository.Builder<StubAggregate> {

            private EventBus eventBus;

            private Builder() {
                super(StubAggregate.class);
            }

            @Override
            public Builder lockFactory(LockFactory lockFactory) {
                super.lockFactory(lockFactory);
                return this;
            }

            @Override
            public Builder spanFactory(RepositorySpanFactory spanFactory) {
                super.spanFactory(spanFactory);
                return this;
            }

            public Builder eventBus(EventBus eventBus) {
                this.eventBus = eventBus;
                return this;
            }

            public InMemoryLockingRepository build() {
                return new InMemoryLockingRepository(this);
            }
        }
    }
}
