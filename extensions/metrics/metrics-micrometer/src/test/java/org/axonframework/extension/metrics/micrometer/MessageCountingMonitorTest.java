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

package org.axonframework.extension.metrics.micrometer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.axonframework.extension.metrics.micrometer.TagsUtil.MESSAGE_TYPE_TAG;
import static org.axonframework.messaging.eventhandling.EventTestUtils.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link MessageCountingMonitor}.
 *
 * @author Martijn Zelst
 */
class MessageCountingMonitorTest {

    private static final String PROCESSOR_NAME = "processorName";

    @Test
    void messagesWithoutTags() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MessageCountingMonitor testSubject = MessageCountingMonitor.buildMonitor(PROCESSOR_NAME, meterRegistry);

        EventMessage foo = asEventMessage(1);
        EventMessage bar = asEventMessage("bar");
        EventMessage baz = asEventMessage("baz");
        Map<? super Message, MessageMonitor.MonitorCallback> callbacks =
                testSubject.onMessagesIngested(Arrays.asList(foo, bar, baz));
        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);
        callbacks.get(baz).reportIgnored();

        Counter ingestedCounter =
                requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".messageCounter.ingested").counter());
        Counter processedCounter =
                requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".messageCounter.processed").counter());
        Counter successCounter =
                requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".messageCounter.success").counter());
        Counter failureCounter =
                requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".messageCounter.failure").counter());
        Counter ignoredCounter =
                requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".messageCounter.ignored").counter());

        assertEquals(3, ingestedCounter.count(), 0);
        assertEquals(2, processedCounter.count(), 0);
        assertEquals(1, successCounter.count(), 0);
        assertEquals(1, failureCounter.count(), 0);
        assertEquals(1, ignoredCounter.count(), 0);
    }

    @Test
    void messagesWithMessageTypeAsCustomTag() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MessageCountingMonitor testSubject = MessageCountingMonitor.buildMonitor(
                PROCESSOR_NAME,
                meterRegistry,
                message -> Tags.of(MESSAGE_TYPE_TAG, message.type().name())
        );

        EventMessage foo = asEventMessage(1);
        EventMessage bar = asEventMessage("bar");
        EventMessage baz = asEventMessage("baz");
        Map<? super Message, MessageMonitor.MonitorCallback> callbacks =
                testSubject.onMessagesIngested(Arrays.asList(foo, bar, baz));
        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);
        callbacks.get(baz).reportIgnored();

        Collection<Counter> ingestedCounters = meterRegistry.find(PROCESSOR_NAME + ".messageCounter.ingested")
                                                            .counters();
        Collection<Counter> processedCounters = meterRegistry.find(PROCESSOR_NAME + ".messageCounter.processed")
                                                             .counters();
        Collection<Counter> successCounters = meterRegistry.find(PROCESSOR_NAME + ".messageCounter.success")
                                                           .counters();
        Collection<Counter> failureCounters = meterRegistry.find(PROCESSOR_NAME + ".messageCounter.failure")
                                                           .counters();
        Collection<Counter> ignoredCounters = meterRegistry.find(PROCESSOR_NAME + ".messageCounter.ignored")
                                                           .counters();

        assertEquals(2, ingestedCounters.size(), 0);
        assertEquals(2, processedCounters.size(), 0);
        assertEquals(2, successCounters.size(), 0);
        assertEquals(2, failureCounters.size(), 0);
        assertEquals(2, ignoredCounters.size(), 0);

        assertTrue(ingestedCounters.stream()
                                   .filter(counter -> Objects.equals(
                                           counter.getId().getTag(MESSAGE_TYPE_TAG), "Integer"
                                   ))
                                   .allMatch(counter -> counter.count() == 1));
        assertTrue(ingestedCounters.stream()
                                   .filter(counter -> Objects.equals(
                                           counter.getId().getTag(MESSAGE_TYPE_TAG), "String"
                                   ))
                                   .allMatch(counter -> counter.count() == 2));

        assertTrue(processedCounters.stream()
                                    .filter(counter -> Objects.equals(
                                            counter.getId().getTag(MESSAGE_TYPE_TAG), "Integer"
                                    ))
                                    .allMatch(counter -> counter.count() == 1));
        assertTrue(processedCounters.stream()
                                    .filter(counter -> Objects.equals(
                                            counter.getId().getTag(MESSAGE_TYPE_TAG), "String"
                                    ))
                                    .allMatch(counter -> counter.count() == 1));

        assertTrue(successCounters.stream()
                                  .filter(counter -> Objects.equals(
                                          counter.getId().getTag(MESSAGE_TYPE_TAG), "Integer"
                                  ))
                                  .allMatch(counter -> counter.count() == 1));
        assertTrue(successCounters.stream()
                                  .filter(counter -> Objects.equals(
                                          counter.getId().getTag(MESSAGE_TYPE_TAG), "String"
                                  ))
                                  .allMatch(counter -> counter.count() == 0));

        assertTrue(failureCounters.stream()
                                  .filter(counter -> Objects.equals(
                                          counter.getId().getTag(MESSAGE_TYPE_TAG), "Integer"
                                  ))
                                  .allMatch(counter -> counter.count() == 0));
        assertTrue(failureCounters.stream()
                                  .filter(counter -> Objects.equals(
                                          counter.getId().getTag(MESSAGE_TYPE_TAG), "String"
                                  ))
                                  .allMatch(counter -> counter.count() == 1));

        assertTrue(ignoredCounters.stream()
                                  .filter(counter -> Objects.equals(
                                          counter.getId().getTag(MESSAGE_TYPE_TAG), "Integer"
                                  ))
                                  .allMatch(counter -> counter.count() == 0));
        assertTrue(ignoredCounters.stream()
                                  .filter(counter -> Objects.equals(
                                          counter.getId().getTag(MESSAGE_TYPE_TAG), "String"
                                  ))
                                  .allMatch(counter -> counter.count() == 1));
    }

    @Test
    void messagesWithMetadataAsCustomTag() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MessageCountingMonitor testSubject = MessageCountingMonitor.buildMonitor(
                PROCESSOR_NAME,
                meterRegistry,
                message -> Tags.of("myMetadata", message.metadata().get("myMetadataKey"))
        );

        EventMessage foo = asEventMessage(1)
                .withMetadata(Collections.singletonMap("myMetadataKey", "myMetadataValue1"));
        EventMessage bar = asEventMessage("bar")
                .withMetadata(Collections.singletonMap("myMetadataKey", "myMetadataValue2"));
        EventMessage baz = asEventMessage("baz")
                .withMetadata(Collections.singletonMap("myMetadataKey", "myMetadataValue2"));

        Map<? super Message, MessageMonitor.MonitorCallback> callbacks =
                testSubject.onMessagesIngested(Arrays.asList(foo, bar, baz));
        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);
        callbacks.get(baz).reportIgnored();

        Collection<Counter> ingestedCounters = meterRegistry.find(PROCESSOR_NAME + ".messageCounter.ingested")
                                                            .counters();
        Collection<Counter> processedCounters = meterRegistry.find(PROCESSOR_NAME + ".messageCounter.processed")
                                                             .counters();
        Collection<Counter> successCounters = meterRegistry.find(PROCESSOR_NAME + ".messageCounter.success").counters();
        Collection<Counter> failureCounters = meterRegistry.find(PROCESSOR_NAME + ".messageCounter.failure").counters();
        Collection<Counter> ignoredCounters = meterRegistry.find(PROCESSOR_NAME + ".messageCounter.ignored").counters();

        assertEquals(2, ingestedCounters.size(), 0);
        assertEquals(2, processedCounters.size(), 0);
        assertEquals(2, successCounters.size(), 0);
        assertEquals(2, failureCounters.size(), 0);
        assertEquals(2, ignoredCounters.size(), 0);

        assertTrue(ingestedCounters.stream()
                                   .filter(counter -> Objects.equals(
                                           counter.getId().getTag("myMetadata"), "myMetadataValue1"
                                   ))
                                   .allMatch(counter -> counter.count() == 1));
        assertTrue(ingestedCounters.stream()
                                   .filter(counter -> Objects.equals(
                                           counter.getId().getTag("myMetadata"), "myMetadataValue2"
                                   ))
                                   .allMatch(counter -> counter.count() == 2));

        assertTrue(processedCounters.stream()
                                    .filter(counter -> Objects.equals(
                                            counter.getId().getTag("myMetadata"), "myMetadataValue1"
                                    ))
                                    .allMatch(counter -> counter.count() == 1));
        assertTrue(processedCounters.stream()
                                    .filter(counter -> Objects.equals(
                                            counter.getId().getTag(MESSAGE_TYPE_TAG), "myMetadataValue2"
                                    ))
                                    .allMatch(counter -> counter.count() == 1));

        assertTrue(successCounters.stream()
                                  .filter(counter -> Objects.equals(
                                          counter.getId().getTag(MESSAGE_TYPE_TAG), "myMetadataValue1"
                                  ))
                                  .allMatch(counter -> counter.count() == 1));
        assertTrue(successCounters.stream()
                                  .filter(counter -> Objects.equals(
                                          counter.getId().getTag(MESSAGE_TYPE_TAG), "myMetadataValue2"
                                  ))
                                  .allMatch(counter -> counter.count() == 0));

        assertTrue(failureCounters.stream()
                                  .filter(counter -> Objects.equals(
                                          counter.getId().getTag(MESSAGE_TYPE_TAG), "myMetadataValue1"
                                  ))
                                  .allMatch(counter -> counter.count() == 0));
        assertTrue(failureCounters.stream()
                                  .filter(counter -> Objects.equals(
                                          counter.getId().getTag(MESSAGE_TYPE_TAG), "myMetadataValue2"
                                  ))
                                  .allMatch(counter -> counter.count() == 1));

        assertTrue(ignoredCounters.stream()
                                  .filter(counter -> Objects.equals(
                                          counter.getId().getTag(MESSAGE_TYPE_TAG), "myMetadataValue1"
                                  ))
                                  .allMatch(counter -> counter.count() == 0));
        assertTrue(ignoredCounters.stream()
                                  .filter(counter -> Objects.equals(
                                          counter.getId().getTag(MESSAGE_TYPE_TAG), "myMetadataValue2"
                                  ))
                                  .allMatch(counter -> counter.count() == 1));
    }
}
