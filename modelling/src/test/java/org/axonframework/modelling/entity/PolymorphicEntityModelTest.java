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

package org.axonframework.modelling.entity;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.StubProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.EntityEvolver;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PolymorphicEntityModelTest {

    public static final QualifiedName SUPER_TYPE_INSTANCE_COMMAND = new QualifiedName("SuperTypeInstanceCommand");
    public static final QualifiedName CONCRETE_ONE_INSTANCE_COMMAND = new QualifiedName("ConcreteOneInstanceCommand");
    public static final QualifiedName CONCRETE_TWO_INSTANCE_COMMAND = new QualifiedName("ConcreteTwoInstanceCommand");

    public static final QualifiedName SUPER_TYPE_CREATIONAL_COMMAND = new QualifiedName("SuperTypeCreationalCommand");
    public static final QualifiedName CONCRETE_ONE_CREATIONAL_COMMAND = new QualifiedName("ConcreteOneCreationalCommand");
    public static final QualifiedName CONCRETE_TWO_CREATIONAL_COMMAND = new QualifiedName("ConcreteTwoCreationalCommand");

    public static final QualifiedName SUPER_TYPE_EVENT = new QualifiedName("SuperTypeEvent");
    public static final QualifiedName CONCRETE_ONE_EVENT = new QualifiedName("ConcreteOneEvent");
    public static final QualifiedName CONCRETE_TWO_EVENT = new QualifiedName("ConcreteTwoEvent");

    private final EntityModel<ConcreteTestEntityOne> concreteTestEntityOneEntityModel = Mockito.mock(EntityModel.class);
    private final EntityModel<ConcreteTestEntityTwo> concreteTestEntityTwoEntityModel = Mockito.mock(EntityModel.class);
    private final EntityCommandHandler<AbstractTestEntity> entityInstanceCommandHandler = Mockito.mock(
            EntityCommandHandler.class);
    private final CommandHandler entityCreationalCommandHandler = Mockito.mock(CommandHandler.class);
    private final EntityEvolver<AbstractTestEntity> entityEvolver = Mockito.mock(EntityEvolver.class);

    private EntityModel<AbstractTestEntity> polymorphicModel;

    @BeforeEach
    public void setup() {
        when(concreteTestEntityOneEntityModel.entityType()).thenReturn(ConcreteTestEntityOne.class);
        when(concreteTestEntityTwoEntityModel.entityType()).thenReturn(ConcreteTestEntityTwo.class);

        // Set entity model responses
        when(concreteTestEntityOneEntityModel.handleInstance(any(), any(), any())).thenReturn(
                MessageStream.just(new GenericCommandResultMessage<>(new MessageType("ConcreteOneResult"),
                                                                     "concrete-one")));
        when(concreteTestEntityOneEntityModel.handleCreate(any(), any())).thenReturn(
                MessageStream.just(new GenericCommandResultMessage<>(new MessageType("ConcreteOneResult"),
                                                                     "concrete-one-create")));
        when(concreteTestEntityTwoEntityModel.handleInstance(any(), any(), any())).thenReturn(
                MessageStream.just(new GenericCommandResultMessage<>(new MessageType("ConcreteTwoResult"),
                                                                     "concrete-two")));
        when(concreteTestEntityTwoEntityModel.handleCreate(any(), any())).thenReturn(
                MessageStream.just(new GenericCommandResultMessage<>(new MessageType("ConcreteTwoResult"),
                                                                     "concrete-two-create")));

        // Set supported commands properly
        when(concreteTestEntityOneEntityModel.supportedCreationalCommands()).thenReturn(Set.of(
                CONCRETE_ONE_CREATIONAL_COMMAND));
        when(concreteTestEntityOneEntityModel.supportedInstanceCommands()).thenReturn(Set.of(CONCRETE_ONE_INSTANCE_COMMAND));
        when(concreteTestEntityOneEntityModel.supportedCommands()).thenReturn(Set.of(
                CONCRETE_ONE_CREATIONAL_COMMAND,
                CONCRETE_ONE_INSTANCE_COMMAND
        ));
        when(concreteTestEntityTwoEntityModel.supportedCreationalCommands()).thenReturn(Set.of(
                CONCRETE_TWO_CREATIONAL_COMMAND));
        when(concreteTestEntityTwoEntityModel.supportedInstanceCommands()).thenReturn(Set.of(CONCRETE_TWO_INSTANCE_COMMAND));
        when(concreteTestEntityTwoEntityModel.supportedCommands()).thenReturn(Set.of(
                CONCRETE_TWO_CREATIONAL_COMMAND,
                CONCRETE_TWO_INSTANCE_COMMAND
        ));

        when(entityInstanceCommandHandler.handle(any(), any(), any())).thenReturn(
                MessageStream.just(new GenericCommandResultMessage<>(new MessageType("SuperTypeResult"),
                                                                     "super-type")));
        when(entityCreationalCommandHandler.handle(any(), any())).thenReturn(
                MessageStream.just(new GenericCommandResultMessage<>(new MessageType("SuperTypeResult"),
                                                                     "super-type-create")));

        when(entityEvolver.evolve(any(), any(), any())).thenAnswer(answ -> answ.getArgument(0));


        this.polymorphicModel = PolymorphicEntityModel
                .forSuperType(AbstractTestEntity.class)
                .addConcreteType(concreteTestEntityOneEntityModel)
                .addConcreteType(concreteTestEntityTwoEntityModel)
                .entityEvolver(entityEvolver)
                .commandHandler(SUPER_TYPE_INSTANCE_COMMAND, entityInstanceCommandHandler)
                .creationalCommandHandler(SUPER_TYPE_CREATIONAL_COMMAND, entityCreationalCommandHandler)
                .build();
    }

    @Test
    void canHandleInstanceCommandForConcreteTypeOne() {
        CommandMessage<?> commandMessage = new GenericCommandMessage<>(new MessageType(CONCRETE_ONE_INSTANCE_COMMAND),
                                                                       "concrete-one-instance");

        ProcessingContext context = new StubProcessingContext();
        ConcreteTestEntityOne entity = new ConcreteTestEntityOne();
        MessageStream<CommandResultMessage<?>> result = polymorphicModel.handleInstance(commandMessage,
                                                                                        entity,
                                                                                        context);

        assertEquals("concrete-one", result.first().asCompletableFuture().join().message().getPayload());
        verify(concreteTestEntityOneEntityModel, times(1)).handleInstance(eq(commandMessage), any(), eq(context));
        verify(concreteTestEntityOneEntityModel, times(0)).handleCreate(eq(commandMessage), eq(context));
        verify(concreteTestEntityTwoEntityModel, times(0)).handleInstance(eq(commandMessage), any(), eq(context));
        verify(concreteTestEntityTwoEntityModel, times(0)).handleCreate(eq(commandMessage), eq(context));
        verify(entityInstanceCommandHandler, times(0)).handle(eq(commandMessage), any(), eq(context));
        verify(entityCreationalCommandHandler, times(0)).handle(eq(commandMessage), eq(context));
    }

    @Test
    void canHandleCreationalCommandForConcreteTypeOne() {
        CommandMessage<?> commandMessage = new GenericCommandMessage<>(new MessageType(CONCRETE_ONE_CREATIONAL_COMMAND),
                                                                       "concrete-one-creational");

        ProcessingContext context = new StubProcessingContext();
        MessageStream<CommandResultMessage<?>> result = polymorphicModel.handleCreate(commandMessage, context);

        assertEquals("concrete-one-create", result.first().asCompletableFuture().join().message().getPayload());
        verify(concreteTestEntityOneEntityModel, times(0)).handleInstance(eq(commandMessage), any(), eq(context));
        verify(concreteTestEntityOneEntityModel, times(1)).handleCreate(eq(commandMessage), eq(context));
        verify(concreteTestEntityTwoEntityModel, times(0)).handleInstance(eq(commandMessage), any(), eq(context));
        verify(concreteTestEntityTwoEntityModel, times(0)).handleCreate(eq(commandMessage), eq(context));
        verify(entityInstanceCommandHandler, times(0)).handle(eq(commandMessage), any(), eq(context));
        verify(entityCreationalCommandHandler, times(0)).handle(eq(commandMessage), eq(context));
    }


    @Test
    void canHandleInstanceCommandForConcreteTypeTwo() {
        CommandMessage<?> commandMessage = new GenericCommandMessage<>(new MessageType(CONCRETE_TWO_INSTANCE_COMMAND),
                                                                       "concrete-two-instance");

        ProcessingContext context = new StubProcessingContext();
        ConcreteTestEntityTwo entity = new ConcreteTestEntityTwo();
        MessageStream<CommandResultMessage<?>> result = polymorphicModel.handleInstance(commandMessage,
                                                                                        entity,
                                                                                        context);

        assertEquals("concrete-two", result.first().asCompletableFuture().join().message().getPayload());
        verify(concreteTestEntityOneEntityModel, times(0)).handleInstance(eq(commandMessage), any(), eq(context));
        verify(concreteTestEntityOneEntityModel, times(0)).handleCreate(eq(commandMessage), eq(context));
        verify(concreteTestEntityTwoEntityModel, times(1)).handleInstance(eq(commandMessage), any(), eq(context));
        verify(concreteTestEntityTwoEntityModel, times(0)).handleCreate(eq(commandMessage), eq(context));
        verify(entityInstanceCommandHandler, times(0)).handle(eq(commandMessage), any(), eq(context));
        verify(entityCreationalCommandHandler, times(0)).handle(eq(commandMessage), eq(context));
    }

    @Test
    void canHandleCreationalCommandForConcreteTypeTwo() {
        CommandMessage<?> commandMessage = new GenericCommandMessage<>(new MessageType(CONCRETE_TWO_CREATIONAL_COMMAND),
                                                                       "concrete-two-creational");

        ProcessingContext context = new StubProcessingContext();
        MessageStream<CommandResultMessage<?>> result = polymorphicModel.handleCreate(commandMessage, context);

        assertEquals("concrete-two-create", result.first().asCompletableFuture().join().message().getPayload());
        verify(concreteTestEntityOneEntityModel, times(0)).handleInstance(eq(commandMessage), any(), eq(context));
        verify(concreteTestEntityOneEntityModel, times(0)).handleCreate(eq(commandMessage), eq(context));
        verify(concreteTestEntityTwoEntityModel, times(0)).handleInstance(eq(commandMessage), any(), eq(context));
        verify(concreteTestEntityTwoEntityModel, times(1)).handleCreate(eq(commandMessage), eq(context));
        verify(entityInstanceCommandHandler, times(0)).handle(eq(commandMessage), any(), eq(context));
        verify(entityCreationalCommandHandler, times(0)).handle(eq(commandMessage), eq(context));
    }

    @Test
    void canHandleInstanceSuperCommandForConcreteTypeOne() {
        CommandMessage<?> commandMessage = new GenericCommandMessage<>(new MessageType(SUPER_TYPE_INSTANCE_COMMAND),
                                                                       "concrete-one");

        ProcessingContext context = new StubProcessingContext();
        ConcreteTestEntityOne entity = new ConcreteTestEntityOne();
        MessageStream<CommandResultMessage<?>> result = polymorphicModel.handleInstance(commandMessage,
                                                                                        entity,
                                                                                        context);

        assertEquals("super-type", result.first().asCompletableFuture().join().message().getPayload());
        verify(entityInstanceCommandHandler).handle(eq(commandMessage), same(entity), eq(context));
        verify(concreteTestEntityOneEntityModel, times(0)).handleInstance(eq(commandMessage), any(), eq(context));
        verify(concreteTestEntityOneEntityModel, times(0)).handleCreate(eq(commandMessage), eq(context));
        verify(concreteTestEntityTwoEntityModel, times(0)).handleInstance(eq(commandMessage), any(), eq(context));
        verify(concreteTestEntityTwoEntityModel, times(0)).handleCreate(eq(commandMessage), eq(context));
        verify(entityInstanceCommandHandler, times(1)).handle(eq(commandMessage), any(), eq(context));
        verify(entityCreationalCommandHandler, times(0)).handle(eq(commandMessage), eq(context));
    }

    @Test
    void canHandleCreationalSuperCommand() {
        CommandMessage<?> commandMessage = new GenericCommandMessage<>(new MessageType(SUPER_TYPE_CREATIONAL_COMMAND),
                                                                       "super-type");

        ProcessingContext context = new StubProcessingContext();
        MessageStream<CommandResultMessage<?>> result = polymorphicModel.handleCreate(commandMessage, context);

        assertEquals("super-type-create", result.first().asCompletableFuture().join().message().getPayload());
        verify(entityCreationalCommandHandler).handle(eq(commandMessage), eq(context));
        verify(concreteTestEntityOneEntityModel, times(0)).handleInstance(eq(commandMessage), any(), eq(context));
        verify(concreteTestEntityOneEntityModel, times(0)).handleCreate(eq(commandMessage), eq(context));
        verify(concreteTestEntityTwoEntityModel, times(0)).handleInstance(eq(commandMessage), any(), eq(context));
        verify(concreteTestEntityTwoEntityModel, times(0)).handleCreate(eq(commandMessage), eq(context));
        verify(entityInstanceCommandHandler, times(0)).handle(eq(commandMessage), any(), eq(context));
        verify(entityCreationalCommandHandler, times(1)).handle(eq(commandMessage), eq(context));
    }

    @Test
    void canHandleInstanceSuperCommandForConcreteTypeTwo() {
        CommandMessage<?> commandMessage = new GenericCommandMessage<>(new MessageType(SUPER_TYPE_INSTANCE_COMMAND),
                                                                       "concrete-two");

        ProcessingContext context = new StubProcessingContext();
        ConcreteTestEntityTwo entity = new ConcreteTestEntityTwo();
        MessageStream<CommandResultMessage<?>> result = polymorphicModel.handleInstance(commandMessage,
                                                                                        entity,
                                                                                        context);

        assertEquals("super-type", result.first().asCompletableFuture().join().message().getPayload());
        verify(entityInstanceCommandHandler).handle(eq(commandMessage), same(entity), eq(context));
        verify(concreteTestEntityOneEntityModel, times(0)).handleInstance(eq(commandMessage), any(), eq(context));
        verify(concreteTestEntityTwoEntityModel, times(0)).handleInstance(eq(commandMessage), any(), eq(context));
    }

    @Test
    void callsSuperTypeAndConcreteOneEntityEvolverForConcreteTypeOne() {
        EventMessage<?> eventMessage = new GenericEventMessage<>(new MessageType(CONCRETE_ONE_EVENT), "event");
        ConcreteTestEntityOne entity = new ConcreteTestEntityOne();
        ProcessingContext context = new StubProcessingContext();

        polymorphicModel.evolve(entity, eventMessage, context);

        InOrder inOrder = inOrder(entityEvolver, concreteTestEntityTwoEntityModel, concreteTestEntityOneEntityModel);
        inOrder.verify(entityEvolver).evolve(eq(entity), eq(eventMessage), eq(context));
        inOrder.verify(concreteTestEntityOneEntityModel).evolve(eq(entity), eq(eventMessage), eq(context));
        inOrder.verify(concreteTestEntityTwoEntityModel, times(0)).evolve(any(), any(), any());
    }

    @Test
    void callsSuperTypeAndConcreteOneEntityEvolverForConcreteTypeTwo() {
        EventMessage<?> eventMessage = new GenericEventMessage<>(new MessageType(CONCRETE_TWO_EVENT), "event");
        ConcreteTestEntityTwo entity = new ConcreteTestEntityTwo();
        ProcessingContext context = new StubProcessingContext();

        polymorphicModel.evolve(entity, eventMessage, context);

        InOrder inOrder = inOrder(entityEvolver, concreteTestEntityOneEntityModel, concreteTestEntityTwoEntityModel);
        inOrder.verify(entityEvolver).evolve(eq(entity), eq(eventMessage), eq(context));
        inOrder.verify(concreteTestEntityTwoEntityModel).evolve(eq(entity), eq(eventMessage), eq(context));
        inOrder.verify(concreteTestEntityOneEntityModel, times(0)).evolve(any(), any(), any());
    }

    @Test
    void superTypeEvolverCanBeUsedToMorphTheConcreteType() {
        reset(entityEvolver, concreteTestEntityOneEntityModel, concreteTestEntityTwoEntityModel);
        when(entityEvolver.evolve(argThat(c -> c instanceof ConcreteTestEntityOne), any(), any())).thenReturn(new ConcreteTestEntityTwo());
        when(concreteTestEntityOneEntityModel.evolve(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(concreteTestEntityTwoEntityModel.evolve(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        EventMessage<?> eventMessage = new GenericEventMessage<>(new MessageType(SUPER_TYPE_EVENT), "event");
        ConcreteTestEntityOne entity = new ConcreteTestEntityOne();
        ProcessingContext context = new StubProcessingContext();
        AbstractTestEntity result = polymorphicModel.evolve(entity, eventMessage, context);
        assertInstanceOf(ConcreteTestEntityTwo.class, result);
    }


    @Test
    void concreteTypeEvolverCanBeUsedToMorphTheConcreteType() {
        reset(entityEvolver, concreteTestEntityOneEntityModel, concreteTestEntityTwoEntityModel);
        when(entityEvolver.evolve(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(concreteTestEntityOneEntityModel.evolve(any(), any(), any())).thenAnswer(invocation -> new ConcreteTestEntityTwo());
        when(concreteTestEntityTwoEntityModel.evolve(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        EventMessage<?> eventMessage = new GenericEventMessage<>(new MessageType(SUPER_TYPE_EVENT), "event");
        ConcreteTestEntityOne entity = new ConcreteTestEntityOne();
        ProcessingContext context = new StubProcessingContext();
        AbstractTestEntity result = polymorphicModel.evolve(entity, eventMessage, context);
        assertInstanceOf(ConcreteTestEntityTwo.class, result);
    }

    @Test
    void returnsSuperTypeAsEntityType() {
        assertEquals(AbstractTestEntity.class, polymorphicModel.entityType());
    }

    @Test
    void returnsAllSupportedCommands() {
        Set<QualifiedName> expectedCommands = Set.of(
                SUPER_TYPE_CREATIONAL_COMMAND,
                SUPER_TYPE_INSTANCE_COMMAND,
                CONCRETE_TWO_CREATIONAL_COMMAND,
                CONCRETE_ONE_CREATIONAL_COMMAND,
                CONCRETE_TWO_INSTANCE_COMMAND,
                CONCRETE_ONE_INSTANCE_COMMAND
        );
        assertEquals(expectedCommands, polymorphicModel.supportedCommands());
    }

    @Test
    void correctlyDescribesComponent() {
        MockComponentDescriptor descriptor = new MockComponentDescriptor();
        polymorphicModel.describeTo(descriptor);

        assertEquals(AbstractTestEntity.class, descriptor.getProperty("entityType"));
        EntityModel<AbstractTestEntity> superTypeModel = descriptor.getProperty("superTypeModel");
        superTypeModel.describeTo(descriptor);

        assertEquals(entityEvolver, descriptor.getProperty("entityEvolver"));
        assertEquals(Map.of(
                SUPER_TYPE_INSTANCE_COMMAND, entityInstanceCommandHandler
        ), descriptor.getProperty("commandHandlers"));

        assertEquals(Map.of(
                ConcreteTestEntityOne.class, concreteTestEntityOneEntityModel,
                ConcreteTestEntityTwo.class, concreteTestEntityTwoEntityModel
        ), descriptor.getProperty("polymorphicModels"));
    }

    @Nested
    @DisplayName("Builder verifications")
    public class BuilderVerifications {

        @Test
        void cannotAddConcreteTypeWithSameEntityType() {
            PolymorphicEntityModelBuilder<AbstractTestEntity> builder = PolymorphicEntityModel
                    .forSuperType(AbstractTestEntity.class)
                    .addConcreteType(concreteTestEntityOneEntityModel)
                    .addConcreteType(concreteTestEntityTwoEntityModel);

            assertThrows(IllegalArgumentException.class, () -> {
                builder.addConcreteType(concreteTestEntityOneEntityModel);
            });
        }

        @Test
        void canNotAddNullChildEntityModel() {
            PolymorphicEntityModelBuilder<AbstractTestEntity> builder = PolymorphicEntityModel.forSuperType(
                    AbstractTestEntity.class);

            assertThrows(NullPointerException.class, () -> {
                //noinspection DataFlowIssue
                builder.addConcreteType(null);
            });
        }
    }

    public static class AbstractTestEntity {

    }

    public static class ConcreteTestEntityOne extends AbstractTestEntity {

    }

    public static class ConcreteTestEntityTwo extends AbstractTestEntity {

    }
}