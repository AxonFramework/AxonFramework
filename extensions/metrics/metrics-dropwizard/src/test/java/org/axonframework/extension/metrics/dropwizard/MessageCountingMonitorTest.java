/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.extension.metrics.dropwizard;

import io.dropwizard.metrics5.Counter;
import io.dropwizard.metrics5.Metric;
import io.dropwizard.metrics5.MetricName;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageCountingMonitorTest {

    @Test
    void messages() {
        MessageCountingMonitor testSubject = new MessageCountingMonitor();
        EventMessage foo = asEventMessage("foo");
        EventMessage bar = asEventMessage("bar");
        EventMessage baz = asEventMessage("baz");
        Map<? super Message, MessageMonitor.MonitorCallback> callbacks =
                testSubject.onMessagesIngested(Arrays.asList(foo, bar, baz));
        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);
        callbacks.get(baz).reportIgnored();

        Map<MetricName, Metric> metricSet = testSubject.getMetrics();

        Counter ingestedCounter = (Counter) metricSet.get(MetricName.build("ingestedCounter"));
        Counter processedCounter = (Counter) metricSet.get(MetricName.build("processedCounter"));
        Counter successCounter = (Counter) metricSet.get(MetricName.build("successCounter"));
        Counter failureCounter = (Counter) metricSet.get(MetricName.build("failureCounter"));
        Counter ignoredCounter = (Counter) metricSet.get(MetricName.build("ignoredCounter"));

        assertEquals(3, ingestedCounter.getCount());
        assertEquals(2, processedCounter.getCount());
        assertEquals(1, successCounter.getCount());
        assertEquals(1, failureCounter.getCount());
        assertEquals(1, ignoredCounter.getCount());
    }

    private static EventMessage asEventMessage(Object payload) {
        return new GenericEventMessage(new MessageType("event"), payload);
    }
}