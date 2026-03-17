/*
 * Copyright (c) 2010-2026. Axon Framework
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

import org.axonframework.messaging.commandhandling.CommandHandler;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.commandhandling.DuplicateCommandHandlerSubscriptionException;
import org.axonframework.messaging.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.commandhandling.NoHandlerForCommandException;
import org.axonframework.common.infra.MockComponentDescriptor;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageStreamTestUtils;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.modelling.EntityEvolver;
import org.axonframework.modelling.entity.child.ChildAmbiguityException;
import org.axonframework.modelling.entity.child.EntityChildMetamodel;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimpleEntityMetamodelTest {

    private static final QualifiedName PARENT_ONLY_INSTANCE_COMMAND = new QualifiedName("ParentCommand");
    private static final QualifiedName PARENT_ONLY_CREATIONAL_COMMAND = new QualifiedName("ParentCreationalCommand");
    private static final QualifiedName SHARED_COMMAND = new QualifiedName("SharedCommand");
    private static final QualifiedName SHARED_CHILD_COMMAND = new QualifiedName("SharedChildCommand");
    private static final QualifiedName CHILD_ONE_ONLY_COMMAND = new QualifiedName("ChildOneOnlyCommand");
    private static final QualifiedName CHILD_TWO_ONLY_COMMAND = new QualifiedName("ChildTwoOnlyCommand");
    private static final MessageType SHARED_EVENT = new MessageType("SharedEvent");

    @SuppressWarnings("unchecked")
    private final EntityChildMetamodel<TestChildEntityOne, TestEntity> childModelMockOne =
            mock(EntityChildMetamodel.class);
    @SuppressWarnings("unchecked")
    private final EntityChildMetamodel<TestChildEntityTwo, TestEntity> childModelMockTwo =
            mock(EntityChildMetamodel.class);
    @SuppressWarnings("unchecked")
    private final EntityCommandHandler<TestEntity> parentInstanceCommandHandler = mock(EntityCommandHandler.class);
    private final CommandHandler parentCreationalCommandHandler = mock(CommandHandler.class);
    @SuppressWarnings("unchecked")
    private final EntityEvolver<TestEntity> parentEntityEvolver = mock(EntityEvolver.class);

    private final TestEntity entity = new TestEntity();
    private final ProcessingContext context = new StubProcessingContext();

    private EntityMetamodel<TestEntity> metamodel;

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
                .thenReturn(MessageStream.just(new GenericCommandResultMessage(new MessageType(String.class),
                                                                               "child-one")));
        when(childModelMockTwo.handle(any(), any(), any()))
                .thenReturn(MessageStream.just(new GenericCommandResultMessage(new MessageType(String.class),
                                                                               "child-two")));
        when(childModelMockOne.evolve(any(), any(), any())).thenAnswer(answ -> answ.getArgument(0));
        when(childModelMockTwo.evolve(any(), any(), any())).thenAnswer(answ -> answ.getArgument(0));
        when(childModelMockOne.entityType()).thenReturn(TestChildEntityOne.class);
        //noinspection rawtypes
        EntityMetamodel childEntityMetamodelMockOne = mock(EntityMetamodel.class);
        //noinspection unchecked
        when(childModelMockOne.entityMetamodel()).thenReturn(childEntityMetamodelMockOne);
        //noinspection rawtypes
        EntityMetamodel childEntityMetamodelMockTwo = mock(EntityMetamodel.class);
        when(childModelMockTwo.entityType()).thenReturn(TestChildEntityTwo.class);
        //noinspection unchecked
        when(childModelMockTwo.entityMetamodel()).thenReturn(childEntityMetamodelMockTwo);
        when(parentInstanceCommandHandler.handle(any(), any(), any()))
                .thenReturn(MessageStream.just(new GenericCommandResultMessage(new MessageType(String.class),
                                                                               "parent")));
        when(parentCreationalCommandHandler.handle(any(), any()))
                .thenReturn(MessageStream.just(new GenericCommandResultMessage(new MessageType(String.class),
                                                                               "parent-creational")));
        metamodel = ConcreteEntityMetamodel
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
            GenericCommandMessage command = new GenericCommandMessage(
                    new MessageType(PARENT_ONLY_INSTANCE_COMMAND), "myPayload");
            MessageStream.Single<CommandResultMessage> result = metamodel.handleInstance(command, entity, context);

            assertEquals("parent", result.asCompletableFuture().join().message().payload());
            verify(parentInstanceCommandHandler, times(1)).handle(command, entity, context);
            verify(parentCreationalCommandHandler, times(0)).handle(command, context);
            verify(childModelMockOne, times(0)).handle(command, entity, context);
            verify(childModelMockTwo, times(0)).handle(command, entity, context);
        }

        @Test
        void commandDefinedInChildOneWillOnlyCallChildOne() {
            GenericCommandMessage command = new GenericCommandMessage(new MessageType(CHILD_ONE_ONLY_COMMAND),
                                                                      "myPayload");
            MessageStream.Single<CommandResultMessage> result = metamodel.handleInstance(command, entity, context);

            assertEquals("child-one", result.asCompletableFuture().join().message().payload());
            verify(childModelMockOne).handle(command, entity, context);
            verify(childModelMockTwo, times(0)).handle(command, entity, context);
            verify(parentInstanceCommandHandler, times(0)).handle(command, entity, context);
            verify(parentCreationalCommandHandler, times(0)).handle(command, context);
        }

        @Test
        void commandDefinedInChildTwoWillOnlyCallChildTwo() {
            GenericCommandMessage command =
                    new GenericCommandMessage(new MessageType(CHILD_TWO_ONLY_COMMAND), "myPayload");
            MessageStream.Single<CommandResultMessage> result = metamodel.handleInstance(command, entity, context);

            assertEquals("child-two", result.asCompletableFuture().join().message().payload());
            verify(childModelMockTwo).handle(command, entity, context);
            verify(childModelMockOne, times(0)).handle(command, entity, context);
            verify(parentInstanceCommandHandler, times(0)).handle(command, entity, context);
            verify(parentCreationalCommandHandler, times(0)).handle(command, context);
        }

        @Test
        void commandDefinedInBothChildrenWillThrowExceptionIfBothCanHandleInstanceCommand() {
            GenericCommandMessage command =
                    new GenericCommandMessage(new MessageType(SHARED_CHILD_COMMAND), "myPayload");

            when(childModelMockOne.canHandle(any(), any(), any())).thenReturn(true);
            when(childModelMockTwo.canHandle(any(), any(), any())).thenReturn(true);
            MessageStreamTestUtils.assertCompletedExceptionally(
                    metamodel.handleInstance(command, entity, context),
                    ChildAmbiguityException.class,
                    "Multiple matching child entity members found for command of type"
            );
        }

        @Test
        void commandDefinedInBothChildrenWillCallChildOneIfOnlyChildOneCanHandleInstance() {
            GenericCommandMessage command = new GenericCommandMessage(
                    new MessageType(SHARED_CHILD_COMMAND), "myPayload");

            when(childModelMockOne.canHandle(any(), any(), any())).thenReturn(true);
            when(childModelMockTwo.canHandle(any(), any(), any())).thenReturn(false);
            MessageStream.Single<CommandResultMessage> result = metamodel.handleInstance(command, entity, context);

            assertEquals("child-one", result.asCompletableFuture().join().message().payload());
            verify(childModelMockOne).handle(command, entity, context);
            verify(parentInstanceCommandHandler, times(0)).handle(command, entity, context);
            verify(parentCreationalCommandHandler, times(0)).handle(command, context);
        }

        @Test
        void commandDefinedInBothChildrenWillCallChildTwoIfOnlyChildTwoCanHandleInstance() {
            GenericCommandMessage command = new GenericCommandMessage(
                    new MessageType(SHARED_CHILD_COMMAND), "myPayload");

            when(childModelMockOne.canHandle(any(), any(), any())).thenReturn(false);
            when(childModelMockTwo.canHandle(any(), any(), any())).thenReturn(true);
            MessageStream.Single<CommandResultMessage> result = metamodel.handleInstance(command, entity, context);

            assertEquals("child-two", result.asCompletableFuture().join().message().payload());
            verify(childModelMockTwo).handle(command, entity, context);
            verify(parentInstanceCommandHandler, times(0)).handle(command, entity, context);
            verify(parentCreationalCommandHandler, times(0)).handle(command, context);
        }

        @Test
        void instanceCommandHandlerBeingCalledWithCreateResultsInFailedMessageStream() {
            GenericCommandMessage command = new GenericCommandMessage(
                    new MessageType(PARENT_ONLY_INSTANCE_COMMAND), "myPayload");

            MessageStreamTestUtils.assertCompletedExceptionally(
                    metamodel.handleCreate(command, context),
                    EntityMissingForInstanceCommandHandlerException.class,
                    "Entity was missing for instance command handler for command [ParentCommand#0.0.1]"
            );
        }

        @Test
        void returnsFailedMessageStreamOnUnknownCommandType() {
            GenericCommandMessage command = new GenericCommandMessage(
                    new MessageType("UnknownCommand"), "myPayload");

            MessageStreamTestUtils.assertCompletedExceptionally(
                    metamodel.handleInstance(command, entity, context),
                    NoHandlerForCommandException.class,
                    "No command handler was found for command of type [UnknownCommand#0.0.1] for entity ["
            );
        }

        @Test
        void returnsFailedMessageStreamIfParentCommandHandlerThrowsException() {
            GenericCommandMessage command = new GenericCommandMessage(
                    new MessageType(PARENT_ONLY_INSTANCE_COMMAND), "myPayload");
            when(parentInstanceCommandHandler.handle(any(), any(), any())).thenThrow(new IllegalStateException(
                    "Test exception"));

            MessageStreamTestUtils.assertCompletedExceptionally(
                    metamodel.handleInstance(command, entity, context),
                    IllegalStateException.class,
                    "Test exception"
            );
        }

        @Test
        void returnsFailedMessageStreamIfChildCommandHandlerThrowsException() {
            GenericCommandMessage command = new GenericCommandMessage(
                    new MessageType(CHILD_TWO_ONLY_COMMAND), "myPayload");
            when(childModelMockTwo.handle(any(), any(), any())).thenThrow(new IllegalStateException("Test exception"));

            MessageStreamTestUtils.assertCompletedExceptionally(
                    metamodel.handleInstance(command, entity, context),
                    IllegalStateException.class,
                    "Test exception"
            );
        }
    }

    @Nested
    class CreationalCommands {

        @Test
        void creationalCommandForParentWillOnlyCallParent() {
            GenericCommandMessage command = new GenericCommandMessage(
                    new MessageType(PARENT_ONLY_CREATIONAL_COMMAND), "myPayload");
            MessageStream.Single<CommandResultMessage> result = metamodel.handleCreate(command, context);

            assertEquals("parent-creational", result.asCompletableFuture().join().message().payload());
            verify(parentCreationalCommandHandler).handle(command, context);
            verify(childModelMockOne, times(0)).handle(command, entity, context);
            verify(childModelMockTwo, times(0)).handle(command, entity, context);
        }

        @Test
        void creationalCommandHandlerBeingCalledWithInstanceResultsInFailedMessageStream() {
            GenericCommandMessage command = new GenericCommandMessage(
                    new MessageType(PARENT_ONLY_CREATIONAL_COMMAND), "myPayload");

            MessageStreamTestUtils.assertCompletedExceptionally(
                    metamodel.handleInstance(command, new TestEntity(), context),
                    EntityAlreadyExistsForCreationalCommandHandlerException.class,
                    "Creational command handler for command [ParentCreationalCommand#0.0.1] encountered an already existing entity"
            );
        }

        @Test
        void returnsFailedMessageStreamOnUnknownCommandType() {
            GenericCommandMessage command = new GenericCommandMessage(
                    new MessageType("UnknownCommand"), "myPayload");

            MessageStreamTestUtils.assertCompletedExceptionally(
                    metamodel.handleCreate(command, context),
                    NoHandlerForCommandException.class,
                    "No command handler was found for command of type [UnknownCommand#0.0.1] for entity ["
            );
        }

        @Test
        void returnsFailedMessageStreamIfParentCommandHandlerThrowsException() {
            GenericCommandMessage command = new GenericCommandMessage(
                    new MessageType(PARENT_ONLY_CREATIONAL_COMMAND), "myPayload");
            when(parentCreationalCommandHandler.handle(any(), any())).thenThrow(new IllegalStateException(
                    "Test exception"));

            MessageStreamTestUtils.assertCompletedExceptionally(
                    metamodel.handleCreate(command, context),
                    IllegalStateException.class,
                    "Test exception"
            );
        }
    }

    @Test
    void withoutEntityEvolverWillStillEvolveChildren() {
        metamodel = ConcreteEntityMetamodel
                .forEntityClass(TestEntity.class)
                .instanceCommandHandler(SHARED_COMMAND, parentInstanceCommandHandler)
                .instanceCommandHandler(PARENT_ONLY_INSTANCE_COMMAND, parentInstanceCommandHandler)
                .addChild(childModelMockOne)
                .addChild(childModelMockTwo)
                .build();

        GenericEventMessage event = new GenericEventMessage(SHARED_EVENT, "myPayload");
        metamodel.evolve(entity, event, context);

        verify(childModelMockOne).evolve(entity, event, context);
        verify(childModelMockTwo).evolve(entity, event, context);
    }

    @Test
    void callsChildrenEvolversAndParentEvolverInThatOrder() {
        GenericEventMessage event = new GenericEventMessage(SHARED_EVENT, "myPayload");
        metamodel.evolve(entity, event, context);

        InOrder inOrder = inOrder(childModelMockOne, childModelMockTwo, parentEntityEvolver);
        inOrder.verify(childModelMockOne).evolve(entity, event, context);
        // Verify without in-order, as the one and two child invocation is undefined
        verify(childModelMockOne).evolve(entity, event, context);
        inOrder.verify(parentEntityEvolver).evolve(entity, event, context);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void returnsSupportedCommandsForBothParentAndChild() {
        Set<QualifiedName> commandNames = metamodel.supportedCommands();
        assertTrue(commandNames.contains(PARENT_ONLY_CREATIONAL_COMMAND));
        assertTrue(commandNames.contains(PARENT_ONLY_INSTANCE_COMMAND));
        assertTrue(commandNames.contains(CHILD_ONE_ONLY_COMMAND));
        assertTrue(commandNames.contains(CHILD_TWO_ONLY_COMMAND));
        assertTrue(commandNames.contains(SHARED_COMMAND));
    }

    @Test
    void returnsFailedMessageStreamIfChildCommandHandlerThrowsException() {
        GenericCommandMessage command =
                new GenericCommandMessage(new MessageType(CHILD_TWO_ONLY_COMMAND), "myPayload");
        when(childModelMockTwo.handle(any(), any(), any())).thenThrow(new IllegalStateException("Test exception"));


        MessageStreamTestUtils.assertCompletedExceptionally(
                metamodel.handleInstance(command, entity, context),
                IllegalStateException.class,
                "Test exception"
        );
    }

    @Test
    void returnsCorrectEntityType() {
        assertEquals(TestEntity.class, metamodel.entityType());
    }


    @Test
    void correctlyDescribesComponent() {
        var descriptor = new MockComponentDescriptor();

        metamodel.describeTo(descriptor);

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
        assertEquals(List.of(childModelMockOne, childModelMockTwo), descriptor.getProperty("children"));
    }

    @Nested
    @DisplayName("Builder verifications")
    class BuilderVerifications {

        EntityMetamodelBuilder<TestEntity> builder = ConcreteEntityMetamodel
                .forEntityClass(TestEntity.class);

        @Test
        void canNotAddCommandHandlerForNullQualifiedName() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> builder.instanceCommandHandler(null, parentInstanceCommandHandler));
        }

        @Test
        void canNotAddNullCommandHandlerForQualifiedName() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class,
                         () -> builder.instanceCommandHandler(PARENT_ONLY_INSTANCE_COMMAND, null));
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
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class, () -> builder.addChild(null));
        }

        @Test
        void canAddNullEvolver() {
            assertDoesNotThrow(() -> builder.entityEvolver(null));
        }

        @Test
        void canNotStartBuilderForNullEntityType() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class, () -> ConcreteEntityMetamodel.forEntityClass(null));
        }

        @Test
        void canNotAddChildThatSupportsCreationalCommand() {
            //noinspection rawtypes
            EntityMetamodel childEntityMetamodel = mock(EntityMetamodel.class);
            when(childEntityMetamodel.supportedCreationalCommands()).thenReturn(Set.of(CHILD_ONE_ONLY_COMMAND));
            //noinspection unchecked
            EntityChildMetamodel<TestChildEntityOne, TestEntity> childModel = mock(EntityChildMetamodel.class);
            //noinspection unchecked
            when(childModel.entityMetamodel()).thenReturn(childEntityMetamodel);
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