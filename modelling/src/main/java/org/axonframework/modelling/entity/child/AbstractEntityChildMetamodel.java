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

package org.axonframework.modelling.entity.child;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.common.BuilderUtils;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.modelling.entity.ChildEntityNotFoundException;
import org.axonframework.modelling.entity.EntityMetamodel;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Abstract {@link EntityChildMetamodel} that implements common functionality for most implementations. It
 * defines how to handle commands and events for a child entity. The implementor is responsible for defining how to
 * resolve the child entities from the parent ({@link #getChildEntities(Object)}) and how to apply the evolved child
 * entities to the parent ({@link #applyEvolvedChildEntities(Object, List)}).
 *
 * @param <C> The type of the child entity.
 * @param <P> The type of the parent entity.
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
public abstract class AbstractEntityChildMetamodel<C, P> implements EntityChildMetamodel<C, P> {

    protected final EntityMetamodel<C> metamodel;
    protected final CommandTargetResolver<C> commandTargetResolver;
    protected final EventTargetMatcher<C> eventTargetMatcher;

    protected AbstractEntityChildMetamodel(
            @Nonnull EntityMetamodel<C> metamodel,
            @Nonnull CommandTargetResolver<C> commandTargetResolver,
            @Nonnull EventTargetMatcher<C> eventTargetMatcher
    ) {
        this.metamodel = requireNonNull(metamodel, "The metamodel may not be null.");
        this.commandTargetResolver =
                requireNonNull(commandTargetResolver, "The commandTargetResolver may not be null.");
        this.eventTargetMatcher =
                requireNonNull(eventTargetMatcher, "The eventTargetMatcher may not be null.");
    }

    @Nonnull
    @Override
    public Set<QualifiedName> supportedCommands() {
        return metamodel.supportedCommands();
    }

    @Override
    public boolean canHandle(@Nonnull CommandMessage message,
                             @Nonnull P parentEntity,
                             @Nonnull ProcessingContext context) {
        if (!supportedCommands().contains(message.type().qualifiedName())) {
            return false;
        }
        List<C> childEntities = getChildEntities(parentEntity);
        if (childEntities.isEmpty()) {
            return false;
        }
        return commandTargetResolver.getTargetChildEntity(childEntities, message, context) != null;
    }

    @Nonnull
    @Override
    public MessageStream.Single<CommandResultMessage> handle(@Nonnull CommandMessage message,
                                                                @Nonnull P parentEntity,
                                                                @Nonnull ProcessingContext context) {
        List<C> childEntities = getChildEntities(parentEntity);
        C targetChildEntity = commandTargetResolver.getTargetChildEntity(childEntities, message, context);
        if (targetChildEntity == null) {
            return MessageStream.failed(new ChildEntityNotFoundException(message, parentEntity));
        }
        return metamodel.handleInstance(message, targetChildEntity, context);
    }

    protected abstract List<C> getChildEntities(P entity);

    @Override
    public P evolve(@Nonnull P entity, @Nonnull EventMessage event, @Nonnull ProcessingContext context) {
        final AtomicBoolean evolvedChildEntity = new AtomicBoolean(false);
        var evolvedEntities = getChildEntities(entity)
                .stream()
                .map(child -> {
                    if (eventTargetMatcher.matches(child, event, context)) {
                        evolvedChildEntity.set(true);
                        return metamodel.evolve(child, event, context);
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
        return metamodel.entityType();
    }

    protected abstract static class Builder<C, P, R extends Builder<C, P, R>> {

        protected final EntityMetamodel<C> metamodel;
        protected CommandTargetResolver<C> commandTargetResolver;
        protected EventTargetMatcher<C> eventTargetMatcher;

        @SuppressWarnings("unused") // Is used for generics
        protected Builder(@Nonnull Class<P> parentClass,
                          @Nonnull EntityMetamodel<C> metamodel) {
            requireNonNull(parentClass, "The parentClass may not be null.");
            this.metamodel = requireNonNull(metamodel, "The metamodel may not be null.");
        }

        /**
         * Sets the {@link CommandTargetResolver} to use for resolving the child entity to handle the command. This
         * should return one child entity, or {@code null} if no child entity should handle the command.
         *
         * @param commandTargetResolver The {@link CommandTargetResolver} to use for resolving the child entity to
         *                              handle the command.
         * @return This builder instance.
         */
        @SuppressWarnings("unchecked")
        public R commandTargetResolver(@Nonnull CommandTargetResolver<C> commandTargetResolver) {
            this.commandTargetResolver = requireNonNull(commandTargetResolver,
                                                        "The commandTargetResolver may not be null.");
            return (R) this;
        }

        protected void validate() {
            BuilderUtils.assertNonNull(commandTargetResolver,
                                       "The commandTargetResolver must be set before building the metamodel.");
            BuilderUtils.assertNonNull(eventTargetMatcher,
                                       "The eventTargetMatcher must be set before building the metamodel.");
        }

        /**
         * Sets the {@link EventTargetMatcher} to determine whether a child entity should handle the given
         * {@link EventMessage}. This should return {@code true} if the child entity should handle the event, or
         * {@code false} if it should not.
         *
         * @param eventTargetMatcher The {@link EventTargetMatcher} to use for matching the child entities to the
         *                           event.
         * @return This builder instance.
         */
        @SuppressWarnings("unchecked")
        public R eventTargetMatcher(@Nonnull EventTargetMatcher<C> eventTargetMatcher) {
            this.eventTargetMatcher = requireNonNull(eventTargetMatcher, "The eventTargetMatcher may not be null.");
            return (R) this;
        }
    }
}
