/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.eventhandling.async;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.axonframework.utils.EventTestUtils.createEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
class AsynchronousEventProcessingStrategyTest {

    private Executor executor;
    private AsynchronousEventProcessingStrategy testSubject;

    @BeforeEach
    void setUp() {
        executor = mock(Executor.class);
        doAnswer(invocation -> {
            // since we need to pretend we run in another thread, we clear the Unit of Work first
            UnitOfWork<?> currentUnitOfWork = null;
            if (CurrentUnitOfWork.isStarted()) {
                currentUnitOfWork = CurrentUnitOfWork.get();
                CurrentUnitOfWork.clear(currentUnitOfWork);
            }

            ((Runnable) invocation.getArguments()[0]).run();

            if (currentUnitOfWork != null) {
                CurrentUnitOfWork.set(currentUnitOfWork);
            }
            return null;
        }).when(executor).execute(isA(Runnable.class));
        testSubject = new AsynchronousEventProcessingStrategy(executor, new SequentialPerAggregatePolicy());
    }

    @Test
    void orderingOfEvents() throws Exception {
        testSubject =
                new AsynchronousEventProcessingStrategy(Executors.newSingleThreadExecutor(), new SequentialPolicy());

        final List<EventMessage> ackedMessages = Collections.synchronizedList(new ArrayList<>());

        EventMessage<?> event1 = createEvent(1);
        EventMessage<?> event2 = createEvent(2);

        final Consumer<List<? extends EventMessage<?>>> processor = mock(Consumer.class);
        CountDownLatch latch = new CountDownLatch(2);
        doAnswer(invocation -> {
            List<? extends EventMessage<?>> events = (List) invocation.getArguments()[0];
            events.forEach(e -> {
                ackedMessages.add(e);
                latch.countDown();
            });
            return null;
        }).when(processor).accept(anyList());

        new DefaultUnitOfWork<>(null).execute(() -> {
            testSubject.handle(Collections.singletonList(event1), processor);
            testSubject.handle(Collections.singletonList(event2), processor);
        });

        latch.await();

        InOrder inOrder = Mockito.inOrder(processor, processor);
        inOrder.verify(processor).accept(Arrays.asList(event1, event2));

        assertEquals(2, ackedMessages.size());
        assertEquals(event1, ackedMessages.get(0));
        assertEquals(event2, ackedMessages.get(1));
    }

    @Test
    void eventsScheduledForHandling() {
        EventMessage<?> message1 = createEvent("aggregate1", 1);
        EventMessage<?> message2 = createEvent("aggregate2", 1);

        testSubject.handle(Arrays.asList(message1, message2), mock(Consumer.class));

        verify(executor, times(2)).execute(isA(Runnable.class));
    }

    @Test
    void eventsScheduledForHandlingWhenSurroundingUnitOfWorkCommits() {
        EventMessage<?> message1 = createEvent("aggregate1", 1);
        EventMessage<?> message2 = createEvent("aggregate2", 1);

        UnitOfWork<EventMessage<?>> uow = DefaultUnitOfWork.startAndGet(message1);
        uow.onPrepareCommit(u -> verify(executor, never()).execute(isA(Runnable.class)));

        testSubject.handle(Arrays.asList(message1, message2), mock(Consumer.class));

        verify(executor, never()).execute(isA(Runnable.class));

        uow.commit();

        verify(executor, times(2)).execute(isA(Runnable.class));
    }
}
