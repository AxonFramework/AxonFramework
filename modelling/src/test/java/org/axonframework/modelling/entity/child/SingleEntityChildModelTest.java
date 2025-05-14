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
import org.axonframework.modelling.entity.child.mock.RecordingParentEntity;
import org.junit.jupiter.api.*;

import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SingleEntityChildModelTest {

    public static final QualifiedName COMMAND = new QualifiedName("Command");
    public static final QualifiedName EVENT = new QualifiedName("Event");

    private final EntityModel<RecordingChildEntity> childEntityEntityModel = mock(EntityModel.class);
    private final ChildEntityFieldDefinition<RecordingParentEntity, RecordingChildEntity> childEntityFieldDefinition = mock(
            ChildEntityFieldDefinition.class);

    private final SingleEntityChildModel<RecordingChildEntity, RecordingParentEntity> testSubject = SingleEntityChildModel
            .forEntityModel(RecordingParentEntity.class, childEntityEntityModel)
            .childEntityFieldDefinition(childEntityFieldDefinition)
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
            when(childEntityEntityModel.handle(any(), any(), any())).thenReturn(
                    MessageStream.just(new GenericCommandResultMessage<>(new MessageType(String.class), "result")));
        }

        @Test
        void commandForChildIsForwardedToFoundChildEntity() {
            RecordingChildEntity entityToBeFound = new RecordingChildEntity();
            when(childEntityFieldDefinition.getChildValue(any())).thenReturn(entityToBeFound);

            GenericCommandMessage<String> command = new GenericCommandMessage<>(new MessageType(COMMAND), "myPayload");

            var result = testSubject.handle(command, parentEntity, context);
            assertEquals("result", result.asCompletableFuture().join().message().getPayload());

            verify(childEntityFieldDefinition).getChildValue(parentEntity);
            verify(childEntityEntityModel).handle(command, entityToBeFound, context);
        }

        @Test
        void commandResultsInFailedMessageStreamWhenChildEntityIsNotFound() {
            when(childEntityFieldDefinition.getChildValue(any())).thenReturn(null);

            MessageStreamTestUtils.assertCompletedExceptionally(
                    testSubject.handle(commandMessage, parentEntity, context),
                    ChildEntityNotFoundException.class,
                    "No available child entity found for command of type [Command#0.0.1]. State of parent entity ["
            );
        }

        @Test
        void supportedCommandsIsSameAsChildEntity() {
            when(childEntityEntityModel.supportedCommands()).thenReturn(Set.of(COMMAND));

            assertEquals(Set.of(COMMAND), testSubject.supportedCommands());
        }
    }

    @Test
    void entityTypeIsSameAsChildEntity() {
        when(childEntityEntityModel.entityType()).thenReturn(RecordingChildEntity.class);

        assertEquals(RecordingChildEntity.class, testSubject.entityType());
    }

    @Nested
    @DisplayName("Event handling")
    public class EventHandling {

        private final EventMessage<String> event = new GenericEventMessage<>(new MessageType(EVENT), "myPayload");


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
            RecordingChildEntity childEntity = new RecordingChildEntity();
            when(childEntityFieldDefinition.getChildValue(any())).thenReturn(childEntity);
            when(childEntityEntityModel.evolve(any(), any(), any())).thenAnswer(answ -> {
                RecordingChildEntity child = answ.getArgument(0);
                EventMessage<String> event = answ.getArgument(1);
                return child.evolve("child evolve: " + event.getPayload());
            });
            when(childEntityFieldDefinition.evolveParentBasedOnChildInput(any(), any())).thenAnswer(answ -> {
                RecordingParentEntity parent = answ.getArgument(0);
                RecordingChildEntity child = answ.getArgument(1);
                return parent.evolve("parent evolve: " + child.getEvolves());
            });

            RecordingParentEntity result = testSubject.evolve(parentEntity, event, context);

            assertEquals("parent evolve: [child evolve: myPayload]", result.getEvolves().getFirst());

            verify(childEntityFieldDefinition).getChildValue(parentEntity);
            verify(childEntityFieldDefinition).evolveParentBasedOnChildInput(
                    eq(parentEntity),
                    argThat(a -> a.getEvolves().contains("child evolve: myPayload"))
            );
            verify(childEntityEntityModel).evolve(childEntity, event, context);
        }

        @Test
        void childEntityCanBeEvolvedToNull() {
            RecordingChildEntity childEntity = new RecordingChildEntity();
            when(childEntityFieldDefinition.getChildValue(any())).thenReturn(childEntity);
            when(childEntityEntityModel.evolve(any(), any(), any())).thenReturn(null);
            when(childEntityFieldDefinition.evolveParentBasedOnChildInput(any(), any())).thenAnswer(answ -> {
                RecordingParentEntity parent = answ.getArgument(0);
                RecordingChildEntity child = answ.getArgument(1);
                return parent.evolve("parent evolve: " + child);
            });

            RecordingParentEntity result = testSubject.evolve(parentEntity, event, context);

            assertEquals("parent evolve: null", result.getEvolves().getFirst());

            verify(childEntityFieldDefinition).getChildValue(parentEntity);
            verify(childEntityFieldDefinition).evolveParentBasedOnChildInput(
                    eq(parentEntity),
                    argThat(Objects::isNull)
            );
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