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

package org.axonframework.modelling.command;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.DuplicateCommandHandlerSubscriptionException;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Priority;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.FixedValueParameterResolver;
import org.axonframework.messaging.annotation.MetaDataValue;
import org.axonframework.messaging.annotation.MultiParameterResolverFactory;
import org.axonframework.messaging.annotation.ParameterResolver;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.axonframework.modelling.utils.StubDomainEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.quality.*;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AggregateAnnotationCommandHandler}.
 *
 * @author Allard Buijze
 * @author Nakul Mishra
 */
@SuppressWarnings({"unchecked"})
@ExtendWith({MockitoExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class AggregateAnnotationCommandHandlerTest {

    private AggregateAnnotationCommandHandler<StubCommandAnnotatedAggregate> testSubject;
    private SimpleCommandBus commandBus;
    private Repository<StubCommandAnnotatedAggregate> mockRepository;
    private AggregateModel<StubCommandAnnotatedAggregate> aggregateModel;
    private CreationPolicyAggregateFactory<StubCommandAnnotatedAggregate> creationPolicyFactory;
    private ArgumentCaptor<Callable<StubCommandAnnotatedAggregate>> newInstanceCallableFactoryCaptor;

    @BeforeEach
    void setUp() throws Exception {
        commandBus = new SimpleCommandBus();
        commandBus = spy(commandBus);
        mockRepository = mock(Repository.class);
        newInstanceCallableFactoryCaptor = ArgumentCaptor.forClass(Callable.class);
        when(mockRepository.newInstance(newInstanceCallableFactoryCaptor.capture()))
                .thenAnswer(invocation -> AnnotatedAggregate.initialize(
                        (Callable<StubCommandAnnotatedAggregate>) invocation.getArguments()[0],
                        aggregateModel,
                        mock(EventBus.class)
                ));
        when(mockRepository.newInstance(newInstanceCallableFactoryCaptor.capture(), any())).thenAnswer(invocation -> {
            AnnotatedAggregate<StubCommandAnnotatedAggregate> aggregate = AnnotatedAggregate.initialize(
                    (Callable<StubCommandAnnotatedAggregate>) invocation.getArguments()[0],
                    aggregateModel,
                    mock(EventBus.class)
            );
            Consumer<Aggregate<?>> consumer = (Consumer<Aggregate<?>>) invocation.getArguments()[1];
            consumer.accept(aggregate);
            return aggregate;
        });

        ParameterResolverFactory parameterResolverFactory = MultiParameterResolverFactory.ordered(
                ClasspathParameterResolverFactory.forClass(AggregateAnnotationCommandHandler.class),
                new CustomParameterResolverFactory());
        aggregateModel = AnnotatedAggregateMetaModelFactory.inspectAggregate(StubCommandAnnotatedAggregate.class,
                                                                             parameterResolverFactory);
        creationPolicyFactory =
                spy(new NoArgumentConstructorCreationPolicyAggregateFactory<>(StubCommandAnnotatedAggregate.class));

        testSubject = AggregateAnnotationCommandHandler.<StubCommandAnnotatedAggregate>builder()
                                                       .aggregateType(StubCommandAnnotatedAggregate.class)
                                                       .parameterResolverFactory(parameterResolverFactory)
                                                       .repository(mockRepository)
                                                       .creationPolicyAggregateFactory(creationPolicyFactory)
                                                       .build();
        //noinspection resource
        testSubject.subscribe(commandBus);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void aggregateConstructorThrowsException() {
        fail("Not implemented");
//        commandBus.dispatch(asCommandMessage(new FailingCreateCommand("id", "parameter")),
//                            (CommandCallback<FailingCreateCommand, Object>) (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    assertEquals("parameter", commandResultMessage.exceptionResult().getMessage());
//                                } else {
//                                    fail("Expected exception");
//                                }
//                            });
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void aggregateCommandHandlerThrowsException() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        when(mockRepository.load(eq(aggregateIdentifier), any()))
//                .thenAnswer(i -> createAggregate(aggregateIdentifier));
//        commandBus.dispatch(asCommandMessage(new FailingUpdateCommand(aggregateIdentifier, "parameter")),
//                            (CommandCallback<FailingUpdateCommand, Object>) (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    assertEquals("parameter", commandResultMessage.exceptionResult().getMessage());
//                                } else {
//                                    fail("Expected exception");
//                                }
//                            }
//        );
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void supportedCommands() {
        Set<String> actual = testSubject.supportedCommandNames();
        Set<String> expected = new HashSet<>(Arrays.asList(
                CreateCommand.class.getName(),
                CreateOrUpdateCommand.class.getName(),
                AlwaysCreateCommand.class.getName(),
                CreateFactoryMethodCommand.class.getName(),
                UpdateCommandWithAnnotatedMethod.class.getName(),
                FailingCreateCommand.class.getName(),
                UpdateCommandWithAnnotatedMethodAndVersion.class.getName(),
                UpdateCommandWithAnnotatedField.class.getName(),
                UpdateCommandWithAnnotatedFieldAndVersion.class.getName(),
                UpdateCommandWithAnnotatedFieldAndIntegerVersion.class.getName(),
                FailingUpdateCommand.class.getName(),
                // declared in the entities
                UpdateNestedEntityStateCommand.class.getName(),
                UpdateEntityStateCommand.class.getName(),
                UpdateEntityFromCollectionStateCommand.class.getName(),
                UpdateEntityFromMapStateCommand.class.getName(),
                UpdateAbstractEntityFromCollectionStateCommand.class.getName()));

        assertEquals(expected, actual);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandlerSubscribesToCommands() {
        //noinspection resource
        verify(commandBus).subscribe(eq(CreateCommand.class.getName()),
                                     any(MessageHandler.class));
        // Is subscribed two times because of the duplicate handler. This is good and indicates usage of the
        // DuplicateCommandHandlerResolver
        //noinspection resource
        verify(commandBus, times(2)).subscribe(eq(UpdateCommandWithAnnotatedMethod.class.getName()),
                                               any(MessageHandler.class));
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandlerCreatesAggregateInstance() throws Exception {
        fail("Not implemented");
//        final CommandCallback<Object, Object> callback = spy(LoggingCallback.INSTANCE);
//        final CommandMessage<Object> message = asCommandMessage(new CreateCommand("id", "Hi"));
//        commandBus.dispatch(message, callback);
//        verify(mockRepository).newInstance(any());
//        // make sure the identifier was invoked in the callback
//        ArgumentCaptor<CommandMessage<Object>> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
//        ArgumentCaptor<CommandResultMessage<String>> responseCaptor = ArgumentCaptor
//                .forClass(CommandResultMessage.class);
//        verify(callback).onResult(commandCaptor.capture(), responseCaptor.capture());
//        assertEquals(message, commandCaptor.getValue());
//        assertEquals("id", responseCaptor.getValue().getPayload());
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandlerCreatesAlwaysAggregateInstance() throws Exception {
        fail("Not implemented");
//        final CommandCallback<Object, Object> callback = spy(LoggingCallback.INSTANCE);
//        final CommandMessage<Object> message = asCommandMessage(new AlwaysCreateCommand("id", "parameter"));
//        commandBus.dispatch(message, callback);
//        verify(mockRepository).newInstance(any(), any());
//        // make sure the identifier was invoked in the callback
//        ArgumentCaptor<CommandMessage<Object>> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
//        ArgumentCaptor<CommandResultMessage<String>> responseCaptor =
//                ArgumentCaptor.forClass(CommandResultMessage.class);
//        verify(callback).onResult(commandCaptor.capture(), responseCaptor.capture());
//        assertEquals(message, commandCaptor.getValue());
//        assertEquals("Always create works fine", responseCaptor.getValue().getPayload());
//        verify(creationPolicyFactory).create("id");
//        newInstanceCallableFactoryCaptor.getValue().call();
//        verify(creationPolicyFactory, VerificationModeFactory.times(2)).create("id");
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandlerCreatesAlwaysAggregateInstanceWithNullId() throws Exception {
        fail("Not implemented");
//        final CommandCallback<Object, Object> callback = spy(LoggingCallback.INSTANCE);
//        final CommandMessage<Object> message = asCommandMessage(new AlwaysCreateCommand(null, "parameter"));
//        commandBus.dispatch(message, callback);
//        verify(mockRepository).newInstance(any(), any());
//        // make sure the identifier was invoked in the callback
//        ArgumentCaptor<CommandMessage<Object>> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
//        ArgumentCaptor<CommandResultMessage<String>> responseCaptor =
//                ArgumentCaptor.forClass(CommandResultMessage.class);
//        verify(callback).onResult(commandCaptor.capture(), responseCaptor.capture());
//        assertEquals(message, commandCaptor.getValue());
//        assertEquals("Always create works fine", responseCaptor.getValue().getPayload());
//        verify(creationPolicyFactory).create(null);
//        newInstanceCallableFactoryCaptor.getValue().call();
//        verify(creationPolicyFactory, VerificationModeFactory.times(2)).create(null);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandlerCreatesOrUpdatesAggregateInstance() throws Exception {
        fail("Not implemented");
//        final CommandCallback<Object, Object> callback = spy(LoggingCallback.INSTANCE);
//        final CommandMessage<Object> message = asCommandMessage(new CreateOrUpdateCommand("id", "Hi"));
//
//        AnnotatedAggregate<StubCommandAnnotatedAggregate> spyAggregate = spy(createAggregate(new StubCommandAnnotatedAggregate()));
//        ArgumentCaptor<Callable<StubCommandAnnotatedAggregate>> factoryCaptor = ArgumentCaptor.forClass(Callable.class);
//        when(mockRepository.loadOrCreate(anyString(), factoryCaptor.capture()))
//                .thenReturn(spyAggregate);
//
//        commandBus.dispatch(message, callback);
//        verify(mockRepository).loadOrCreate(anyString(), any());
//        ArgumentCaptor<CommandMessage<Object>> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
//        ArgumentCaptor<CommandResultMessage<String>> responseCaptor = ArgumentCaptor
//                .forClass(CommandResultMessage.class);
//        verify(callback).onResult(commandCaptor.capture(), responseCaptor.capture());
//        verify(spyAggregate).handle(message);
//        assertEquals(message, commandCaptor.getValue());
//        assertEquals("Create or update works fine", responseCaptor.getValue().getPayload());
//        verifyNoInteractions(creationPolicyFactory);
//        factoryCaptor.getValue().call();
//        verify(creationPolicyFactory).create("id");
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandlerCreatesOrUpdatesAggregateInstanceSupportsNullId() throws Exception {
        fail("Not implemented");
//        final CommandCallback<Object, Object> callback = spy(LoggingCallback.INSTANCE);
//        final CommandMessage<Object> message = asCommandMessage(new CreateOrUpdateCommand(null, "Hi"));
//
//        ArgumentCaptor<Callable<StubCommandAnnotatedAggregate>> loadOrCreateFactoryCaptor =
//                ArgumentCaptor.forClass(Callable.class);
//        when(mockRepository.loadOrCreate(anyString(), loadOrCreateFactoryCaptor.capture()))
//                .thenThrow(new IllegalArgumentException("This is not supposed to be invoked"));
//        commandBus.dispatch(message, callback);
//        verify(mockRepository).newInstance(any(), any());
//        ArgumentCaptor<CommandMessage<Object>> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
//        ArgumentCaptor<CommandResultMessage<String>> responseCaptor = ArgumentCaptor
//                .forClass(CommandResultMessage.class);
//        verify(callback).onResult(commandCaptor.capture(), responseCaptor.capture());
//        assertEquals(message, commandCaptor.getValue());
//        assertEquals("Create or update works fine", responseCaptor.getValue().getPayload());
//
//        verify(creationPolicyFactory).create(null);
//        newInstanceCallableFactoryCaptor.getValue().call();
//        verify(creationPolicyFactory, VerificationModeFactory.times(2)).create(null);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    public void commandHandlerAlwaysCreatesAggregateInstance() throws Exception {
        fail("Not implemented");
//        final CommandCallback<Object, Object> callback = spy(LoggingCallback.INSTANCE);
//        final CommandMessage<Object> message = asCommandMessage(new AlwaysCreateCommand("id", "Hi"));
//
//        commandBus.dispatch(message, callback);
//        verify(mockRepository).newInstance(any(), any());
//        ArgumentCaptor<CommandMessage<Object>> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
//        ArgumentCaptor<CommandResultMessage<String>> responseCaptor =
//                ArgumentCaptor.forClass(CommandResultMessage.class);
//        verify(callback).onResult(commandCaptor.capture(), responseCaptor.capture());
//        assertEquals(message, commandCaptor.getValue());
//        assertEquals("Always create works fine", responseCaptor.getValue().getPayload());
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandlerCreatesAggregateInstanceWithFactoryMethod() throws Exception {
        fail("Not implemented");
//        final CommandCallback<Object, Object> callback = spy(LoggingCallback.INSTANCE);
//        final CommandMessage<Object> message = asCommandMessage(new CreateFactoryMethodCommand("id", "Hi"));
//        commandBus.dispatch(message, callback);
//        verify(mockRepository).newInstance(any());
//        // make sure the identifier was invoked in the callback
//        ArgumentCaptor<CommandMessage<Object>> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
//        ArgumentCaptor<CommandResultMessage<String>> responseCaptor = ArgumentCaptor
//                .forClass(CommandResultMessage.class);
//        verify(callback).onResult(commandCaptor.capture(), responseCaptor.capture());
//        assertEquals(message, commandCaptor.getValue());
//        assertEquals("id", responseCaptor.getValue().getPayload());
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandlerUpdatesAggregateInstanceAnnotatedMethod() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(aggregateIdentifier));
//        commandBus.dispatch(asCommandMessage(new UpdateCommandWithAnnotatedMethod("abc123")),
//                            (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    commandResultMessage.optionalExceptionResult()
//                                                        .ifPresent(Throwable::printStackTrace);
//                                    fail("Did not expect exception");
//                                }
//                                assertEquals("Method works fine", commandResultMessage.getPayload());
//                            }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandlerUpdatesAggregateInstanceWithCorrectVersionAnnotatedMethod() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        when(mockRepository.load(any(String.class), anyLong())).thenAnswer(i -> createAggregate(aggregateIdentifier));
//        commandBus.dispatch(asCommandMessage(new UpdateCommandWithAnnotatedMethodAndVersion("abc123", 12L)),
//                            (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    commandResultMessage.optionalExceptionResult()
//                                                        .ifPresent(Throwable::printStackTrace);
//                                    fail("Did not expect exception");
//                                }
//                                assertEquals("Method with version works fine", commandResultMessage.getPayload());
//                            }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, 12L);
    }

    AnnotatedAggregate<StubCommandAnnotatedAggregate> createAggregate(String aggregateIdentifier) {
        return AnnotatedAggregate.initialize(new StubCommandAnnotatedAggregate(aggregateIdentifier),
                                             aggregateModel, null);
    }

    AnnotatedAggregate<StubCommandAnnotatedAggregate> createAggregate(StubCommandAnnotatedAggregate root) {
        return AnnotatedAggregate.initialize(root, aggregateModel, null);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandlerUpdatesAggregateInstanceWithNullVersionAnnotatedMethod() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(aggregateIdentifier));
//        commandBus.dispatch(asCommandMessage(new UpdateCommandWithAnnotatedMethodAndVersion("abc123", null)),
//                            (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    commandResultMessage.optionalExceptionResult()
//                                                        .ifPresent(Throwable::printStackTrace);
//                                    fail("Did not expect exception");
//                                }
//                                assertEquals("Method with version works fine", commandResultMessage.getPayload());
//                            }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandlerUpdatesAggregateInstanceAnnotatedField() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(aggregateIdentifier));
//        commandBus.dispatch(asCommandMessage(new UpdateCommandWithAnnotatedField("abc123")),
//                            (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    commandResultMessage.optionalExceptionResult()
//                                                        .ifPresent(Throwable::printStackTrace);
//                                    fail("Did not expect exception");
//                                }
//                                assertEquals("Field works fine", commandResultMessage.getPayload());
//                            }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandlerUpdatesAggregateInstanceWithCorrectVersionAnnotatedField() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        when(mockRepository.load(any(String.class), anyLong())).thenAnswer(i -> createAggregate(aggregateIdentifier));
//        commandBus.dispatch(asCommandMessage(new UpdateCommandWithAnnotatedFieldAndVersion("abc123", 321L)),
//                            (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    commandResultMessage.optionalExceptionResult()
//                                                        .ifPresent(Throwable::printStackTrace);
//                                    fail("Did not expect exception");
//                                }
//                                assertEquals("Field with version works fine", commandResultMessage.getPayload());
//                            }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, 321L);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandlerUpdatesAggregateInstanceWithCorrectVersionAnnotatedIntegerField() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        when(mockRepository.load(any(String.class), anyLong())).thenAnswer(i -> createAggregate(aggregateIdentifier));
//        commandBus.dispatch(asCommandMessage(new UpdateCommandWithAnnotatedFieldAndIntegerVersion("abc123", 321)),
//                            (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    commandResultMessage.optionalExceptionResult()
//                                                        .ifPresent(Throwable::printStackTrace);
//                                    fail("Did not expect exception");
//                                }
//                                assertEquals("Field with integer version works fine",
//                                             commandResultMessage.getPayload());
//                            }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, 321L);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandForEntityRejectedWhenNoInstanceIsAvailable() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(aggregateIdentifier));
//        commandBus.dispatch(asCommandMessage(new UpdateEntityStateCommand("abc123")),
//                            (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    Throwable cause = commandResultMessage.exceptionResult();
//                                    if (!cause.getMessage().contains("entity")) {
//                                        fail("Got an exception, but not the right one.");
//                                    }
//                                } else {
//                                    fail("Expected an exception, as the entity was not initialized");
//                                }
//                            }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandledByEntity() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        final StubCommandAnnotatedAggregate aggregate = new StubCommandAnnotatedAggregate(aggregateIdentifier);
//        aggregate.initializeEntity("1");
//        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(aggregate));
//        commandBus.dispatch(asCommandMessage(new UpdateEntityStateCommand("abc123")),
//                            (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    commandResultMessage.optionalExceptionResult()
//                                                        .ifPresent(Throwable::printStackTrace);
//                                    fail("Did not expect exception");
//                                }
//                                assertEquals("entity command handled just fine",
//                                             commandResultMessage.getPayload());
//                            }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandledByEntityFromCollection() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
//        root.initializeEntity("1");
//        root.initializeEntity("2");
//        root.initializeEntity("3");
//        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(root));
//        commandBus.dispatch(asCommandMessage(new UpdateEntityFromCollectionStateCommand("abc123", "2")),
//                            (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    commandResultMessage.optionalExceptionResult()
//                                                        .ifPresent(Throwable::printStackTrace);
//                                    fail("Did not expect exception");
//                                }
//                                assertEquals("handled by 2", commandResultMessage.getPayload());
//                            }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandledByEntityFromCollectionNoEntityAvailable() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
//        root.initializeEntity("1");
//        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(root));
//        commandBus.dispatch(asCommandMessage(new UpdateEntityFromCollectionStateCommand("abc123", "2")),
//                            (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    Throwable cause = commandResultMessage.exceptionResult();
//                                    assertTrue(cause instanceof AggregateEntityNotFoundException);
//                                } else {
//                                    fail("Expected exception");
//                                }
//                            }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandledByEntityFromCollectionNullIdInCommand() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
//        root.initializeEntity("1");
//        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(root));
//        commandBus.dispatch(asCommandMessage(new UpdateEntityFromCollectionStateCommand("abc123", null)),
//                            (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    Throwable cause = commandResultMessage.exceptionResult();
//                                    assertTrue(cause instanceof AggregateEntityNotFoundException);
//                                } else {
//                                    fail("Expected exception");
//                                }
//                            }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandledByNestedEntity() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
//        root.initializeEntity("1");
//        root.initializeEntity("2");
//        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(root));
//        commandBus.dispatch(asCommandMessage(new UpdateNestedEntityStateCommand("abc123")),
//                            (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    commandResultMessage.optionalExceptionResult()
//                                                        .ifPresent(Throwable::printStackTrace);
//                                    fail("Did not expect exception");
//                                }
//                                assertEquals("nested entity command handled just fine",
//                                             commandResultMessage.getPayload());
//                            }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    void annotatedCollectionFieldMustContainGenericParameterWhenTypeIsNotExplicitlyDefined(
            @Mock Repository<CollectionFieldWithoutGenerics> mockRepo
    ) {
        AggregateAnnotationCommandHandler.Builder<CollectionFieldWithoutGenerics> repoBuilder =
                AggregateAnnotationCommandHandler.<CollectionFieldWithoutGenerics>builder()
                                                 .aggregateType(CollectionFieldWithoutGenerics.class)
                                                 .repository(mockRepo);

        assertThrows(AxonConfigurationException.class, repoBuilder::build);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandledByEntityFromMap() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
//        root.initializeEntity("1");
//        root.initializeEntity("2");
//        root.initializeEntity("3");
//        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(root));
//        commandBus.dispatch(asCommandMessage(new UpdateEntityFromMapStateCommand("abc123", "2")),
//                            (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    commandResultMessage.optionalExceptionResult()
//                                                        .ifPresent(Throwable::printStackTrace);
//                                    fail("Did not expect exception");
//                                }
//                                assertEquals("handled by 2", commandResultMessage.getPayload());
//                            }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandledByEntityFromMapNoEntityAvailable() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
//        root.initializeEntity("1");
//        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(root));
//        commandBus.dispatch(asCommandMessage(new UpdateEntityFromMapStateCommand("abc123", "2")),
//                            (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    Throwable cause = commandResultMessage.exceptionResult();
//                                    assertTrue(cause instanceof AggregateEntityNotFoundException);
//                                } else {
//                                    fail("Expected exception");
//                                }
//                            }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandledByEntityFromMapNullIdInCommand() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
//        root.initializeEntity("1");
//        when(mockRepository.load(anyString(), any())).thenAnswer(i -> createAggregate(root));
//        commandBus.dispatch(asCommandMessage(new UpdateEntityFromMapStateCommand("abc123", null)),
//                            (commandMessage, commandResultMessage) -> {
//                                if (commandResultMessage.isExceptional()) {
//                                    Throwable cause = commandResultMessage.exceptionResult();
//                                    assertTrue(cause instanceof AggregateEntityNotFoundException);
//                                } else {
//                                    fail("Expected exception");
//                                }
//                            }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void rejectsDuplicateRegistrations() {
        commandBus = new SimpleCommandBus();
        commandBus = spy(commandBus);
        mockRepository = mock(Repository.class);

        testSubject = AggregateAnnotationCommandHandler.<StubCommandAnnotatedAggregate>builder()
                                                       .aggregateType(StubCommandAnnotatedAggregate.class)
                                                       .repository(mockRepository)
                                                       .build();

        //noinspection resource
        assertThrows(DuplicateCommandHandlerSubscriptionException.class, () -> testSubject.subscribe(commandBus));
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandlerByAbstractEntityWithTheSameCommandType() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
//        root.initializeEntity("1");
//        when(mockRepository.load(anyString(), any())).thenAnswer(i -> createAggregate(root));
//        commandBus.dispatch(
//                asCommandMessage(new UpdateAbstractEntityFromCollectionStateCommand(aggregateIdentifier, "1_b")),
//                (commandMessage, commandResultMessage) -> {
//                    if (commandResultMessage.isExceptional()) {
//                        commandResultMessage.optionalExceptionResult()
//                                            .ifPresent(Throwable::printStackTrace);
//                        fail("Did not expect exception");
//                    }
//                    assertEquals("handled by 1_b", commandResultMessage.getPayload());
//                }
//        );
//
//        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    @Disabled("TODO #3068 - Revise Aggregate Modelling")
    void commandHandlerByAbstractEntityWithNoAvailableEntity() {
        fail("Not implemented");
//        String aggregateIdentifier = "abc123";
//        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
//        root.initializeEntity("1");
//        when(mockRepository.load(anyString(), any())).thenAnswer(i -> createAggregate(root));
//        commandBus.dispatch(
//                asCommandMessage(new UpdateAbstractEntityFromCollectionStateCommand(aggregateIdentifier, "1_c")),
//                (commandMessage, commandResultMessage) -> {
//                    if (commandResultMessage.isExceptional()) {
//                        Throwable cause = commandResultMessage.exceptionResult();
//                        assertTrue(cause instanceof AggregateEntityNotFoundException);
//                    } else {
//                        fail("Expected exception");
//                    }
//                }
//        );
    }

    @Test
    void duplicateCommandHandlerSubscriptionExceptionIsNotThrownForPolymorphicAggregateWithRootCommandHandler() {
        commandBus = new SimpleCommandBus();

        Repository<RootAggregate> repository = mock(Repository.class);

        Set<Class<? extends RootAggregate>> subtypes = new HashSet<>();
        subtypes.add(ChildAggregate.class);
        AggregateModel<RootAggregate> polymorphicAggregateModel =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(RootAggregate.class, subtypes);

        AggregateAnnotationCommandHandler<RootAggregate> polymorphicAggregateTestSubject =
                AggregateAnnotationCommandHandler.<RootAggregate>builder()
                                                 .aggregateType(RootAggregate.class)
                                                 .repository(repository)
                                                 .aggregateModel(polymorphicAggregateModel)
                                                 .build();

        //noinspection resource
        assertDoesNotThrow(() -> polymorphicAggregateTestSubject.subscribe(commandBus));
    }

    @SuppressWarnings("unused")
    private abstract static class AbstractStubCommandAnnotatedAggregate {

        @AggregateIdentifier
        private String identifier;

        AbstractStubCommandAnnotatedAggregate(String identifier) {
            this.identifier = identifier;
        }

        public AbstractStubCommandAnnotatedAggregate() {
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        @CommandHandler
        public abstract String handleUpdate(UpdateCommandWithAnnotatedMethod updateCommand);
    }

    @SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
    private static class StubCommandAnnotatedAggregate extends AbstractStubCommandAnnotatedAggregate {

        @AggregateMember
        private StubCommandAnnotatedEntity entity;

        @AggregateMember(type = StubCommandAnnotatedCollectionEntity.class)
        private List<Object> entities;

        @AggregateMember(type = StubCommandAnnotatedMapEntity.class)
        private Map<String, StubCommandAnnotatedMapEntity> entityMap;

        @AggregateMember
        private List<StubCommandAnnotatedAbstractEntityA> absEntitiesA;

        @AggregateMember
        private List<StubCommandAnnotatedAbstractEntityB> absEntitiesB;

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(CreateCommand createCommand,
                           MetaData metaData,
                           UnitOfWork<CommandMessage<?>> unitOfWork,
                           @MetaDataValue("notExist") String value) {
            this.setIdentifier(createCommand.getId());
            assertNotNull(metaData);
            assertNotNull(unitOfWork);
            assertNull(value);
            apply(new StubDomainEvent());
        }


        @CommandHandler
        public static StubCommandAnnotatedAggregate createStubCommandAnnotatedAggregate(
                CreateFactoryMethodCommand createFactoryMethodCommand
        ) {
            return new StubCommandAnnotatedAggregate(createFactoryMethodCommand.getId());
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public void handle(FailingCreateCommand createCommand) {
            throw new RuntimeException(createCommand.getParameter());
        }

        public StubCommandAnnotatedAggregate() {
            super();
        }

        public StubCommandAnnotatedAggregate(String aggregateIdentifier) {
            super(aggregateIdentifier);
        }

        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
        public String handleCreateOrUpdate(CreateOrUpdateCommand createOrUpdateCommand) {
            this.setIdentifier(createOrUpdateCommand.id);
            return "Create or update works fine";
        }


        @CommandHandler
        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        public String handleAlwaysCreate(AlwaysCreateCommand alwaysCreateCommand) {
            this.setIdentifier(alwaysCreateCommand.id);
            return "Always create works fine";
        }

        @Override
        public String handleUpdate(UpdateCommandWithAnnotatedMethod updateCommand) {
            return "Method works fine";
        }

        @CommandHandler
        public String handleUpdate(UpdateCommandWithAnnotatedMethodAndVersion updateCommand) {
            return "Method with version works fine";
        }

        @CommandHandler
        public String handleUpdate(UpdateCommandWithAnnotatedField updateCommand) {
            return "Field works fine";
        }

        @CommandHandler
        public String handleUpdate(UpdateCommandWithAnnotatedFieldAndVersion updateCommand) {
            return "Field with version works fine";
        }

        @CommandHandler
        public String handleUpdate(UpdateCommandWithAnnotatedFieldAndIntegerVersion updateCommand) {
            return "Field with integer version works fine";
        }

        @CommandHandler
        public void handleFailingUpdate(FailingUpdateCommand updateCommand) {
            throw new RuntimeException(updateCommand.getMessage());
        }

        void initializeEntity(String id) {
            if (this.entity == null) {
                this.entity = new StubCommandAnnotatedEntity();
            } else {
                this.entity.initializeEntity();
            }
            if (this.entities == null) {
                this.entities = new ArrayList<>();
            }
            this.entities.add(new StubCommandAnnotatedCollectionEntity(id));
            if (this.entityMap == null) {
                this.entityMap = new HashMap<>();
            }
            this.entityMap.put(id, new StubCommandAnnotatedMapEntity(id));
            if (absEntitiesA == null && absEntitiesB == null) {
                this.absEntitiesA = new ArrayList<>();
                this.absEntitiesB = new ArrayList<>();
            }
            //noinspection ConstantConditions - absEntitiesA is configured in previous if-block.
            this.absEntitiesA.add(new StubCommandAnnotatedAbstractEntityA(id + "_a"));
            this.absEntitiesB.add(new StubCommandAnnotatedAbstractEntityB(id + "_b"));
        }
    }

    @SuppressWarnings("unused")
    private static class CollectionFieldWithoutGenerics extends StubCommandAnnotatedAggregate {

        @SuppressWarnings("rawtypes") // Intended for test
        @AggregateMember
        private List wrongField;

        public CollectionFieldWithoutGenerics(String aggregateIdentifier) {
            super(aggregateIdentifier);
        }
    }

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private static class StubCommandAnnotatedEntity {

        @AggregateMember
        private StubNestedCommandAnnotatedEntity entity;

        @CommandHandler
        public String handle(UpdateEntityStateCommand command) {
            return "entity command handled just fine";
        }

        void initializeEntity() {
            this.entity = new StubNestedCommandAnnotatedEntity();
        }
    }

    @SuppressWarnings("unused")
    private static class StubCommandAnnotatedCollectionEntity {

        @EntityId
        private final String id;

        private StubCommandAnnotatedCollectionEntity(String id) {
            this.id = id;
        }

        @CommandHandler(routingKey = "entityId")
        public String handle(UpdateEntityFromCollectionStateCommand command) {
            return "handled by " + getId();
        }

        public String getId() {
            return id;
        }
    }

    @SuppressWarnings("unused")
    private static class StubNestedCommandAnnotatedEntity {

        @CommandHandler
        public String handle(UpdateNestedEntityStateCommand command) {
            return "nested entity command handled just fine";
        }
    }

    @SuppressWarnings("unused")
    private static class StubCommandAnnotatedMapEntity {

        @EntityId(routingKey = "entityId")
        private final String id;

        private StubCommandAnnotatedMapEntity(String id) {
            this.id = id;
        }

        @CommandHandler
        public String handle(UpdateEntityFromMapStateCommand command) {
            return "handled by " + getId();
        }

        private String getId() {
            return id;
        }
    }

    private static abstract class StubCommandAbstractEntity {

        @EntityId
        public String abstractId;
    }

    @SuppressWarnings("unused")
    private static class StubCommandAnnotatedAbstractEntityA extends StubCommandAbstractEntity {

        public StubCommandAnnotatedAbstractEntityA() {
        }

        public StubCommandAnnotatedAbstractEntityA(String abstractId) {
            super();
            this.abstractId = abstractId;
        }

        @CommandHandler
        public String handle(UpdateAbstractEntityFromCollectionStateCommand command) {
            return "handled by " + getAbstractId();
        }

        public String getAbstractId() {
            return abstractId;
        }
    }

    @SuppressWarnings("unused")
    private static class StubCommandAnnotatedAbstractEntityB extends StubCommandAbstractEntity {

        public StubCommandAnnotatedAbstractEntityB() {
        }

        public StubCommandAnnotatedAbstractEntityB(String abstractId) {
            super();
            this.abstractId = abstractId;
        }

        @CommandHandler
        public String handle(UpdateAbstractEntityFromCollectionStateCommand command) {
            return "handled by " + getAbstractId();
        }

        public String getAbstractId() {
            return abstractId;
        }
    }

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private static class UpdateNestedEntityStateCommand {

        @TargetAggregateIdentifier
        private final String aggregateId;

        private UpdateNestedEntityStateCommand(String aggregateId) {
            this.aggregateId = aggregateId;
        }
    }

    private static class CreateCommand {

        private final String id;
        private final String parameter;

        private CreateCommand(String id, String parameter) {
            this.id = id;
            this.parameter = parameter;
        }

        public String getParameter() {
            return parameter;
        }

        public String getId() {
            return id;
        }
    }

    @SuppressWarnings("unused")
    private static class CreateOrUpdateCommand {

        @TargetAggregateIdentifier
        private final String id;
        private final String parameter;

        private CreateOrUpdateCommand(String id, String parameter) {
            this.id = id;
            this.parameter = parameter;
        }

        public String getParameter() {
            return parameter;
        }

        public String getId() {
            return id;
        }
    }

    @SuppressWarnings("unused")
    private static class AlwaysCreateCommand {

        @TargetAggregateIdentifier
        private final String id;
        private final String parameter;

        private AlwaysCreateCommand(String id, String parameter) {
            this.id = id;
            this.parameter = parameter;
        }

        public String getParameter() {
            return parameter;
        }

        public String getId() {
            return id;
        }
    }

    @SuppressWarnings("unused")
    private static class CreateFactoryMethodCommand {

        private final String id;
        private final String parameter;

        private CreateFactoryMethodCommand(String id, String parameter) {
            this.id = id;
            this.parameter = parameter;
        }

        public String getParameter() {
            return parameter;
        }

        public String getId() {
            return id;
        }
    }


    private static class FailingCreateCommand extends CreateCommand {

        private FailingCreateCommand(String id, String parameter) {
            super(id, parameter);
        }
    }

    private static class UpdateCommandWithAnnotatedMethod {

        private final String aggregateIdentifier;

        private UpdateCommandWithAnnotatedMethod(String aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        @TargetAggregateIdentifier
        public String getAggregateIdentifier() {
            return aggregateIdentifier;
        }
    }

    private static class FailingUpdateCommand extends UpdateCommandWithAnnotatedField {

        private final String message;

        private FailingUpdateCommand(String aggregateIdentifier, String message) {
            super(aggregateIdentifier);
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    @SuppressWarnings("unused")
    private static class UpdateCommandWithAnnotatedMethodAndVersion {

        private final String aggregateIdentifier;

        private final Long expectedVersion;

        private UpdateCommandWithAnnotatedMethodAndVersion(String aggregateIdentifier, Long expectedVersion) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.expectedVersion = expectedVersion;
        }

        @TargetAggregateIdentifier
        public String getAggregateIdentifier() {
            return aggregateIdentifier;
        }

        @TargetAggregateVersion
        public Long getExpectedVersion() {
            return expectedVersion;
        }
    }

    private static class UpdateCommandWithAnnotatedField {

        @TargetAggregateIdentifier
        private final String aggregateIdentifier;

        private UpdateCommandWithAnnotatedField(String aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        public String getAggregateIdentifier() {
            return aggregateIdentifier;
        }
    }

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private static class UpdateCommandWithAnnotatedFieldAndVersion {

        @TargetAggregateIdentifier
        private final String aggregateIdentifier;
        @TargetAggregateVersion
        private final Long expectedVersion;

        private UpdateCommandWithAnnotatedFieldAndVersion(String aggregateIdentifier, Long expectedVersion) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.expectedVersion = expectedVersion;
        }
    }

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private static class UpdateCommandWithAnnotatedFieldAndIntegerVersion {

        @TargetAggregateIdentifier
        private final String aggregateIdentifier;

        @TargetAggregateVersion
        private final int expectedVersion;

        private UpdateCommandWithAnnotatedFieldAndIntegerVersion(String aggregateIdentifier, int expectedVersion) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.expectedVersion = expectedVersion;
        }
    }

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private static class UpdateEntityStateCommand {

        @TargetAggregateIdentifier
        private final String aggregateIdentifier;

        private UpdateEntityStateCommand(String aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }
    }

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private static class UpdateEntityFromMapStateCommand {

        @TargetAggregateIdentifier
        private final String aggregateId;

        private final String entityKey;

        private UpdateEntityFromMapStateCommand(String aggregateId, String entityId) {
            this.aggregateId = aggregateId;
            this.entityKey = entityId;
        }

        public String getEntityId() {
            return entityKey;
        }
    }

    @Priority(Priority.LAST)
    private static class CustomParameterResolverFactory implements ParameterResolverFactory {

        @SuppressWarnings("rawtypes")
        @Override
        public ParameterResolver createInstance(Executable member, Parameter[] params, int index) {
            if (String.class.equals(params[index].getType())) {
                return new FixedValueParameterResolver<>("It works");
            }
            return null;
        }
    }

    @SuppressWarnings("unused")
    private static class UpdateEntityFromCollectionStateCommand {

        @TargetAggregateIdentifier
        private final String aggregateId;

        private final String entityId;

        UpdateEntityFromCollectionStateCommand(String aggregateId,
                                               String entityId) {
            this.aggregateId = aggregateId;
            this.entityId = entityId;
        }

        public String getAggregateId() {
            return aggregateId;
        }

        public String getEntityId() {
            return entityId;
        }
    }

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private static class UpdateAbstractEntityFromCollectionStateCommand {

        @TargetAggregateIdentifier
        private final String aggregateId;

        private final String abstractId;

        UpdateAbstractEntityFromCollectionStateCommand(String aggregateId,
                                                       String abstractId) {
            this.aggregateId = aggregateId;
            this.abstractId = abstractId;
        }

        public String getAggregateId() {
            return aggregateId;
        }

        public String getAbstractId() {
            return abstractId;
        }
    }

    @SuppressWarnings("unused")
    abstract static class RootAggregate {

        @CommandHandler
        public String handle(String command) {
            return "ROOT";
        }
    }

    static class ChildAggregate extends RootAggregate {

        @CommandHandler
        @Override
        public String handle(String command) {
            return "CHILD";
        }
    }
}
