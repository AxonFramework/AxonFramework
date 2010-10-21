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

package org.axonframework.integrationtests;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.callbacks.NoOpCallback;
import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.UUIDAggregateIdentifier;
import org.axonframework.integrationtests.commandhandling.CreateStubAggregateCommand;
import org.axonframework.integrationtests.commandhandling.UpdateStubAggregateCommand;
import org.axonframework.integrationtests.eventhandling.RegisteringEventHandler;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "/META-INF/spring/infrastructure-context.xml",
        "/META-INF/spring/application-context-pessimistic.xml"})
@Transactional
public class ConcurrentModificationTest_PessimisticLocking implements Thread.UncaughtExceptionHandler {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RegisteringEventHandler registeringEventHandler;

    private List<Throwable> uncaughtExceptions = new ArrayList<Throwable>();
    private static final int THREAD_COUNT = 50;

    @Before
    public void clearUnitsOfWork() {
        // somewhere, a process is not properly clearing the UnitOfWork
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    /**
     * This test shows the presence, or better, the absence of a problem caused by repository locks being released
     * before a database transaction is committed. This would cause an aggregate waiting for a lock to be release, to
     * load in events, while another thread is committing a transaction (and thus adding them).
     *
     * @throws InterruptedException
     */
    @Test
    public void testConcurrentModifications() throws Exception {
        assertFalse("Something is wrong", CurrentUnitOfWork.isStarted());
        final AggregateIdentifier aggregateId = new UUIDAggregateIdentifier();
        commandBus.dispatch(new CreateStubAggregateCommand(aggregateId), NoOpCallback.INSTANCE);
        final CountDownLatch cdl = new CountDownLatch(THREAD_COUNT);
        for (int t = 0; t < THREAD_COUNT; t++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        commandBus.dispatch(new UpdateStubAggregateCommand(aggregateId), NoOpCallback.INSTANCE);
                        commandBus.dispatch(new UpdateStubAggregateCommand(aggregateId), NoOpCallback.INSTANCE);
                        cdl.countDown();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
            });
            thread.setUncaughtExceptionHandler(ConcurrentModificationTest_PessimisticLocking.this);
            thread.start();
        }
        cdl.await(THREAD_COUNT / 2, TimeUnit.SECONDS);
        assertEquals(0, uncaughtExceptions.size());
        assertEquals(THREAD_COUNT * 2 + 1, registeringEventHandler.getCapturedEvents().size());
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        uncaughtExceptions.add(e);
    }
}
