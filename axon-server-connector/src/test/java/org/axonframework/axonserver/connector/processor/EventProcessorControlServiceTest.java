/*
 * Copyright (c) 2010-2019. Axon Framework
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

package org.axonframework.axonserver.connector.processor;

import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.junit.Test;

import java.util.function.Consumer;

import static io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction.RequestCase.*;
import static org.mockito.Mockito.*;

public class EventProcessorControlServiceTest {

    private final AxonServerConnectionManager axonServerConnectionManager = mock(AxonServerConnectionManager.class);
    private final EventProcessorController eventProcessorController = mock(EventProcessorController.class);

    private EventProcessorControlService testSubject;

    @SuppressWarnings("unchecked")
    @Test
    public void testStartAddOutboundInstructionToTheAxonServerConnectionManager() {
        when(axonServerConnectionManager.getDefaultContext()).thenReturn("test");
        testSubject = new EventProcessorControlService(axonServerConnectionManager, eventProcessorController);
        testSubject.start();

        verify(axonServerConnectionManager).onOutboundInstruction(eq("test"), eq(PAUSE_EVENT_PROCESSOR), any(Consumer.class));
        verify(axonServerConnectionManager).onOutboundInstruction(eq("test"), eq(START_EVENT_PROCESSOR), any(Consumer.class));
        verify(axonServerConnectionManager).onOutboundInstruction(eq("test"), eq(RELEASE_SEGMENT), any(Consumer.class));
        verify(axonServerConnectionManager).onOutboundInstruction(eq("test"), eq(REQUEST_EVENT_PROCESSOR_INFO), any(Consumer.class));
        verify(axonServerConnectionManager).onOutboundInstruction(eq("test"), eq(SPLIT_EVENT_PROCESSOR_SEGMENT), any(Consumer.class));
        verify(axonServerConnectionManager).onOutboundInstruction(eq("test"), eq(MERGE_EVENT_PROCESSOR_SEGMENT), any(Consumer.class));
        verify(axonServerConnectionManager, atLeastOnce()).getDefaultContext();
        verifyNoMoreInteractions(axonServerConnectionManager);
    }
}
