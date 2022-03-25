/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.test.aggregate;


import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.correlation.SimpleCorrelationDataProvider;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.junit.jupiter.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import javax.annotation.Nonnull;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.axonframework.test.aggregate.FixtureTest_CommandInterceptors.InterceptorAggregate.AGGREGATE_IDENTIFIER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the registration of {@link MessageDispatchInterceptor} and {@link MessageHandlerInterceptor}
 * for {@link CommandMessage}s within an {@link AggregateTestFixture}.
 *
 * @author Steven van Beelen
 */
@ExtendWith(MockitoExtension.class)
class FixtureTest_CommandInterceptors {

    private static final String DISPATCH_META_DATA_KEY = "dispatchKey";
    private static final String DISPATCH_META_DATA_VALUE = "dispatchValue";
    private static final String HANDLER_META_DATA_KEY = "handlerKey";
    private static final String HANDLER_META_DATA_VALUE = "handlerValue";

    private FixtureConfiguration<InterceptorAggregate> fixture;

    @Mock
    private MessageDispatchInterceptor<CommandMessage<?>> firstMockCommandDispatchInterceptor;
    @Mock
    private MessageDispatchInterceptor<CommandMessage<?>> secondMockCommandDispatchInterceptor;
    @Mock
    private MessageHandlerInterceptor<CommandMessage<?>> mockCommandHandlerInterceptor;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(InterceptorAggregate.class);
    }

    @Test
    void testRegisteredCommandDispatchInterceptorsAreInvoked() {
        when(firstMockCommandDispatchInterceptor.handle(any(CommandMessage.class)))
                .thenAnswer(it -> it.getArguments()[0]);
        fixture.registerCommandDispatchInterceptor(firstMockCommandDispatchInterceptor);
        when(secondMockCommandDispatchInterceptor.handle(any(CommandMessage.class)))
                .thenAnswer(it -> it.getArguments()[0]);
        fixture.registerCommandDispatchInterceptor(secondMockCommandDispatchInterceptor);

        TestCommand expectedCommand = new TestCommand(AGGREGATE_IDENTIFIER);
        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
               .when(expectedCommand);

        //noinspection unchecked
        ArgumentCaptor<GenericCommandMessage<?>> firstCommandMessageCaptor =
                ArgumentCaptor.forClass(GenericCommandMessage.class);
        verify(firstMockCommandDispatchInterceptor).handle(firstCommandMessageCaptor.capture());
        GenericCommandMessage<?> firstResult = firstCommandMessageCaptor.getValue();
        assertEquals(expectedCommand, firstResult.getPayload());

        //noinspection unchecked
        ArgumentCaptor<GenericCommandMessage<?>> secondCommandMessageCaptor =
                ArgumentCaptor.forClass(GenericCommandMessage.class);
        verify(secondMockCommandDispatchInterceptor).handle(secondCommandMessageCaptor.capture());
        GenericCommandMessage<?> secondResult = secondCommandMessageCaptor.getValue();
        assertEquals(expectedCommand, secondResult.getPayload());
    }

    @Test
    void testRegisteredCommandDispatchInterceptorIsInvokedAndAltersAppliedEvent() {
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
    void testRegisteredCommandDispatchInterceptorIsInvokedForFixtureMethodsGivenCommands() {
        fixture.registerCommandDispatchInterceptor(new TestCommandDispatchInterceptor());

        MetaData expectedValues =
                new MetaData(Collections.singletonMap(DISPATCH_META_DATA_KEY, DISPATCH_META_DATA_VALUE));

        fixture.givenCommands(new CreateStandardAggregateCommand(AGGREGATE_IDENTIFIER))
               .when(new TestCommand(AGGREGATE_IDENTIFIER))
               .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, expectedValues));
    }

    @Test
    void testRegisteredCommandHandlerInterceptorsAreInvoked() throws Exception {
        fixture.registerCommandHandlerInterceptor(new TestCommandHandlerInterceptor());
        //noinspection unchecked
        when(mockCommandHandlerInterceptor.handle(any(UnitOfWork.class), any(InterceptorChain.class)))
                .thenAnswer(InvocationOnMock::getArguments);
        fixture.registerCommandHandlerInterceptor(mockCommandHandlerInterceptor);

        TestCommand expectedCommand = new TestCommand(AGGREGATE_IDENTIFIER);
        Map<String, Object> expectedMetaDataMap = new HashMap<>();
        expectedMetaDataMap.put(HANDLER_META_DATA_KEY, HANDLER_META_DATA_VALUE);

        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
               .when(expectedCommand, expectedMetaDataMap);

        //noinspection unchecked
        ArgumentCaptor<UnitOfWork<? extends CommandMessage<?>>> unitOfWorkCaptor =
                ArgumentCaptor.forClass(UnitOfWork.class);
        ArgumentCaptor<InterceptorChain> interceptorChainCaptor = ArgumentCaptor.forClass(InterceptorChain.class);
        verify(mockCommandHandlerInterceptor).handle(unitOfWorkCaptor.capture(), interceptorChainCaptor.capture());
        UnitOfWork<? extends CommandMessage<?>> unitOfWorkResult = unitOfWorkCaptor.getValue();
        Message<?> messageResult = unitOfWorkResult.getMessage();
        assertEquals(expectedCommand, messageResult.getPayload());
        assertEquals(expectedMetaDataMap, messageResult.getMetaData());
    }

    @Test
    void testRegisteredCommandHandlerInterceptorIsInvokedAndAltersEvent() {
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
    void testRegisteredCommandHandlerInterceptorIsInvokedForFixtureMethodsGivenCommands() {
        fixture.registerCommandHandlerInterceptor(new TestCommandHandlerInterceptor());

        Map<String, Object> expectedMetaDataMap = new HashMap<>();
        expectedMetaDataMap.put(HANDLER_META_DATA_KEY, HANDLER_META_DATA_VALUE);

        fixture.givenCommands(new CreateStandardAggregateCommand(AGGREGATE_IDENTIFIER))
               .when(new TestCommand(AGGREGATE_IDENTIFIER), expectedMetaDataMap)
               .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, expectedMetaDataMap));
    }

    @Test
    void testRegisteredCommandDispatchAndHandlerInterceptorAreBothInvokedAndAlterEvent() {
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

    @Test
    void testRegisteredHandlerInterceptorIsInvokedOnceOnGivenCommandsTestExecution() {
        AtomicInteger invocations = new AtomicInteger(0);
        fixture.registerCommandHandlerInterceptor((unitOfWork, interceptorChain) -> {
            invocations.incrementAndGet();
            interceptorChain.proceed();
            return null;
        });

        fixture.givenCommands(new CreateStandardAggregateCommand(AGGREGATE_IDENTIFIER))
               .when(new TestCommand(AGGREGATE_IDENTIFIER))
               .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, MetaData.emptyInstance()))
               .expectSuccessfulHandlerExecution();

        assertEquals(2, invocations.get());
    }

    @Test
    void testRegisteredDispatchInterceptorIsInvokedOnceOnGivenCommandsTestExecution() {
        AtomicInteger invocations = new AtomicInteger(0);
        fixture.registerCommandDispatchInterceptor(messages -> (i, command) -> {
            invocations.incrementAndGet();
            return command;
        });

        fixture.givenCommands(new CreateStandardAggregateCommand(AGGREGATE_IDENTIFIER))
               .when(new TestCommand(AGGREGATE_IDENTIFIER))
               .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, MetaData.emptyInstance()))
               .expectSuccessfulHandlerExecution();

        assertEquals(2, invocations.get());
    }

    @SuppressWarnings("unused")
    public static class InterceptorAggregate {

        public static final String AGGREGATE_IDENTIFIER = "id1";

        @SuppressWarnings("UnusedDeclaration")
        private transient int counter;
        private Integer lastNumber;
        @AggregateIdentifier
        private String identifier;
        private MyEntity entity;

        public InterceptorAggregate() {
        }

        public InterceptorAggregate(Object aggregateIdentifier) {
            identifier = aggregateIdentifier.toString();
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
            this.identifier = event.getAggregateIdentifier().toString();
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

    private static class TestCommandDispatchInterceptor implements MessageDispatchInterceptor<CommandMessage<?>> {

        @Nonnull
        @Override
        public BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handle(
                @Nonnull List<? extends CommandMessage<?>> messages
        ) {
            return (index, message) -> {
                Map<String, Object> testMetaDataMap = new HashMap<>();
                testMetaDataMap.put(DISPATCH_META_DATA_KEY, DISPATCH_META_DATA_VALUE);
                message = message.andMetaData(testMetaDataMap);
                return message;
            };
        }
    }

    private static class TestCommandHandlerInterceptor implements MessageHandlerInterceptor<CommandMessage<?>> {
        @Override
        public Object handle(@Nonnull UnitOfWork<? extends CommandMessage<?>> unitOfWork,
                             @Nonnull InterceptorChain interceptorChain) throws Exception {
            unitOfWork.registerCorrelationDataProvider(new SimpleCorrelationDataProvider(HANDLER_META_DATA_KEY));
            return interceptorChain.proceed();
        }
    }
}
