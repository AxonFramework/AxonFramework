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
 * Abstract {@link EntityChildModel} that implements common functionality for most implementations. It defines how to
 * handle commands and events for a child entity. The implementor is responsible for defining how to resolve the child
 * entities from the parent ({@link #getChildEntities(Object)}) and how to apply the evolved child entities to the
 * parent ({@link #applyEvolvedChildEntities(Object, List)}).
 *
 * @param <C> The type of the child entity.
 * @param <P> The type of the parent entity.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public abstract class AbstractEntityChildModel<C, P> implements EntityChildModel<C, P> {

    protected final EntityModel<C> childEntityModel;
    protected final ChildEntityMatcher<C, CommandMessage<?>> commandTargetMatcher;
    protected final ChildEntityMatcher<C, EventMessage<?>> eventTargetMatcher;

    protected AbstractEntityChildModel(
            @Nonnull EntityModel<C> childEntityModel,
            @Nonnull ChildEntityMatcher<C, CommandMessage<?>> commandTargetMatcher,
            @Nonnull ChildEntityMatcher<C, EventMessage<?>> eventTargetMatcher
    ) {
        this.childEntityModel = requireNonNull(childEntityModel, "The childEntityModel may not be null.");
        this.commandTargetMatcher =
                requireNonNull(commandTargetMatcher, "The commandTargetMatcher may not be null.");
        this.eventTargetMatcher =
                requireNonNull(eventTargetMatcher, "The eventTargetMatcher may not be null.");
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
        return childEntityModel.handleInstance(message, matchingChildEntities.getFirst(), context);
    }

    protected abstract List<C> getChildEntities(P entity);

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
        return applyEvolvedChildEntities(entity, evolvedEntities);
    }

    protected abstract P applyEvolvedChildEntities(P entity, List<C> evolvedChildEntities);

    @Nonnull
    @Override
    public Class<C> entityType() {
        return childEntityModel.entityType();
    }

    protected abstract static class Builder<C, P, R extends Builder<C, P, R>> {

        protected final EntityModel<C> childEntityModel;
        protected ChildEntityMatcher<C, CommandMessage<?>> commandTargetMatcher = (child, command) -> true;
        protected ChildEntityMatcher<C, EventMessage<?>> eventTargetMatcher = (child, event) -> true;

        @SuppressWarnings("unused") // Is used for generics
        protected Builder(@Nonnull Class<P> parentClass,
                          @Nonnull EntityModel<C> childEntityModel) {
            requireNonNull(parentClass, "The parentClass may not be null.");
            this.childEntityModel = requireNonNull(childEntityModel, "The childEntityModel may not be null.");
        }

        /**
         * Sets the {@link ChildEntityMatcher} to use for matching the child entities to the command. This is used to
         * filter the child entities based on the command. One entity must match an incoming command. If no match is
         * found, an {@link ChildEntityNotFoundException} is thrown. If multiple matches are found, an
         * {@link ChildAmbiguityException} is thrown.
         *
         * @param commandTargetMatcher The {@link ChildEntityMatcher} to use for matching the child entities to the
         *                             command.
         * @return This builder instance.
         */
        @SuppressWarnings("unchecked")
        public R commandTargetMatcher(
                @Nonnull ChildEntityMatcher<C, CommandMessage<?>> commandTargetMatcher) {
            this.commandTargetMatcher = requireNonNull(commandTargetMatcher,
                                                       "The commandTargetMatcher may not be null.");
            return (R) this;
        }

        /**
         * Sets the {@link ChildEntityMatcher} to use for matching the child entities to the event. This is used to
         * filter the child entities based on the event. The amount of entities that match an incoming event is not
         * restricted. Entities that match are evolved, while entities that do not match are ignored and remain in the
         * same state.
         *
         * @param eventTargetMatcher The {@link ChildEntityMatcher} to use for matching the child entities to the
         *                           event.
         * @return This builder instance.
         */
        @SuppressWarnings("unchecked")
        public R eventTargetMatcher(@Nonnull ChildEntityMatcher<C, EventMessage<?>> eventTargetMatcher) {
            this.eventTargetMatcher = requireNonNull(eventTargetMatcher, "The eventTargetMatcher may not be null.");
            return (R) this;
        }
    }
}
