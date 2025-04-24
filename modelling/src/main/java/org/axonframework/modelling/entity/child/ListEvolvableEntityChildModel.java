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
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.infra.DescribableComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.entity.EvolvableEntityModel;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ListEvolvableEntityChildModel<C, P> implements EvolvableEntityChildModel<C, P>,
        DescribableComponent {

    private final ListEntityChildModel<C, P> commandEntityModelChild;
    private final EvolvableEntityModel<C> childEntityModel;
    private final Function<P, List<C>> childEntityResolver;
    private final BiFunction<P, List<C>, P> parentEntityEvolver;
    private final BiPredicate<C, EventMessage<?>> eventTargetMatcher;

    private ListEvolvableEntityChildModel(
            ListEntityChildModel<C, P> delegate,
            EvolvableEntityModel<C> childEntityModel,
            Function<P, List<C>> childEntityResolver,
            BiFunction<P, List<C>, P> parentEntityEvolver,
            BiPredicate<C, EventMessage<?>> eventTargetMatcher
    ) {
        this.commandEntityModelChild = delegate;
        this.childEntityModel = childEntityModel;
        this.childEntityResolver = childEntityResolver;
        this.parentEntityEvolver = parentEntityEvolver;
        this.eventTargetMatcher = eventTargetMatcher;
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
    public Set<QualifiedName> supportedCommands() {
        return commandEntityModelChild.supportedCommands();
    }

    @Override
    public MessageStream.Single<? extends CommandResultMessage<?>> handle(CommandMessage<?> message, P entity,
                                                                          ProcessingContext context) {
        return commandEntityModelChild.handle(message, entity, context);
    }

    @Override
    public Class<C> entityType() {
        return commandEntityModelChild.entityType();
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("entityDefinition", childEntityModel);
        descriptor.describeProperty("eventTargetMatcher", eventTargetMatcher);
        descriptor.describeProperty("childEntityResolver", childEntityResolver);
        descriptor.describeProperty("parentEntityEvolver", parentEntityEvolver);
    }


    public static <C, P> Builder<C, P> forEntityModel(Class<P> parentClass,
                                                      EvolvableEntityModel<C> entityModel
    ) {
        return new Builder<>(parentClass, entityModel);
    }

    public static class Builder<C, P> {

        private ListEntityChildModel.Builder<C, P> delegateBuilder;
        private Class<P> parentClass;
        private EvolvableEntityModel<C> childEntityModel;
        private Function<P, List<C>> childEntityResolver;
        private BiPredicate<C, CommandMessage<?>> commandTargetMatcher;
        private BiFunction<P, List<C>, P> parentEntityEvolver;
        private BiPredicate<C, EventMessage<?>> eventTargetMatcher;

        private Builder(Class<P> parentClass, EvolvableEntityModel<C> entityModel) {
            delegateBuilder = ListEntityChildModel.forEntityModel(parentClass, entityModel);
            this.parentClass = parentClass;
            this.childEntityModel = entityModel;
            this.childEntityResolver = p -> List.of();
            this.commandTargetMatcher = (child, message) -> true;
            this.eventTargetMatcher = (child, event) -> true;
        }

        public Builder<C, P> childEntityResolver(Function<P, List<C>> childEntityResolver) {
            delegateBuilder.childEntityResolver(childEntityResolver);
            this.childEntityResolver = childEntityResolver;
            return this;
        }

        public Builder<C, P> commandTargetMatcher(BiPredicate<C, CommandMessage<?>> commandTargetMatcher) {
            delegateBuilder.commandTargetMatcher(commandTargetMatcher);
            this.commandTargetMatcher = commandTargetMatcher;
            return this;
        }

        public Builder<C, P> parentEntityEvolver(BiFunction<P, List<C>, P> parentEntityEvolver) {
            this.parentEntityEvolver = parentEntityEvolver;
            return this;
        }

        public Builder<C, P> eventTargetMatcher(BiPredicate<C, EventMessage<?>> eventTargetMatcher) {
            this.eventTargetMatcher = eventTargetMatcher;
            return this;
        }

        public EntityChildModel<C, P> build() {
            return new ListEvolvableEntityChildModel<>(
                    delegateBuilder.build(),
                    childEntityModel,
                    childEntityResolver,
                    parentEntityEvolver,
                    eventTargetMatcher
            );
        }
    }
}
