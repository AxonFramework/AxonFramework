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

import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.InstructionResult;
import io.axoniq.axonserver.grpc.control.EventProcessorSegmentReference;
import io.axoniq.axonserver.grpc.control.PlatformInboundInstruction;
import io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction;
import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

import static io.axoniq.axonserver.grpc.control.PlatformOutboundInstruction.RequestCase.*;
import static org.axonframework.axonserver.connector.TestTargetContextResolver.BOUNDED_CONTEXT;
import static org.mockito.Mockito.*;

class EventProcessorControlServiceTest {

    private static final String GOOD_INSTRUCTION_ID = "goodInstructionId";
    private static final String TRACKING_EVENT_PROCESSOR = "TEP";
    private static final String BAD_INSTRUCTION_ID = "badInstructionId";
    private static final String SUBSCRIBING_EVENT_PROCESSOR = "SEP";
    private final AxonServerConfiguration configuration = mock(AxonServerConfiguration.class);
    private final AxonServerConnectionManager axonServerConnectionManager = mock(AxonServerConnectionManager.class);
    private final EventProcessorController eventProcessorController = mock(EventProcessorController.class);
    private final AtomicReference<Consumer<PlatformOutboundInstruction>> splitRequestConsumer = new AtomicReference<>();
    private final AtomicReference<Consumer<PlatformOutboundInstruction>> mergeRequestConsumer = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        when(configuration.getContext()).thenReturn(BOUNDED_CONTEXT);

        doAnswer(invocation -> {
            splitRequestConsumer.set(invocation.getArgument(2));
            return null;
        }).when(axonServerConnectionManager)
          .onOutboundInstruction(eq(BOUNDED_CONTEXT), eq(SPLIT_EVENT_PROCESSOR_SEGMENT), any(Consumer.class));

        doAnswer(invocation -> {
            mergeRequestConsumer.set(invocation.getArgument(2));
            return null;
        }).when(axonServerConnectionManager)
          .onOutboundInstruction(eq(BOUNDED_CONTEXT), eq(MERGE_EVENT_PROCESSOR_SEGMENT), any(Consumer.class));

        EventProcessorControlService testSubject =
                new EventProcessorControlService(axonServerConnectionManager,
                                                 eventProcessorController,
                                                 configuration);
        testSubject.start();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testStartAddOutboundInstructionToTheAxonServerConnectionManager() {
        verify(axonServerConnectionManager).onOutboundInstruction(
                eq(BOUNDED_CONTEXT), eq(PAUSE_EVENT_PROCESSOR), any(Consumer.class)
        );
        verify(axonServerConnectionManager).onOutboundInstruction(
                eq(BOUNDED_CONTEXT), eq(START_EVENT_PROCESSOR), any(Consumer.class)
        );
        verify(axonServerConnectionManager).onOutboundInstruction(
                eq(BOUNDED_CONTEXT), eq(RELEASE_SEGMENT), any(Consumer.class)
        );
        verify(axonServerConnectionManager).onOutboundInstruction(
                eq(BOUNDED_CONTEXT), eq(REQUEST_EVENT_PROCESSOR_INFO), any(Consumer.class)
        );
        verify(axonServerConnectionManager).onOutboundInstruction(
                eq(BOUNDED_CONTEXT), eq(SPLIT_EVENT_PROCESSOR_SEGMENT), any(Consumer.class)
        );
        verify(axonServerConnectionManager).onOutboundInstruction(
                eq(BOUNDED_CONTEXT), eq(MERGE_EVENT_PROCESSOR_SEGMENT), any(Consumer.class)
        );
        verify(configuration, atLeastOnce()).getContext();
        verifyNoMoreInteractions(axonServerConnectionManager);
    }

    @Test
    void testSuccessSplit() {
        when(eventProcessorController.splitSegment(eq(TRACKING_EVENT_PROCESSOR), eq(0))).thenReturn(true);
        splitRequestConsumer.get().accept(PlatformOutboundInstruction.newBuilder()
                                                                     .setInstructionId(GOOD_INSTRUCTION_ID)
                                                                     .setSplitEventProcessorSegment(tep())
                                                                     .build());

        PlatformInboundInstruction successResult = successResult();
        verify(axonServerConnectionManager).send(eq(BOUNDED_CONTEXT), eq(successResult));
    }

    @Test
    void testFailedSplit() {
        splitRequestConsumer.get().accept(PlatformOutboundInstruction.newBuilder()
                                                                     .setInstructionId(BAD_INSTRUCTION_ID)
                                                                     .setSplitEventProcessorSegment(sep())
                                                                     .build());
        PlatformInboundInstruction failureResult = failureResult();
        verify(axonServerConnectionManager).send(eq(BOUNDED_CONTEXT), eq(failureResult));
    }

    @Test
    void testSuccessMerge() {
        when(eventProcessorController.mergeSegment(eq(TRACKING_EVENT_PROCESSOR), eq(0))).thenReturn(true);
        mergeRequestConsumer.get().accept(PlatformOutboundInstruction.newBuilder()
                                                                     .setInstructionId("goodInstructionId")
                                                                     .setMergeEventProcessorSegment(tep())
                                                                     .build());
        verify(axonServerConnectionManager).send(eq(BOUNDED_CONTEXT), eq(successResult()));
    }

    @Test
    void testFailedMerge() {
        splitRequestConsumer.get().accept(PlatformOutboundInstruction
                                                  .newBuilder()
                                                  .setInstructionId(BAD_INSTRUCTION_ID)
                                                  .setSplitEventProcessorSegment(sep())
                                                  .build());
        verify(axonServerConnectionManager).send(eq(BOUNDED_CONTEXT), eq(failureResult()));
    }

    @Nonnull
    private EventProcessorSegmentReference tep() {
        return EventProcessorSegmentReference
                .newBuilder()
                .setProcessorName(TRACKING_EVENT_PROCESSOR)
                .setSegmentIdentifier(0)
                .build();
    }

    @Nonnull
    private PlatformInboundInstruction successResult() {
        return PlatformInboundInstruction
                .newBuilder()
                .setResult(InstructionResult
                                   .newBuilder()
                                   .setSuccess(true)
                                   .setInstructionId(GOOD_INSTRUCTION_ID)

                ).build();
    }

    @Nonnull
    private PlatformInboundInstruction failureResult() {
        return PlatformInboundInstruction
                .newBuilder()
                .setResult(InstructionResult
                                   .newBuilder()
                                   .setSuccess(false)
                                   .setInstructionId(BAD_INSTRUCTION_ID)
                                   .setError(ErrorMessage.newBuilder()
                                                         .setMessage("Failed to split segment [0] for processor [SEP]")
                                                         .setErrorCode("AXONIQ-1004")
                                                         .addDetails("Failed to split segment [0] for processor [SEP]")
                                   )
                )
                .build();
    }

    @Nonnull
    private EventProcessorSegmentReference sep() {
        return EventProcessorSegmentReference
                .newBuilder().setProcessorName(SUBSCRIBING_EVENT_PROCESSOR).build();
    }
}
