/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.commandhandling.annotation;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.callbacks.VoidCallback;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.FixedValueParameterResolver;
import org.axonframework.common.annotation.MultiParameterResolverFactory;
import org.axonframework.common.annotation.ParameterResolverFactory;
import org.axonframework.domain.IdentifierFactory;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.StubDomainEvent;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.eventsourcing.annotation.AbstractAnnotatedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.metadata.CorrelationDataProvider;
import org.axonframework.messaging.metadata.MetaData;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWorkFactory;
import org.axonframework.repository.Repository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
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

    @Before
    public void setUp() throws Exception {
        commandBus = new SimpleCommandBus();
        commandBus.setUnitOfWorkFactory(new UnitOfWorkFactory() {
            @Override
            public Registration registerCorrelationDataProvider(CorrelationDataProvider correlationDataProvider) {
                return () -> true;
            }

            @Override
            public UnitOfWork createUnitOfWork(Message message) {
                UnitOfWork unitOfWork = DefaultUnitOfWork.startAndGet(message);
                unitOfWork.resources().put(EventBus.KEY, mock(EventBus.class));
                return unitOfWork;
            }
        });
        commandBus = spy(commandBus);
        mockRepository = mock(Repository.class);

        ParameterResolverFactory parameterResolverFactory = MultiParameterResolverFactory.ordered(
                ClasspathParameterResolverFactory.forClass(AggregateAnnotationCommandHandler.class),
                (memberAnnotations, parameterType, parameterAnnotations) -> {
                    if (String.class.equals(parameterType)) {
                        return new FixedValueParameterResolver<>("It works");
                    }
                    return null;
                });
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
                .thenReturn(new StubCommandAnnotatedAggregate(aggregateIdentifier));
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
    public void testCommandHandlerCreatesAggregateInstance() {

        final CommandCallback callback = mock(CommandCallback.class);
        final CommandMessage<Object> message = GenericCommandMessage.asCommandMessage(new CreateCommand("id", "Hi"));
        commandBus.dispatch(message, callback);
        verify(mockRepository).add(isA(StubCommandAnnotatedAggregate.class));
        // make sure the identifier was invoked in the callback
        verify(callback).onSuccess(message, "id");
    }

    @Test
    public void testCommandHandlerUpdatesAggregateInstance_AnnotatedMethod() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), anyLong()))
                .thenReturn(new StubCommandAnnotatedAggregate(aggregateIdentifier));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new UpdateCommandWithAnnotatedMethod("abc123")),
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
                .thenReturn(new StubCommandAnnotatedAggregate(aggregateIdentifier));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(
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

    @Test
    public void testCommandHandlerUpdatesAggregateInstanceWithNullVersion_AnnotatedMethod() {
        String aggregateIdentifier = "abc123";
        when(mockRepository.load(any(String.class), anyLong()))
                .thenReturn(new StubCommandAnnotatedAggregate(aggregateIdentifier));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(
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
                .thenReturn(new StubCommandAnnotatedAggregate(aggregateIdentifier));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(new UpdateCommandWithAnnotatedField("abc123")),
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
                .thenReturn(new StubCommandAnnotatedAggregate(aggregateIdentifier));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(
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
                .thenReturn(new StubCommandAnnotatedAggregate(aggregateIdentifier));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(
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
                .thenReturn(new StubCommandAnnotatedAggregate(aggregateIdentifier));
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(
                                    new UpdateEntityStateCommand("abc123")),
                            new CommandCallback<Object, Object>() {
                                @Override
                                public void onSuccess(CommandMessage<?> commandMessage, Object result) {
                                    fail("Expected an exception, as the entity was not initialized");
                                }

                                @Override
                                public void onFailure(CommandMessage<?> commandMessage, Throwable cause) {
                                    if (!cause.getMessage().contains("entity")) {
                                        cause.printStackTrace();
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
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(aggregate);
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(
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
        final StubCommandAnnotatedAggregate aggregate = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        aggregate.initializeEntity("1");
        aggregate.initializeEntity("2");
        aggregate.initializeEntity("3");
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(aggregate);
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(
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
        final StubCommandAnnotatedAggregate aggregate = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        aggregate.initializeEntity("1");
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(aggregate);
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(
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
        final StubCommandAnnotatedAggregate aggregate = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        aggregate.initializeEntity("1");
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(aggregate);
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(
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
        final StubCommandAnnotatedAggregate aggregate = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        aggregate.initializeEntity("1");
        aggregate.initializeEntity("2");
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(aggregate);
        commandBus.dispatch(new GenericCommandMessage<Object>(new UpdateNestedEntityStateCommand("abc123")),
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
    public void testAnnotatedFieldMustBeACollection() throws Exception {
        new AggregateAnnotationCommandHandler(AnnotatedFieldIsNotCollection.class, mockRepository);
    }

    @Test(expected = AxonConfigurationException.class)
    public void testAnnotatedCollectionFieldMustContainGenericParameterWhenTypeIsNotExplicitlyDefined()
            throws Exception {
        new AggregateAnnotationCommandHandler(CollectionFieldWithoutGenerics.class, mockRepository);
    }

    @Test
    public void testCommandHandledByEntityFromMap() {
        String aggregateIdentifier = "abc123";
        final StubCommandAnnotatedAggregate aggregate = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        aggregate.initializeEntity("1");
        aggregate.initializeEntity("2");
        aggregate.initializeEntity("3");
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(aggregate);
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(
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
        final StubCommandAnnotatedAggregate aggregate = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        aggregate.initializeEntity("1");
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(aggregate);
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(
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
        final StubCommandAnnotatedAggregate aggregate = new StubCommandAnnotatedAggregate(aggregateIdentifier);
        aggregate.initializeEntity("1");
        when(mockRepository.load(any(String.class), anyLong())).thenReturn(aggregate);
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(
                                    new UpdateEntityFromMapStateCommand("abc123", null)),
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

    @Test(expected = AxonConfigurationException.class)
    public void testAnnotatedFieldMustBeAMap() throws Exception {
        new AggregateAnnotationCommandHandler(AnnotatedFieldIsNotMap.class, mockRepository);
    }

    @Test(expected = AxonConfigurationException.class)
    public void testAnnotatedMapFieldMustContainGenericParameterWhenTypeIsNotExplicitlyDefined() throws Exception {
        new AggregateAnnotationCommandHandler(MapFieldWithoutGenerics.class, mockRepository);
    }


    private abstract static class AbstractStubCommandAnnotatedAggregate extends AbstractAnnotatedAggregateRoot {

        private final String identifier;

        public AbstractStubCommandAnnotatedAggregate(String identifier) {
            this.identifier = identifier;
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        @CommandHandler
        public abstract String handleUpdate(UpdateCommandWithAnnotatedMethod updateCommand);
    }

    private static class StubCommandAnnotatedAggregate extends AbstractStubCommandAnnotatedAggregate {

        @CommandHandlingMember
        private StubCommandAnnotatedEntity entity;

        @CommandHandlingMemberCollection(entityId = "id", commandTargetProperty = "entityId")
        private List<StubCommandAnnotatedCollectionEntity> entities;

        @CommandHandlingMemberMap(commandTargetProperty = "entityId")
        private Map<String, StubCommandAnnotatedMapEntity> entityMap;

        @CommandHandler
        public StubCommandAnnotatedAggregate(CreateCommand createCommand, MetaData metaData, UnitOfWork unitOfWork,
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

        @EventSourcingHandler
        public void on(StubDomainEvent event, String value) {
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
                this.entityMap = new HashMap<String, StubCommandAnnotatedMapEntity>();
            }
            this.entityMap.put(id, new StubCommandAnnotatedMapEntity(id));
        }
    }

    private static class AnnotatedFieldIsNotCollection extends StubCommandAnnotatedAggregate {

        @CommandHandlingMemberCollection(entityId = "id", commandTargetProperty = "entityId")
        private Map<String, String> wrongField;

        public AnnotatedFieldIsNotCollection(String aggregateIdentifier) {
            super(aggregateIdentifier);
        }
    }

    private static class CollectionFieldWithoutGenerics extends StubCommandAnnotatedAggregate {

        @CommandHandlingMemberCollection(entityId = "id", commandTargetProperty = "entityId")
        private List wrongField;

        public CollectionFieldWithoutGenerics(String aggregateIdentifier) {
            super(aggregateIdentifier);
        }
    }

    private static class StubCommandAnnotatedEntity extends AbstractAnnotatedEntity {

        @CommandHandlingMember
        private StubNestedCommandAnnotatedEntity entity;

        @CommandHandler
        public String handle(UpdateEntityStateCommand command) {
            return "entity command handled just fine";
        }

        public void initializeEntity() {
            this.entity = new StubNestedCommandAnnotatedEntity();
        }
    }

    private static class StubCommandAnnotatedCollectionEntity extends AbstractAnnotatedEntity {

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

    private static class StubNestedCommandAnnotatedEntity extends AbstractAnnotatedEntity {

        @CommandHandler
        public String handle(UpdateNestedEntityStateCommand command) {
            return "nested entity command handled just fine";
        }
    }

    private static class StubCommandAnnotatedMapEntity extends AbstractAnnotatedEntity {

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

    private static class AnnotatedFieldIsNotMap extends StubCommandAnnotatedAggregate {

        @CommandHandlingMemberMap(commandTargetProperty = "entityId")
        private Set<String> wrongField;

        public AnnotatedFieldIsNotMap(String aggregateIdentifier) {
            super(aggregateIdentifier);
        }
    }

    private static class MapFieldWithoutGenerics extends StubCommandAnnotatedAggregate {

        @CommandHandlingMemberMap(commandTargetProperty = "entityId")
        private Map wrongField;

        public MapFieldWithoutGenerics(String aggregateIdentifier) {
            super(aggregateIdentifier);
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
