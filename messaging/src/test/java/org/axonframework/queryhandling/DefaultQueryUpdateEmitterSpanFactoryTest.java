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

package org.axonframework.queryhandling;

import org.axonframework.tracing.IntermediateSpanFactoryTest;
import org.axonframework.tracing.SpanFactory;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;

class DefaultQueryUpdateEmitterSpanFactoryTest extends
        IntermediateSpanFactoryTest<DefaultQueryUpdateEmitterSpanFactory.Builder, DefaultQueryUpdateEmitterSpanFactory> {

    @Test
    void createsDefaultScheduleSpan() {
        SubscriptionQueryUpdateMessage<?> message = Mockito.mock(SubscriptionQueryUpdateMessage.class);
        test(spanFactory -> spanFactory.createUpdateScheduleEmitSpan(message),
             expectedSpan("QueryUpdateEmitter.scheduleQueryUpdateMessage", TestSpanFactory.TestSpanType.INTERNAL)
                     .withMessage(message)
        );
    }

    @Test
    void createsDefaultEmitSpan() {
        SubscriptionQueryUpdateMessage<?> message = Mockito.mock(SubscriptionQueryUpdateMessage.class);
        test(spanFactory -> spanFactory.createUpdateEmitSpan(message),
             expectedSpan("QueryUpdateEmitter.emitQueryUpdateMessage", TestSpanFactory.TestSpanType.DISPATCH)
                     .withMessage(message)
        );
    }

    @Test
    void testPropagateContext() {
        SubscriptionQueryUpdateMessage<?> message = Mockito.mock(SubscriptionQueryUpdateMessage.class);
        testContextPropagation(message, DefaultQueryUpdateEmitterSpanFactory::propagateContext);
    }


    @Override
    protected DefaultQueryUpdateEmitterSpanFactory.Builder createBuilder(SpanFactory spanFactory) {
        return DefaultQueryUpdateEmitterSpanFactory.builder().spanFactory(spanFactory);
    }

    @Override
    protected DefaultQueryUpdateEmitterSpanFactory createFactoryBasedOnBuilder(
            DefaultQueryUpdateEmitterSpanFactory.Builder builder) {
        return builder.build();
    }
}