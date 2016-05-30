/*
 * Copyright (c) 2010-2016. Axon Framework
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


import org.axonframework.common.annotation.AnnotatedHandlerInspector;
import org.axonframework.common.annotation.ClasspathParameterResolverFactory;
import org.axonframework.common.annotation.MessageHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.saga.AssociationValue;
import org.axonframework.eventhandling.saga.SagaMethodMessageHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DefaultSagaMetaModelFactory implements SagaMetaModelFactory {

    private final Map<Class<?>, SagaModel<?>> registry = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public <T> SagaModel<T> modelOf(Class<T> sagaType) {
        return (SagaModel<T>) registry.computeIfAbsent(sagaType, this::doCreateModel);
    }

    private <T> SagaModel<T> doCreateModel(Class<T> sagaType) {
        AnnotatedHandlerInspector<T> handlerInspector = AnnotatedHandlerInspector.inspectType(sagaType, ClasspathParameterResolverFactory.forClass(sagaType));

        return new InspectedSagaModel<>(handlerInspector.getHandlers());
    }

    private class InspectedSagaModel<T> implements SagaModel<T> {
        private final List<MessageHandler<? super T>> handlers;

        public InspectedSagaModel(List<MessageHandler<? super T>> handlers) {
            this.handlers = handlers;
        }

        @Override
        public Optional<AssociationValue> resolveAssociation(EventMessage<?> eventMessage) {
            for (MessageHandler<? super T> handler : handlers) {
                if (handler.canHandle(eventMessage)) {
                    return handler.unwrap(SagaMethodMessageHandler.class).map(mh -> mh.getAssociationValue(eventMessage));
                }
            }
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        @Override
        public List<SagaMethodMessageHandler<T>> findHandlerMethods(EventMessage<?> eventMessage) {
            return handlers.stream()
                    .filter(h -> h.canHandle(eventMessage))
                    .map(h -> (SagaMethodMessageHandler<T>) h.unwrap(SagaMethodMessageHandler.class).orElse(null))
                    .filter(h -> h != null)
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        @Override
        public SagaMetaModelFactory modelFactory() {
            return DefaultSagaMetaModelFactory.this;
        }
    }
}
