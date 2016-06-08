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

package org.axonframework.integrationtests.eventstore.benchmark;

import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.integrationtests.commandhandling.StubDomainEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for benchmarks of eventstore implementations.
 *
 * @author Jettro Coenradie
 */
public abstract class AbstractEventStoreBenchmark {

    private static final int THREAD_COUNT = 100;
    private static final int TRANSACTION_COUNT = 50;
    private static final int TRANSACTION_SIZE = 50;

    protected static AbstractEventStoreBenchmark prepareBenchMark(String... appContexts) {
        Assert.notEmpty(appContexts);
        ApplicationContext context = new ClassPathXmlApplicationContext(appContexts);
        return context.getBean(AbstractEventStoreBenchmark.class);
    }

    protected abstract void prepareEventStore();

    public void startBenchMark() throws InterruptedException {
        prepareEventStore();

        long start = System.currentTimeMillis();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < getThreadCount(); t++) {
            Thread thread = new Thread(getRunnableInstance());
            thread.start();
            threads.add(thread);
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long end = System.currentTimeMillis();

        System.out.println(String.format(
                "Result (%s): %s threads concurrently wrote %s * %s events each in %s milliseconds. That is an average of %.0f events per second",
                getClass().getSimpleName(), getThreadCount(), getTransactionCount(), getTransactionSize(),
                (end - start), (((float) getThreadCount() * getTransactionCount() * getTransactionSize()) /
                        ((float) (end - start) / 1000))));
    }

    protected abstract Runnable getRunnableInstance();

    protected int saveAndLoadLargeNumberOfEvents(String aggregateId, EventStore eventStore, int eventSequence) {
        List<DomainEventMessage<?>> events = new ArrayList<>(getTransactionSize());
        for (int t = 0; t < getTransactionSize(); t++) {
            events.add(
                    new GenericDomainEventMessage<>("test", aggregateId, eventSequence++, new StubDomainEvent(), null));
        }
        eventStore.publish(events);
        return eventSequence;
    }

    protected int getThreadCount() {
        return THREAD_COUNT;
    }

    protected int getTransactionCount() {
        return TRANSACTION_COUNT;
    }

    protected int getTransactionSize() {
        return TRANSACTION_SIZE;
    }
}
