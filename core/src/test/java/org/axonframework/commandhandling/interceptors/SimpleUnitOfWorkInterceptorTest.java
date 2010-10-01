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

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandContext;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.InterceptorChain;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.StubAggregate;
import org.axonframework.domain.StubDomainEvent;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.repository.InMemoryLockingRepository;
import org.axonframework.repository.LockingRepository;
import org.axonframework.repository.LockingStrategy;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.junit.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

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
    private AggregateIdentifier aggregateIdentifier1;
    private AggregateIdentifier aggregateIdentifier2;
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

    @After
    public void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    private AggregateIdentifier createAggregate() {
        StubAggregate aggregate = new StubAggregate();
        repository.add(aggregate);
        CurrentUnitOfWork.commit();
        return aggregate.getIdentifier();
    }

    @Test
    public void testLoadSingleAggregate() throws Exception {
        commandBus.dispatch(new SimpleCommand(aggregateIdentifier1));
        assertEquals(1, repository.getSaveCount());
        verify(eventBus).publish(isA(StubDomainEvent.class));
        verifyNoLocksRemain();
    }

    @Test
    public void testLoadSingleAggregate_RevertTransaction() throws Exception {
        RuntimeException failure = new RuntimeException();
        dispatchCommand(new SimpleCommand(failure, aggregateIdentifier1), failure);

        assertEquals(0, repository.getSaveCount());
        verify(eventBus, never()).publish(isA(StubDomainEvent.class));
        verifyNoLocksRemain();
    }

    @Test
    public void testLoadTwoAggregates() throws Exception {
        commandBus.dispatch(new SimpleCommand(aggregateIdentifier1, aggregateIdentifier2));
        assertEquals(2, repository.getSaveCount());
        verify(eventBus, times(2)).publish(isA(StubDomainEvent.class));
        verifyNoLocksRemain();
    }

    @Test
    public void testLoadTwoAggregates_RevertTransaction() throws Exception {
        RuntimeException failure = new RuntimeException();
        dispatchCommand(new SimpleCommand(failure, aggregateIdentifier1, aggregateIdentifier2), failure);

        assertEquals(0, repository.getSaveCount());
        verify(eventBus, never()).publish(isA(StubDomainEvent.class));
        verifyNoLocksRemain();
    }

    @Test
    public void testNesting_NestingAllowed() throws Throwable {
        SimpleUnitOfWorkInterceptor interceptor = new SimpleUnitOfWorkInterceptor();
        final DefaultUnitOfWork unitOfWork = new DefaultUnitOfWork();
        unitOfWork.start();
        InterceptorChain chain = mock(InterceptorChain.class);
        when(chain.proceed(isA(CommandContext.class))).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                assertNotSame(unitOfWork, CurrentUnitOfWork.get());
                return null;
            }
        });
        interceptor.handle(mock(CommandContext.class), chain);
        unitOfWork.commit();
    }

    private void dispatchCommand(SimpleCommand command, final RuntimeException failure) {
        commandBus.dispatch(command, new CommandCallback<SimpleCommand, Object>() {
            @Override
            public void onSuccess(Object result,
                                  CommandContext<SimpleCommand> simpleCommandCommandContext) {
                fail("Expected exception to be propagated");
            }

            @Override
            public void onFailure(Throwable actual,
                                  CommandContext<SimpleCommand> simpleCommandCommandContext) {
                assertSame(failure, actual);
            }
        });
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
        public Object handle(SimpleCommand command, CommandContext<SimpleCommand> context) {
            for (AggregateIdentifier aggregateIdentifier : command.getAggregatesToActOn()) {
                StubAggregate aggregate = repository.load(aggregateIdentifier, null);
                aggregate.doSomething();
            }
            command.causeFailure();
            return Void.TYPE;
        }
    }

    private static class SimpleCommand {

        private List<AggregateIdentifier> aggregatesToActOn;
        private RuntimeException failure;

        private SimpleCommand(AggregateIdentifier... aggregatesToActOn) {
            this(null, aggregatesToActOn);
        }

        private SimpleCommand(RuntimeException failure, AggregateIdentifier... aggregatesToActOn) {
            this.failure = failure;
            this.aggregatesToActOn = asList(aggregatesToActOn);
        }

        public List<AggregateIdentifier> getAggregatesToActOn() {
            return aggregatesToActOn;
        }

        public void causeFailure() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
