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

package org.axonframework.integrationtests;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.integrationtests.commandhandling.CreateStubAggregateCommand;
import org.axonframework.integrationtests.commandhandling.LoopingCommand;
import org.axonframework.integrationtests.commandhandling.ProblematicCommand;
import org.axonframework.integrationtests.commandhandling.UpdateStubAggregateCommand;
import org.axonframework.integrationtests.eventhandling.RegisteringEventHandler;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "/META-INF/spring/infrastructure-context.xml",
        "/META-INF/spring/application-context-pessimistic.xml"})
public class ConcurrentModificationTest_PessimisticLocking {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RegisteringEventHandler registeringEventHandler;

    private static final int THREAD_COUNT = 50;

    @Before
    public void clearUnitsOfWork() {
        // somewhere, a process is not properly clearing the UnitOfWork
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    /*
     * This test shows the presence, or better, the absence of a problem caused by repository locks being released
     * before a database transaction is committed. This would cause an aggregate waiting for a lock to be release, to
     * load in events, while another thread is committing a transaction (and thus adding them).
     */
    @Test(timeout = 30000)
    public void testConcurrentModifications() throws Exception {
        assertFalse("Something is wrong", CurrentUnitOfWork.isStarted());
        final UUID aggregateId = UUID.randomUUID();
        commandBus.dispatch(asCommandMessage(new CreateStubAggregateCommand(aggregateId)));
        ExecutorService service = Executors.newFixedThreadPool(THREAD_COUNT);
        final AtomicLong counter = new AtomicLong(0);
        List<Future<?>> results = new LinkedList<Future<?>>();
        for (int t = 0; t < 30; t++) {
            results.add(service.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        commandBus.dispatch(asCommandMessage(new UpdateStubAggregateCommand(aggregateId)));
                        commandBus.dispatch(asCommandMessage(new ProblematicCommand(aggregateId)),
                                            SilentCallback.INSTANCE);
                        commandBus.dispatch(asCommandMessage(new LoopingCommand(aggregateId)));
                        counter.incrementAndGet();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }));
        }
        service.shutdown();
        while (!service.awaitTermination(3, TimeUnit.SECONDS)) {
            System.out.println("Did " + counter.get() + " batches");
        }

        for (Future<?> result : results) {
            if (result.isDone()) {
                result.get();
            }
        }
        assertEquals(91, registeringEventHandler.getCapturedEvents().size());
        validateDispatchingOrder();
    }

    private void validateDispatchingOrder() {
        long expectedSequenceNumber = 0L;
        for (EventMessage event : registeringEventHandler.getCapturedEvents()) {
            assertTrue(event instanceof DomainEventMessage);
            assertEquals("Events are dispatched in the wrong order!",
                         expectedSequenceNumber,
                         ((DomainEventMessage) event).getSequenceNumber());
            expectedSequenceNumber++;
        }
    }

    private static class SilentCallback implements CommandCallback<Object> {

        public static final CommandCallback<Object> INSTANCE = new SilentCallback();

        @Override
        public void onSuccess(Object result) {
        }

        @Override
        public void onFailure(Throwable cause) {
        }
    }
}
