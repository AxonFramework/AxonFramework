/*
 * Copyright (c) 2010-2025. Axon Framework
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


import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.MessageDispatchInterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.correlation.SimpleCorrelationDataProvider;
import org.axonframework.messaging.unitofwork.LegacyUnitOfWork;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.CommandHandlerInterceptor;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.modelling.entity.ChildEntityNotFoundException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.invocation.*;
import org.mockito.junit.jupiter.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import jakarta.annotation.Nonnull;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
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

    private static final String AGGREGATE_IDENTIFIER = "id1";
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
    private static AtomicBoolean intercepted;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(InterceptorAggregate.class);
        intercepted = new AtomicBoolean(false);
    }

    @Test
    @Disabled("TODO #3073 - Revisit Aggregate Test Fixture")
    void registeredCommandDispatchInterceptorsAreInvoked() {
        /*
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
        assertEquals(expectedCommand, firstResult.payload());

        //noinspection unchecked
        ArgumentCaptor<GenericCommandMessage<?>> secondCommandMessageCaptor =
                ArgumentCaptor.forClass(GenericCommandMessage.class);
        verify(secondMockCommandDispatchInterceptor).handle(secondCommandMessageCaptor.capture());
        GenericCommandMessage<?> secondResult = secondCommandMessageCaptor.getValue();
        assertEquals(expectedCommand, secondResult.payload());

         */
    }

    @Test
    @Disabled("TODO #3103 - Revised Interceptor Support")
    void registeredCommandDispatchInterceptorIsInvokedAndAltersAppliedEvent() {
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
    @Disabled("TODO #3073 - Revisit Aggregate Test Fixture")
    void registeredCommandDispatchInterceptorIsInvokedForFixtureMethodsGivenCommands() {
        fixture.registerCommandDispatchInterceptor(new TestCommandDispatchInterceptor());

        MetaData expectedValues =
                new MetaData(Collections.singletonMap(DISPATCH_META_DATA_KEY, DISPATCH_META_DATA_VALUE));

        fixture.givenCommands(new CreateStandardAggregateCommand(AGGREGATE_IDENTIFIER))
               .when(new TestCommand(AGGREGATE_IDENTIFIER))
               .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, expectedValues));
    }

    @Test
    @Disabled("TODO #3073 - Revisit Aggregate Test Fixture")
    void registeredCommandHandlerInterceptorsAreInvoked() throws Exception {
        /*
        fixture.registerCommandHandlerInterceptor(new TestCommandHandlerInterceptor());
        //noinspection unchecked
        when(mockCommandHandlerInterceptor.handle(any(LegacyUnitOfWork.class), any(), any(MessageHandlerInterceptorChain.class)))
                .thenAnswer(InvocationOnMock::getArguments);
        fixture.registerCommandHandlerInterceptor(mockCommandHandlerInterceptor);

        TestCommand expectedCommand = new TestCommand(AGGREGATE_IDENTIFIER);
        Map<String, String> expectedMetaDataMap = new HashMap<>();
        expectedMetaDataMap.put(HANDLER_META_DATA_KEY, HANDLER_META_DATA_VALUE);

        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
               .when(expectedCommand, expectedMetaDataMap);

        //noinspection unchecked
        ArgumentCaptor<LegacyUnitOfWork<? extends CommandMessage<?>>> unitOfWorkCaptor =
                ArgumentCaptor.forClass(LegacyUnitOfWork.class);
        ArgumentCaptor<MessageHandlerInterceptorChain> interceptorChainCaptor = ArgumentCaptor.forClass(MessageHandlerInterceptorChain.class);
        verify(mockCommandHandlerInterceptor).handle(unitOfWorkCaptor.capture(), any(), interceptorChainCaptor.capture());
        LegacyUnitOfWork<? extends CommandMessage<?>> unitOfWorkResult = unitOfWorkCaptor.getValue();
        Message<?> messageResult = unitOfWorkResult.getMessage();
        assertEquals(expectedCommand, messageResult.payload());
        assertEquals(expectedMetaDataMap, messageResult.metaData());

         */
    }

    @Test
    @Disabled("TODO #3103 - Revised Interceptor Support")
    void registeredCommandHandlerInterceptorIsInvokedAndAltersEvent() {
        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
               .when(new TestCommand(AGGREGATE_IDENTIFIER))
               .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, Collections.emptyMap()));

        fixture.registerCommandHandlerInterceptor(new TestCommandHandlerInterceptor());

        Map<String, String> expectedMetaDataMap = new HashMap<>();
        expectedMetaDataMap.put(HANDLER_META_DATA_KEY, HANDLER_META_DATA_VALUE);

        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
               .when(new TestCommand(AGGREGATE_IDENTIFIER), expectedMetaDataMap)
               .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, expectedMetaDataMap));
    }

    @Test
    @Disabled("TODO #3073 - Revisit Aggregate Test Fixture")
    void registeredCommandHandlerInterceptorIsInvokedForFixtureMethodsGivenCommands() {
        fixture.registerCommandHandlerInterceptor(new TestCommandHandlerInterceptor());

        Map<String, String> expectedMetaDataMap = new HashMap<>();
        expectedMetaDataMap.put(HANDLER_META_DATA_KEY, HANDLER_META_DATA_VALUE);

        fixture.givenCommands(new CreateStandardAggregateCommand(AGGREGATE_IDENTIFIER))
               .when(new TestCommand(AGGREGATE_IDENTIFIER), expectedMetaDataMap)
               .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, expectedMetaDataMap));
    }

    @Test
    @Disabled("TODO #3103 - Revised Interceptor Support")
    void registeredCommandDispatchAndHandlerInterceptorAreBothInvokedAndAlterEvent() {
        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
               .when(new TestCommand(AGGREGATE_IDENTIFIER))
               .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, Collections.emptyMap()));

        fixture.registerCommandDispatchInterceptor(new TestCommandDispatchInterceptor());
        fixture.registerCommandHandlerInterceptor(new TestCommandHandlerInterceptor());

        Map<String, String> testMetaDataMap = new HashMap<>();
        testMetaDataMap.put(HANDLER_META_DATA_KEY, HANDLER_META_DATA_VALUE);

        Map<String, String> expectedMetaDataMap = new HashMap<>(testMetaDataMap);
        expectedMetaDataMap.put(DISPATCH_META_DATA_KEY, DISPATCH_META_DATA_VALUE);

        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
               .when(new TestCommand(AGGREGATE_IDENTIFIER), testMetaDataMap)
               .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, new MetaData(expectedMetaDataMap)));
    }

    @Test
    @Disabled("TODO #3073 - Revisit Aggregate Test Fixture")
    void registeredHandlerInterceptorIsInvokedOnceOnGivenCommandsTestExecution() {

        AtomicInteger invocations = new AtomicInteger(0);
        /*
        fixture.registerCommandHandlerInterceptor((unitOfWork, context, interceptorChain) -> {
            invocations.incrementAndGet();
            interceptorChain.proceedSync(context);
            return null;
        });

         */

        fixture.givenCommands(new CreateStandardAggregateCommand(AGGREGATE_IDENTIFIER))
               .when(new TestCommand(AGGREGATE_IDENTIFIER))
               .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, MetaData.emptyInstance()))
               .expectSuccessfulHandlerExecution();

        assertEquals(2, invocations.get());
    }

    @Test
    @Disabled("TODO #3073 - Revisit Aggregate Test Fixture")
    void registeredDispatchInterceptorIsInvokedOnceOnGivenCommandsTestExecution() {
        AtomicInteger invocations = new AtomicInteger(0);
        /*
        fixture.registerCommandDispatchInterceptor(messages -> (i, command) -> {
            invocations.incrementAndGet();
            return command;
        });

         */

        fixture.givenCommands(new CreateStandardAggregateCommand(AGGREGATE_IDENTIFIER))
               .when(new TestCommand(AGGREGATE_IDENTIFIER))
               .expectEvents(new TestEvent(AGGREGATE_IDENTIFIER, MetaData.emptyInstance()))
               .expectSuccessfulHandlerExecution();

        assertEquals(2, invocations.get());
    }

    @Test
    @Disabled("TODO #3103 - Revised Interceptor Support")
    void interceptorChainIsInvokedWhenInterceptorForEntityWiresInterceptorChainWithoutExistingEntity() {
        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
               .when(new DoWithEntityCommand(AGGREGATE_IDENTIFIER))
               .expectSuccessfulHandlerExecution()
               .expectNoEvents()
               .expectResultMessagePayload("invoked-without-entity");
    }

    @Test
    @Disabled("TODO #3103 - Revised Interceptor Support")
    void interceptorChainIsNotInvokedWhenInterceptorForEntityDoesNotWireInterceptorChainWithoutExistingEntity() {
        fixture.given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
               .when(new DoWithEntityWithoutInterceptorCommand(AGGREGATE_IDENTIFIER))
               .expectNoEvents()
               .expectException(ChildEntityNotFoundException.class);
    }

    @Test
    @Disabled("TODO #3103 - Revised Interceptor Support")
    void messageInterceptorOnAggregateMemberOnlyIsInvokedAsExpected() {
        FixtureConfiguration<AggregateWithAggregateMemberInterceptorOnly> memberOnlyInterceptorFixture =
                new AggregateTestFixture<>(AggregateWithAggregateMemberInterceptorOnly.class);

        memberOnlyInterceptorFixture
                .given(new StandardAggregateCreatedEvent(AGGREGATE_IDENTIFIER))
                .when(new DoWithEntityCommand(AGGREGATE_IDENTIFIER))
                .expectEvents(new DoneWithEntityEvent(AGGREGATE_IDENTIFIER))
                .expectSuccessfulHandlerExecution();
        assertTrue(intercepted.get());
    }

    @SuppressWarnings("unused")
    private static class InterceptorAggregate {

        @SuppressWarnings("UnusedDeclaration")
        private transient int counter;
        @SuppressWarnings("unused")
        private Integer lastNumber;
        @AggregateIdentifier
        private String identifier;
        @SuppressWarnings("unused")
        @AggregateMember
        private InterceptorEntity entity;

        @SuppressWarnings("unused")
        public InterceptorAggregate() {
        }

        @SuppressWarnings("unused")
        public InterceptorAggregate(Object aggregateIdentifier) {
            identifier = aggregateIdentifier.toString();
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(CreateStandardAggregateCommand cmd) {
            apply(new StandardAggregateCreatedEvent(cmd.getAggregateIdentifier()));
        }

        /*
        TODO #3103 - Revised Interceptor Support
        @CommandHandlerInterceptor
        public String intercept(DoWithEntityCommand command, MessageHandlerInterceptorChain interceptorChain, ProcessingContext context) throws Exception {
            if (this.entity == null) {
                return "invoked-without-entity";
            }
            return (String) interceptorChain.proceedSync(context);
        }

         */

        @CommandHandlerInterceptor
        public void intercept(DoWithEntityWithoutInterceptorCommand command) {
            // Do nothing, as it's here to break!
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

    @SuppressWarnings("unused")
    private static class InterceptorEntity {

        /*
        TODO #3103 - Revised Interceptor Support
        @CommandHandlerInterceptor
        public void intercept(CommandMessage<?> command,
                              MessageHandlerInterceptorChain interceptorChain,
                              ProcessingContext context
        ) throws Exception {
            intercepted.set(true);
            interceptorChain.proceedSync(context);
        }

         */

        @CommandHandler
        public void handle(DoWithEntityCommand command) {
            apply(new DoneWithEntityEvent(command.getAggregateIdentifier()));
        }

        @CommandHandler
        public void handle(DoWithEntityWithoutInterceptorCommand command) {
            // Nothing to do here
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

    private static class DoWithEntityCommand {

        @TargetAggregateIdentifier
        private final Object aggregateIdentifier;

        public DoWithEntityCommand(Object aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        public Object getAggregateIdentifier() {
            return aggregateIdentifier;
        }
    }

    @SuppressWarnings("unused")
    private static class DoneWithEntityEvent {

        private final Object aggregateIdentifier;

        public DoneWithEntityEvent(Object aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        public Object getAggregateIdentifier() {
            return aggregateIdentifier;
        }
    }

    @SuppressWarnings("unused")
    private static class DoWithEntityWithoutInterceptorCommand {

        @TargetAggregateIdentifier
        private final Object aggregateIdentifier;

        public DoWithEntityWithoutInterceptorCommand(Object aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        public Object getAggregateIdentifier() {
            return aggregateIdentifier;
        }
    }

    private static class TestCommandDispatchInterceptor implements MessageDispatchInterceptor<CommandMessage<?>> {

        @Override
        public @NotNull MessageStream<?> interceptOnDispatch(@NotNull CommandMessage<?> message,
                                                             @Nullable ProcessingContext context,
                                                             @NotNull MessageDispatchInterceptorChain<CommandMessage<?>> interceptorChain) {
            Map<String, String> testMetaDataMap = new HashMap<>();
            testMetaDataMap.put(DISPATCH_META_DATA_KEY, DISPATCH_META_DATA_VALUE);
            CommandMessage<?> newMessage = message.andMetaData(testMetaDataMap);
            return interceptorChain.proceed(
                 newMessage, context
            );
        }
    }

    private static class TestCommandHandlerInterceptor implements MessageHandlerInterceptor<CommandMessage<?>> {

        /*
         TODO #3103 - Revised Interceptor Support
        @Override
        public Object handle(@Nonnull LegacyUnitOfWork<? extends CommandMessage<?>> unitOfWork,
                             @Nonnull ProcessingContext context, @Nonnull MessageHandlerInterceptorChain interceptorChain) throws Exception {
            unitOfWork.registerCorrelationDataProvider(new SimpleCorrelationDataProvider(HANDLER_META_DATA_KEY));
            return interceptorChain.proceedSync(context);
        }

         */

        @Override
        public @NotNull MessageStream<?> interceptOnHandle(@NotNull CommandMessage<?> message,
                                                           @NotNull ProcessingContext context,
                                                           @NotNull MessageHandlerInterceptorChain<CommandMessage<?>> interceptorChain) {

            return interceptorChain.proceed(message, context);
        }
    }

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private static class AggregateWithAggregateMemberInterceptorOnly {

        @AggregateIdentifier
        private String identifier;
        @AggregateMember
        private InterceptorEntity entity;

        private AggregateWithAggregateMemberInterceptorOnly() {
            // No-arg constructor required by Axon
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(CreateStandardAggregateCommand cmd) {
            apply(new StandardAggregateCreatedEvent(cmd.getAggregateIdentifier()));
        }

        @EventHandler
        public void handle(StandardAggregateCreatedEvent event) {
            this.identifier = event.getAggregateIdentifier().toString();
            this.entity = new InterceptorEntity();
        }
    }
}
