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

import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.commandhandling.GenericCommandResultMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.StubProcessingContext;
import org.axonframework.modelling.entity.EntityModel;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SingleEntityChildModelTest {

    public static final QualifiedName COMMAND = new QualifiedName("Command");
    public static final QualifiedName EVENT = new QualifiedName("Event");

    private final EntityModel<SingleChildEntity> childEntityEntityModel = mock(EntityModel.class);
    private final ChildEntityFieldDefinition<ParentEntity, SingleChildEntity> childEntityFieldDefinition = mock(
            ChildEntityFieldDefinition.class);

    SingleEntityChildModel<SingleChildEntity, ParentEntity> testSubject = SingleEntityChildModel
            .forEntityModel(ParentEntity.class, childEntityEntityModel)
            .childEntityFieldDefinition(childEntityFieldDefinition)
            .build();

    @BeforeEach
    void setUp() {

        when(childEntityEntityModel.handle(any(), any(), any())).thenReturn(
                MessageStream.just(new GenericCommandResultMessage<>(new MessageType(String.class), "result")));
    }

    @Test
    void commandForChildIsForwardedToFoundChildEntity() {
        var testSubject = SingleEntityChildModel.forEntityModel(ParentEntity.class, childEntityEntityModel)
                                                .childEntityFieldDefinition(childEntityFieldDefinition)
                                                .build();

        ParentEntity parentEntity = mock(ParentEntity.class);
        SingleChildEntity entityToBeFound = new SingleChildEntity();
        StubProcessingContext context = new StubProcessingContext();
        when(childEntityFieldDefinition.getChildEntities(any())).thenReturn(entityToBeFound);

        GenericCommandMessage<String> command = new GenericCommandMessage<>(new MessageType(COMMAND), "myPayload");

        var result = testSubject.handle(command, parentEntity, context);
        assertEquals("result", result.asCompletableFuture().join().message().getPayload());

        verify(childEntityFieldDefinition).getChildEntities(parentEntity);
        verify(childEntityEntityModel).handle(command, entityToBeFound, context);
    }

    @Test
    void commandResultsInFailedMessageStreamWhenChildEntityIsNotFound() {
        var testSubject = SingleEntityChildModel.forEntityModel(ParentEntity.class, childEntityEntityModel)
                                                .childEntityFieldDefinition(childEntityFieldDefinition)
                                                .build();

        ParentEntity parentEntity = new ParentEntity();
        StubProcessingContext context = new StubProcessingContext();
        when(childEntityFieldDefinition.getChildEntities(any())).thenReturn(null);

        GenericCommandMessage<String> command = new GenericCommandMessage<>(new MessageType(COMMAND), "myPayload");

        var result = testSubject.handle(command, parentEntity, context);
        var exception = assertThrows(CompletionException.class, () -> {
            result.asCompletableFuture().join();
        });
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals("No child entity found for command Command on parent entity ParentEntityToString",
                     exception.getCause().getMessage());
    }

    @Test
    void supportedCommandsIsSameAsChildEntity() {
        var testSubject = SingleEntityChildModel.forEntityModel(ParentEntity.class, childEntityEntityModel)
                                                .childEntityFieldDefinition(childEntityFieldDefinition)
                                                .build();

        when(childEntityEntityModel.supportedCommands()).thenReturn(Set.of(COMMAND));

        assertEquals(Set.of(COMMAND), testSubject.supportedCommands());
    }

    @Test
    void entityTypeIsSameAsChildEntity() {
        var testSubject = SingleEntityChildModel.forEntityModel(ParentEntity.class, childEntityEntityModel)
                                                .childEntityFieldDefinition(childEntityFieldDefinition)
                                                .build();

        when(childEntityEntityModel.entityType()).thenReturn(SingleChildEntity.class);

        assertEquals(SingleChildEntity.class, testSubject.entityType());
    }

    @Test
    void doesNotEvolveEntityWhenChildEntityIsNotFound() {

        ParentEntity parentEntity = new ParentEntity();
        StubProcessingContext context = new StubProcessingContext();
        when(childEntityFieldDefinition.getChildEntities(any())).thenReturn(null);

        EventMessage<String> event = new GenericEventMessage<>(new MessageType(EVENT), "myPayload");

        ParentEntity result = testSubject.evolve(parentEntity, event, context);

        verify(childEntityFieldDefinition).getChildEntities(parentEntity);
        verify(childEntityFieldDefinition, never()).evolveParentBasedOnChildEntities(any(), any());

        assertEquals(parentEntity, result);
        assertTrue(parentEntity.getEvolves().isEmpty());
    }

    @Test
    void evolvesChildEntityAndParentEntityWhenChildEntityIsFound() {
        ParentEntity parentEntity = new ParentEntity();
        SingleChildEntity childEntity = new SingleChildEntity();
        StubProcessingContext context = new StubProcessingContext();
        when(childEntityFieldDefinition.getChildEntities(any())).thenReturn(childEntity);
        when(childEntityEntityModel.evolve(any(), any(), any())).thenAnswer(answ -> {
            SingleChildEntity child = answ.getArgument(0);
            EventMessage<String> event = answ.getArgument(1);
            return child.evolve("child evolve: " + event.getPayload());
        });
        when(childEntityFieldDefinition.evolveParentBasedOnChildEntities(any(), any())).thenAnswer(answ -> {
            ParentEntity parent = answ.getArgument(0);
            SingleChildEntity child = answ.getArgument(1);
            return parent.evolve("parent evolve: " + child.getEvolves());
        });

        EventMessage<String> event = new GenericEventMessage<>(new MessageType(EVENT), "myPayload");
        ParentEntity result = testSubject.evolve(parentEntity, event, context);

        assertEquals("parent evolve: [child evolve: myPayload]", result.getEvolves().getFirst());

        verify(childEntityFieldDefinition).getChildEntities(parentEntity);
        verify(childEntityFieldDefinition).evolveParentBasedOnChildEntities(eq(parentEntity), argThat(a -> a.getEvolves().contains("child evolve: myPayload")));
        verify(childEntityEntityModel).evolve(childEntity, event, context);
    }


    public static class SingleChildEntity {

        private final List<String> evolves;

        public SingleChildEntity(List<String> evolves) {
            this.evolves = evolves;
        }

        public SingleChildEntity() {
            this.evolves = List.of();
        }


        @Override
        public String toString() {
            return "ChildEntityToString";
        }

        public SingleChildEntity evolve(String description) {
            return new SingleChildEntity(List.of(description));
        }

        public List<String> getEvolves() {
            return evolves;
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Nested
    @DisplayName("Builder verification")
    public class BuilderVerification {
        @Test
        void canNotCompleteBuilderWithoutFieldDefinition() {
            var builder = SingleEntityChildModel.forEntityModel(ParentEntity.class, childEntityEntityModel);
            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        void canNotStartBuilderWithNullParentEntityClass() {
            assertThrows(NullPointerException.class, () -> SingleEntityChildModel.forEntityModel(null, childEntityEntityModel));
        }
        @Test
        void canNotStartBuilderWithNullEntityModel() {
            assertThrows(NullPointerException.class, () -> SingleEntityChildModel.forEntityModel(ParentEntity.class, null));
        }
    }

    public static class ParentEntity {

        private final List<String> evolves;

        public ParentEntity(List<String> evolves) {
            this.evolves = evolves;
        }

        public ParentEntity() {
            this.evolves = List.of();
        }

        @Override
        public String toString() {
            return "ParentEntityToString";
        }

        public ParentEntity evolve(String description) {
            return new ParentEntity(List.of(description));
        }

        public List<String> getEvolves() {
            return evolves;
        }
    }
}