package org.axonframework.metrics;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
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
import org.mockito.runners.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Map;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class PayloadTypeMessageMonitorWrapperTest<T extends MessageMonitor<Message<?>> & MetricSet> {

    private static final CommandMessage<Object> STRING_MESSAGE = asCommandMessage("stringCommand");
    private static final CommandMessage<Object> INTEGER_MESSAGE = asCommandMessage(1);

    private PayloadTypeMessageMonitorWrapper<CapacityMonitor> testSubject;

    private Class<CapacityMonitor> expectedMonitorClass;

    private final Appender appender = mock(Appender.class);
    private final Logger logger = Logger.getRootLogger();

    @Before
    public void setUp() throws Exception {
        logger.addAppender(appender);

        expectedMonitorClass = CapacityMonitor.class;
        testSubject = new PayloadTypeMessageMonitorWrapper<>(CapacityMonitor::new);
    }

    @After
    public void tearDown() throws Exception {
        logger.removeAppender(appender);
    }

    @Test
    public void testInstantiateMessageMonitorOfTypeMonitorOnMessageIngested() throws Exception {
        Field payloadTypeMonitorsField = testSubject.getClass().getDeclaredField("payloadTypeMonitors");
        payloadTypeMonitorsField.setAccessible(true);

        String expectedMonitorName = STRING_MESSAGE.getPayloadType().getName();

        testSubject.onMessageIngested(STRING_MESSAGE);

        Map<String, T> payloadTypeMonitors = ReflectionUtils.getFieldValue(payloadTypeMonitorsField, testSubject);
        assertTrue(payloadTypeMonitors.size() == 1);
        MessageMonitor<Message<?>> messageMessageMonitor = payloadTypeMonitors.get(expectedMonitorName);
        assertNotNull(messageMessageMonitor);
        assertTrue(expectedMonitorClass.isInstance(messageMessageMonitor));

        Map<String, Metric> resultMetrics = testSubject.getMetrics();
        assertTrue(resultMetrics.size() == 1);
        assertNotNull(resultMetrics.get(expectedMonitorName));
    }

    @Test
    public void testInstantiatesOneMessageMonitorPerIngestedPayloadType() throws Exception {
        Field payloadTypeMonitorsField = testSubject.getClass().getDeclaredField("payloadTypeMonitors");
        payloadTypeMonitorsField.setAccessible(true);

        String expectedStringMonitorName = STRING_MESSAGE.getPayloadType().getName();
        String expectedIntegerMonitorName = INTEGER_MESSAGE.getPayloadType().getName();

        testSubject.onMessageIngested(STRING_MESSAGE); // First unique payload type
        testSubject.onMessageIngested(STRING_MESSAGE);
        testSubject.onMessageIngested(INTEGER_MESSAGE); // Second unique payload type

        Map<String, T> payloadTypeMonitors = ReflectionUtils.getFieldValue(payloadTypeMonitorsField, testSubject);
        assertTrue(payloadTypeMonitors.size() == 2);

        MessageMonitor<Message<?>> messageMessageMonitor = payloadTypeMonitors.get(expectedStringMonitorName);
        assertNotNull(messageMessageMonitor);
        assertTrue(expectedMonitorClass.isInstance(messageMessageMonitor));

        messageMessageMonitor = payloadTypeMonitors.get(expectedIntegerMonitorName);
        assertNotNull(messageMessageMonitor);
        assertTrue(expectedMonitorClass.isInstance(messageMessageMonitor));

        Map<String, Metric> resultMetrics = testSubject.getMetrics();
        assertTrue(resultMetrics.size() == 2);
        assertNotNull(resultMetrics.get(expectedStringMonitorName));
        assertNotNull(resultMetrics.get(expectedStringMonitorName));
    }

    @Test
    public void testMonitorNameFollowsGivenMonitorNameBuilderSpecifics() throws Exception {
        String testPrefix = "additional-monitor-name.";
        PayloadTypeMessageMonitorWrapper<CapacityMonitor> testSubject = new PayloadTypeMessageMonitorWrapper<>(
                CapacityMonitor::new, payloadType -> testPrefix + payloadType.getName());

        String expectedMonitorName = testPrefix + STRING_MESSAGE.getPayloadType().getName();

        testSubject.onMessageIngested(STRING_MESSAGE);

        Map<String, Metric> resultMetrics = testSubject.getMetrics();
        assertTrue(resultMetrics.size() == 1);
        assertNotNull(resultMetrics.get(expectedMonitorName));
    }

}