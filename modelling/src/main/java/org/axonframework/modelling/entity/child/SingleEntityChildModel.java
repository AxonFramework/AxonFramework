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

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class SingleEntityChildModel<C, P> implements EntityChildModel<C, P> {

    private final Class<P> parentClass;
    private final EntityModel<C> childEntityModel;
    private final Function<P, C> childEntityResolver;
    private final BiFunction<P, C, P> parentEntityEvolver;

    private SingleEntityChildModel(
            Class<P> parentclass,
            EntityModel<C> childEntityModel,
            Function<P, C> childEntityResolver,
            BiFunction<P, C, P> parentEntityEvolver
    ) {
        this.parentClass = parentclass;
        this.childEntityModel = childEntityModel;
        this.childEntityResolver = childEntityResolver;
        this.parentEntityEvolver = parentEntityEvolver;
    }

    public Set<QualifiedName> supportedCommands() {
        return childEntityModel.supportedCommands();
    }


    @Override
    public P evolve(@Nonnull P entity, @Nonnull EventMessage<?> event, @Nonnull ProcessingContext context) {
        C childEntity = childEntityResolver.apply(entity);
        if (childEntity != null) {
            C evolvedEntity = childEntityModel.evolve(childEntity, event, context);
            return parentEntityEvolver.apply(entity, evolvedEntity);
        }
        return entity;
    }
    @Override
    public MessageStream.Single<? extends CommandResultMessage<?>> handle(CommandMessage<?> message,
                                                                          P entity,
                                                                          ProcessingContext context) {
        C childEntity = childEntityResolver.apply(entity);
        if (childEntity == null) {
            throw new IllegalArgumentException(
                    "No child entity found for command " + message.type().qualifiedName()
                            + " on parent entity " + entity
            );
        }
        return childEntityModel.handle(message, childEntity, context);
    }

    @Override
    public Class<C> entityType() {
        return childEntityModel.entityType();
    }

    public static <C, P> Builder<C, P> forEntityClass(Class<P> parentClass,
                                                        EntityModel<C> childEntityModel) {
        return new Builder<>(parentClass, childEntityModel);
    }


    public static class Builder<C, P> {

        private final Class<P> parentClass;
        private final EntityModel<C> childEntityModel;
        private Function<P, C> childEntityResolver;
        private BiFunction<P, C, P> parentEntityEvolver;

        private Builder(Class<P> parentClass,
                        EntityModel<C> childEntityModel
        ) {
            this.parentClass = parentClass;
            this.childEntityModel = childEntityModel;
        }

        public Builder<C, P> childEntityResolver(Function<P, C> childEntityResolver) {
            this.childEntityResolver = childEntityResolver;
            return this;
        }

        public Builder<C, P> parentEntityEvolver(
                BiFunction<P, C, P> parentEntityEvolver) {
            this.parentEntityEvolver = parentEntityEvolver;
            return this;
        }

        public SingleEntityChildModel<C, P> build() {
            return new SingleEntityChildModel<>(parentClass,
                                                childEntityModel,
                                                childEntityResolver,
                                                parentEntityEvolver);
        }
    }
}
