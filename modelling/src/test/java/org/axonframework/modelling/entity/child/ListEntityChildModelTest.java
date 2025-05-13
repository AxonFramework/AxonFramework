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

package org.axonframework.modelling.entity.child;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageStreamTestUtils;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.StubProcessingContext;
import org.axonframework.modelling.entity.ChildEntityNotFoundException;
import org.axonframework.modelling.entity.EntityModel;
import org.axonframework.modelling.entity.child.mock.RecordingChildEntity;
import org.axonframework.modelling.entity.child.mock.RecordingEntity;
import org.axonframework.modelling.entity.child.mock.RecordingParentEntity;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListEntityChildModelTest {

    private static final QualifiedName COMMAND = new QualifiedName("Command");
    private static final QualifiedName EVENT = new QualifiedName("Event");
    private static final String COMMAND_MATCHING_ID = "1337";
    private static final String COMMAND_SKIPPING_ID = "123";
    private static final String EVENT_MATCHING_ID = "42";
    private static final String EVENT_SKIPPING_ID = "456";

    private final EntityModel<RecordingChildEntity> childEntityEntityModel = mock(EntityModel.class);
    private final ChildEntityFieldDefinition<RecordingParentEntity, List<RecordingChildEntity>> childEntityFieldDefinition = mock(
            ChildEntityFieldDefinition.class);

    private final ListEntityChildModel<RecordingChildEntity, RecordingParentEntity> testSubject = ListEntityChildModel
            .forEntityModel(RecordingParentEntity.class, childEntityEntityModel)
            .childEntityFieldDefinition(childEntityFieldDefinition)
            .commandTargetMatcher((child, msg) -> child.getId().contains(COMMAND_MATCHING_ID))
            .eventTargetMatcher((child, msg) -> child.getId().contains(EVENT_MATCHING_ID))
            .build();

    private final StubProcessingContext context = new StubProcessingContext();
    private final RecordingParentEntity parentEntity = new RecordingParentEntity();

    @Nested
    @DisplayName("Command handling")
    public class CommandHandling {
        private final CommandMessage<String> commandMessage = new GenericCommandMessage<>(
                new MessageType(COMMAND), "myPayload"
        );

        @BeforeEach
        void setUp() {
            when(childEntityEntityModel.handleInstance(any(), any(), any())).thenAnswer(answ -> {
                RecordingChildEntity child = answ.getArgument(1);
                return MessageStream.just(new GenericCommandResultMessage<>(
                        new MessageType(String.class),
                        child.getId() + "-result")
                );
            });
        }

        @Test
        void commandForChildIsForwardedToMatchingChildEntity() {
            RecordingChildEntity entityToBeFound = new RecordingChildEntity(COMMAND_MATCHING_ID);
            RecordingChildEntity entityToBeSkipped = new RecordingChildEntity(COMMAND_SKIPPING_ID);
            when(childEntityFieldDefinition.getChildValue(any())).thenReturn(
                    List.of(entityToBeFound, entityToBeSkipped)
            );

            var result = testSubject.handle(commandMessage, parentEntity, context);
            assertEquals("1337-result", result.asCompletableFuture().join().message().getPayload());

            verify(childEntityFieldDefinition).getChildValue(parentEntity);
            verify(childEntityEntityModel).handleInstance(commandMessage, entityToBeFound, context);
            verify(childEntityEntityModel, times(0)).handleInstance(commandMessage, entityToBeSkipped, context);
        }

        @Test
        void commandResultsInFailedMessageStreamWhenChildEntityIsNotFound() {
            RecordingParentEntity parentEntity = new RecordingParentEntity();
            when(childEntityFieldDefinition.getChildValue(any())).thenReturn(null);

            MessageStreamTestUtils.assertCompletedExceptionally(
                    testSubject.handle(commandMessage, parentEntity, context),
                    ChildEntityNotFoundException.class,
                    "No available child entity found for command of type [Command#0.0.1]. State of parent entity ["
            );
        }

        @Test
        void commandResultsInFailedMessageStreamWhenNoChildEntityMatches() {
            RecordingParentEntity parentEntity = new RecordingParentEntity();
            RecordingChildEntity entityToBeSkipped = new RecordingChildEntity("l0ser");
            when(childEntityFieldDefinition.getChildValue(any())).thenReturn(List.of(entityToBeSkipped));

            MessageStreamTestUtils.assertCompletedExceptionally(
                    testSubject.handle(commandMessage, parentEntity, context),
                    ChildEntityNotFoundException.class,
                    "No available child entity found for command of type [Command#0.0.1]. State of parent entity ["
            );
        }

        @Test
        void commandResultsInFailedMessageStreamWhenMultipleMatchingEntitiesAreFound() {
            RecordingParentEntity parentEntity = new RecordingParentEntity();
            RecordingChildEntity entityToBeFound1 = new RecordingChildEntity("1337-1");
            RecordingChildEntity entityToBeFound2 = new RecordingChildEntity("1337-2");
            when(childEntityFieldDefinition.getChildValue(any())).thenReturn(List.of(entityToBeFound1,
                                                                                     entityToBeFound2));


            MessageStreamTestUtils.assertCompletedExceptionally(
                    testSubject.handle(commandMessage, parentEntity, context),
                    ChildAmbiguityException.class,
                    "Multiple child entities found for command of type [Command#0.0.1]. State of parent entity ["
            );
        }
    }

    @Test
    void supportedCommandsIsSameAsChildEntity() {
        when(childEntityEntityModel.supportedCommands()).thenReturn(Set.of(COMMAND));

        assertEquals(Set.of(COMMAND), testSubject.supportedCommands());
    }

    @Test
    void entityTypeIsSameAsChildEntity() {
        when(childEntityEntityModel.entityType()).thenReturn(RecordingChildEntity.class);

        assertEquals(RecordingChildEntity.class, testSubject.entityType());
    }

    @Test
    void returnsEntityModel() {
        assertEquals(childEntityEntityModel, testSubject.entityModel());
    }

    @Nested
    @DisplayName("Event handling")
    public class EventHandling {

        private final EventMessage<String> event = new GenericEventMessage<>(new MessageType(EVENT), "myPayload");

        @BeforeEach
        void setUp() {
            when(childEntityEntityModel.evolve(any(), any(), any())).thenAnswer(answ -> {
                RecordingChildEntity child = answ.getArgument(0);
                EventMessage<String> event = answ.getArgument(1);
                return child.evolve("child evolve: " + event.getPayload());
            });
            when(childEntityFieldDefinition.evolveParentBasedOnChildInput(any(), any())).thenAnswer(answ -> {
                RecordingParentEntity parent = answ.getArgument(0);
                List<RecordingChildEntity> child = answ.getArgument(1);
                return parent.evolve(
                        "parent evolve: [" + child.stream().map(RecordingEntity::getEvolves).map(Objects::toString)
                                                  .collect(
                                                          Collectors.joining(",")) + "]");
            });
        }

        @Test
        void doesNotEvolveEntityWhenChildEntityIsNotFound() {
            when(childEntityFieldDefinition.getChildValue(any())).thenReturn(null);

            RecordingParentEntity result = testSubject.evolve(parentEntity, event, context);

            verify(childEntityFieldDefinition).getChildValue(parentEntity);
            verify(childEntityFieldDefinition, never()).evolveParentBasedOnChildInput(any(), any());

            assertEquals(parentEntity, result);
            assertTrue(parentEntity.getEvolves().isEmpty());
        }

        @Test
        void evolvesChildEntityAndParentEntityWhenChildEntityIsFound() {
            RecordingChildEntity childEntity = new RecordingChildEntity("42");
            when(childEntityFieldDefinition.getChildValue(any())).thenReturn(List.of(childEntity));

            RecordingParentEntity result = testSubject.evolve(parentEntity, event, context);

            assertEquals("parent evolve: [[child evolve: myPayload]]", result.getEvolves().getFirst());
            verify(childEntityFieldDefinition).getChildValue(parentEntity);
            verify(childEntityFieldDefinition).evolveParentBasedOnChildInput(
                    eq(parentEntity), argThat(a -> a.getFirst().getEvolves().contains("child evolve: myPayload")));
            verify(childEntityEntityModel).evolve(childEntity, event, context);
        }

        @Test
        void evolvesOnlyMatchingChildEvolves() {
            RecordingChildEntity matchingEntityOne = new RecordingChildEntity(EVENT_MATCHING_ID + "-1");
            RecordingChildEntity matchingEntityTwo = new RecordingChildEntity(EVENT_MATCHING_ID + "-2");
            RecordingChildEntity nonMatchingEntity1 = new RecordingChildEntity(EVENT_SKIPPING_ID + "-3");
            RecordingChildEntity nonMatchingEntity2 = new RecordingChildEntity(EVENT_SKIPPING_ID + "-4");
            when(childEntityFieldDefinition.getChildValue(any())).thenReturn(
                    List.of(matchingEntityOne, nonMatchingEntity2, matchingEntityTwo, nonMatchingEntity1)
            );
            RecordingParentEntity result = testSubject.evolve(parentEntity, event, context);

            assertEquals("parent evolve: [[child evolve: myPayload],[],[child evolve: myPayload],[]]",
                         result.getEvolves().getFirst());
            verify(childEntityFieldDefinition).getChildValue(parentEntity);
            verify(childEntityEntityModel).evolve(matchingEntityOne, event, context);
            verify(childEntityEntityModel).evolve(matchingEntityTwo, event, context);
            verify(childEntityEntityModel, times(0)).evolve(nonMatchingEntity1, event, context);
            verify(childEntityEntityModel, times(0)).evolve(nonMatchingEntity2, event, context);
        }

        @Test
        void evolvedChildEntitiesToNullAreRemovedFromParent() {
            RecordingChildEntity childEntity = new RecordingChildEntity("42");
            when(childEntityFieldDefinition.getChildValue(any())).thenReturn(List.of(childEntity));

            // Reset the standard evolve, to evolve to null
            reset(childEntityEntityModel);
            when(childEntityEntityModel.evolve(any(), any(), any())).thenAnswer(answ -> null);

            RecordingParentEntity result = testSubject.evolve(parentEntity, event, context);

            assertEquals("parent evolve: []", result.getEvolves().getFirst());
            verify(childEntityFieldDefinition).getChildValue(parentEntity);
            verify(childEntityFieldDefinition).evolveParentBasedOnChildInput(
                    eq(parentEntity), argThat(List::isEmpty));
            verify(childEntityEntityModel).evolve(childEntity, event, context);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Nested
    @DisplayName("Builder verification")
    public class BuilderVerification {

        @Test
        void canNotCompleteBuilderWithoutFieldDefinition() {
            var builder = SingleEntityChildModel.forEntityModel(RecordingParentEntity.class, childEntityEntityModel);
            assertThrows(NullPointerException.class, builder::build);
        }

        @Test
        void canNotStartBuilderWithNullParentEntityClass() {
            assertThrows(NullPointerException.class,
                         () -> SingleEntityChildModel.forEntityModel(null, childEntityEntityModel));
        }

        @Test
        void canNotStartBuilderWithNullEntityModel() {
            assertThrows(NullPointerException.class,
                         () -> SingleEntityChildModel.forEntityModel(RecordingParentEntity.class, null));
        }
    }
}