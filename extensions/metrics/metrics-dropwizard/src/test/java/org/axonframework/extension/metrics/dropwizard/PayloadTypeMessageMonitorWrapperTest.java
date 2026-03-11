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

import io.dropwizard.metrics5.Metric;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricSet;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.*;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PayloadTypeMessageMonitorWrapperTest<T extends MessageMonitor<Message> & MetricSet> {

    private static final CommandMessage STRING_MESSAGE =
            new GenericCommandMessage(new MessageType("command"), "stringCommand");
    private static final CommandMessage INTEGER_MESSAGE =
            new GenericCommandMessage(new MessageType("command"), 1);

    private PayloadTypeMessageMonitorWrapper<CapacityMonitor> testSubject;

    private Class<CapacityMonitor> expectedMonitorClass;

    @BeforeEach
    void setUp() {
        expectedMonitorClass = CapacityMonitor.class;
        testSubject = new PayloadTypeMessageMonitorWrapper<>(CapacityMonitor::new);
    }

    @Test
    void instantiateMessageMonitorOfTypeMonitorOnMessageIngested() throws Exception {
        Field payloadTypeMonitorsField = testSubject.getClass().getDeclaredField("payloadTypeMonitors");
        payloadTypeMonitorsField.setAccessible(true);

        String expectedMonitorName = STRING_MESSAGE.payloadType().getName();

        testSubject.onMessageIngested(STRING_MESSAGE);

        Map<String, T> payloadTypeMonitors = ReflectionUtils.getFieldValue(payloadTypeMonitorsField, testSubject);
        assertEquals(1, payloadTypeMonitors.size());
        MessageMonitor<Message> messageMessageMonitor = payloadTypeMonitors.get(expectedMonitorName);
        assertNotNull(messageMessageMonitor);
        assertTrue(expectedMonitorClass.isInstance(messageMessageMonitor));

        Map<MetricName, Metric> resultMetrics = testSubject.getMetrics();
        assertEquals(1, resultMetrics.size());
        assertNotNull(resultMetrics.get(MetricName.build(expectedMonitorName)));
    }

    @Test
    void instantiatesOneMessageMonitorPerIngestedPayloadType() throws Exception {
        Field payloadTypeMonitorsField = testSubject.getClass().getDeclaredField("payloadTypeMonitors");
        payloadTypeMonitorsField.setAccessible(true);

        String expectedStringMonitorName = STRING_MESSAGE.payloadType().getName();
        String expectedIntegerMonitorName = INTEGER_MESSAGE.payloadType().getName();

        testSubject.onMessageIngested(STRING_MESSAGE); // First unique payload type
        testSubject.onMessageIngested(STRING_MESSAGE);
        testSubject.onMessageIngested(INTEGER_MESSAGE); // Second unique payload type

        Map<String, T> payloadTypeMonitors = ReflectionUtils.getFieldValue(payloadTypeMonitorsField, testSubject);
        assertEquals(2, payloadTypeMonitors.size());

        MessageMonitor<Message> messageMessageMonitor = payloadTypeMonitors.get(expectedStringMonitorName);
        assertNotNull(messageMessageMonitor);
        assertTrue(expectedMonitorClass.isInstance(messageMessageMonitor));

        messageMessageMonitor = payloadTypeMonitors.get(expectedIntegerMonitorName);
        assertNotNull(messageMessageMonitor);
        assertTrue(expectedMonitorClass.isInstance(messageMessageMonitor));

        Map<MetricName, Metric> resultMetrics = testSubject.getMetrics();
        assertEquals(2, resultMetrics.size());
        assertNotNull(resultMetrics.get(MetricName.build(expectedStringMonitorName)));
        assertNotNull(resultMetrics.get(MetricName.build(expectedStringMonitorName)));
    }

    @Test
    void monitorNameFollowsGivenMonitorNameBuilderSpecifics() {
        String testPrefix = "additional-monitor-name.";
        PayloadTypeMessageMonitorWrapper<CapacityMonitor> testSubject = new PayloadTypeMessageMonitorWrapper<>(
                CapacityMonitor::new, payloadType -> testPrefix + payloadType.getName());

        String expectedMonitorName = testPrefix + STRING_MESSAGE.payloadType().getName();

        testSubject.onMessageIngested(STRING_MESSAGE);

        Map<MetricName, Metric> resultMetrics = testSubject.getMetrics();
        assertEquals(1, resultMetrics.size());
        assertNotNull(resultMetrics.get(MetricName.build(expectedMonitorName)));
    }
}
