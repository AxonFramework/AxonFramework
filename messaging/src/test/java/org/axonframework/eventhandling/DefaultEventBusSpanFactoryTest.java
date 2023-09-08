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

class DefaultEventBusSpanFactoryTest extends IntermediateSpanFactoryTest<DefaultEventBusSpanFactory.Builder, DefaultEventBusSpanFactory> {

    @Test
    void createCommitEventsSpan() {
        test(builder -> builder,
             DefaultEventBusSpanFactory::createCommitEventsSpan,
             expectedSpan("EventBus.commitEvents", TestSpanFactory.TestSpanType.INTERNAL)
        );
    }

    @Test
    void createsQuerySpanNonDistributed() {
        EventMessage<?> eventMessage = Mockito.mock(EventMessage.class);
        test(builder -> builder,
             factory -> factory.createPublishEventSpan(eventMessage),
             expectedSpan("EventBus.publishEvent", TestSpanFactory.TestSpanType.DISPATCH)
                     .withMessage(eventMessage)
        );
    }


    @Override
    protected DefaultEventBusSpanFactory.Builder createBuilder(SpanFactory spanFactory) {
        return DefaultEventBusSpanFactory.builder().spanFactory(spanFactory);
    }

    @Override
    protected DefaultEventBusSpanFactory createFactoryBasedOnBuilder(DefaultEventBusSpanFactory.Builder builder) {
        return builder.build();
    }
}