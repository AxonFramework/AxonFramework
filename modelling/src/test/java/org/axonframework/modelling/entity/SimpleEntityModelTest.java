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
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.DuplicateCommandHandlerSubscriptionException;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageStreamTestUtils;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.StubProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.entity.child.ChildAmbiguityException;
import org.axonframework.modelling.entity.child.EntityChildModel;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimpleEntityModelTest {

    private static final QualifiedName PARENT_ONLY_INSTANCE_COMMAND = new QualifiedName("ParentCommand");
    private static final QualifiedName PARENT_ONLY_CREATIONAL_COMMAND = new QualifiedName("ParentCreationalCommand");
    private static final QualifiedName SHARED_COMMAND = new QualifiedName("SharedCommand");
    private static final QualifiedName SHARED_CHILD_COMMAND = new QualifiedName("SharedChildCommand");
    private static final QualifiedName CHILD_ONE_ONLY_COMMAND = new QualifiedName("ChildOneOnlyCommand");
    private static final QualifiedName CHILD_TWO_ONLY_COMMAND = new QualifiedName("ChildTwoOnlyCommand");
    private static final MessageType SHARED_EVENT = new MessageType("SharedEvent");

    private final EntityChildModel<TestChildEntityOne, TestEntity> childModelMockOne = mock(EntityChildModel.class);
    private final EntityChildModel<TestChildEntityTwo, TestEntity> childModelMockTwo = mock(EntityChildModel.class);
    private final EntityCommandHandler<TestEntity> parentInstanceCommandHandler = mock(EntityCommandHandler.class);
    private final CommandHandler parentCreationalCommandHandler = mock(CommandHandler.class);
    private final EntityEvolver<TestEntity> parentEntityEvolver = mock(EntityEvolver.class);

    private final TestEntity entity = new TestEntity();
    private final ProcessingContext context = new StubProcessingContext();

    private EntityModel<TestEntity> entityModel;

    @BeforeEach
    void setUp() {
        when(childModelMockOne.supportedCommands()).thenReturn(Set.of(CHILD_ONE_ONLY_COMMAND,
                                                                      SHARED_CHILD_COMMAND,
                                                                      SHARED_COMMAND));
        when(childModelMockTwo.supportedCommands()).thenReturn(Set.of(CHILD_TWO_ONLY_COMMAND,
                                                                      SHARED_CHILD_COMMAND,
                                                                      SHARED_COMMAND));
        when(parentEntityEvolver.evolve(any(), any(), any())).thenAnswer(answ -> answ.getArgument(0));
        when(childModelMockOne.handle(any(), any(), any()))
                .thenReturn(MessageStream.just(new GenericCommandResultMessage<>(new MessageType(String.class),
                                                                                 "child-one")));
        when(childModelMockTwo.handle(any(), any(), any()))
                .thenReturn(MessageStream.just(new GenericCommandResultMessage<>(new MessageType(String.class),
                                                                                 "child-two")));
        when(childModelMockOne.evolve(any(), any(), any())).thenAnswer(answ -> answ.getArgument(0));
        when(childModelMockTwo.evolve(any(), any(), any())).thenAnswer(answ -> answ.getArgument(0));
        when(childModelMockOne.entityType()).thenReturn(TestChildEntityOne.class);
        EntityModel childEntityModelMockOne = mock(EntityModel.class);
        when(childModelMockOne.entityModel()).thenReturn(childEntityModelMockOne);
        EntityModel childEntityModelMockTwo = mock(EntityModel.class);
        when(childModelMockTwo.entityType()).thenReturn(TestChildEntityTwo.class);
        when(childModelMockTwo.entityModel()).thenReturn(childEntityModelMockTwo);
        when(parentInstanceCommandHandler.handle(any(), any(), any()))
                .thenReturn(MessageStream.just(new GenericCommandResultMessage<>(new MessageType(String.class),
                                                                                 "parent")));
        when(parentCreationalCommandHandler.handle(any(), any()))
                .thenReturn(MessageStream.just(new GenericCommandResultMessage<>(new MessageType(String.class),
                                                                                 "parent-creational")));
        entityModel = SimpleEntityModel
                .forEntityClass(TestEntity.class)
                .entityEvolver(parentEntityEvolver)
                .instanceCommandHandler(SHARED_COMMAND, parentInstanceCommandHandler)
                .instanceCommandHandler(PARENT_ONLY_INSTANCE_COMMAND, parentInstanceCommandHandler)
                .creationalCommandHandler(PARENT_ONLY_CREATIONAL_COMMAND, parentCreationalCommandHandler)
                .addChild(childModelMockOne)
                .addChild(childModelMockTwo)
                .build();
    }

    @Nested
    class InstanceCommands {

        @Test
        void instanceCommandForParentWillOnlyCallParent() {
            GenericCommandMessage<String> command = new GenericCommandMessage<>(
                    new MessageType(PARENT_ONLY_INSTANCE_COMMAND), "myPayload");
            MessageStream.Single<CommandResultMessage<?>> result = entityModel.handleInstance(command, entity, context);

            assertEquals("parent", result.asCompletableFuture().join().message().getPayload());
            verify(parentInstanceCommandHandler, times(1)).handle(command, entity, context);
            verify(parentCreationalCommandHandler, times(0)).handle(command, context);
            verify(childModelMockOne, times(0)).handle(command, entity, context);
            verify(childModelMockTwo, times(0)).handle(command, entity, context);
        }

        @Test
        void commandDefinedInChildOneWillOnlyCallChildOne() {
            GenericCommandMessage<String> command = new GenericCommandMessage<>(new MessageType(CHILD_ONE_ONLY_COMMAND),
                                                                                "myPayload");
            MessageStream.Single<CommandResultMessage<?>> result = entityModel.handleInstance(command, entity, context);

            assertEquals("child-one", result.asCompletableFuture().join().message().getPayload());
            verify(childModelMockOne).handle(command, entity, context);
            verify(childModelMockTwo, times(0)).handle(command, entity, context);
            verify(parentInstanceCommandHandler, times(0)).handle(command, entity, context);
            verify(parentCreationalCommandHandler, times(0)).handle(command, context);
        }

        @Test
        void commandDefinedInChildTwoWillOnlyCallChildTwo() {
            GenericCommandMessage<String> command = new GenericCommandMessage<>(new MessageType(CHILD_TWO_ONLY_COMMAND),
                                                                                "myPayload");
            MessageStream.Single<CommandResultMessage<?>> result = entityModel.handleInstance(command, entity, context);

            assertEquals("child-two", result.asCompletableFuture().join().message().getPayload());
            verify(childModelMockTwo).handle(command, entity, context);
            verify(childModelMockOne, times(0)).handle(command, entity, context);
            verify(parentInstanceCommandHandler, times(0)).handle(command, entity, context);
            verify(parentCreationalCommandHandler, times(0)).handle(command, context);
        }

        @Test
        void commandDefinedInBothChildrenWillThrowExceptionIfBothCanHandleInstanceCommand() {
            GenericCommandMessage<String> command = new GenericCommandMessage<>(new MessageType(SHARED_CHILD_COMMAND),
                                                                                "myPayload");

            when(childModelMockOne.canHandle(any(), any(), any())).thenReturn(true);
            when(childModelMockTwo.canHandle(any(), any(), any())).thenReturn(true);
            MessageStreamTestUtils.assertCompletedExceptionally(
                    entityModel.handleInstance(command, entity, context),
                    ChildAmbiguityException.class,
                    "Multiple child entities found for command of type [SharedChildCommand#0.0.1]. State of parent entity ["
            );
        }

        @Test
        void commandDefinedInBothChildrenWillCallChildOneIfOnlyChildOneCanHandleInstance() {
            GenericCommandMessage<String> command = new GenericCommandMessage<>(
                    new MessageType(SHARED_CHILD_COMMAND), "myPayload");

            when(childModelMockOne.canHandle(any(), any(), any())).thenReturn(true);
            when(childModelMockTwo.canHandle(any(), any(), any())).thenReturn(false);
            MessageStream.Single<CommandResultMessage<?>> result = entityModel.handleInstance(command, entity, context);

            assertEquals("child-one", result.asCompletableFuture().join().message().getPayload());
            verify(childModelMockOne).handle(command, entity, context);
            verify(parentInstanceCommandHandler, times(0)).handle(command, entity, context);
            verify(parentCreationalCommandHandler, times(0)).handle(command, context);
        }

        @Test
        void commandDefinedInBothChildrenWillCallChildTwoIfOnlyChildTwoCanHandleInstance() {
            GenericCommandMessage<String> command = new GenericCommandMessage<>(
                    new MessageType(SHARED_CHILD_COMMAND), "myPayload");

            when(childModelMockOne.canHandle(any(), any(), any())).thenReturn(false);
            when(childModelMockTwo.canHandle(any(), any(), any())).thenReturn(true);
            MessageStream.Single<CommandResultMessage<?>> result = entityModel.handleInstance(command, entity, context);

            assertEquals("child-two", result.asCompletableFuture().join().message().getPayload());
            verify(childModelMockTwo).handle(command, entity, context);
            verify(parentInstanceCommandHandler, times(0)).handle(command, entity, context);
            verify(parentCreationalCommandHandler, times(0)).handle(command, context);
        }

        @Test
        void instanceCommandHandlerBeingCalledWithCreateResultsInFailedMessageStream() {
            GenericCommandMessage<String> command = new GenericCommandMessage<>(
                    new MessageType(PARENT_ONLY_INSTANCE_COMMAND), "myPayload");

            MessageStreamTestUtils.assertCompletedExceptionally(
                    entityModel.handleCreate(command, context),
                    EntityMissingForInstanceCommandHandler.class,
                    "Entity was missing for instance command handler for command [ParentCommand#0.0.1]"
            );
        }

        @Test
        void returnsFailedMessageStreamOnUnknownCommandType() {
            GenericCommandMessage<String> command = new GenericCommandMessage<>(
                    new MessageType("UnknownCommand"), "myPayload");

            MessageStreamTestUtils.assertCompletedExceptionally(
                    entityModel.handleInstance(command, entity, context),
                    NoHandlerForCommandException.class,
                    "No command handler was found for command of type [UnknownCommand#0.0.1] for entity ["
            );
        }

        @Test
        void returnsFailedMessageStreamIfParentCommandHandlerThrowsException() {
            GenericCommandMessage<String> command = new GenericCommandMessage<>(
                    new MessageType(PARENT_ONLY_INSTANCE_COMMAND), "myPayload");
            when(parentInstanceCommandHandler.handle(any(), any(), any())).thenThrow(new IllegalStateException(
                    "Test exception"));

            MessageStreamTestUtils.assertCompletedExceptionally(
                    entityModel.handleInstance(command, entity, context),
                    IllegalStateException.class,
                    "Test exception"
            );
        }
        @Test
        void returnsFailedMessageStreamIfChildCommandHandlerThrowsException() {
            GenericCommandMessage<String> command = new GenericCommandMessage<>(
                    new MessageType(CHILD_TWO_ONLY_COMMAND), "myPayload");
            when(childModelMockTwo.handle(any(), any(), any())).thenThrow(new IllegalStateException("Test exception"));

            MessageStreamTestUtils.assertCompletedExceptionally(
                    entityModel.handleInstance(command, entity, context),
                    IllegalStateException.class,
                    "Test exception"
            );
        }
    }

    @Nested
    class CreationalCommands {

        @Test
        void creationalCommandForParentWillOnlyCallParent() {
            GenericCommandMessage<String> command = new GenericCommandMessage<>(
                    new MessageType(PARENT_ONLY_CREATIONAL_COMMAND), "myPayload");
            MessageStream.Single<CommandResultMessage<?>> result = entityModel.handleCreate(command, context);

            assertEquals("parent-creational", result.asCompletableFuture().join().message().getPayload());
            verify(parentCreationalCommandHandler).handle(command, context);
            verify(childModelMockOne, times(0)).handle(command, entity, context);
            verify(childModelMockTwo, times(0)).handle(command, entity, context);
        }

        @Test
        void creationalCommandHandlerBeingCalledWithInstanceResultsInFailedMessageStream() {
            GenericCommandMessage<String> command = new GenericCommandMessage<>(
                    new MessageType(PARENT_ONLY_CREATIONAL_COMMAND), "myPayload");

            MessageStreamTestUtils.assertCompletedExceptionally(
                    entityModel.handleInstance(command, new TestEntity(), context),
                    EntityExistsForCreationalCommandHandler.class,
                    "Creational command handler for command [ParentCreationalCommand#0.0.1] encountered an already existing entity"
            );
        }

        @Test
        void returnsFailedMessageStreamOnUnknownCommandType() {
            GenericCommandMessage<String> command = new GenericCommandMessage<>(
                    new MessageType("UnknownCommand"), "myPayload");

            MessageStreamTestUtils.assertCompletedExceptionally(
                    entityModel.handleCreate(command, context),
                    NoHandlerForCommandException.class,
                    "No command handler was found for command of type [UnknownCommand#0.0.1] for entity ["
            );
        }

        @Test
        void returnsFailedMessageStreamIfParentCommandHandlerThrowsException() {
            GenericCommandMessage<String> command = new GenericCommandMessage<>(
                    new MessageType(PARENT_ONLY_CREATIONAL_COMMAND), "myPayload");
            when(parentCreationalCommandHandler.handle(any(), any())).thenThrow(new IllegalStateException(
                    "Test exception"));

            MessageStreamTestUtils.assertCompletedExceptionally(
                    entityModel.handleCreate(command, context),
                    IllegalStateException.class,
                    "Test exception"
            );
        }
    }

    @Test
    void withoutEntityEvolverWillStillEvolveChildren() {
        entityModel = SimpleEntityModel
                .forEntityClass(TestEntity.class)
                .instanceCommandHandler(SHARED_COMMAND, parentInstanceCommandHandler)
                .instanceCommandHandler(PARENT_ONLY_INSTANCE_COMMAND, parentInstanceCommandHandler)
                .addChild(childModelMockOne)
                .addChild(childModelMockTwo)
                .build();

        GenericEventMessage<String> event = new GenericEventMessage<>(SHARED_EVENT, "myPayload");
        entityModel.evolve(entity, event, context);

        verify(childModelMockOne).evolve(entity, event, context);
        verify(childModelMockTwo).evolve(entity, event, context);
    }

    @Test
    void callsChildrenEvolversAndParentEvolverInThatOrder() {
        GenericEventMessage<String> event = new GenericEventMessage<>(SHARED_EVENT, "myPayload");
        entityModel.evolve(entity, event, context);

        InOrder inOrder = inOrder(childModelMockOne, childModelMockTwo, parentEntityEvolver);
        inOrder.verify(childModelMockOne).evolve(entity, event, context);
        // Verify without in-order, as the one and two child invocation is undefined
        verify(childModelMockOne).evolve(entity, event, context);
        inOrder.verify(parentEntityEvolver).evolve(entity, event, context);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void returnsSupportedCommandsForBothParentAndChild() {
        Set<QualifiedName> commandNames = entityModel.supportedCommands();
        assertTrue(commandNames.contains(PARENT_ONLY_CREATIONAL_COMMAND));
        assertTrue(commandNames.contains(PARENT_ONLY_INSTANCE_COMMAND));
        assertTrue(commandNames.contains(CHILD_ONE_ONLY_COMMAND));
        assertTrue(commandNames.contains(CHILD_TWO_ONLY_COMMAND));
        assertTrue(commandNames.contains(SHARED_COMMAND));
    }

    @Test
    void returnsFailedMessageStreamIfChildCommandHandlerThrowsException() {
        GenericCommandMessage<String> command = new GenericCommandMessage<>(new MessageType(CHILD_TWO_ONLY_COMMAND),
                                                                            "myPayload");
        when(childModelMockTwo.handle(any(), any(), any())).thenThrow(new IllegalStateException("Test exception"));


        MessageStreamTestUtils.assertCompletedExceptionally(
                entityModel.handleInstance(command, entity, context),
                IllegalStateException.class,
                "Test exception"
        );
    }

    @Test
    void returnsCorrectEntityType() {
        assertEquals(TestEntity.class, entityModel.entityType());
    }


    @Test
    void correctlyDescribesComponent() {
        var descriptor = new MockComponentDescriptor();

        entityModel.describeTo(descriptor);

        assertEquals(TestEntity.class, descriptor.getProperty("entityType"));
        assertEquals(Set.of(PARENT_ONLY_INSTANCE_COMMAND,
                            PARENT_ONLY_CREATIONAL_COMMAND,
                            SHARED_COMMAND,
                            CHILD_ONE_ONLY_COMMAND,
                            CHILD_TWO_ONLY_COMMAND,
                            SHARED_CHILD_COMMAND),
                     descriptor.getProperty("supportedCommandNames"));
        assertEquals(Set.of(PARENT_ONLY_CREATIONAL_COMMAND),
                     descriptor.getProperty("supportedCreationalCommandNames"));
        assertEquals(Set.of(PARENT_ONLY_INSTANCE_COMMAND,
                            SHARED_COMMAND,
                            CHILD_ONE_ONLY_COMMAND,
                            CHILD_TWO_ONLY_COMMAND,
                            SHARED_CHILD_COMMAND),
                     descriptor.getProperty("supportedInstanceCommandNames"));
        assertEquals(Map.of(SHARED_COMMAND,
                            parentInstanceCommandHandler,
                            PARENT_ONLY_INSTANCE_COMMAND,
                            parentInstanceCommandHandler), descriptor.getProperty("commandHandlers"));
        assertEquals(parentEntityEvolver, descriptor.getProperty("entityEvolver"));
        assertEquals(Map.of(TestChildEntityOne.class, childModelMockOne, TestChildEntityTwo.class, childModelMockTwo),
                     descriptor.getProperty("children"));
    }

    @Nested
    @DisplayName("Builder verifications")
    class BuilderVerifications {

        EntityModelBuilder<TestEntity> builder = SimpleEntityModel
                .forEntityClass(TestEntity.class);

        @Test
        void canNotAddCommandHandlerForNullQualifiedName() {
            assertThrows(NullPointerException.class, () -> builder.instanceCommandHandler(null, parentInstanceCommandHandler));
        }

        @Test
        void canNotAddNullCommandHandlerForQualifiedName() {
            assertThrows(NullPointerException.class, () -> builder.instanceCommandHandler(PARENT_ONLY_INSTANCE_COMMAND, null));
        }

        @Test
        void canNotAddSecondCommandHandlerForSameQualifiedName() {
            builder.instanceCommandHandler(PARENT_ONLY_INSTANCE_COMMAND, parentInstanceCommandHandler);
            assertThrows(DuplicateCommandHandlerSubscriptionException.class,
                         () -> builder.instanceCommandHandler(PARENT_ONLY_INSTANCE_COMMAND,
                                                              parentInstanceCommandHandler));
        }

        @Test
        void canNotAddNullChild() {
            assertThrows(NullPointerException.class, () -> builder.addChild(null));
        }

        @Test
        void canAddNullEvolver() {
            assertDoesNotThrow(() -> builder.entityEvolver(null));
        }

        @Test
        void canNotStartBuilderForNullEntityType() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class, () -> SimpleEntityModel.forEntityClass(null));
        }

        @Test
        void canNotAddChildThatSupportsCreationalCommand() {
            EntityModel childEntityModel = mock(EntityModel.class);
            when(childEntityModel.supportedCreationalCommands()).thenReturn(Set.of(CHILD_ONE_ONLY_COMMAND));
            EntityChildModel<TestChildEntityOne, TestEntity> childModel = mock(EntityChildModel.class);
            when(childModel.entityModel()).thenReturn(childEntityModel);
            assertThrows(IllegalArgumentException.class, () -> builder.addChild(childModel));
        }
    }

    private static class TestEntity {

    }

    private static class TestChildEntityOne {

    }

    private static class TestChildEntityTwo {

    }
}