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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link EventProcessorLatencyMonitor}.
 *
 * @author Marijn van Zelst
 */
class EventProcessorLatencyMonitorTest {

    private static final String METER_NAME_PREFIX = "processor";

    private MeterRegistry meterRegistry;
    private EventProcessorLatencyMonitor.Builder testSubjectBuilder;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        testSubjectBuilder = EventProcessorLatencyMonitor.builder()
                                                         .meterNamePrefix(METER_NAME_PREFIX)
                                                         .meterRegistry(meterRegistry);
    }

    @Test
    void messagesWithoutTags() {
        EventProcessorLatencyMonitor testSubject = testSubjectBuilder.build();

        //noinspection unchecked
        EventMessage<String> firstEventMessage = mock(EventMessage.class);
        when(firstEventMessage.getTimestamp()).thenReturn(Instant.ofEpochMilli(0));
        when(firstEventMessage.getPayloadType()).thenReturn(String.class);

        //noinspection unchecked
        EventMessage<Integer> secondEventMessage = mock(EventMessage.class);
        when(secondEventMessage.getTimestamp()).thenReturn(Instant.ofEpochMilli(1000));
        when(secondEventMessage.getPayloadType()).thenReturn(Integer.class);

        Map<? super EventMessage<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(firstEventMessage, secondEventMessage));
        callbacks.get(firstEventMessage).reportSuccess();

        Gauge latencyGauge = Objects.requireNonNull(meterRegistry.find(METER_NAME_PREFIX + ".latency").gauge());
        assertTrue(latencyGauge.value() >= 1000);
    }

    @Test
    void messagesWithPayloadAsCustomTag() {
        EventProcessorLatencyMonitor testSubject = testSubjectBuilder.tagsBuilder(
                message -> Tags.of(TagsUtil.PAYLOAD_TYPE_TAG, message.getPayloadType().getSimpleName())
        ).build();

        //noinspection unchecked
        EventMessage<String> firstEventMessage = mock(EventMessage.class);
        when(firstEventMessage.getTimestamp()).thenReturn(Instant.now());
        when(firstEventMessage.getPayloadType()).thenReturn(String.class);

        //noinspection unchecked
        EventMessage<Integer> secondEventMessage = mock(EventMessage.class);
        when(secondEventMessage.getTimestamp()).thenReturn(Instant.now().minusMillis(1000));
        when(secondEventMessage.getPayloadType()).thenReturn(Integer.class);

        Map<? super EventMessage<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(firstEventMessage, secondEventMessage));
        callbacks.get(firstEventMessage).reportSuccess();

        assertEquals(2, meterRegistry.find(METER_NAME_PREFIX + ".latency").gauges().size());

        Gauge latencyGauge = Objects.requireNonNull(meterRegistry.find(METER_NAME_PREFIX + ".latency")
                                                                 .tags("payloadType", Integer.class.getSimpleName())
                                                                 .gauge());
        assertTrue(latencyGauge.value() >= 1000);
    }

    @Test
    void failureMessageWithPayloadAsCustomTag() {
        EventProcessorLatencyMonitor testSubject = testSubjectBuilder.tagsBuilder(
                message -> Tags.of(TagsUtil.PAYLOAD_TYPE_TAG, message.getPayloadType().getSimpleName())
        ).build();

        //noinspection unchecked
        EventMessage<String> firstEventMessage = mock(EventMessage.class);
        when(firstEventMessage.getTimestamp()).thenReturn(Instant.now().minusMillis(1000));
        when(firstEventMessage.getPayloadType()).thenReturn(String.class);

        //noinspection unchecked
        EventMessage<Integer> secondEventMessage = mock(EventMessage.class);
        when(secondEventMessage.getTimestamp()).thenReturn(Instant.now());
        when(secondEventMessage.getPayloadType()).thenReturn(Integer.class);

        Map<? super EventMessage<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(firstEventMessage, secondEventMessage));
        callbacks.get(firstEventMessage).reportFailure(null);

        Gauge latencyGauge = Objects.requireNonNull(meterRegistry.find(METER_NAME_PREFIX + ".latency").gauge());
        assertTrue(latencyGauge.value() >= 1000);
    }

    @Test
    void buildWithNullMeterNamePrefixThrowsAxonConfigurationException() {
        EventProcessorLatencyMonitor.Builder testSubject = EventProcessorLatencyMonitor.builder();
        assertThrows(AxonConfigurationException.class, () -> testSubject.meterNamePrefix(null));
    }

    @Test
    void buildWithEmptyMeterNamePrefixThrowsAxonConfigurationException() {
        EventProcessorLatencyMonitor.Builder testSubject = EventProcessorLatencyMonitor.builder();
        assertThrows(AxonConfigurationException.class, () -> testSubject.meterNamePrefix(""));
    }

    @Test
    void buildWithoutMeterNamePrefixThrowsAxonConfigurationException() {
        EventProcessorLatencyMonitor.Builder testSubject =
                EventProcessorLatencyMonitor.builder().meterRegistry(meterRegistry);
        assertThrows(AxonConfigurationException.class, testSubject::build);
    }

    @Test
    void buildWithNullMeterRegistryThrowsAxonConfigurationException() {
        EventProcessorLatencyMonitor.Builder testSubject = EventProcessorLatencyMonitor.builder();
        assertThrows(AxonConfigurationException.class, () -> testSubject.meterRegistry(null));
    }

    @Test
    void buildWithoutMeterRegistryThrowsAxonConfigurationException() {
        EventProcessorLatencyMonitor.Builder testSubject =
                EventProcessorLatencyMonitor.builder().meterNamePrefix(METER_NAME_PREFIX);
        assertThrows(AxonConfigurationException.class, testSubject::build);
    }

    @Test
    void buildWithNullClockThrowsAxonConfigurationException() {
        EventProcessorLatencyMonitor.Builder testSubject = EventProcessorLatencyMonitor.builder();
        assertThrows(AxonConfigurationException.class, () -> testSubject.clock(null));
    }

    @Test
    void buildWithNullTagsBuilderThrowsAxonConfigurationException() {
        EventProcessorLatencyMonitor.Builder testSubject = EventProcessorLatencyMonitor.builder();
        assertThrows(AxonConfigurationException.class, () -> testSubject.tagsBuilder(null));
    }
}
