package org.axonframework.axonserver.connector.processor.grpc;

import com.google.common.collect.ImmutableMap;
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.junit.*;

import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * Unit test to verify expected behaviour of the {@link GrpcEventProcessorInfoSource}.
 *
 * @author Steven van Beelen
 */
public class GrpcEventProcessorInfoSourceTest {

    private static final String BOUNDED_CONTEXT = "some-context";

    private final EventProcessingConfiguration eventProcessingConfiguration = mock(EventProcessingConfiguration.class);
    private final AxonServerConnectionManager axonServerConnectionManager = mock(AxonServerConnectionManager.class);

    private GrpcEventProcessorInfoSource testSubject = new GrpcEventProcessorInfoSource(
            eventProcessingConfiguration, axonServerConnectionManager, BOUNDED_CONTEXT
    );

    @Test
    public void testNotifyInformation() {
        EventProcessor mockEventProcessor = mock(TrackingEventProcessor.class);
        when(mockEventProcessor.getName()).thenReturn("eventProcessor");
        Map<String, EventProcessor> testEventProcessors = ImmutableMap.of("eventProcessor", mockEventProcessor);
        when(eventProcessingConfiguration.eventProcessors()).thenReturn(testEventProcessors);

        testSubject.notifyInformation();

        verify(eventProcessingConfiguration).eventProcessors();
        verify(axonServerConnectionManager).send(eq(BOUNDED_CONTEXT), any(PlatformInboundInstruction.class));
    }
}