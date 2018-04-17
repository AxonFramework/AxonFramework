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

package org.axonframework.messaging.annotation;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.axonframework.common.annotation.PriorityAnnotationComparator;

/**
 * HandlerDefinition instance that delegates to multiple other instances, in the order provided.
 *
 * @author Allard Buijze
 * @since 2.1
 */
public class MultiHandlerDefinition implements HandlerDefinition {

    private final HandlerDefinition[] factories;

    /**
     * Creates a MultiParameterResolverFactory instance with the given {@code delegates}, which are automatically
     * ordered based on the {@link org.axonframework.common.Priority @Priority} annotation on their respective classes.
     * Classes with the same Priority are kept in the order as provided in the {@code delegates}.
     * <p>
     * If one of the delegates is a MultiParameterResolverFactory itself, that factory's delegates are 'mixed' with
     * the given {@code delegates}, based on their respective order.
     *
     * @param delegates The delegates to include in the factory
     * @return an instance delegating to the given {@code delegates}
     */
    public static MultiHandlerDefinition ordered(HandlerDefinition... delegates) {
        return ordered(Arrays.asList(delegates));
    }

    /**
     * Creates a MultiParameterResolverFactory instance with the given {@code delegates}, which are automatically
     * ordered based on the {@link org.axonframework.common.Priority @Priority} annotation on their respective classes.
     * Classes with the same Priority are kept in the order as provided in the {@code delegates}.
     * <p>
     * If one of the delegates is a MultiParameterResolverFactory itself, that factory's delegates are 'mixed' with
     * the given {@code delegates}, based on their respective order.
     *
     * @param delegates The delegates to include in the factory
     * @return an instance delegating to the given {@code delegates}
     */
    public static MultiHandlerDefinition ordered(List<HandlerDefinition> delegates) {
        return new MultiHandlerDefinition(flatten(delegates));
    }

    /**
     * Initializes an instance that delegates to the given {@code delegates}, in the order provided. Changes in
     * the given array are not reflected in the created instance.
     *
     * @param delegates The factories providing the parameter values to use
     */
    public MultiHandlerDefinition(HandlerDefinition... delegates) {
        this.factories = Arrays.copyOf(delegates, delegates.length);
    }

    /**
     * Initializes an instance that delegates to the given {@code delegates}, in the order provided. Changes in
     * the given List are not reflected in the created instance.
     *
     * @param delegates The list of factories providing the parameter values to use
     */
    public MultiHandlerDefinition(List<HandlerDefinition> delegates) {
        this.factories = delegates.toArray(new HandlerDefinition[delegates.size()]);
    }

    private static HandlerDefinition[] flatten(List<HandlerDefinition> factories) {
        List<HandlerDefinition> flattened = new ArrayList<>(factories.size());
        for (HandlerDefinition handlerEnhancer : factories) {
            if (handlerEnhancer instanceof MultiHandlerDefinition) {
                flattened.addAll(((MultiHandlerDefinition) handlerEnhancer).getDelegates());
            } else {
                flattened.add(handlerEnhancer);
            }
        }
        Collections.sort(flattened, PriorityAnnotationComparator.getInstance());
        return flattened.toArray(new HandlerDefinition[flattened.size()]);
    }

    /**
     * Returns the delegates of this instance, in the order they are evaluated to resolve parameters.
     *
     * @return the delegates of this instance, in the order they are evaluated to resolve parameters
     */
    public List<HandlerDefinition> getDelegates() {
        return Arrays.asList(factories);
    }

    @Override
    public <T> Optional<MessageHandlingMember<T>> createHandler(Class<T> declaringType,
                                                                Executable executable,
                                                                ParameterResolverFactory parameterResolverFactory) {
        Optional<MessageHandlingMember<T>> resolver = Optional.empty();
        for (HandlerDefinition factory : factories) {
            resolver = factory.createHandler(declaringType, executable, parameterResolverFactory);
            if (resolver.isPresent()) {
                return resolver;
            }
        }
        return resolver;
    }
}
