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

package org.axonframework.commandhandling.model.inspection;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;

import java.util.List;
import java.util.Map;

/**
 * Inspector of an entity of type {@code T} that creates command and event handlers that delegate to a target entity and
 * its child entities.
 *
 * @param <T> the target type of the inspector
 * @deprecated in favor of using one of the {@link AggregateMetaModelFactory} implementations
 */
@Deprecated
public class ModelInspector<T> implements AggregateModel<T> {

    private final AggregateModel<T> model;

    private ModelInspector(Class<T> aggregateType, ParameterResolverFactory parameterResolverFactory) {
        model = new AnnotatedAggregateMetaModelFactory(parameterResolverFactory).createModel(aggregateType);
    }

    /**
     * Create an inspector for given {@code aggregateType} that uses a {@link ClasspathParameterResolverFactory} to
     * resolve method parameters.
     *
     * @param aggregateType the target aggregate type
     * @param <AT>          the aggregate's type
     * @return a new inspector instance for the inspected class
     */
    public static <AT> AggregateModel<AT> inspectAggregate(Class<AT> aggregateType) {
        return inspectAggregate(aggregateType, ClasspathParameterResolverFactory.forClass(aggregateType));
    }

    /**
     * Create an inspector for given {@code aggregateType} that uses given {@code parameterResolverFactory} to resolve
     * method parameters.
     *
     * @param aggregateType            the target aggregate type
     * @param parameterResolverFactory the resolver factory to use during detection
     * @param <T>                      the aggregate's type
     * @return a new inspector instance for the inspected class
     */
    public static <T> AggregateModel<T> inspectAggregate(Class<T> aggregateType,
                                                         ParameterResolverFactory parameterResolverFactory) {
        return new ModelInspector<>(aggregateType, parameterResolverFactory);
    }

    @Override
    public String type() {
        return model.type();
    }

    @Override
    public Long getVersion(T target) {
        return model.getVersion(target);
    }

    @Override
    public List<MessageHandlingMember<? super T>> commandHandlerInterceptors() {
        return model.commandHandlerInterceptors();
    }

    @Override
    public Object getIdentifier(T target) {
        return model.getIdentifier(target);
    }

    @Override
    public String routingKey() {
        return model.routingKey();
    }

    @Override
    public void publish(EventMessage<?> message, T target) {
        model.publish(message, target);
    }

    @Override
    public Map<String, MessageHandlingMember<? super T>> commandHandlers() {
        return model.commandHandlers();
    }

    @Override
    public MessageHandlingMember<? super T> commandHandler(String commandName) {
        return model.commandHandler(commandName);
    }

    @Override
    public <C> EntityModel<C> modelOf(Class<? extends C> childEntityType) {
        return model.modelOf(childEntityType);
    }

    @Override
    public Class<? extends T> entityClass() {
        return model.entityClass();
    }
}
