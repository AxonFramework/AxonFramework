/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.eventhandling;

import org.axonframework.tracing.IntermediateSpanFactoryTest;
import org.axonframework.tracing.SpanFactory;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

import static org.mockito.Mockito.*;

class DefaultEventProcessorSpanFactoryTest extends
        IntermediateSpanFactoryTest<DefaultEventProcessorSpanFactory.Builder, DefaultEventProcessorSpanFactory> {

    @Test
    void createBatchSpanWithDefaultsForNonStreaming() {
        test(
                factory -> factory.createBatchSpan(false, Collections.emptyList()),
                noOpSpan()
        );
    }

    @Test
    void createBatchSpanWithDefaultsForStreaming() {
        test(
                factory -> factory.createBatchSpan(true, Collections.emptyList()),
                expectedSpan("StreamingEventProcessor.batch", TestSpanFactory.TestSpanType.ROOT)
        );
    }

    @Test
    void createBatchSpanWithDisabledBatchSpanForNonStreaming() {
        test(
                builder -> builder.disableBatchTrace(true),
                factory -> factory.createBatchSpan(false, Collections.emptyList()),
                noOpSpan()
        );
    }

    @Test
    void createBatchSpanWithDisabledBatchSpanForStreaming() {
        test(
                builder -> builder.disableBatchTrace(true),
                factory -> factory.createBatchSpan(true, Collections.emptyList()),
                noOpSpan()
        );
    }

    @Test
    void createHandleEventSpanWithDefaultsForNonStreaming() {
        EventMessage<?> eventMessage = Mockito.mock(EventMessage.class);
        test(
                factory -> factory.createHandleEventSpan(false, eventMessage),
                expectedSpan("EventProcessor.handle", TestSpanFactory.TestSpanType.HANDLER_CHILD)
                        .withMessage(eventMessage)
        );
    }

    @Test
    void createHandleEventSpanWithDefaultsForStreaming() {
        EventMessage<?> eventMessage = Mockito.mock(EventMessage.class);
        test(
                factory -> factory.createHandleEventSpan(true, eventMessage),
                expectedSpan("StreamingEventProcessor.handle", TestSpanFactory.TestSpanType.HANDLER_CHILD)
                        .withMessage(eventMessage)
        );
    }

    @Test
    void createHandleEventSpanWithDisabledBatchSpanForNonStreaming() {
        EventMessage<?> eventMessage = Mockito.mock(EventMessage.class);
        test(
                builder -> builder.disableBatchTrace(true),
                factory -> factory.createHandleEventSpan(false, eventMessage),
                expectedSpan("EventProcessor.handle", TestSpanFactory.TestSpanType.HANDLER_CHILD)
                        .withMessage(eventMessage)
        );
    }

    @Test
    void createHandleEventSpanWithDisabledBatchSpanForStreaming() {
        EventMessage<?> eventMessage = Mockito.mock(EventMessage.class);
        test(
                builder -> builder.disableBatchTrace(true),
                factory -> factory.createHandleEventSpan(true, eventMessage),
                expectedSpan("StreamingEventProcessor.handle", TestSpanFactory.TestSpanType.HANDLER_LINK)
                        .withMessage(eventMessage)
        );
    }

    @Test
    void createHandleEventSpanWithDistributedInSameTraceWithRecentMessage() {
        EventMessage<?> eventMessage = Mockito.mock(EventMessage.class);
        when(eventMessage.getTimestamp()).thenReturn(Instant.now());
        test(
                builder -> builder.distributedInSameTrace(true),
                factory -> factory.createHandleEventSpan(true, eventMessage),
                expectedSpan("StreamingEventProcessor.handle", TestSpanFactory.TestSpanType.HANDLER_CHILD)
                        .withMessage(eventMessage)
        );
    }
    @Test
    void createHandleEventSpanWithDistributedInSameTraceWithOldMessage() {
        EventMessage<?> eventMessage = Mockito.mock(EventMessage.class);
        when(eventMessage.getTimestamp()).thenReturn(Instant.now().minus(Duration.ofSeconds(600)));
        test(
                builder -> builder.distributedInSameTrace(true).distributedInSameTraceTimeLimit(Duration.ofSeconds(500)),
                factory -> factory.createHandleEventSpan(true, eventMessage),
                expectedSpan("StreamingEventProcessor.handle", TestSpanFactory.TestSpanType.HANDLER_CHILD)
                        .withMessage(eventMessage)
        );
    }

    @Test
    void createProcessSpanWithDefaultsForNonStreaming() {
        EventMessage<?> eventMessage = Mockito.mock(EventMessage.class);
        test(
                factory -> factory.createProcesEventSpan(eventMessage),
                expectedSpan("EventProcessor.process", TestSpanFactory.TestSpanType.INTERNAL)
                        .withMessage(eventMessage)
        );
    }


    @Override
    protected DefaultEventProcessorSpanFactory.Builder createBuilder(SpanFactory spanFactory) {
        return DefaultEventProcessorSpanFactory.builder().spanFactory(spanFactory);
    }

    @Override
    protected DefaultEventProcessorSpanFactory createFactoryBasedOnBuilder(
            DefaultEventProcessorSpanFactory.Builder builder) {
        return builder.build();
    }
}