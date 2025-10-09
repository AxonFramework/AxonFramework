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

import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.queryhandling.QueryBus;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

// TODO 3488 - Introduce monitoring test logic here.
class MonitoringQueryBusTest {

    private final QueryBus mockDelegate = mock(QueryBus.class);
    private final MessageMonitor.MonitorCallback mockMonitorCallback = mock(MessageMonitor.MonitorCallback.class);
    private final MessageMonitor<Message> fakeMessageMonitor = new MessageMonitor<>() {
        @Override
        public MonitorCallback onMessageIngested(Message message) {
            return mockMonitorCallback;
        }

        @Override
        public String toString() {
            return "anonymous QueryMessageMonitor";
        }
    };

    private final MonitoringQueryBus testSubject = new MonitoringQueryBus(mockDelegate, fakeMessageMonitor);

    @Test
    void describeIncludesAllRelevantProperties() {
        var componentDescriptor = new MockComponentDescriptor();
        testSubject.describeTo(componentDescriptor);

        assertThat(componentDescriptor.getProperty("delegate").toString()).startsWith("Mock for QueryBus, hashCode:");
        assertThat(componentDescriptor.getProperty("messageMonitor").toString()).isEqualTo(
                "anonymous QueryMessageMonitor");
    }

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