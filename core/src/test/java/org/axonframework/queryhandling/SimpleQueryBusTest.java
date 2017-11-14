/*
 * Copyright (c) 2010-2017. Axon Framework
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
package org.axonframework.queryhandling;

import org.axonframework.messaging.Message;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Author: marc
 */
public class SimpleQueryBusTest {
    private SimpleQueryBus testSubject;
    @Before
    public void setUp() throws Exception {
        testSubject = new SimpleQueryBus();
    }

    @Test
    public void subscribe() throws Exception {
        testSubject.subscribe("test", "test", Message::getPayload);
        assertEquals(1, testSubject.subscriptions.size());
        assertEquals(1, testSubject.subscriptions.values().iterator().next().size());
        testSubject.subscribe("test", "test", (q) -> "aa" + q.getPayload());
        assertEquals(1, testSubject.subscriptions.size());
        assertEquals(2, testSubject.subscriptions.values().iterator().next().size());
        testSubject.subscribe("test2", "test", (q) -> "aa" + q.getPayload());
        assertEquals(2, testSubject.subscriptions.size());
    }

    @Test
    public void query() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class.getName(), (q) -> q.getPayload() + "1234");
        QueryMessage<?> queryMessage = new GenericQueryMessage<>("hello", String.class.getName());
        CompletableFuture<String> result = testSubject.query(queryMessage);
        assertEquals("hello1234", result.get());
    }

    @Test(expected = NoHandlerForQueryException.class)
    public void queryUnknown() throws Exception {
        QueryMessage<String> queryMessage = new GenericQueryMessage<>("Hello, World", "test123");
        testSubject.query(queryMessage);
    }

    @Test
    public void queryAll() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class.getName(), (q) -> q.getPayload() + "1234");
        testSubject.subscribe(String.class.getName(), String.class.getName(), (q) -> q.getPayload() + "567");
        QueryMessage<String> queryMessage = new GenericQueryMessage<>("Hello, World",  String.class.getName());

        Set<Object> allResults = testSubject.queryAll(queryMessage, 0, TimeUnit.SECONDS).collect(Collectors.toSet());
        assertEquals(2, allResults.size());
    }



}