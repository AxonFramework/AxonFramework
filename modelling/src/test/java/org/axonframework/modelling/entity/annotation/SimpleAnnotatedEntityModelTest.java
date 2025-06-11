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

import org.axonframework.modelling.entity.domain.development.Project;
import org.axonframework.modelling.entity.domain.todo.Todo;
import org.axonframework.modelling.entity.domain.todo.commands.CreateTodoItem;
import org.axonframework.modelling.entity.domain.todo.commands.FinishTodoItem;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the {@link AnnotatedEntityModel} through the {@link Project} domain model. This domain model has been designed
 * to touch as many aspects of the {@link AnnotatedEntityModel} as possible, such as polymorphic types, command routing,
 * and event publication.
 * <p>
 * Note that the domain might not be feature-complete or realistic. In addition, while the model is not event-sourced
 * but state-sourced, it does apply events that are then applied to the model state. This is done to ensure that the
 * model behaves as expected and that the events are published correctly. This allows us to assert both the events
 * published and the state of the model after the commands have been handled.
 *
 * @author Mitchell Herrijgers
 */
class SimpleAnnotatedEntityModelTest extends AbstractAnnotatedEntityModelTest<Todo> {

    @Override
    protected AnnotatedEntityModel<Todo> getModel() {
        return AnnotatedEntityModel.forConcreteType(
                Todo.class,
                parameterResolverFactory,
                messageTypeResolver
        );
    }

    @Test
    void canCreateTodoItem() {
        // Given no existing model state, i.e. no-arg constructor
        modelState = new Todo();

        // When
        handleInstanceCommand(new CreateTodoItem("af-5", "Create the best ES-framework ever!"));

        // Then
        assertThat(modelState.getDescription()).isEqualTo("Create the best ES-framework ever!");
        assertThat(modelState.getId()).isEqualTo("af-5");
        assertThat(modelState.isCompleted()).isFalse();
    }

    @Test
    void canFinishTodoItem() {
        // Given a model state with an existing item
        modelState = new Todo();
        handleInstanceCommand(new CreateTodoItem("af-5", "Create the best ES-framework ever!"));

        // When
        handleInstanceCommand(new FinishTodoItem("af-5"));

        // Then
        assertThat(modelState.isCompleted()).isTrue();
    }
}