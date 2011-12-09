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

package org.axonframework.integrationtests.loopbacktest;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.domain.EventMessage;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventListener;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.atomic.AtomicInteger;

import static org.axonframework.commandhandling.annotation.GenericCommandMessage.asCommandMessage;
import static org.junit.Assert.*;

/**
 * Integration test to find out that the command bus is able to cope with event listeners dispatching commands of their
 * own.
 *
 * @author Allard Buijze
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "/META-INF/spring/async-command-loopback-context.xml")
public class AsynchronousLoopbackTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private EventBus eventBus;

    @Test
    public void testAsynchronousLoopBack() throws InterruptedException {
        WaitingEventListener listener = new WaitingEventListener(2);
        eventBus.subscribe(listener);
        commandBus.dispatch(asCommandMessage(AsynchronousLoopbackEventHandler.MAGIC_WORDS));
        listener.waitForEvents(1000);
        assertEquals(0, listener.eventsRemaining.get());
    }

    private class WaitingEventListener implements EventListener {

        private final AtomicInteger eventsRemaining;
        private final Object lock = new Object();

        private WaitingEventListener(int eventCountToWaitFor) {
            this.eventsRemaining = new AtomicInteger(eventCountToWaitFor);
        }

        @Override
        public void handle(EventMessage event) {
            synchronized (lock) {
                int remaining = eventsRemaining.decrementAndGet();
                if (remaining <= 0) {
                    lock.notifyAll();
                }
            }
        }

        public void waitForEvents(long timeout) throws InterruptedException {
            synchronized (lock) {
                if (eventsRemaining.get() > 0) {
                    lock.wait(timeout);
                    if (eventsRemaining.get() > 0) {
                        fail("Did not get the events in time");
                    }
                }
            }
        }
    }
}
