/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling;

import org.axonframework.commandhandling.callbacks.LoggingCallback;
import org.axonframework.commandhandling.callbacks.VoidCallback;
import org.axonframework.commandhandling.model.AggregateMember;
import org.axonframework.commandhandling.model.Repository;
import org.axonframework.commandhandling.model.inspection.AggregateModel;
import org.axonframework.commandhandling.model.inspection.AnnotatedAggregate;
import org.axonframework.commandhandling.model.inspection.EventSourcedAggregate;
import org.axonframework.commandhandling.model.inspection.ModelInspector;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.IdentifierFactory;
import org.axonframework.common.Registration;
import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.FixedValueParameterResolver;
import org.axonframework.common.annotation.MultiParameterResolverFactory;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.eventsourcing.AggregateIdentifier;
import org.axonframework.eventsourcing.EntityId;
import org.axonframework.eventsourcing.StubDomainEvent;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.metadata.CorrelationDataProvider;
import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.Callable;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
@SuppressWarnings({"unchecked"})
public class AggregateAnnotationCommandHandlerTest {

    private AggregateAnnotationCommandHandler<StubCommandAnnotatedAggregate> testSubject;
    private SimpleCommandBus commandBus;
    private Repository<StubCommandAnnotatedAggregate> mockRepository;
    private AggregateModel<StubCommandAnnotatedAggregate> aggregateModel;

    @Before
    public void setUp() throws Exception {
        commandBus = new SimpleCommandBus();
        commandBus.setUnitOfWorkFactory(new UnitOfWorkFactory<UnitOfWork<?>>() {
            @Override
            public Registration registerCorrelationDataProvider(CorrelationDataProvider correlationDataProvider) {
                return () -> true;
            }

            @Override
            @SuppressWarnings("unchecked")
            public UnitOfWork<CommandMessage<?>> createUnitOfWork(Message<?> message) {
                return DefaultUnitOfWork.startAndGet((CommandMessage<?>) message);
            }
        });
        commandBus = spy(commandBus);
        mockRepository = mock(Repository.class);
        when(mockRepository.newInstance(any()))
                .thenAnswer(
                        invocation ->
                                EventSourcedAggregate.initialize((Callable<StubCommandAnnotatedAggregate>) invocation.getArguments()[0],
                                                                 aggregateModel,
                                                                 mock(EventStore.class)));

        ParameterResolverFactory parameterResolverFactory = MultiParameterResolverFactory.ordered(
                ClasspathParameterResolverFactory.forClass(AggregateAnnotationCommandHandler.class),
                (member, params, index) -> {
                    if (String.class.equals(params[index].getType())) {
                        return new FixedValueParameterResolver<>("It works");
                    }
                    return null;
                });
        aggregateModel = ModelInspector.inspectAggregate(StubCommandAnnotatedAggregate.class,
                                                         parameterResolverFactory);
        testSubject = new AggregateAnnotationCommandHandler<>(StubCommandAnnotatedAggregate.class,
                                                              mockRepository,
                                                              new AnnotationCommandTargetResolver(),
                                                              parameterResolverFactory);
        testSubject.subscribe(commandBus);
    }

    @Test
    public void testAggregateConstructorThrowsException() {
        commandBus.dispatch(asCommandMessage(new FailingCreateCommand("id", "parameter")), new VoidCallback<Object>() {
            @Override
            protected void onSuccess(CommandMessage<?> commandMessage) {
                fail("Expected exception");
            }

            @Override
            public void onFailure(CommandMessage commandMessage, Throwable cause) {
                assertEquals("parameter", cause.getMessage());
            }
        });
    }

    @Test
    public void testAggregateCommandHandlerThrowsException() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(eq(aggregateIdentifier), anyLong()))
                .thenReturn(createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(new FailingUpdateCommand(aggregateIdentifier, "parameter")),
                            new VoidCallback<Object>() {
                                @Override
                                protected void onSuccess(CommandMessage<?> commandMessage) {
                                    fail("Expected exception");
                                }

                                @Override
                                public void onFailure(CommandMessage commandMessage, Throwable cause) {
                                    assertEquals("parameter", cause.getMessage());
                                }
                            }
        );
    }

    @Test
    public void testSupportedCommands() {
        Set<String> actual = testSubject.supportedCommandNames();
        Set<String> expected = new HashSet<>(Arrays.asList(
                CreateCommand.class.getName(),
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
        verify(callback).onSuccess(message, "id");
    }

    @Test
    public void testCommandHandlerUpdatesAggregateInstance_AnnotatedMethod() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), anyLong()))
                .thenReturn(createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(new UpdateCommandWithAnnotatedMethod("abc123")),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    assertEquals("Method works fine", result);
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    cause.printStackTrace();
                                    fail("Did not expect exception");
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandlerUpdatesAggregateInstanceWithCorrectVersion_AnnotatedMethod() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), anyLong()))
                .thenReturn(createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(
                new UpdateCommandWithAnnotatedMethodAndVersion("abc123", 12L)),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    assertEquals("Method with version works fine", result);
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    cause.printStackTrace();
                                    fail("Did not expect exception");
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, 12L);
    }

    protected AnnotatedAggregate<StubCommandAnnotatedAggregate> createAggregate(String aggregateIdentifier) {
        return new AnnotatedAggregate<>(
                new StubCommandAnnotatedAggregate(aggregateIdentifier),
                aggregateModel,
                null);
    }

    protected AnnotatedAggregate<StubCommandAnnotatedAggregate> createAggregate(StubCommandAnnotatedAggregate root) {
        return new AnnotatedAggregate<>(
                root,
                aggregateModel,
                null);
    }

    @Test
    public void testCommandHandlerUpdatesAggregateInstanceWithNullVersion_AnnotatedMethod() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), anyLong()))
                .thenReturn(createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(
                new UpdateCommandWithAnnotatedMethodAndVersion("abc123", null)),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    assertEquals("Method with version works fine", result);
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    cause.printStackTrace();
                                    fail("Did not expect exception");
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandlerUpdatesAggregateInstance_AnnotatedField() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), anyLong()))
                .thenReturn(createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(new UpdateCommandWithAnnotatedField("abc123")),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    assertEquals("Field works fine", result);
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    cause.printStackTrace();
                                    fail("Did not expect exception");
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandlerUpdatesAggregateInstanceWithCorrectVersion_AnnotatedField() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), anyLong()))
                .thenReturn(createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(
                new UpdateCommandWithAnnotatedFieldAndVersion("abc123", 321L)),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    assertEquals("Field with version works fine", result);
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    cause.printStackTrace();
                                    fail("Did not expect exception");
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, 321L);
    }

    @Test
    public void testCommandHandlerUpdatesAggregateInstanceWithCorrectVersion_AnnotatedIntegerField() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), anyLong()))
                .thenReturn(createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(
                new UpdateCommandWithAnnotatedFieldAndIntegerVersion("abc123", 321)),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    assertEquals("Field with integer version works fine", result);
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    cause.printStackTrace();
                                    fail("Did not expect exception");
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, 321L);
    }

    @Test
    public void testCommandForEntityRejectedWhenNoInstanceIsAvailable() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), anyLong()))
                .thenReturn(createAggregate(aggregateIdentifier));
        commandBus.dispatch(asCommandMessage(
                new UpdateEntityStateCommand("abc123")),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    fail("Expected an exception, as the entity was not initialized");
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    cause.printStackTrace();
                                    if (!cause.getMessage().contains("entity")) {
                                        fail("Got an exception, but not the right one.");
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
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(createAggregate(aggregate));
        commandBus.dispatch(asCommandMessage(
                new UpdateEntityStateCommand("abc123")),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    assertEquals("entity command handled just fine", result);
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    cause.printStackTrace();
                                    fail("Did not expect exception");
                                }
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
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(createAggregate(root));
        commandBus.dispatch(asCommandMessage(
                new UpdateEntityFromCollectionStateCommand("abc123", "2")),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    assertEquals("handled by 2", result);
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    cause.printStackTrace();
                                    fail("Did not expect exception");
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandledByEntityFromCollection_NoEntityAvailable() {
        String aggregateIdentifier = "abc123";
        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        root.initializeEntity("1");
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(createAggregate(root));
        commandBus.dispatch(asCommandMessage(
                new UpdateEntityFromCollectionStateCommand("abc123", "2")),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    fail("Expected exception");
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    assertTrue(cause instanceof IllegalStateException);
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandledByEntityFromCollection_NullIdInCommand() {
        String aggregateIdentifier = "abc123";
        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        root.initializeEntity("1");
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(createAggregate(root));
        commandBus.dispatch(asCommandMessage(
                new UpdateEntityFromCollectionStateCommand("abc123", null)),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    fail("Expected exception");
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    assertTrue(cause instanceof IllegalStateException);
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
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(createAggregate(root));
        commandBus.dispatch(asCommandMessage(new UpdateNestedEntityStateCommand("abc123")),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    assertEquals("nested entity command handled just fine", result);
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    cause.printStackTrace();
                                    fail("Did not expect exception");
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test(expected = AxonConfigurationException.class)
    public void testAnnotatedCollectionFieldMustContainGenericParameterWhenTypeIsNotExplicitlyDefined()
            throws Exception {
        new AggregateAnnotationCommandHandler(CollectionFieldWithoutGenerics.class, mockRepository);
    }

    @Test
    public void testCommandHandledByEntityFromMap() {
        String aggregateIdentifier = "abc123";
        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        root.initializeEntity("1");
        root.initializeEntity("2");
        root.initializeEntity("3");
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(createAggregate(root));
        commandBus.dispatch(asCommandMessage(
                new UpdateEntityFromMapStateCommand("abc123", "2")),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    assertEquals("handled by 2", result);
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    cause.printStackTrace();
                                    fail("Did not expect exception");
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandledByEntityFromMap_NoEntityAvailable() {
        String aggregateIdentifier = "abc123";
        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        root.initializeEntity("1");
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(createAggregate(root));
        commandBus.dispatch(asCommandMessage(
                new UpdateEntityFromMapStateCommand("abc123", "2")),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    fail("Expected exception");
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    assertTrue(cause instanceof IllegalStateException);
                                }
                            }
        );

        verify(mockRepository).load(aggregateIdentifier, null);
    }

    @Test
    public void testCommandHandledByEntityFromMap_NullIdInCommand() {
        String aggregateIdentifier = "abc123";
        final StubCommandAnnotatedAggregate root = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        root.initializeEntity("1");
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(createAggregate(root));
        commandBus.dispatch(asCommandMessage(
                new UpdateEntityFromMapStateCommand("abc123", null)),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    fail("Expected exception");
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    cause.printStackTrace();
                                    assertTrue(cause instanceof IllegalStateException);
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
                                             @org.axonframework.common.annotation.MetaData("notExist") String value) {
            super(createCommand.getId());
            Assert.assertNotNull(metaData);
            Assert.assertNotNull(unitOfWork);
            Assert.assertNull(value);
            apply(new StubDomainEvent());
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

        @EntityId(routingKey = "entityId")
        private String id;

        private StubCommandAnnotatedCollectionEntity(String id) {
            this.id = id;
        }

        @CommandHandler
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

    private static class CommandWithoutRequiredRoutingProperty {
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
}
