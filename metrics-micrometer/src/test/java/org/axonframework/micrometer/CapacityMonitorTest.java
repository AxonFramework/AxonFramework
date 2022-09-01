/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.micrometer;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

class CapacityMonitorTest {

    @Test
    void capacityWithoutTags() {
        MockClock testClock = new MockClock();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CapacityMonitor testSubject = CapacityMonitor.buildMonitor("1",
                                                                   meterRegistry,
                                                                   1,
                                                                   TimeUnit.SECONDS,
                                                                   testClock);

        EventMessage<Object> foo = asEventMessage(1);
        EventMessage<Object> bar = asEventMessage("bar");
        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(foo, bar));

        testClock.addSeconds(1);

        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);

        Gauge capacityGauge = meterRegistry.get("1.capacity").gauge();
        assertEquals(2, capacityGauge.value(), 0);
    }

    @Test
    void capacityWithPayloadTypeAsCustomTag() {
        MockClock testClock = new MockClock();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CapacityMonitor testSubject = CapacityMonitor.buildMonitor("1",
                                                                   meterRegistry,
                                                                   1,
                                                                   TimeUnit.SECONDS,
                                                                   testClock,
                                                                   message -> Tags
                                                                           .of(TagsUtil.PAYLOAD_TYPE_TAG,
                                                                               message.getPayloadType()
                                                                                      .getSimpleName()));

        EventMessage<Object> foo = asEventMessage(1);
        EventMessage<Object> bar = asEventMessage("bar");
        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(foo, bar));

        testClock.addSeconds(1);

        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);

        Collection<Gauge> capacityGauges = meterRegistry.find("1.capacity").gauges();
        assertEquals(2, capacityGauges.size(), 0);
        assertTrue(capacityGauges.stream()
                                 .anyMatch(gauge -> Objects.equals(gauge.getId().getTag("payloadType"), "Integer")));
        assertTrue(capacityGauges.stream()
                                 .anyMatch(gauge -> Objects.equals(gauge.getId().getTag("payloadType"), "String")));
    }

    @Test
    void capacityWithMetadataAsCustomTag() {
        MockClock testClock = new MockClock();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CapacityMonitor testSubject = CapacityMonitor.buildMonitor("1",
                                                                   meterRegistry,
                                                                   1,
                                                                   TimeUnit.SECONDS,
                                                                   testClock,
                                                                   message -> Tags.of("myPayloadType",
                                                                                      message.getPayloadType()
                                                                                             .getSimpleName(),
                                                                                      "myMetaData",
                                                                                      message.getMetaData()
                                                                                             .get("myMetadataKey")
                                                                                             .toString()));

        EventMessage<Object> foo = asEventMessage(1).withMetaData(Collections.singletonMap("myMetadataKey",
                                                                                           "myMetadataValue1"));
        EventMessage<Object> bar = asEventMessage("bar").withMetaData(Collections.singletonMap("myMetadataKey",
                                                                                               "myMetadataValue2"));
        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(foo, bar));

        testClock.addSeconds(1);

        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);

        Collection<Gauge> capacityGauges = meterRegistry.find("1.capacity").gauges();
        assertEquals(2, capacityGauges.size(), 0);
        assertTrue(capacityGauges.stream()
                                 .anyMatch(gauge -> Objects.equals(gauge.getId().getTag("myPayloadType"), "Integer")));
        assertTrue(capacityGauges.stream()
                                 .anyMatch(gauge -> Objects.equals(gauge.getId().getTag("myPayloadType"), "String")));
        assertTrue(capacityGauges.stream()
                                 .anyMatch(gauge -> Objects
                                         .equals(gauge.getId().getTag("myMetaData"), "myMetadataValue1")));
        assertTrue(capacityGauges.stream()
                                 .anyMatch(gauge -> Objects
                                         .equals(gauge.getId().getTag("myMetaData"), "myMetadataValue2")));
    }
}
