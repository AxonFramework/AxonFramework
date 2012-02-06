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
import org.axonframework.commandhandling.callbacks.VoidCallback;
import org.axonframework.domain.DomainEventMessage;
import org.axonframework.domain.EventMessage;
import org.axonframework.integrationtests.commandhandling.CreateStubAggregateCommand;
import org.axonframework.integrationtests.commandhandling.ProblematicCommand;
import org.axonframework.integrationtests.commandhandling.UpdateStubAggregateCommand;
import org.axonframework.integrationtests.eventhandling.RegisteringEventHandler;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.junit.*;
import org.junit.runner.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Log4jConfigurer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "/META-INF/spring/infrastructure-context.xml",
        "/META-INF/spring/application-context-optimistic.xml"})
@Transactional
public class ConcurrentModificationTest_OptimisticLocking implements Thread.UncaughtExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrentModificationTest_OptimisticLocking.class);

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RegisteringEventHandler registeringEventHandler;

    private List<Throwable> uncaughtExceptions = new ArrayList<Throwable>();
    private static final int THREAD_COUNT = 50;
    private static final int COMMAND_PER_THREAD_COUNT = 20;

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
        Log4jConfigurer.initLogging("classpath:log4j_silenced.properties");
        assertFalse("Something is wrong", CurrentUnitOfWork.isStarted());
        final UUID aggregateId = UUID.randomUUID();
        commandBus.dispatch(asCommandMessage(new CreateStubAggregateCommand(aggregateId)));
        final CountDownLatch cdl = new CountDownLatch(THREAD_COUNT);
        final CountDownLatch starter = new CountDownLatch(1);

        final AtomicInteger successCounter = new AtomicInteger();
        final AtomicInteger failCounter = new AtomicInteger();
        for (int t = 0; t < THREAD_COUNT; t++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        starter.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    for (int t = 0; t < COMMAND_PER_THREAD_COUNT; t++) {
                        commandBus.dispatch(asCommandMessage(new ProblematicCommand(aggregateId)),
                                            SilentCallback.INSTANCE);
                        commandBus.dispatch(asCommandMessage(new UpdateStubAggregateCommand(aggregateId)),
                                            new VoidCallback() {
                                                @Override
                                                protected void onSuccess() {
                                                    successCounter.incrementAndGet();
                                                }

                                                @Override
                                                public void onFailure(Throwable cause) {
                                                    failCounter.incrementAndGet();
                                                }
                                            });
                    }
                    cdl.countDown();
                }
            });
            thread.setUncaughtExceptionHandler(ConcurrentModificationTest_OptimisticLocking.this);
            thread.start();
        }
        starter.countDown();
        cdl.await((THREAD_COUNT * COMMAND_PER_THREAD_COUNT) / 4, TimeUnit.SECONDS);
        if (uncaughtExceptions.size() > 0) {
            System.out.println("*** Uncaught Exceptions ***");
            for (Throwable uncaught : uncaughtExceptions) {
                uncaught.printStackTrace();
            }
        }
        assertEquals("Got exceptions", 0, uncaughtExceptions.size());
        assertEquals(successCounter.get() + 1, registeringEventHandler.getCapturedEvents().size());
        assertEquals(THREAD_COUNT * COMMAND_PER_THREAD_COUNT, successCounter.get(), failCounter.get());

        reportOutOfSyncEvents();

        logger.info("Results: {} successful, {} failed.", successCounter.get(), failCounter.get());

        // to prove that all locks are properly cleared, this command must succeed.
        commandBus.dispatch(asCommandMessage(new UpdateStubAggregateCommand(aggregateId)), new VoidCallback() {
            @Override
            protected void onSuccess() {

            }

            @Override
            public void onFailure(Throwable cause) {
                cause.printStackTrace();
                fail("Should be succesful. Is there a lock hanging?");
            }
        });
    }

    private void reportOutOfSyncEvents() {
        if (!logger.isInfoEnabled()) {
            return;
        }
        Long expectedSequenceNumber = 0L;
        Map<Long, Long> outOfSyncs = new HashMap<Long, Long>();
        for (EventMessage event : registeringEventHandler.getCapturedEvents()) {
            assertTrue(event instanceof DomainEventMessage);
            Long actual = ((DomainEventMessage) event).getSequenceNumber();
            if (!expectedSequenceNumber.equals(actual)) {
                outOfSyncs.put(expectedSequenceNumber, actual);
            }
            expectedSequenceNumber++;
        }
        for (Map.Entry<Long, Long> entry : outOfSyncs.entrySet()) {
            logger.info(String.format("Got %s, where expected %s", entry.getValue(), entry.getKey()));
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        uncaughtExceptions.add(e);
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
