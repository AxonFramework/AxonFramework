/*
 * Copyright (c) 2010-2018. Axon Framework
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

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.callbacks.LoggingCallback;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.IdentifierFactory;
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
import org.junit.*;
import org.mockito.*;

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

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 * @author Nakul Mishra
 */
@SuppressWarnings({"unchecked"})
public class AggregateAnnotationCommandHandlerTest {

    private AggregateAnnotationCommandHandler<StubCommandAnnotatedAggregate> testSubject;
    private SimpleCommandBus commandBus;
    private Repository<StubCommandAnnotatedAggregate> mockRepository;
    private AggregateModel<StubCommandAnnotatedAggregate> aggregateModel;

    @Before
    public void setUp() throws Exception {
        commandBus = SimpleCommandBus.builder().build();
        commandBus = spy(commandBus);
        mockRepository = mock(Repository.class);
        when(mockRepository.newInstance(any())).thenAnswer(
                invocation -> AnnotatedAggregate.initialize(
                        (Callable<StubCommandAnnotatedAggregate>) invocation.getArguments()[0],
                        aggregateModel,
                        mock(EventBus.class)
                ));

        ParameterResolverFactory parameterResolverFactory = MultiParameterResolverFactory.ordered(
                ClasspathParameterResolverFactory.forClass(AggregateAnnotationCommandHandler.class),
                new CustomParameterResolverFactory());
        aggregateModel = AnnotatedAggregateMetaModelFactory.inspectAggregate(StubCommandAnnotatedAggregate.class,
                                                                             parameterResolverFactory);
        testSubject = AggregateAnnotationCommandHandler.<StubCommandAnnotatedAggregate>builder()
                .aggregateType(StubCommandAnnotatedAggregate.class)
                .parameterResolverFactory(parameterResolverFactory)
                .repository(mockRepository)
                .build();
        testSubject.subscribe(commandBus);
    }

    @Test
    public void testAggregateConstructorThrowsException() {
        commandBus.dispatch(asCommandMessage(new FailingCreateCommand("id", "parameter")),
                            (CommandCallback<FailingCreateCommand, Object>) (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    assertEquals("parameter", commandResultMessage.exceptionResult().getMessage());
                                } else {
                                    fail("Expected exception");
                                }
                            });
    }

    @Test
    public void testAggregateCommandHandlerThrowsException() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(eq(aggregateIdentifier), any()))
                .thenAnswer(i -> createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(new FailingUpdateCommand(aggregateIdentifier, "parameter")),
                            (CommandCallback<FailingUpdateCommand, Object>) (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    assertEquals("parameter", commandResultMessage.exceptionResult().getMessage());
                                } else {
                                    fail("Expected exception");
                                }
                            }
        );
    }

    @Test
    public void testSupportedCommands() {
        Set<String> actual = testSubject.supportedCommandNames();
        Set<String> expected = new HashSet<>(Arrays.asList(
                CreateCommand.class.getName(),
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
                UpdateEntityFromMapStateCommand.class.getName()));

        assertEquals(expected, actual);
    }

    @Test
    public void testCommandHandlerSubscribesToCommands() {
        verify(commandBus).subscribe(eq(CreateCommand.class.getName()),
                                     any(MessageHandler.class));
        verify(commandBus).subscribe(eq(UpdateCommandWithAnnotatedMethod.class.getName()),
                                     any(MessageHandler.class));
    }

    @Test
    public void testCommandHandlerCreatesAggregateInstance() throws Exception {

        final CommandCallback callback = spy(LoggingCallback.INSTANCE);
        final CommandMessage<Object> message = asCommandMessage(new CreateCommand("id", "Hi"));
        commandBus.dispatch(message, callback);
        verify(mockRepository).newInstance(any());
        // make sure the identifier was invoked in the callback
        ArgumentCaptor<CommandMessage<Object>> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        ArgumentCaptor<CommandResultMessage<String>> responseCaptor = ArgumentCaptor
                .forClass(CommandResultMessage.class);
        verify(callback).onResult(commandCaptor.capture(), responseCaptor.capture());
        assertEquals(message, commandCaptor.getValue());
        assertEquals("id", responseCaptor.getValue().getPayload());
    }


    @Test
    public void testCommandHandlerCreatesAggregateInstanceWithFactoryMethod() throws Exception {

        final CommandCallback callback = spy(LoggingCallback.INSTANCE);
        final CommandMessage<Object> message = asCommandMessage(new CreateFactoryMethodCommand("id", "Hi"));
        commandBus.dispatch(message, callback);
        verify(mockRepository).newInstance(any());
        // make sure the identifier was invoked in the callback
        ArgumentCaptor<CommandMessage<Object>> commandCaptor = ArgumentCaptor.forClass(CommandMessage.class);
        ArgumentCaptor<CommandResultMessage<String>> responseCaptor = ArgumentCaptor
                .forClass(CommandResultMessage.class);
        verify(callback).onResult(commandCaptor.capture(), responseCaptor.capture());
        assertEquals(message, commandCaptor.getValue());
        assertEquals("id", responseCaptor.getValue().getPayload());
    }

    @Test
    public void testCommandHandlerUpdatesAggregateInstanceAnnotatedMethod() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), any()))
                .thenAnswer(i -> createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(new UpdateCommandWithAnnotatedMethod("abc123")),
                            (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    commandResultMessage.optionalExceptionResult()
                                                        .ifPresent(Throwable::printStackTrace);
                                    fail("Did not expect exception");
                                }
                                assertEquals("Method works fine", commandResultMessage.getPayload());
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandlerUpdatesAggregateInstanceWithCorrectVersionAnnotatedMethod() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), anyLong()))
                .thenAnswer(i -> createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(
                new UpdateCommandWithAnnotatedMethodAndVersion("abc123", 12L)),
                            (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    commandResultMessage.optionalExceptionResult()
                                                        .ifPresent(Throwable::printStackTrace);
                                    fail("Did not expect exception");
                                }
                                assertEquals("Method with version works fine", commandResultMessage.getPayload());
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, 12L);
    }

    protected AnnotatedAggregate<StubCommandAnnotatedAggregate> createAggregate(String aggregateIdentifier) {
        return AnnotatedAggregate.initialize(new StubCommandAnnotatedAggregate(aggregateIdentifier),
                                             aggregateModel, null);
    }

    protected AnnotatedAggregate<StubCommandAnnotatedAggregate> createAggregate(StubCommandAnnotatedAggregate root) {
        return AnnotatedAggregate.initialize(root, aggregateModel, null);
    }

    @Test
    public void testCommandHandlerUpdatesAggregateInstanceWithNullVersionAnnotatedMethod() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), any()))
                .thenAnswer(i -> createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(
                new UpdateCommandWithAnnotatedMethodAndVersion("abc123", null)),
                            (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    commandResultMessage.optionalExceptionResult()
                                                        .ifPresent(Throwable::printStackTrace);
                                    fail("Did not expect exception");
                                }
                                assertEquals("Method with version works fine", commandResultMessage.getPayload());
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandlerUpdatesAggregateInstanceAnnotatedField() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), any()))
                .thenAnswer(i -> createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(new UpdateCommandWithAnnotatedField("abc123")),
                            (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    commandResultMessage.optionalExceptionResult()
                                                        .ifPresent(Throwable::printStackTrace);
                                    fail("Did not expect exception");
                                }
                                assertEquals("Field works fine", commandResultMessage.getPayload());
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandlerUpdatesAggregateInstanceWithCorrectVersionAnnotatedField() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), anyLong()))
                .thenAnswer(i -> createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(
                new UpdateCommandWithAnnotatedFieldAndVersion("abc123", 321L)),
                            (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    commandResultMessage.optionalExceptionResult()
                                                        .ifPresent(Throwable::printStackTrace);
                                    fail("Did not expect exception");
                                }
                                assertEquals("Field with version works fine", commandResultMessage.getPayload());
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, 321L);
    }

    @Test
    public void testCommandHandlerUpdatesAggregateInstanceWithCorrectVersionAnnotatedIntegerField() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), anyLong()))
                .thenAnswer(i -> createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(
                new UpdateCommandWithAnnotatedFieldAndIntegerVersion("abc123", 321)),
                            (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    commandResultMessage.optionalExceptionResult()
                                                        .ifPresent(Throwable::printStackTrace);
                                    fail("Did not expect exception");
                                }
                                assertEquals("Field with integer version works fine",
                                             commandResultMessage.getPayload());
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, 321L);
    }

    @Test
    public void testCommandForEntityRejectedWhenNoInstanceIsAvailable() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), any()))
                .thenAnswer(i -> createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(
                new UpdateEntityStateCommand("abc123")),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onResult(CommandMessage<?> commandMessage,
                                                     CommandResultMessage<?> commandResultMessage) {
                                    if (commandResultMessage.isExceptional()) {
                                        Throwable cause = commandResultMessage.exceptionResult();
                                        if (!cause.getMessage().contains("entity")) {
                                            fail("Got an exception, but not the right one.");
                                        }
                                    } else {
                                        fail("Expected an exception, as the entity was not initialized");
                                    }
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandledByEntity() {
        String aggregateIdentifier = "abc123";
        final StubCommandAnnotatedAggregate aggregate = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        aggregate.initializeEntity("1");
        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(aggregate));
        commandBus.dispatch(asCommandMessage(
                new UpdateEntityStateCommand("abc123")),
                            (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    commandResultMessage.optionalExceptionResult()
                                                        .ifPresent(Throwable::printStackTrace);
                                    fail("Did not expect exception");
                                }
                                assertEquals("entity command handled just fine",
                                             commandResultMessage.getPayload());
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandledByEntityFromCollection() {
        String aggregateIdentifier = "abc123";
        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        root.initializeEntity("1");
        root.initializeEntity("2");
        root.initializeEntity("3");
        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(root));
        commandBus.dispatch(asCommandMessage(
                new UpdateEntityFromCollectionStateCommand("abc123", "2")),
                            (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    commandResultMessage.optionalExceptionResult()
                                                        .ifPresent(Throwable::printStackTrace);
                                    fail("Did not expect exception");
                                }
                                assertEquals("handled by 2", commandResultMessage.getPayload());
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandledByEntityFromCollectionNoEntityAvailable() {
        String aggregateIdentifier = "abc123";
        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        root.initializeEntity("1");
        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(root));
        commandBus.dispatch(asCommandMessage(
                new UpdateEntityFromCollectionStateCommand("abc123", "2")),
                            (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    Throwable cause = commandResultMessage.exceptionResult();
                                    assertTrue(cause instanceof AggregateEntityNotFoundException);
                                } else {
                                    fail("Expected exception");
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandledByEntityFromCollectionNullIdInCommand() {
        String aggregateIdentifier = "abc123";
        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        root.initializeEntity("1");
        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(root));
        commandBus.dispatch(asCommandMessage(
                new UpdateEntityFromCollectionStateCommand("abc123", null)),
                            (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    Throwable cause = commandResultMessage.exceptionResult();
                                    assertTrue(cause instanceof AggregateEntityNotFoundException);
                                } else {
                                    fail("Expected exception");
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandledByNestedEntity() {
        String aggregateIdentifier = "abc123";
        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        root.initializeEntity("1");
        root.initializeEntity("2");
        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(root));
        commandBus.dispatch(asCommandMessage(new UpdateNestedEntityStateCommand("abc123")),
                            (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    commandResultMessage.optionalExceptionResult()
                                                        .ifPresent(Throwable::printStackTrace);
                                    fail("Did not expect exception");
                                }
                                assertEquals("nested entity command handled just fine",
                                             commandResultMessage.getPayload());
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test(expected = AxonConfigurationException.class)
    public void testAnnotatedCollectionFieldMustContainGenericParameterWhenTypeIsNotExplicitlyDefined() {
        Repository<CollectionFieldWithoutGenerics> mockRepo =
                (Repository<CollectionFieldWithoutGenerics>) mock(Repository.class);
        AggregateAnnotationCommandHandler.<CollectionFieldWithoutGenerics>builder()
                .aggregateType(CollectionFieldWithoutGenerics.class)
                .repository(mockRepo)
                .build();
    }

    @Test
    public void testCommandHandledByEntityFromMap() {
        String aggregateIdentifier = "abc123";
        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        root.initializeEntity("1");
        root.initializeEntity("2");
        root.initializeEntity("3");
        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(root));
        commandBus.dispatch(asCommandMessage(
                new UpdateEntityFromMapStateCommand("abc123", "2")),
                            (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    commandResultMessage.optionalExceptionResult()
                                                        .ifPresent(Throwable::printStackTrace);
                                    fail("Did not expect exception");
                                }
                                assertEquals("handled by 2", commandResultMessage.getPayload());
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandledByEntityFromMapNoEntityAvailable() {
        String aggregateIdentifier = "abc123";
        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        root.initializeEntity("1");
        when(mockRepository.load(any(String.class), any())).thenAnswer(i -> createAggregate(root));
        commandBus.dispatch(asCommandMessage(
                new UpdateEntityFromMapStateCommand("abc123", "2")),
                            (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    Throwable cause = commandResultMessage.exceptionResult();
                                    assertTrue(cause instanceof AggregateEntityNotFoundException);
                                } else {
                                    fail("Expected exception");
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandledByEntityFromMapNullIdInCommand() {
        String aggregateIdentifier = "abc123";
        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        root.initializeEntity("1");
        when(mockRepository.load(anyString(), any())).thenAnswer(i -> createAggregate(root));
        commandBus.dispatch(asCommandMessage(new UpdateEntityFromMapStateCommand("abc123", null)),
                            (commandMessage, commandResultMessage) -> {
                                if (commandResultMessage.isExceptional()) {
                                    Throwable cause = commandResultMessage.exceptionResult();
                                    assertTrue(cause instanceof AggregateEntityNotFoundException);
                                } else {
                                    fail("Expected exception");
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    private abstract static class AbstractStubCommandAnnotatedAggregate {

        @AggregateIdentifier(routingKey = "aggregateIdentifier")
        private final String identifier;

        public AbstractStubCommandAnnotatedAggregate(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }

        @CommandHandler
        public abstract String handleUpdate(UpdateCommandWithAnnotatedMethod updateCommand);
    }

    @SuppressWarnings("unused")
    private static class StubCommandAnnotatedAggregate extends AbstractStubCommandAnnotatedAggregate {

        @AggregateMember
        private StubCommandAnnotatedEntity entity;

        @AggregateMember(type = StubCommandAnnotatedCollectionEntity.class)
        private List<Object> entities;

        @AggregateMember(type = StubCommandAnnotatedMapEntity.class)
        private Map<String, StubCommandAnnotatedMapEntity> entityMap;

        @CommandHandler
        public StubCommandAnnotatedAggregate(CreateCommand createCommand, MetaData metaData,
                                             UnitOfWork<CommandMessage<?>> unitOfWork,
                                             @MetaDataValue("notExist") String value) {
            super(createCommand.getId());
            Assert.assertNotNull(metaData);
            Assert.assertNotNull(unitOfWork);
            Assert.assertNull(value);
            apply(new StubDomainEvent());
        }

        @CommandHandler
        public static StubCommandAnnotatedAggregate createStubCommandAnnotatedAggregate(
                CreateFactoryMethodCommand createFactoryMethodCommand) {
            return new StubCommandAnnotatedAggregate(createFactoryMethodCommand.getId());
        }

        @CommandHandler
        public StubCommandAnnotatedAggregate(FailingCreateCommand createCommand) {
            super(IdentifierFactory.getInstance().generateIdentifier());
            throw new RuntimeException(createCommand.getParameter());
        }

        public StubCommandAnnotatedAggregate(String aggregateIdentifier) {
            super(aggregateIdentifier);
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

        public void initializeEntity(String id) {
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
        }
    }

    private static class CollectionFieldWithoutGenerics extends StubCommandAnnotatedAggregate {

        @AggregateMember
        private List wrongField;

        public CollectionFieldWithoutGenerics(String aggregateIdentifier) {
            super(aggregateIdentifier);
        }
    }

    private static class StubCommandAnnotatedEntity {

        @AggregateMember
        private StubNestedCommandAnnotatedEntity entity;

        @CommandHandler
        public String handle(UpdateEntityStateCommand command) {
            return "entity command handled just fine";
        }

        public void initializeEntity() {
            this.entity = new StubNestedCommandAnnotatedEntity();
        }
    }

    private static class StubCommandAnnotatedCollectionEntity {

        @EntityId
        private String id;

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

    private static class StubNestedCommandAnnotatedEntity {

        @CommandHandler
        public String handle(UpdateNestedEntityStateCommand command) {
            return "nested entity command handled just fine";
        }
    }

    private static class StubCommandAnnotatedMapEntity {

        @EntityId(routingKey = "entityId")
        private String id;

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

    private static class UpdateNestedEntityStateCommand {

        @TargetAggregateIdentifier
        private final String aggregateId;

        private UpdateNestedEntityStateCommand(String aggregateId) {
            this.aggregateId = aggregateId;
        }
    }

    private static class CreateCommand {

        private final String id;

        private String parameter;

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


    private static class CreateFactoryMethodCommand {

        private final String id;

        private String parameter;

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

        private String aggregateIdentifier;

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

    private static class UpdateEntityStateCommand {

        @TargetAggregateIdentifier
        private final String aggregateIdentifier;

        private UpdateEntityStateCommand(String aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }
    }

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

        @Override
        public ParameterResolver createInstance(Executable member, Parameter[] params, int index) {
            if (String.class.equals(params[index].getType())) {
                return new FixedValueParameterResolver<>("It works");
            }
            return null;
        }
    }

    private class UpdateEntityFromCollectionStateCommand {

        @TargetAggregateIdentifier
        private final String aggregateId;

        private final String entityId;

        public UpdateEntityFromCollectionStateCommand(String aggregateId,
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
}
