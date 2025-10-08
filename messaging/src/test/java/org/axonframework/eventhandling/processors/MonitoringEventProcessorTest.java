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

package org.axonframework.eventhandling.processors;

import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.MessageMonitor.MonitorCallback;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MonitoringEventProcessorTest {

    private final EventProcessor mockDelegate = mock(EventProcessor.class);

    private final MessageMonitor<EventMessage> testMonitor = new MessageMonitor<>() {
        @Override
        public MonitorCallback onMessageIngested(EventMessage message) {
            return mock(MonitorCallback.class);
        }

        @Override
        public String toString() {
            return "anonymous EventMessageMonitor";
        }
    };

    private final MonitoringEventProcessor testSubject = new MonitoringEventProcessor(mockDelegate, testMonitor);

    @Test
    void describeIncludesAllRelevantProperties() {
        var componentDescriptor = new MockComponentDescriptor();
        testSubject.describeTo(componentDescriptor);

        assertThat(componentDescriptor.getProperty("delegate").toString()).startsWith("Mock for EventProcessor, hashCode:");
        assertThat(componentDescriptor.getProperty("messageMonitor").toString()).isEqualTo(
                "anonymous EventMessageMonitor");
    }
}
