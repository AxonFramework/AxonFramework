/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.axonframework.axonserver.connector.processor.grpc;

import com.google.common.collect.ImmutableMap;
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * Unit test to verify expected behaviour of the {@link GrpcEventProcessorInfoSource}.
 *
 * @author Steven van Beelen
 */
class GrpcEventProcessorInfoSourceTest {

    private static final String BOUNDED_CONTEXT = "some-context";

    private final EventProcessingConfiguration eventProcessingConfiguration = mock(EventProcessingConfiguration.class);
    private final AxonServerConnectionManager axonServerConnectionManager = mock(AxonServerConnectionManager.class);

    private final GrpcEventProcessorInfoSource testSubject = new GrpcEventProcessorInfoSource(
            eventProcessingConfiguration, axonServerConnectionManager, BOUNDED_CONTEXT
    );

    @Test
    void testNotifyInformation() {
        TrackingEventProcessor mockEventProcessor = mock(TrackingEventProcessor.class);
        when(mockEventProcessor.getName()).thenReturn("eventProcessor");
        when(mockEventProcessor.getTokenStoreIdentifier()).thenReturn("tokenStoreIdentifier");
        Map<String, EventProcessor> testEventProcessors = ImmutableMap.of("eventProcessor", mockEventProcessor);
        when(eventProcessingConfiguration.eventProcessors()).thenReturn(testEventProcessors);

        testSubject.notifyInformation();

        verify(eventProcessingConfiguration).eventProcessors();
        verify(axonServerConnectionManager).send(eq(BOUNDED_CONTEXT), any(PlatformInboundInstruction.class));
    }
}