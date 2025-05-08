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

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.entity.ChildEntityNotFoundException;
import org.axonframework.modelling.entity.EntityModel;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * An {@link EntityChildModel} that handles commands and events for a list of child entities. It will use the provided
 * {@link ChildEntityFieldDefinition} to resolve the child entities from the parent entity. Once the entities are
 * resolved, it will delegate the command- and event-handling to the child entity model(s), based on the
 * {@code commandTargetMatcher} and {@code eventTargetMatcher} respectively.
 * <p>
 * If the result of {@link ChildEntityFieldDefinition#getChildValue(Object)} is null, an empty list will be assumed.
 *
 * @param <C> The type of the child entity.
 * @param <P> The type of the parent entity.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public class ListEntityChildModel<C, P> implements EntityChildModel<C, P> {

    private final EntityModel<C> childEntityModel;
    private final ChildEntityFieldDefinition<P, List<C>> childEntityFieldDefinition;
    private final ChildEntityMatcher<C, CommandMessage<?>> commandTargetMatcher;
    private final ChildEntityMatcher<C, EventMessage<?>> eventTargetMatcher;

    private ListEntityChildModel(
            @Nonnull EntityModel<C> childEntityModel,
            @Nonnull ChildEntityFieldDefinition<P, List<C>> childEntityFieldDefinition,
            @Nonnull ChildEntityMatcher<C, CommandMessage<?>> commandTargetMatcher,
            @Nonnull ChildEntityMatcher<C, EventMessage<?>> eventTargetMatcher
    ) {
        this.childEntityModel = requireNonNull(childEntityModel, "The childEntityModel may not be null.");
        this.childEntityFieldDefinition =
                requireNonNull(childEntityFieldDefinition, "The childEntityFieldDefinition may not be null.");
        this.commandTargetMatcher =
                requireNonNull(commandTargetMatcher, "The commandTargetMatcher may not be null.");
        ;
        this.eventTargetMatcher =
                requireNonNull(eventTargetMatcher, "The eventTargetMatcher may not be null.");
        ;
    }

    @Nonnull
    @Override
    public Set<QualifiedName> supportedCommands() {
        return childEntityModel.supportedCommands();
    }

    @Override
    public boolean canHandle(@Nonnull CommandMessage<?> message, @Nonnull P parentEntity,
                             @Nonnull ProcessingContext context) {
        if (!supportedCommands().contains(message.type().qualifiedName())) {
            return false;
        }
        return getChildEntities(parentEntity)
                .stream()
                .anyMatch(child -> commandTargetMatcher.matches(child, message));
    }

    @Nonnull
    @Override
    public MessageStream.Single<CommandResultMessage<?>> handle(@Nonnull CommandMessage<?> message,
                                                                @Nonnull P parentEntity,
                                                                @Nonnull ProcessingContext context) {
        List<C> matchingChildEntities = getChildEntities(parentEntity)
                .stream()
                .filter(child -> commandTargetMatcher.matches(child, message))
                .toList();
        if (matchingChildEntities.isEmpty()) {
            return MessageStream.failed(new ChildEntityNotFoundException(message, parentEntity));
        }
        if (matchingChildEntities.size() > 1) {
            return MessageStream.failed(new ChildAmbiguityException(message, parentEntity));
        }
        return childEntityModel.handle(message, matchingChildEntities.getFirst(), context);
    }

    private List<C> getChildEntities(P entity) {
        List<C> childEntities = childEntityFieldDefinition
                .getChildValue(entity);
        if (childEntities == null) {
            return List.of();
        }
        return childEntities;
    }

    @Override
    public P evolve(@Nonnull P entity, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        final AtomicBoolean evolvedChildEntity = new AtomicBoolean(false);
        var evolvedEntities = getChildEntities(entity)
                .stream()
                .map(child -> {
                    if (eventTargetMatcher.matches(child, event)) {
                        evolvedChildEntity.set(true);
                        return childEntityModel.evolve(child, event, context);
                    }
                    return child;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!evolvedChildEntity.get()) {
            return entity;
        }
        return childEntityFieldDefinition.evolveParentBasedOnChildEntities(entity, evolvedEntities);
    }

    @Nonnull
    @Override
    public Class<C> entityType() {
        return childEntityModel.entityType();
    }

    @Override
    public String toString() {
        return "ListEntityChildModel{entityType=" + entityType().getName() + '}';
    }

    /**
     * Creates a new {@link Builder} for the given parent class and child entity model. The
     * {@link ChildEntityFieldDefinition} is required to resolve the child entities from the parent entity and evolve
     * the parent entity based on the child entities. The {@link ChildEntityMatcher commandTargetMatcher} and
     * {@link ChildEntityMatcher eventTargetMatcher} are used to match the child entities to the command and event
     * respectively. Without specifying these matchers, all child entities will be considered for all commands and
     * events.
     *
     * @param parentClass The class of the parent entity.
     * @param entityModel The {@link EntityModel} of the child entity.
     * @param <C>         The type of the child entity.
     * @param <P>         The type of the parent entity.
     * @return A new {@link Builder} for the given parent class and child entity model.
     */
    @Nonnull
    public static <C, P> Builder<C, P> forEntityModel(@Nonnull Class<P> parentClass,
                                                      @Nonnull EntityModel<C> entityModel
    ) {
        return new Builder<>(parentClass, entityModel);
    }

    /**
     * Builder for creating a {@link ListEntityChildModel} for the given parent class and child entity model. The
     * builder can be used to configure the child entity model and create a new instance of
     * {@link ListEntityChildModel}. The {@link ChildEntityFieldDefinition} is required to resolve the child entities
     * from the parent entity and evolve the parent entity based on the child entities. The
     * {@link ChildEntityMatcher commandTargetMatcher} and {@link ChildEntityMatcher eventTargetMatcher} are used to
     * match the child entities to the command and event respectively. Without specifying these matchers, all child
     * entities will be considered for all commands and events.
     *
     * @param <C> The type of the child entity.
     * @param <P> The type of the parent entity.
     */
    public static class Builder<C, P> {

        private final EntityModel<C> childEntityModel;
        private ChildEntityFieldDefinition<P, List<C>> childEntityFieldDefinition;
        private ChildEntityMatcher<C, CommandMessage<?>> commandTargetMatcher = (child, command) -> true;
        private ChildEntityMatcher<C, EventMessage<?>> eventTargetMatcher = (child, event) -> true;

        @SuppressWarnings("unused") // Is used for generics
        private Builder(@Nonnull Class<P> parentClass,
                        @Nonnull EntityModel<C> childEntityModel) {
            requireNonNull(parentClass, "The parentClass may not be null.");
            this.childEntityModel = requireNonNull(childEntityModel, "The childEntityModel may not be null.");
        }

        /**
         * Sets the {@link ChildEntityFieldDefinition} to use for resolving the child entities from the parent entity
         * and evolving the parent entity based on the evolved child entities.
         *
         * @param fieldDefinition The {@link ChildEntityFieldDefinition} to use for resolving the child entities from
         *                        the parent entity
         * @return This builder instance for a fluent API.
         */
        public Builder<C, P> childEntityFieldDefinition(
                @Nonnull ChildEntityFieldDefinition<P, List<C>> fieldDefinition) {
            this.childEntityFieldDefinition = requireNonNull(fieldDefinition,
                                                             "The childEntityFieldDefinition may not be null.");
            return this;
        }

        /**
         * Sets the {@link ChildEntityMatcher} to use for matching the child entities to the command. This is used to
         * filter the child entities based on the command. One entity must match an incoming command. If no match is
         * found, an exception is thrown. If multiple matches are found, an exception is thrown.
         *
         * @param commandTargetMatcher The {@link ChildEntityMatcher} to use for matching the child entities to the
         *                             command.
         * @return This builder instance.
         */
        public Builder<C, P> commandTargetMatcher(
                @Nonnull ChildEntityMatcher<C, CommandMessage<?>> commandTargetMatcher) {
            this.commandTargetMatcher = requireNonNull(commandTargetMatcher,
                                                       "The commandTargetMatcher may not be null.");
            return this;
        }

        /**
         * Sets the {@link ChildEntityMatcher} to use for matching the child entities to the event. This is used to
         * filter the child entities based on the event. The amount of entities that match an incoming event is not
         * restricted. Entities that match are evolved, while entities that do not match are ignored.
         *
         * @param eventTargetMatcher The {@link ChildEntityMatcher} to use for matching the child entities to the
         *                           event.
         * @return This builder instance.
         */
        public Builder<C, P> eventTargetMatcher(@Nonnull ChildEntityMatcher<C, EventMessage<?>> eventTargetMatcher) {
            this.eventTargetMatcher = requireNonNull(eventTargetMatcher, "The eventTargetMatcher may not be null.");
            return this;
        }

        /**
         * Builds a new {@link ListEntityChildModel} instance with the configured properties. The
         * {@link ChildEntityFieldDefinition} is required to be set before calling this method.
         *
         * @return A new {@link ListEntityChildModel} instance with the configured properties.
         */
        public ListEntityChildModel<C, P> build() {
            return new ListEntityChildModel<>(
                    childEntityModel,
                    childEntityFieldDefinition,
                    commandTargetMatcher,
                    eventTargetMatcher
            );
        }
    }
}
