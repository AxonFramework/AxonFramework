/*
 * Copyright (c) 2010-2011. Axon Framework
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
import org.axonframework.commandhandling.callbacks.NoOpCallback;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.DomainEvent;
import org.axonframework.domain.Event;
import org.axonframework.domain.UUIDAggregateIdentifier;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "/META-INF/spring/infrastructure-context.xml",
        "/META-INF/spring/application-context-pessimistic.xml"})
@Transactional
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
        final AggregateIdentifier aggregateId = new UUIDAggregateIdentifier();
        commandBus.dispatch(new CreateStubAggregateCommand(aggregateId));
        ExecutorService service = Executors.newFixedThreadPool(THREAD_COUNT);
        final AtomicLong counter = new AtomicLong(0);
        List<Future<?>> results = new LinkedList<Future<?>>();
        for (int t = 0; t < 30; t++) {
            results.add(service.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        commandBus.dispatch(new UpdateStubAggregateCommand(aggregateId));
                        commandBus.dispatch(new ProblematicCommand(aggregateId), SilentCallback.INSTANCE);
                        commandBus.dispatch(new LoopingCommand(aggregateId));
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
        Long expectedSequenceNumber = 0L;
        for (Event event : registeringEventHandler.getCapturedEvents()) {
            assertTrue(event instanceof DomainEvent);
            assertEquals("Events are dispatched in the wrong order!",
                         expectedSequenceNumber,
                         ((DomainEvent) event).getSequenceNumber());
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
    }}
