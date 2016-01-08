/*
 * Copyright (c) 2010-2015. Axon Framework
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

package org.axonframework.eventhandling.amqp.spring;

import com.rabbitmq.client.Channel;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.GenericEventMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;

/**
 * @author Allard Buijze
 */
@ContextConfiguration(locations = "/META-INF/spring/messaging-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class SpringAMQPEventBusIntegrationTest {

    private static final int EVENT_COUNT = 100;
    private static final int THREAD_COUNT = 10;
    @Autowired
    private EventBus eventBus;
    @Autowired
    private ConnectionFactory connectionFactory;
    @Autowired
    private SpringAMQPEventBus terminal;

    @Autowired
    @Qualifier("eventProcessor1")
    private EventProcessor eventProcessor;

    @Before
    public void setUp() throws Exception {
        try {
            Channel channel = connectionFactory.createConnection().createChannel(false);
            if (channel.isOpen()) {
                channel.close();
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
        eventBus.subscribe(eventProcessor);

    }

    @DirtiesContext
    @Test(timeout = 10000)
    public void testConnectAndDispatch_DefaultQueueAndExchange() throws Exception {
        final EventMessage<String> sentEvent = GenericEventMessage.asEventMessage("Hello world");
        final CountDownLatch cdl = new CountDownLatch(EVENT_COUNT * THREAD_COUNT);

        eventProcessor.subscribe(event -> {
            assertEquals(sentEvent.getPayload(), event.getPayload());
            assertEquals(sentEvent.getIdentifier(), event.getIdentifier());
            cdl.countDown();
        });

        List<Thread> threads = new ArrayList<>();
        final AtomicBoolean failed = new AtomicBoolean(false);
        for (int t = 0; t < THREAD_COUNT; t++) {
            threads.add(new Thread(() -> {
                for (int i = 0; i < EVENT_COUNT; i++) {
                    boolean sent = false;
                    while (!sent && !failed.get()) {
                        try {
                            eventBus.publish(sentEvent);
                            sent = true;
                        } catch (Exception e) {
                            e.printStackTrace();
                            failed.set(true);
                        }
                    }
                }
            }));
        }

        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }

        while (!cdl.await(1, TimeUnit.SECONDS)) {
            System.out.println("Waiting for more messages: " + cdl.getCount());
        }
        assertFalse("At least one failure was detected while publishing messages", failed.get());
        assertEquals("Did not receive message in time", 0, cdl.getCount());
    }

    @DirtiesContext
    @Test(timeout = 10000)
    public void testSendMessageWithPublisherAck() throws Exception {
        terminal.setTransactional(false);
        terminal.setWaitForPublisherAck(true);
        terminal.setDurable(false);
        testConnectAndDispatch_DefaultQueueAndExchange();
    }

    @DirtiesContext
    @Test(timeout = 10000)
    public void testSendMessageWithoutAnyTransactions() throws Exception {
        terminal.setTransactional(false);
        terminal.setWaitForPublisherAck(false);
        terminal.setDurable(false);
        testConnectAndDispatch_DefaultQueueAndExchange();
    }
}
