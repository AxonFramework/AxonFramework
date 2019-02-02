/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.micrometer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.*;

import java.util.Arrays;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.Assert.*;

public class MessageCountingMonitorTest {

    private static final String PROCESSOR_NAME = "processorName";

    @Test
    public void testMessages() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MessageCountingMonitor testSubject = MessageCountingMonitor.buildMonitor(PROCESSOR_NAME, meterRegistry);
        EventMessage<Object> foo = asEventMessage("foo");
        EventMessage<Object> bar = asEventMessage("bar");
        EventMessage<Object> baz = asEventMessage("baz");
        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(foo, bar, baz));
        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);
        callbacks.get(baz).reportIgnored();

        Counter ingestedCounter = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".ingestedCounter").counter());
        Counter processedCounter = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".processedCounter").counter());
        Counter successCounter = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".successCounter").counter());
        Counter failureCounter = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".failureCounter").counter());
        Counter ignoredCounter = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".ignoredCounter").counter());

        assertEquals(3, ingestedCounter.count(), 0);
        assertEquals(2, processedCounter.count(), 0);
        assertEquals(1, successCounter.count(), 0);
        assertEquals(1, failureCounter.count(), 0);
        assertEquals(1, ignoredCounter.count(), 0);
    }
}