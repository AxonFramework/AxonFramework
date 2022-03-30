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

import io.micrometer.core.instrument.Counter;
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

import static java.util.Objects.requireNonNull;
import static org.axonframework.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;


class MessageCountingMonitorTest {

    private static final String PROCESSOR_NAME = "processorName";

    @Test
    void testMessagesWithoutTags() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MessageCountingMonitor testSubject = MessageCountingMonitor.buildMonitor(PROCESSOR_NAME,
                                                                                 meterRegistry);
        EventMessage<Object> foo = asEventMessage(1);
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

    @Test
    void testMessagesWithPayloadTypeAsCustomTag() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MessageCountingMonitor testSubject = MessageCountingMonitor.buildMonitor(PROCESSOR_NAME,
                                                                                 meterRegistry,
                                                                                 message -> Tags
                                                                                         .of(TagsUtil.PAYLOAD_TYPE_TAG,
                                                                                             message.getPayloadType()
                                                                                                    .getSimpleName()));
        EventMessage<Object> foo = asEventMessage(1);
        EventMessage<Object> bar = asEventMessage("bar");
        EventMessage<Object> baz = asEventMessage("baz");
        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(foo, bar, baz));
        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);
        callbacks.get(baz).reportIgnored();

        Collection<Counter> ingestedCounters = meterRegistry.find(PROCESSOR_NAME + ".ingestedCounter").counters();
        Collection<Counter> processedCounters = meterRegistry.find(PROCESSOR_NAME + ".processedCounter").counters();
        Collection<Counter> successCounters = meterRegistry.find(PROCESSOR_NAME + ".successCounter").counters();
        Collection<Counter> failureCounters = meterRegistry.find(PROCESSOR_NAME + ".failureCounter").counters();
        Collection<Counter> ignoredCounters = meterRegistry.find(PROCESSOR_NAME + ".ignoredCounter").counters();

        assertEquals(2, ingestedCounters.size(), 0);
        assertEquals(2, processedCounters.size(), 0);
        assertEquals(2, successCounters.size(), 0);
        assertEquals(2, failureCounters.size(), 0);
        assertEquals(2, ignoredCounters.size(), 0);


        assertTrue(ingestedCounters.stream()
                                   .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "Integer"))
                                   .allMatch(counter -> counter.count() == 1));
        assertTrue(ingestedCounters.stream()
                                   .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "String"))
                                   .allMatch(counter -> counter.count() == 2));


        assertTrue(processedCounters.stream()
                                    .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "Integer"))
                                    .allMatch(counter -> counter.count() == 1));
        assertTrue(processedCounters.stream()
                                    .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "String"))
                                    .allMatch(counter -> counter.count() == 1));


        assertTrue(successCounters.stream()
                                  .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "Integer"))
                                  .allMatch(counter -> counter.count() == 1));
        assertTrue(successCounters.stream()
                                  .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "String"))
                                  .allMatch(counter -> counter.count() == 0));


        assertTrue(failureCounters.stream()
                                  .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "Integer"))
                                  .allMatch(counter -> counter.count() == 0));
        assertTrue(failureCounters.stream()
                                  .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "String"))
                                  .allMatch(counter -> counter.count() == 1));


        assertTrue(ignoredCounters.stream()
                                  .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "Integer"))
                                  .allMatch(counter -> counter.count() == 0));
        assertTrue(ignoredCounters.stream()
                                  .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "String"))
                                  .allMatch(counter -> counter.count() == 1));
    }

    @Test
    void testMessagesWithMetadataAsCustomTag() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MessageCountingMonitor testSubject = MessageCountingMonitor.buildMonitor(PROCESSOR_NAME,
                                                                                 meterRegistry,
                                                                                 message -> Tags
                                                                                         .of("myMetaData",
                                                                                             message.getMetaData()
                                                                                                    .get("myMetadataKey")
                                                                                                    .toString()));
        EventMessage<Object> foo = asEventMessage(1).withMetaData(Collections.singletonMap("myMetadataKey",
                                                                                           "myMetadataValue1"));
        EventMessage<Object> bar = asEventMessage("bar").withMetaData(Collections.singletonMap("myMetadataKey",
                                                                                               "myMetadataValue2"));
        ;
        EventMessage<Object> baz = asEventMessage("baz").withMetaData(Collections.singletonMap("myMetadataKey",
                                                                                               "myMetadataValue2"));
        ;
        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(foo, bar, baz));
        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);
        callbacks.get(baz).reportIgnored();

        Collection<Counter> ingestedCounters = meterRegistry.find(PROCESSOR_NAME + ".ingestedCounter").counters();
        Collection<Counter> processedCounters = meterRegistry.find(PROCESSOR_NAME + ".processedCounter").counters();
        Collection<Counter> successCounters = meterRegistry.find(PROCESSOR_NAME + ".successCounter").counters();
        Collection<Counter> failureCounters = meterRegistry.find(PROCESSOR_NAME + ".failureCounter").counters();
        Collection<Counter> ignoredCounters = meterRegistry.find(PROCESSOR_NAME + ".ignoredCounter").counters();

        assertEquals(2, ingestedCounters.size(), 0);
        assertEquals(2, processedCounters.size(), 0);
        assertEquals(2, successCounters.size(), 0);
        assertEquals(2, failureCounters.size(), 0);
        assertEquals(2, ignoredCounters.size(), 0);


        assertTrue(ingestedCounters.stream()
                                   .filter(counter -> Objects
                                           .equals(counter.getId().getTag("myMetaData"), "myMetadataValue1"))
                                   .allMatch(counter -> counter.count() == 1));
        assertTrue(ingestedCounters.stream()
                                   .filter(counter -> Objects
                                           .equals(counter.getId().getTag("myMetaData"), "myMetadataValue2"))
                                   .allMatch(counter -> counter.count() == 2));


        assertTrue(processedCounters.stream()
                                    .filter(counter -> Objects
                                            .equals(counter.getId().getTag("myMetaData"), "myMetadataValue1"))
                                    .allMatch(counter -> counter.count() == 1));
        assertTrue(processedCounters.stream()
                                    .filter(counter -> Objects
                                            .equals(counter.getId().getTag("payloadType"), "myMetadataValue2"))
                                    .allMatch(counter -> counter.count() == 1));


        assertTrue(successCounters.stream()
                                  .filter(counter -> Objects
                                          .equals(counter.getId().getTag("payloadType"), "myMetadataValue1"))
                                  .allMatch(counter -> counter.count() == 1));
        assertTrue(successCounters.stream()
                                  .filter(counter -> Objects
                                          .equals(counter.getId().getTag("payloadType"), "myMetadataValue2"))
                                  .allMatch(counter -> counter.count() == 0));


        assertTrue(failureCounters.stream()
                                  .filter(counter -> Objects
                                          .equals(counter.getId().getTag("payloadType"), "myMetadataValue1"))
                                  .allMatch(counter -> counter.count() == 0));
        assertTrue(failureCounters.stream()
                                  .filter(counter -> Objects
                                          .equals(counter.getId().getTag("payloadType"), "myMetadataValue2"))
                                  .allMatch(counter -> counter.count() == 1));


        assertTrue(ignoredCounters.stream()
                                  .filter(counter -> Objects
                                          .equals(counter.getId().getTag("payloadType"), "myMetadataValue1"))
                                  .allMatch(counter -> counter.count() == 0));
        assertTrue(ignoredCounters.stream()
                                  .filter(counter -> Objects
                                          .equals(counter.getId().getTag("payloadType"), "myMetadataValue2"))
                                  .allMatch(counter -> counter.count() == 1));
    }
}
