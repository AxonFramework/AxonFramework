/*
 * Copyright (c) 2018. AxonIQ
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

package io.axoniq.axonhub.client.command;

import io.axoniq.axonhub.client.PlatformConnectionManager;
import io.axoniq.axonhub.client.AxonIQPlatformConfiguration;
import org.axonframework.commandhandling.*;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.common.Registration;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Author: marc
 */
public class AxonIQCommandBusTest {
    private AxonIQCommandBus testSubject;
    private DummyMessagePlatformServer dummyMessagePlatformServer;


    @Before
    public void setup() throws Exception {
        AxonIQPlatformConfiguration conf = new AxonIQPlatformConfiguration();
        conf.setRoutingServers("localhost:4343");
        conf.setClientName("JUnit");
        conf.setComponentName("JUnit");
        conf.setInitialNrOfPermits(100);
        conf.setNewPermitsThreshold(10);
        conf.setNrOfNewPermits(1000);
        CommandBus localSegment = new SimpleCommandBus();
        Serializer ser = new XStreamSerializer();
        testSubject = new AxonIQCommandBus(new PlatformConnectionManager(conf), conf, localSegment, ser,
                new RoutingStrategy() {
                    @Override
                    public String getRoutingKey(CommandMessage<?> command) {
                        return "RoutingKey";
                    }
                }, new CommandPriorityCalculator() {});
        dummyMessagePlatformServer = new DummyMessagePlatformServer(4343);
        dummyMessagePlatformServer.start();
    }

    @After
    public void tearDown() throws Exception {
        dummyMessagePlatformServer.stop();
    }

    @Test
    public void dispatch() throws Exception {
        CommandMessage<String> commandMessage = new GenericCommandMessage<>("this is the payload");
        CountDownLatch waiter = new CountDownLatch(1);
        AtomicReference<String> resultHolder = new AtomicReference<>();
        AtomicBoolean failure = new AtomicBoolean(false);
        testSubject.dispatch(commandMessage, new CommandCallback<String, String>() {
            @Override
            public void onSuccess(CommandMessage<? extends String> commandMessage, String result) {
                resultHolder.set(result);
                waiter.countDown();
            }

            @Override
            public void onFailure(CommandMessage<? extends String> commandMessage, Throwable cause) {
                failure.set(true);
                waiter.countDown();
            }
        });
        waiter.await();
        assertEquals(resultHolder.get(), "this is the payload");
        assertFalse(failure.get());
    }
    @Test
    public void dispatchWithError() throws Exception {
        CommandMessage<String> commandMessage = new GenericCommandMessage<>("this is an error request");
        CountDownLatch waiter = new CountDownLatch(1);
        AtomicReference<String> resultHolder = new AtomicReference<>();
        AtomicBoolean failure = new AtomicBoolean(false);
        testSubject.dispatch(commandMessage, new CommandCallback<String, String>() {
            @Override
            public void onSuccess(CommandMessage<? extends String> commandMessage, String result) {
                resultHolder.set(result);
                waiter.countDown();
            }

            @Override
            public void onFailure(CommandMessage<? extends String> commandMessage, Throwable cause) {
                failure.set(true);
                waiter.countDown();
            }
        });
        waiter.await();
        assertTrue(failure.get());
    }

    @Test
    public void subscribe() throws Exception {
        Registration registration = testSubject.subscribe(String.class.getName(), c -> "Done");
        Thread.sleep(30);
        assertEquals(1, dummyMessagePlatformServer.subscriptions(String.class.getName()).size());
        registration.cancel();
        Thread.sleep(30);
        assertEquals(0, dummyMessagePlatformServer.subscriptions(String.class.getName()).size());
    }
    @Test
    public void resubscribe() throws Exception {
        testSubject.subscribe(String.class.getName(), c -> "Done");
        Thread.sleep(30);
        assertEquals(1, dummyMessagePlatformServer.subscriptions(String.class.getName()).size());
        dummyMessagePlatformServer.stop();
        assertNull(dummyMessagePlatformServer.subscriptions(String.class.getName()));
        dummyMessagePlatformServer.start();
        Thread.sleep(3000);
        assertEquals(1, dummyMessagePlatformServer.subscriptions(String.class.getName()).size());
    }

}