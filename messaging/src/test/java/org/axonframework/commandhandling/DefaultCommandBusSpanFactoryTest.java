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

package org.axonframework.commandhandling;

import org.axonframework.tracing.IntermediateSpanFactoryTest;
import org.axonframework.tracing.SpanFactory;
import org.axonframework.tracing.TestSpanFactory;
import org.junit.jupiter.api.*;

class DefaultCommandBusSpanFactoryTest
        extends IntermediateSpanFactoryTest<DefaultCommandBusSpanFactory.Builder, DefaultCommandBusSpanFactory> {

    @Test
    void createLocalDispatchCommand() {
        CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("MyCommand");
        test(spanFactory -> spanFactory.createDispatchCommandSpan(command, false),
             expectedSpan("CommandBus.dispatchCommand", TestSpanFactory.TestSpanType.INTERNAL)
                     .withMessage(command)
        );
    }

    @Test
    void createDistributedDispatchCommand() {
        CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("MyCommand");
        test(spanFactory -> spanFactory.createDispatchCommandSpan(command, true),
             expectedSpan("CommandBus.dispatchDistributedCommand", TestSpanFactory.TestSpanType.DISPATCH)
                     .withMessage(command)
        );
    }


    @Test
    void createLocalHandleCommand() {
        CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("MyCommand");
        test(spanFactory -> spanFactory.createHandleCommandSpan(command, false),
             expectedSpan("CommandBus.handleCommand", TestSpanFactory.TestSpanType.HANDLER_CHILD)
                     .withMessage(command)
        );
    }

    @Test
    void createDistributedHandleCommandDefault() {
        CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("MyCommand");
        test(spanFactory -> spanFactory.createHandleCommandSpan(command, true),
             expectedSpan("CommandBus.handleDistributedCommand", TestSpanFactory.TestSpanType.HANDLER_CHILD)
                     .withMessage(command)
        );
    }

    @Test
    void createDistributedHandleCommandDistributedNotSameTrace() {
        CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("MyCommand");
        test(builder -> builder.distributedInSameTrace(false),
             spanFactory -> spanFactory.createHandleCommandSpan(command, true),
             expectedSpan("CommandBus.handleDistributedCommand", TestSpanFactory.TestSpanType.HANDLER_LINK)
                     .withMessage(command)
        );
    }

    @Test
    void propagateContext() {
        CommandMessage<Object> command = GenericCommandMessage.asCommandMessage("MyCommand");
        testContextPropagation(command, DefaultCommandBusSpanFactory::propagateContext);
    }

    @Override
    protected DefaultCommandBusSpanFactory.Builder createBuilder(SpanFactory spanFactory) {
        return DefaultCommandBusSpanFactory.builder().spanFactory(spanFactory);
    }

    @Override
    protected DefaultCommandBusSpanFactory createFactoryBasedOnBuilder(DefaultCommandBusSpanFactory.Builder builder) {
        return builder.build();
    }
}