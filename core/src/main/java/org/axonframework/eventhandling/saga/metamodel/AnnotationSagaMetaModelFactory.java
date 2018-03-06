/*
 * Copyright (c) 2010-2017. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventhandling.saga.metamodel;


import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.SagaMethodMessageHandlingMember;
import org.axonframework.messaging.annotation.AnnotatedHandlerInspector;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default implementation of a {@link SagaMetaModelFactory}.
 */
public class AnnotationSagaMetaModelFactory implements SagaMetaModelFactory {

    private final Map<Class<?>, SagaModel<?>> registry = new ConcurrentHashMap<>();

    private final ParameterResolverFactory parameterResolverFactory;

    /**
     * Initializes a {@link AnnotationSagaMetaModelFactory} with {@link ClasspathParameterResolverFactory}.
     */
    public AnnotationSagaMetaModelFactory() {
        parameterResolverFactory = ClasspathParameterResolverFactory.forClassLoader(getClass().getClassLoader());
    }

    /**
     * Initializes a {@link AnnotationSagaMetaModelFactory} with given {@code parameterResolverFactory}.
     *
     * @param parameterResolverFactory factory for event handler parameter resolvers
     */
    public AnnotationSagaMetaModelFactory(ParameterResolverFactory parameterResolverFactory) {
        this.parameterResolverFactory = parameterResolverFactory;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> SagaModel<T> modelOf(Class<T> sagaType) {
        return (SagaModel<T>) registry.computeIfAbsent(sagaType, this::doCreateModel);
    }

    private <T> SagaModel<T> doCreateModel(Class<T> sagaType) {
        AnnotatedHandlerInspector<T> handlerInspector =
                AnnotatedHandlerInspector.inspectType(sagaType, parameterResolverFactory);

        return new InspectedSagaModel<>(handlerInspector.getHandlers());
    }

    private class InspectedSagaModel<T> implements SagaModel<T> {
        private final List<MessageHandlingMember<? super T>> handlers;

        public InspectedSagaModel(List<MessageHandlingMember<? super T>> handlers) {
            this.handlers = handlers;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<AssociationValue> resolveAssociation(EventMessage<?> eventMessage) {
            for (MessageHandlingMember<? super T> handler : handlers) {
                if (handler.canHandle(eventMessage)) {
                    return handler.unwrap(SagaMethodMessageHandlingMember.class)
                                  .map(mh -> mh.getAssociationValue(eventMessage));
                }
            }
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<SagaMethodMessageHandlingMember<T>> findHandlerMethods(EventMessage<?> eventMessage) {
            return handlers.stream().filter(h -> h.canHandle(eventMessage))
                           .map(h -> (SagaMethodMessageHandlingMember<T>) h.unwrap(SagaMethodMessageHandlingMember.class)
                                                                           .orElse(null))
                           .filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
        }

        @Override
        public boolean hasHandlerMethod(EventMessage<?> eventMessage) {
            for (MessageHandlingMember<? super T> handler : handlers) {
                if (handler.canHandle(eventMessage)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public SagaMetaModelFactory modelFactory() {
            return AnnotationSagaMetaModelFactory.this;
        }
    }
}
