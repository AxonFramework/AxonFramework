package org.axonframework.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.ReflectionUtils;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.junit.*;

import java.lang.reflect.Field;
import java.util.Map;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PayloadTypeMessageMonitorWrapperTest<T extends MessageMonitor<Message<?>>> {

    private static final CommandMessage<Object> STRING_MESSAGE = asCommandMessage("stringCommand");
    private static final CommandMessage<Object> INTEGER_MESSAGE = asCommandMessage(1);
    private SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();


    private PayloadTypeMessageMonitorWrapper<CapacityMonitor> testSubject;

    private Class<CapacityMonitor> expectedMonitorClass;

    private final Appender appender = mock(Appender.class);
    private final Logger logger = Logger.getRootLogger();

    @Before
    public void setUp() {
        logger.addAppender(appender);

        expectedMonitorClass = CapacityMonitor.class;
        testSubject = new PayloadTypeMessageMonitorWrapper<>(name -> CapacityMonitor.buildMonitor(name, meterRegistry));
    }

    @After
    public void tearDown() {
        logger.removeAppender(appender);
    }

    @Test
    public void testInstantiateMessageMonitorOfTypeMonitorOnMessageIngested() throws Exception {
        Field payloadTypeMonitorsField = testSubject.getClass().getDeclaredField("payloadTypeMonitors");
        payloadTypeMonitorsField.setAccessible(true);

        String expectedMonitorName = STRING_MESSAGE.getPayloadType().getName();

        testSubject.onMessageIngested(STRING_MESSAGE);

        Map<String, T> payloadTypeMonitors = ReflectionUtils.getFieldValue(payloadTypeMonitorsField, testSubject);
        assertEquals(1, payloadTypeMonitors.size());
        MessageMonitor<Message<?>> messageMessageMonitor = payloadTypeMonitors.get(expectedMonitorName);
        assertNotNull(messageMessageMonitor);
        assertTrue(expectedMonitorClass.isInstance(messageMessageMonitor));

        assertEquals(1, meterRegistry.find(expectedMonitorName + ".capacity").meters().size());
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
        assertEquals(2, payloadTypeMonitors.size());

        MessageMonitor<Message<?>> messageMessageMonitor = payloadTypeMonitors.get(expectedStringMonitorName);
        assertNotNull(messageMessageMonitor);
        assertTrue(expectedMonitorClass.isInstance(messageMessageMonitor));

        messageMessageMonitor = payloadTypeMonitors.get(expectedIntegerMonitorName);
        assertNotNull(messageMessageMonitor);
        assertTrue(expectedMonitorClass.isInstance(messageMessageMonitor));

        assertEquals(1, meterRegistry.find(expectedStringMonitorName + ".capacity").meters().size());
        assertEquals(1, meterRegistry.find(expectedIntegerMonitorName + ".capacity").meters().size());
    }

    @Test
    public void testMonitorNameFollowsGivenMonitorNameBuilderSpecifics() {
        String testPrefix = "additional-monitor-name.";
        PayloadTypeMessageMonitorWrapper<CapacityMonitor> testSubject = new PayloadTypeMessageMonitorWrapper<>(
                name -> CapacityMonitor.buildMonitor(name, meterRegistry),
                payloadType -> testPrefix + payloadType.getName());

        String expectedMonitorName = testPrefix + STRING_MESSAGE.getPayloadType().getName();

        testSubject.onMessageIngested(STRING_MESSAGE);

        assertEquals(1, meterRegistry.find(expectedMonitorName + ".capacity").meters().size());
    }
}
