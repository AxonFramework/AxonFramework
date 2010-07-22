/*
 * Copyright (c) 2010. Axon Framework
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

package org.axonframework.commandhandling.interceptors;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.domain.Event;
import org.axonframework.domain.StubAggregate;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.repository.InMemoryLockingRepository;
import org.axonframework.repository.LockingRepository;
import org.axonframework.repository.LockingStrategy;
import org.junit.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@SuppressWarnings({"ThrowableInstanceNeverThrown"})
public class SimpleUnitOfWorkInterceptorTest {

    private SimpleCommandBus commandBus;
    private InMemoryLockingRepository repository;
    private UUID aggregateIdentifier1;
    private UUID aggregateIdentifier2;
    private EventBus eventBus;

    @Before
    public void setUp() {
        commandBus = new SimpleCommandBus();
        commandBus.setInterceptors(asList(new SimpleUnitOfWorkInterceptor()));
        repository = new InMemoryLockingRepository(LockingStrategy.PESSIMISTIC);
        eventBus = mock(EventBus.class);
        repository.setEventBus(eventBus);
        aggregateIdentifier1 = createAggregate();
        aggregateIdentifier2 = createAggregate();
        commandBus.subscribe(SimpleCommand.class, new LoadAndSaveCommandHandler());
        repository.resetSaveCount();
    }

    private UUID createAggregate() {
        StubAggregate aggregate = new StubAggregate();
        repository.save(aggregate);
        return aggregate.getIdentifier();
    }

    @Test
    public void testLoadAndSaveSingleAggregate() throws Exception {
        commandBus.dispatch(new SimpleCommand(true, aggregateIdentifier1));
        assertEquals(1, repository.getSaveCount());
        verify(eventBus).publish(isA(StubDomainEvent.class));
    }

    @Test
    public void testLoadAndSaveSingleAggregate_RevertTransaction() throws Exception {
        RuntimeException failure = new RuntimeException();
        dispatchCommand(new SimpleCommand(true, failure, aggregateIdentifier1), failure);

        assertEquals(1, repository.getSaveCount());
        // an explicit save will always result in the events being thrown
        verify(eventBus).publish(isA(StubDomainEvent.class));
        verifyNoLocksRemain();
    }

    @Test
    public void testLoadAndSaveTwoAggregates() throws Exception {
        commandBus.dispatch(new SimpleCommand(true, aggregateIdentifier1, aggregateIdentifier2));
        assertEquals(2, repository.getSaveCount());
        verify(eventBus, times(2)).publish(isA(StubDomainEvent.class));
    }

    @Test
    public void testLoadAndSaveTwoAggregates_RevertTransaction() throws Exception {
        RuntimeException failure = new RuntimeException();
        dispatchCommand(new SimpleCommand(true, failure, aggregateIdentifier1, aggregateIdentifier2), failure);

        // an explicit save will always result in the events being thrown
        assertEquals(2, repository.getSaveCount());
        verify(eventBus, times(2)).publish(isA(StubDomainEvent.class));
        verifyNoLocksRemain();
    }

    @Test
    public void testLoadSingleAggregate() throws Exception {
        commandBus.dispatch(new SimpleCommand(false, aggregateIdentifier1));
        assertEquals(1, repository.getSaveCount());
        verify(eventBus).publish(isA(StubDomainEvent.class));
        verifyNoLocksRemain();
    }

    @Test
    public void testLoadSingleAggregate_RevertTransaction() throws Exception {
        RuntimeException failure = new RuntimeException();
        dispatchCommand(new SimpleCommand(false, failure, aggregateIdentifier1), failure);

        assertEquals(0, repository.getSaveCount());
        verify(eventBus, never()).publish(isA(StubDomainEvent.class));
        verifyNoLocksRemain();
    }

    @Test
    public void testLoadTwoAggregates() throws Exception {
        commandBus.dispatch(new SimpleCommand(false, aggregateIdentifier1, aggregateIdentifier2));
        assertEquals(2, repository.getSaveCount());
        verify(eventBus, times(2)).publish(isA(StubDomainEvent.class));
        verifyNoLocksRemain();
    }

    @Test
    public void testLoadTwoAggregates_RevertTransaction() throws Exception {
        RuntimeException failure = new RuntimeException();
        dispatchCommand(new SimpleCommand(false, failure, aggregateIdentifier1, aggregateIdentifier2), failure);

        assertEquals(0, repository.getSaveCount());
        verify(eventBus, never()).publish(isA(StubDomainEvent.class));
        verifyNoLocksRemain();
    }

    private void dispatchCommand(SimpleCommand command, RuntimeException failure) {
        try {
            commandBus.dispatch(command);
            fail("Expected exception to be propagated");
        } catch (Exception e) {
            assertSame(failure, e);
        }
    }

    private void verifyNoLocksRemain() throws Exception {
        Field lockManagerField = LockingRepository.class.getDeclaredField("lockManager");
        lockManagerField.setAccessible(true);
        Object lockManager = lockManagerField.get(repository);
        Field locksField = lockManager.getClass().getDeclaredField("locks");
        locksField.setAccessible(true);
        Map locks = (Map) locksField.get(lockManager);
        assertEquals("There were locks remaining.", 0, locks.size());
    }

    private class LoadAndSaveCommandHandler implements CommandHandler<SimpleCommand> {

        @Override
        public Object handle(SimpleCommand command) {
            for (UUID aggregateIdentifier : command.getAggregatesToActOn()) {
                StubAggregate aggregate = repository.load(aggregateIdentifier, null);
                aggregate.doSomething();
                if (command.isIncludeSave()) {
                    repository.save(aggregate);
                }
            }
            if (command.isIncludeSave()) {
                verify(eventBus, times(command.getAggregatesToActOn().size())).publish(isA(Event.class));
            }
            command.causeFailure();
            return Void.TYPE;
        }
    }

    private static class SimpleCommand {

        private List<UUID> aggregatesToActOn;
        private boolean includeSave;
        private RuntimeException failure;

        private SimpleCommand(boolean includeSave, UUID... aggregatesToActOn) {
            this(includeSave, null, aggregatesToActOn);
        }

        private SimpleCommand(boolean includeSave, RuntimeException failure, UUID... aggregatesToActOn) {
            this.includeSave = includeSave;
            this.failure = failure;
            this.aggregatesToActOn = asList(aggregatesToActOn);
        }

        public boolean isIncludeSave() {
            return includeSave;
        }

        public List<UUID> getAggregatesToActOn() {
            return aggregatesToActOn;
        }

        public void causeFailure() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
