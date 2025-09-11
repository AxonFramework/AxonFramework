/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.axonframework.queryhandling.monitoring;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageType;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.NoHandlerForQueryException;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import org.junit.jupiter.api.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// TODO 3488 - Introduce monitoring test logic here.
class MonitoringQueryBusTest {

    // private MessageMonitor.MonitorCallback monitorCallback;

//    @Test
//    void scatterGather() {
//        int expectedResults = 3;
//
//        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> q.payload() + "1234");
//        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> q.payload() + "5678");
//        testSubject.subscribe(String.class.getName(), String.class, (q, ctx) -> q.payload() + "90");
//
//        QueryMessage testQuery = new GenericQueryMessage(
//                new MessageType(String.class), "Hello, World", singleStringResponse
//        );
//        Set<QueryResponseMessage> results = testSubject.scatterGather(testQuery, 0, TimeUnit.SECONDS)
//                                                       .collect(toSet());
//
//        assertEquals(expectedResults, results.size());
//        Set<Object> resultSet = results.stream().map(Message::payload).collect(toSet());
//        assertEquals(expectedResults, resultSet.size());
//        verify(messageMonitor, times(1)).onMessageIngested(any());
//        verify(monitorCallback, times(3)).reportSuccess();
//    }

}