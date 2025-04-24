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
import org.axonframework.modelling.entity.EvolvableEntityModel;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class SingleEvolvableEntityChildModel<C, P> implements EvolvableEntityChildModel<C, P> {

    private final SingleEntityChildModel<C, P> commandEntityModelChild;
    private final EvolvableEntityModel<C> childEntityModel;
    private final Function<P, C> childEntityResolver;
    private final BiFunction<P, C, P> parentEntityEvolver;

    private SingleEvolvableEntityChildModel(
            SingleEntityChildModel<C, P> delegate,
            EvolvableEntityModel<C> childEntityModel,
            Function<P, C> childEntityResolver,
            BiFunction<P, C, P> parentEntityEvolver
    ) {
        this.commandEntityModelChild = delegate;
        this.childEntityModel = childEntityModel;
        this.childEntityResolver = childEntityResolver;
        this.parentEntityEvolver = parentEntityEvolver;
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
    public Set<QualifiedName> supportedCommands() {
        return commandEntityModelChild.supportedCommands();
    }

    @Override
    public MessageStream.Single<? extends CommandResultMessage<?>> handle(CommandMessage<?> message, P entity,
                                                                          ProcessingContext context) {
        return commandEntityModelChild.handle(message, entity, context);
    }

    public static <C, P> Builder<C, P> forEntityClass(Class<P> parentClass,
                                                      EvolvableEntityModel<C> childEntityModel) {
        return new Builder<>(parentClass, childEntityModel);
    }

    @Override
    public Class<C> entityType() {
        return commandEntityModelChild.entityType();
    }

    public static class Builder<C, P> {

        private final SingleEntityChildModel.Builder<C, P> delegateBuilder;
        private final Class<P> parentClass;
        private final EvolvableEntityModel<C> childEntityModel;
        private Function<P, C> childEntityResolver;
        private BiFunction<P, C, P> parentEntityEvolver;

        private Builder(Class<P> parentClass,
                        EvolvableEntityModel<C> childEntityModel
        ) {
            this.delegateBuilder = SingleEntityChildModel.forEntityClass(parentClass, childEntityModel);
            this.parentClass = parentClass;
            this.childEntityModel = childEntityModel;
        }

        public Builder<C, P> childEntityResolver(Function<P, C> childEntityResolver) {
            delegateBuilder.childEntityResolver(childEntityResolver);
            this.childEntityResolver = childEntityResolver;
            return this;
        }

        public Builder<C, P> parentEntityEvolver(BiFunction<P, C, P> parentEntityEvolver) {
            this.parentEntityEvolver = parentEntityEvolver;
            return this;
        }

        public SingleEvolvableEntityChildModel<C, P> build() {
            return new SingleEvolvableEntityChildModel<>(delegateBuilder.build(),
                                                         childEntityModel,
                                                         childEntityResolver,
                                                         parentEntityEvolver);
        }
    }
}
