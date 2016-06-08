/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test;


import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventsourcing.AggregateIdentifier;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.messaging.metadata.SimpleCorrelationDataProvider;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.axonframework.test.FixtureTest_CommandInterceptors.InterceptorAggregate.AGGREGATE_IDENTIFIER;
import static org.axonframework.test.FixtureTest_CommandInterceptors.TestCommandDispatchInterceptor.DISPATCH_META_DATA_KEY;
import static org.axonframework.test.FixtureTest_CommandInterceptors.TestCommandDispatchInterceptor.DISPATCH_META_DATA_VALUE;
import static org.axonframework.test.FixtureTest_CommandInterceptors.TestCommandHandlerInterceptor.HANDLER_META_DATA_KEY;
import static org.axonframework.test.FixtureTest_CommandInterceptors.TestCommandHandlerInterceptor.HANDLER_META_DATA_VALUE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FixtureTest_CommandInterceptors {

    private FixtureConfiguration<InterceptorAggregate> fixture;

    @Mock
    private MessageDispatchInterceptor<CommandMessage<?>> firstMockCommandDispatchInterceptor;
    @Mock
    private MessageDispatchInterceptor<CommandMessage<?>> secondMockCommandDispatchInterceptor;
    @Mock
    private MessageHandlerInterceptor<CommandMessage<?>> mockCommandHandlerInterceptor;

    @Before
    public void setUp() throws Exception {
        fixture = Fixtures.newGivenWhenThenFixture(InterceptorAggregate.class);
    }

    @Test
    public void testRegisteredCommandDispatchInterceptorsAreInvoked() throws Exception {
        when(firstMockCommandDispatchInterceptor.handle(any(CommandMessage.class)))
                .thenAnswer(it -> it.getArguments()[0]);
        fixture.registerCommandDispatchInterceptor(firstMockCommandDispatchInterceptor);
        when(secondMockCommandDispatchInterceptor.handle(any(CommandMessage.class)))
                .thenAnswer(it -> it.getArguments()[0]);
        fixture.registerCommandDispatchInterceptor(secondMockCommandDispatchInterceptor);

        TestCommand expectedCommand = new TestCommand(AGGREGATE_IDENTIFIER);
        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
                .when(expectedCommand);

        ArgumentCaptor<GenericCommandMessage> firstCommandMessageCaptor =
                ArgumentCaptor.forClass(GenericCommandMessage.class);
        verify(firstMockCommandDispatchInterceptor).handle(firstCommandMessageCaptor.capture());
        GenericCommandMessage firstResult = firstCommandMessageCaptor.getValue();
        assertEquals(expectedCommand, firstResult.getPayload());

        ArgumentCaptor<GenericCommandMessage> secondCommandMessageCaptor =
                ArgumentCaptor.forClass(GenericCommandMessage.class);
        verify(secondMockCommandDispatchInterceptor).handle(secondCommandMessageCaptor.capture());
        GenericCommandMessage secondResult = secondCommandMessageCaptor.getValue();
        assertEquals(expectedCommand, secondResult.getPayload());
    }

    @Test
    public void testRegisteredCommandDispatchInterceptorIsInvokedAndAltersAppliedEvent() throws Exception {
        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
                .when(new TestCommand(AGGREGATE_IDENTIFIER))
                .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, Collections.emptyMap()));

        fixture.registerCommandDispatchInterceptor(new TestCommandDispatchInterceptor());

        MetaData expectedValues =
                new MetaData(Collections.singletonMap(DISPATCH_META_DATA_KEY, DISPATCH_META_DATA_VALUE));

        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
                .when(new TestCommand(AGGREGATE_IDENTIFIER))
                .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, expectedValues));
    }

    @Test
    public void testRegisteredCommandDispatchInterceptorIsInvokedForFixtureMethodsGivenCommands()
            throws Exception {
        fixture.registerCommandDispatchInterceptor(new TestCommandDispatchInterceptor());

        MetaData expectedValues =
                new MetaData(Collections.singletonMap(DISPATCH_META_DATA_KEY, DISPATCH_META_DATA_VALUE));

        fixture.givenCommands(new CreateStandardAggregateCommand(AGGREGATE_IDENTIFIER))
                .when(new TestCommand(AGGREGATE_IDENTIFIER))
                .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, expectedValues));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRegisteredCommandHandlerInterceptorsAreInvoked() throws Exception {
        fixture.registerCommandHandlerInterceptor(new TestCommandHandlerInterceptor());
        when(mockCommandHandlerInterceptor.handle(any(UnitOfWork.class), any(InterceptorChain.class)))
                .thenAnswer(InvocationOnMock::getArguments);
        fixture.registerCommandHandlerInterceptor(mockCommandHandlerInterceptor);

        TestCommand expectedCommand = new TestCommand(AGGREGATE_IDENTIFIER);
        Map<String, Object> expectedMetaDataMap = new HashMap<>();
        expectedMetaDataMap.put(HANDLER_META_DATA_KEY, HANDLER_META_DATA_VALUE);

        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
                .when(expectedCommand, expectedMetaDataMap);

        ArgumentCaptor<UnitOfWork> unitOfWorkCaptor = ArgumentCaptor.forClass(UnitOfWork.class);
        ArgumentCaptor<InterceptorChain> interceptorChainCaptor = ArgumentCaptor.forClass(InterceptorChain.class);
        verify(mockCommandHandlerInterceptor).handle(unitOfWorkCaptor.capture(), interceptorChainCaptor.capture());
        UnitOfWork unitOfWorkResult = unitOfWorkCaptor.getValue();
        Message messageResult = unitOfWorkResult.getMessage();
        assertEquals(expectedCommand, messageResult.getPayload());
        assertEquals(expectedMetaDataMap, messageResult.getMetaData());
    }

    @Test
    public void testRegisteredCommandHandlerInterceptorIsInvokedAndAltersEvent() throws Exception {
        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
                .when(new TestCommand(AGGREGATE_IDENTIFIER))
                .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, Collections.emptyMap()));

        fixture.registerCommandHandlerInterceptor(new TestCommandHandlerInterceptor());

        Map<String, Object> expectedMetaDataMap = new HashMap<>();
        expectedMetaDataMap.put(HANDLER_META_DATA_KEY, HANDLER_META_DATA_VALUE);

        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
                .when(new TestCommand(AGGREGATE_IDENTIFIER), expectedMetaDataMap)
                .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, expectedMetaDataMap));
    }

    @Test
    public void testRegisteredCommandHandlerInterceptorIsInvokedForFixtureMethodsGivenCommands() throws Exception {
        fixture.registerCommandHandlerInterceptor(new TestCommandHandlerInterceptor());

        Map<String, Object> expectedMetaDataMap = new HashMap<>();
        expectedMetaDataMap.put(HANDLER_META_DATA_KEY, HANDLER_META_DATA_VALUE);

        fixture.givenCommands(new CreateStandardAggregateCommand(AGGREGATE_IDENTIFIER))
                .when(new TestCommand(AGGREGATE_IDENTIFIER), expectedMetaDataMap)
                .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, expectedMetaDataMap));
    }

    @Test
    public void testRegisteredCommandDispatchAndHandlerInterceptorAreBothInvokedAndAlterEvent() throws Exception {
        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
                .when(new TestCommand(AGGREGATE_IDENTIFIER))
                .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, Collections.emptyMap()));

        fixture.registerCommandDispatchInterceptor(new TestCommandDispatchInterceptor());
        fixture.registerCommandHandlerInterceptor(new TestCommandHandlerInterceptor());

        Map<String, Object> testMetaDataMap = new HashMap<>();
        testMetaDataMap.put(HANDLER_META_DATA_KEY, HANDLER_META_DATA_VALUE);

        Map<String, Object> expectedMetaDataMap = new HashMap<>(testMetaDataMap);
        expectedMetaDataMap.put(DISPATCH_META_DATA_KEY, DISPATCH_META_DATA_VALUE);

        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
                .when(new TestCommand(AGGREGATE_IDENTIFIER), testMetaDataMap)
                .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, new MetaData(expectedMetaDataMap)));
    }

    public static class InterceptorAggregate {

        public static final String AGGREGATE_IDENTIFIER = "id1";

        @SuppressWarnings("UnusedDeclaration")
        private transient int counter;
        private Integer lastNumber;
        @AggregateIdentifier
        private Object identifier;
        private MyEntity entity;

        public InterceptorAggregate() {
        }

        public InterceptorAggregate(Object aggregateIdentifier) {
        }

        @CommandHandler
        public InterceptorAggregate(CreateStandardAggregateCommand cmd) {
            apply(new StandardAggregateCreatedEvent(cmd.getAggregateIdentifier()));
        }

        @SuppressWarnings("UnusedParameters")
        @CommandHandler
        public void handle(TestCommand command, MetaData metaData) {
            apply(new TestEvent(command.getAggregateIdentifier(), metaData));
        }

        @EventHandler
        public void handle(StandardAggregateCreatedEvent event) {
            this.identifier = event.getAggregateIdentifier();
        }

    }

    private static class CreateStandardAggregateCommand {

        private final Object aggregateIdentifier;

        public CreateStandardAggregateCommand(Object aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        public Object getAggregateIdentifier() {
            return aggregateIdentifier;
        }

    }

    private static class StandardAggregateCreatedEvent {

        private final Object aggregateIdentifier;

        public StandardAggregateCreatedEvent(Object aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        public Object getAggregateIdentifier() {
            return aggregateIdentifier;
        }

    }

    class TestCommandDispatchInterceptor implements MessageDispatchInterceptor<CommandMessage<?>> {

        static final String DISPATCH_META_DATA_KEY = "dispatchKey";
        static final String DISPATCH_META_DATA_VALUE = "dispatchValue";

        @Override
        public Function<Integer, CommandMessage<?>> handle(List<CommandMessage<?>> messages) {
            return index -> {
                CommandMessage<?> message = messages.get(index);
                Map<String, Object> testMetaDataMap = new HashMap<>();
                testMetaDataMap.put(DISPATCH_META_DATA_KEY, DISPATCH_META_DATA_VALUE);
                message = message.andMetaData(testMetaDataMap);
                return message;
            };
        }

    }

    class TestCommandHandlerInterceptor implements MessageHandlerInterceptor<CommandMessage<?>> {

        static final String HANDLER_META_DATA_KEY = "handlerKey";
        static final String HANDLER_META_DATA_VALUE = "handlerValue";

        @Override
        public Object handle(UnitOfWork<? extends CommandMessage<?>> unitOfWork, InterceptorChain interceptorChain) throws Exception {
            unitOfWork.registerCorrelationDataProvider(new SimpleCorrelationDataProvider(HANDLER_META_DATA_KEY));
            return interceptorChain.proceed();
        }
    }

}
