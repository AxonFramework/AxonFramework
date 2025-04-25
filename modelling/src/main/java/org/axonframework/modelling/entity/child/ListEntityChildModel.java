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
import org.axonframework.modelling.entity.EntityModel;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ListEntityChildModel<C, P> implements EntityChildModel<C, P> {

    private final Class<P> parentClass;
    private final EntityModel<C> childEntityModel;
    private final Function<P, List<C>> childEntityResolver;
    private final BiFunction<P, List<C>, P> parentEntityEvolver;
    private final BiPredicate<C, CommandMessage<?>> commandTargetMatcher;
    private final BiPredicate<C, EventMessage<?>> eventTargetMatcher;

    private ListEntityChildModel(
            Class<P> parentclass,
            EntityModel<C> childEntityModel,
            Function<P, List<C>> childEntityResolver,
            BiPredicate<C, CommandMessage<?>> commandTargetMatcher,
            BiFunction<P, List<C>, P> parentEntityEvolver,
            BiPredicate<C, EventMessage<?>> eventTargetMatcher
    ) {
        this.parentClass = parentclass;
        this.childEntityModel = childEntityModel;
        this.childEntityResolver = childEntityResolver;
        this.commandTargetMatcher = commandTargetMatcher;
        this.parentEntityEvolver = parentEntityEvolver;
        this.eventTargetMatcher = eventTargetMatcher;
    }

    public Set<QualifiedName> supportedCommands() {
        return childEntityModel.supportedCommands();
    }

    @Override
    public MessageStream.Single<? extends CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                                          P entity,
                                                                          ProcessingContext context) {
        List<C> matchingChildEntities = childEntityResolver.apply(entity)
                                                           .stream()
                                                           .filter(child -> commandTargetMatcher.test(child, message))
                                                           .toList();
        if (matchingChildEntities.isEmpty()) {
            throw new IllegalArgumentException(
                    "No child entity found for command " + message.type().qualifiedName()
                            + " on parent entity " + entity + " for command " + message
            );
        }
        if (matchingChildEntities.size() > 1) {
            throw new IllegalArgumentException(
                    "Multiple child entities found for command " + message.type().qualifiedName()
                            + " on parent entity " + entity
            );
        }
        return childEntityModel.handle(message, matchingChildEntities.getFirst(), context);
    }

    @Override
    public P evolve(@Nonnull P entity, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        var result = childEntityResolver.apply(entity)
                                        .stream()
                                        .map(child -> {
                                            if (eventTargetMatcher.test(child, event)) {
                                                return childEntityModel.evolve(child, event, context);
                                            }
                                            return child;
                                        })
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toList());
        return parentEntityEvolver.apply(entity, result);
    }

    @Override
    public Class<C> entityType() {
        return childEntityModel.entityType();
    }

    public static <C, P> Builder<C, P> forEntityModel(Class<P> parentClass,
                                                      EntityModel<C> entityModel
    ) {
        return new Builder<>(parentClass, entityModel);
    }

    public static class Builder<C, P> {

        private Class<P> parentClass;
        private EntityModel<C> childEntityModel;
        private Function<P, List<C>> childEntityResolver;
        private BiPredicate<C, CommandMessage<?>> commandTargetMatcher;
        private BiPredicate<C, EventMessage<?>> eventTargetMatcher;
        private BiFunction<P, List<C>, P> parentEntityEvolver;

        protected Builder(Class<P> parentClass, EntityModel<C> childEntityModel) {
            this.parentClass = parentClass;
            this.childEntityModel = childEntityModel;
        }

        public Builder<C, P> childEntityResolver(Function<P, List<C>> childEntityResolver) {
            this.childEntityResolver = childEntityResolver;
            return this;
        }

        public Builder<C, P> commandTargetMatcher(BiPredicate<C, CommandMessage<?>> commandTargetMatcher) {
            this.commandTargetMatcher = commandTargetMatcher;
            return this;
        }

        public Builder<C, P> eventTargetMatcher(
                BiPredicate<C, EventMessage<?>> eventTargetMatcher) {
            this.eventTargetMatcher = eventTargetMatcher;
            return this;
        }

        public Builder<C, P> parentEntityEvolver(
                BiFunction<P, List<C>, P> parentEntityEvolver) {
            this.parentEntityEvolver = parentEntityEvolver;
            return this;
        }

        public ListEntityChildModel<C, P> build() {
            return new ListEntityChildModel<>(parentClass,
                                              childEntityModel,
                                              childEntityResolver,
                                              commandTargetMatcher,
                                              parentEntityEvolver,
                                              eventTargetMatcher);
        }
    }
}
