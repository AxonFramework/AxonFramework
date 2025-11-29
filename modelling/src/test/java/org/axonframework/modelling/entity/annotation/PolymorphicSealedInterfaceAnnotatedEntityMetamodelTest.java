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

package org.axonframework.modelling.entity.annotation;

import org.axonframework.messaging.core.unitofwork.StubProcessingContext;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.modelling.entity.domain.development.TaskState;
import org.axonframework.modelling.entity.domain.development.events.TaskAssigned;
import org.axonframework.modelling.entity.domain.development.events.TaskCreated;
import org.junit.jupiter.api.*;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link AnnotatedEntityMetamodel} through the {@link TaskState} domain model. This domain model has been
 * designed to test support for polymorphic immutable entities (sealed interface) of the
 * {@link AnnotatedEntityMetamodel}.
 *
 * @author Jakob Hatzl
 */
class PolymorphicSealedInterfaceAnnotatedEntityMetamodelTest extends AbstractAnnotatedEntityMetamodelTest<TaskState> {

    @Override
    protected AnnotatedEntityMetamodel<TaskState> getMetamodel() {
        return AnnotatedEntityMetamodel.forPolymorphicType(
                TaskState.class,
                Set.of(TaskState.InitialTask.class,
                       TaskState.CreatedTask.class,
                       TaskState.AssignedTask.class,
                       TaskState.CompletedTask.class),
                parameterResolverFactory,
                messageTypeResolver,
                messageConverter,
                eventConverter
        );
    }

    @Nested
    @DisplayName("")
    class SealedInterfaceEntityEvolutionTests {

        @Test
        void canEvolveFromInitialState() {
            // Given no earlier state
            var initialState = new TaskState.InitialTask();
            var taskId = "a9a530c7-7af8-4ad8-904d-65928d480dc2";
            var taskTitle = "Migrate Documentation";
            final EventMessage message = createEvent(new TaskCreated(taskId, taskTitle));

            // When
            var evolvedEntity = metamodel.evolve(initialState, message, StubProcessingContext.forMessage(message));

            // Then
            assertThat(evolvedEntity).isInstanceOf(TaskState.CreatedTask.class);
        }

        @Test
        void canEvolveCreatedToAssigned() {
            // Given no earlier state
            var taskId = "9e184278-52e4-496f-84b8-f4e4276c7a21";
            var assignee = "John Dorian";
            var currentState = new TaskState.CreatedTask(taskId);
            final EventMessage message = createEvent(new TaskAssigned(taskId, assignee));

            // When
            var evolvedEntity = metamodel.evolve(currentState, message, StubProcessingContext.forMessage(message));

            // Then
            assertThat(evolvedEntity).isInstanceOf(TaskState.AssignedTask.class);
        }
    }
}