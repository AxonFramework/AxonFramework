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

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.DefaultCommandBusSpanFactory;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.DefaultEventBusSpanFactory;
import org.axonframework.tracing.IntermediateSpanFactoryTest;
import org.axonframework.tracing.SpanFactory;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;

class DefaultQueryBusSpanFactoryTest extends
        IntermediateSpanFactoryTest<DefaultQueryBusSpanFactory.Builder, DefaultQueryBusSpanFactory> {


    @Test
    void createsQuerySpanNonDistributed() {
        QueryMessage<?, ?> queryMessage = Mockito.mock(QueryMessage.class);
        test(spanFactory -> spanFactory.createQuerySpan(queryMessage, false),
             expectedSpan("QueryBus.query", TestSpanFactory.TestSpanType.INTERNAL)
                     .withMessage(queryMessage)
        );
    }

    @Test
    void createsQuerySpanDistributed() {
        QueryMessage<?, ?> queryMessage = Mockito.mock(QueryMessage.class);
        test(spanFactory -> spanFactory.createQuerySpan(queryMessage, true),
             expectedSpan("QueryBus.queryDistributed", TestSpanFactory.TestSpanType.DISPATCH)
                     .withMessage(queryMessage)
        );
    }

    @Test
    void createsSubscriptionQuerySpanNonDistributed() {
        SubscriptionQueryMessage<?, ?, ?> queryMessage = Mockito.mock(SubscriptionQueryMessage.class);
        test(spanFactory -> spanFactory.createSubscriptionQuerySpan(queryMessage, false),
             expectedSpan("QueryBus.subscriptionQuery", TestSpanFactory.TestSpanType.INTERNAL)
                     .withMessage(queryMessage)
        );
    }

    @Test
    void createsSubscriptionQuerySpanDistributed() {
        SubscriptionQueryMessage<?, ?, ?> queryMessage = Mockito.mock(SubscriptionQueryMessage.class);
        test(spanFactory -> spanFactory.createSubscriptionQuerySpan(queryMessage, true),
             expectedSpan("QueryBus.subscriptionQueryDistributed", TestSpanFactory.TestSpanType.DISPATCH)
                     .withMessage(queryMessage)
        );
    }

    @Test
    void createsScatterGatherQuerySpanNonDistributed() {
        QueryMessage<?, ?> queryMessage = Mockito.mock(QueryMessage.class);
        test(spanFactory -> spanFactory.createScatterGatherSpan(queryMessage, false),
             expectedSpan("QueryBus.scatterGatherQuery", TestSpanFactory.TestSpanType.INTERNAL)
                     .withMessage(queryMessage)
        );
    }

    @Test
    void createsScatterGatherQuerySpanDistributed() {
        QueryMessage<?, ?> queryMessage = Mockito.mock(QueryMessage.class);
        test(spanFactory -> spanFactory.createScatterGatherSpan(queryMessage, true),
             expectedSpan("QueryBus.scatterGatherQueryDistributed", TestSpanFactory.TestSpanType.DISPATCH)
                     .withMessage(queryMessage)
        );
    }

    @Test
    void createsScatterGatherHandlerSpan() {
        SubscriptionQueryMessage<?, ?, ?> queryMessage = Mockito.mock(SubscriptionQueryMessage.class);
        test(spanFactory -> spanFactory.createScatterGatherHandlerSpan(queryMessage, 4),
             expectedSpan("QueryBus.scatterGatherHandler-4", TestSpanFactory.TestSpanType.INTERNAL)
                     .withMessage(queryMessage)
        );
    }

    @Test
    void createsStreamingQuerySpanNonDistributed() {
        QueryMessage<?, ?> queryMessage = Mockito.mock(QueryMessage.class);
        test(spanFactory -> spanFactory.createStreamingQuerySpan(queryMessage, false),
             expectedSpan("QueryBus.streamingQuery", TestSpanFactory.TestSpanType.HANDLER_CHILD)
                     .withMessage(queryMessage)
        );
    }

    @Test
    void createsStreamingQuerySpanDistributed() {
        QueryMessage<?, ?> queryMessage = Mockito.mock(QueryMessage.class);
        test(spanFactory -> spanFactory.createStreamingQuerySpan(queryMessage, true),
             expectedSpan("QueryBus.streamingQueryDistributed", TestSpanFactory.TestSpanType.DISPATCH)
                     .withMessage(queryMessage)
        );
    }

    @Test
    void createsQueryProcessingSpanDistributed() {
        QueryMessage<?, ?> queryMessage = Mockito.mock(QueryMessage.class);
        test(builder -> builder.distributedInSameTrace(true),
             spanFactory -> spanFactory.createQueryProcessingSpan(queryMessage),
             expectedSpan("QueryBus.processQueryMessage", TestSpanFactory.TestSpanType.HANDLER_CHILD)
                     .withMessage(queryMessage)
        );
    }

    @Test
    void createsQueryProcessingSpanDistributedButSeparateTrace() {
        QueryMessage<?, ?> queryMessage = Mockito.mock(QueryMessage.class);
        test(builder -> builder.distributedInSameTrace(false),
             spanFactory -> spanFactory.createQueryProcessingSpan(queryMessage),
             expectedSpan("QueryBus.processQueryMessage", TestSpanFactory.TestSpanType.HANDLER_LINK)
                     .withMessage(queryMessage)
        );
    }

    @Test
    void createsResponseProcessingSpan() {
        QueryMessage<?, ?> queryMessage = Mockito.mock(QueryMessage.class);
        test(spanFactory -> spanFactory.createResponseProcessingSpan(queryMessage),
             expectedSpan("QueryBus.processQueryResponse", TestSpanFactory.TestSpanType.INTERNAL)
                     .withMessage(queryMessage)
        );
    }

    @Test
    void propagateContext() {
        QueryMessage<?, ?> queryMessage = Mockito.mock(QueryMessage.class);
        testContextPropagation(queryMessage, DefaultQueryBusSpanFactory::propagateContext);
    }

    @Override
    protected DefaultQueryBusSpanFactory.Builder createBuilder(SpanFactory spanFactory) {
        return DefaultQueryBusSpanFactory.builder().spanFactory(spanFactory);
    }

    @Override
    protected DefaultQueryBusSpanFactory createFactoryBasedOnBuilder(
            DefaultQueryBusSpanFactory.Builder builder) {
        return builder.build();
    }
}