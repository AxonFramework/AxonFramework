package org.axonframework.metrics;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Map;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@RunWith(MockitoJUnitRunner.class)
public class PayloadTypeMessageMonitorWrapperTest {

    private static final String GROUP_NAME = "groupName";
    private static final CommandMessage<Object> STRING_MESSAGE = asCommandMessage("stringCommand");
    private static final CommandMessage<Object> INTEGER_MESSAGE = asCommandMessage(1);

    private PayloadTypeMessageMonitorWrapper<CapacityMonitor> testSubject;

    @Mock
    private MetricRegistry metricRegistry;
    private Class<CapacityMonitor> monitorClass;

    private final Appender appender = mock(Appender.class);
    private final Logger logger = Logger.getRootLogger();

    @Before
    public void setUp() throws Exception {
        logger.addAppender(appender);

        monitorClass = CapacityMonitor.class;
        testSubject = new PayloadTypeMessageMonitorWrapper<>(monitorClass, metricRegistry, GROUP_NAME);
    }

    @After
    public void tearDown() throws Exception {
        logger.removeAppender(appender);
    }

    @Test
    public void testInstantiateMessageMonitorOfTypeMonitorOnMessageIngested() throws Exception {
        Field payloadTypeMonitorsField = testSubject.getClass().getDeclaredField("payloadTypeMonitors");
        payloadTypeMonitorsField.setAccessible(true);

        Message<?> testMessage = asCommandMessage("stringCommand");

        testSubject.onMessageIngested(testMessage);

        Map<Class<?>, MessageMonitor<Message<?>>> payloadTypeMonitors = ReflectionUtils.getFieldValue(payloadTypeMonitorsField, testSubject);
        assertTrue(payloadTypeMonitors.size() == 1);
        MessageMonitor<Message<?>> messageMessageMonitor = payloadTypeMonitors.get(testMessage.getPayloadType());
        assertNotNull(messageMessageMonitor);
        assertTrue(monitorClass.isInstance(messageMessageMonitor));
    }

    @Test
    public void testInstantiatesOneMessageMonitorPerIngestedPayloadType() throws Exception {
        Field payloadTypeMonitorsField = testSubject.getClass().getDeclaredField("payloadTypeMonitors");
        payloadTypeMonitorsField.setAccessible(true);

        testSubject.onMessageIngested(STRING_MESSAGE); // First unique payload type
        testSubject.onMessageIngested(STRING_MESSAGE);
        testSubject.onMessageIngested(INTEGER_MESSAGE); // Second unique payload type

        Map<Class<?>, MessageMonitor<Message<?>>> payloadTypeMonitors = ReflectionUtils.getFieldValue(payloadTypeMonitorsField, testSubject);
        assertTrue(payloadTypeMonitors.size() == 2);

        MessageMonitor<Message<?>> messageMessageMonitor = payloadTypeMonitors.get(STRING_MESSAGE.getPayloadType());
        assertNotNull(messageMessageMonitor);
        assertTrue(monitorClass.isInstance(messageMessageMonitor));

        messageMessageMonitor = payloadTypeMonitors.get(INTEGER_MESSAGE.getPayloadType());
        assertNotNull(messageMessageMonitor);
        assertTrue(monitorClass.isInstance(messageMessageMonitor));
    }

    @Test
    public void testRegistersAMessageMonitorOnMessageIngested() throws Exception {
        String expectedMetricName = MetricRegistry.name(GROUP_NAME,
                PayloadTypeMessageMonitorWrapper.class.getSimpleName(), monitorClass.getSimpleName(),
                STRING_MESSAGE.getPayloadType().getSimpleName());

        testSubject.onMessageIngested(STRING_MESSAGE);

        ArgumentCaptor<Metric> registeredMetricCaptor = ArgumentCaptor.forClass(Metric.class);
        verify(metricRegistry).register(eq(expectedMetricName), registeredMetricCaptor.capture());
        Metric registeredMetric = registeredMetricCaptor.getValue();
        assertTrue(monitorClass.isInstance(registeredMetric));
    }

    @Test
    public void testRegistersAMessageMonitorPerIngestedPayloadType() throws Exception {
        String expectedStringMetricName = MetricRegistry.name(GROUP_NAME,
                PayloadTypeMessageMonitorWrapper.class.getSimpleName(), monitorClass.getSimpleName(),
                STRING_MESSAGE.getPayloadType().getSimpleName());
        String expectedIntegerMetricName = MetricRegistry.name(GROUP_NAME,
                PayloadTypeMessageMonitorWrapper.class.getSimpleName(), monitorClass.getSimpleName(),
                INTEGER_MESSAGE.getPayloadType().getSimpleName());

        testSubject.onMessageIngested(STRING_MESSAGE); // First unique payload type
        testSubject.onMessageIngested(INTEGER_MESSAGE); // Second unique payload type
        testSubject.onMessageIngested(INTEGER_MESSAGE);

        verify(metricRegistry, times(2)).register(anyString(), any());

        ArgumentCaptor<Metric> registeredMetricCaptor = ArgumentCaptor.forClass(Metric.class);
        verify(metricRegistry).register(eq(expectedStringMetricName), registeredMetricCaptor.capture());
        Metric registeredMetric = registeredMetricCaptor.getValue();
        assertTrue(monitorClass.isInstance(registeredMetric));

        verify(metricRegistry).register(eq(expectedIntegerMetricName), registeredMetricCaptor.capture());
        registeredMetric = registeredMetricCaptor.getValue();
        assertTrue(monitorClass.isInstance(registeredMetric));
    }

}