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

package org.axonframework.deadline;

import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.tracing.IntermediateSpanFactoryTest;
import org.axonframework.tracing.SpanFactory;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;

class DefaultDeadlineManagerSpanFactoryTest
        extends IntermediateSpanFactoryTest<DefaultDeadlineManagerSpanFactory.Builder, DeadlineManagerSpanFactory> {

    @Test
    void createScheduleSpanWithDefaultSettings() {
        DeadlineMessage<?> message = Mockito.mock(DeadlineMessage.class);
        test(
                spanFactory -> spanFactory.createScheduleSpan("myDeadline", "myDeadlineId", message),
                expectedSpan("DeadlineManager.scheduleDeadline(myDeadline)", TestSpanFactory.TestSpanType.DISPATCH)
                        .withMessage(message)
                        .expectAttribute("axon.deadlineId", "myDeadlineId")
        );
    }

    @Test
    void createScheduleSpanWithModifiedDeadlineIdAtrribute() {
        DeadlineMessage<?> message = Mockito.mock(DeadlineMessage.class);
        test(
                builder -> builder.deadlineIdAttribute("this-is-a-stupidly-long-attribute-name"),
                spanFactory -> spanFactory.createScheduleSpan("myDeadline", "myDeadlineId", message),
                expectedSpan("DeadlineManager.scheduleDeadline(myDeadline)", TestSpanFactory.TestSpanType.DISPATCH)
                        .withMessage(message)
                        .expectAttribute("this-is-a-stupidly-long-attribute-name", "myDeadlineId")
        );
    }

    @Test
    void createCancelScheduleSpanWithDefaultSettings() {
        test(
                spanFactory -> spanFactory.createCancelScheduleSpan("myDeadline", "myDeadlineId"),
                expectedSpan("DeadlineManager.cancelDeadline(myDeadline)", TestSpanFactory.TestSpanType.INTERNAL)
                        .expectAttribute("axon.deadlineId", "myDeadlineId")
        );
    }

    @Test
    void createCancelScheduleSpanWithModifiedDeadlineIdAttribute() {
        test(
                builder -> builder.deadlineIdAttribute("this-is-a-stupidly-long-attribute-name"),
                spanFactory -> spanFactory.createCancelScheduleSpan("myDeadline", "myDeadlineId"),
                expectedSpan("DeadlineManager.cancelDeadline(myDeadline)", TestSpanFactory.TestSpanType.INTERNAL)
                        .expectAttribute("this-is-a-stupidly-long-attribute-name", "myDeadlineId")
        );
    }

    @Test
    void createCancelAllSpanWithDefaultSettings() {
        test(
                spanFactory -> spanFactory.createCancelAllSpan("myDeadline"),
                expectedSpan("DeadlineManager.cancelAllDeadlines(myDeadline)", TestSpanFactory.TestSpanType.INTERNAL)
        );
    }


    @Test
    void createCancelAllWithinScopeSpanWithDefaultSettings() {
        ScopeDescriptor scope = Mockito.mock(ScopeDescriptor.class);
        Mockito.when(scope.scopeDescription()).thenReturn("myScopeDescription");
        test(
                spanFactory -> spanFactory.createCancelAllWithinScopeSpan("myDeadline", scope),
                expectedSpan("DeadlineManager.cancelAllWithinScope(myDeadline)", TestSpanFactory.TestSpanType.INTERNAL)
                        .expectAttribute("axon.deadlineScope", "myScopeDescription")
        );
    }
    @Test
    void createCancelAllWithinScopeSpanWithModifiedScopeAttribute() {
        ScopeDescriptor scope = Mockito.mock(ScopeDescriptor.class);
        Mockito.when(scope.scopeDescription()).thenReturn("myScopeDescription");
        test(
                builder -> builder.scopeAttribute("this-is-a-stupidly-long-attribute-name"),
                spanFactory -> spanFactory.createCancelAllWithinScopeSpan("myDeadline", scope),
                expectedSpan("DeadlineManager.cancelAllWithinScope(myDeadline)", TestSpanFactory.TestSpanType.INTERNAL)
                        .expectAttribute("this-is-a-stupidly-long-attribute-name", "myScopeDescription")
        );
    }

    @Test
    void createExecuteSpanWithDefaultSettings() {
        DeadlineMessage<?> message = Mockito.mock(DeadlineMessage.class);
        test(spanFactory -> spanFactory.createExecuteSpan("myDeadline", "myDeadlineId", message),
                expectedSpan("DeadlineManager.executeDeadline(myDeadline)", TestSpanFactory.TestSpanType.HANDLER_LINK)
                        .withMessage(message)
                        .expectAttribute("axon.deadlineId", "myDeadlineId")
        );
    }

    @Test
    void createExecuteSpanWithModifiedDeadlineIdAtrribute() {
        DeadlineMessage<?> message = Mockito.mock(DeadlineMessage.class);
        test(
                builder -> builder.deadlineIdAttribute("this-is-a-stupidly-long-attribute-name"),
                spanFactory -> spanFactory.createExecuteSpan("myDeadline", "myDeadlineId", message),
                expectedSpan("DeadlineManager.executeDeadline(myDeadline)", TestSpanFactory.TestSpanType.HANDLER_LINK)
                        .withMessage(message)
                        .expectAttribute("this-is-a-stupidly-long-attribute-name", "myDeadlineId")
        );
    }

    @Test
    void propagateContext() {
        DeadlineMessage<?> message = Mockito.mock(DeadlineMessage.class);
        testContextPropagation(message, DeadlineManagerSpanFactory::propagateContext);
    }

    @Override
    protected DefaultDeadlineManagerSpanFactory.Builder createBuilder(SpanFactory spanFactory) {
        return DefaultDeadlineManagerSpanFactory.builder().spanFactory(spanFactory);
    }

    @Override
    protected DeadlineManagerSpanFactory createFactoryBasedOnBuilder(
            DefaultDeadlineManagerSpanFactory.Builder builder) {
        return builder.build();
    }
}