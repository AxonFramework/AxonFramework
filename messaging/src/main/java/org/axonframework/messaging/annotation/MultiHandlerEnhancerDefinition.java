/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.messaging.annotation;

import org.axonframework.common.annotation.PriorityAnnotationComparator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * HandlerEnhancerDefinition instance that delegates to multiple other instances, in the order provided.
 *
 * @author Tyler Thrailkill
 * @since 3.3
 */
public class MultiHandlerEnhancerDefinition implements HandlerEnhancerDefinition {

    private final HandlerEnhancerDefinition[] enhancers;

    /**
     * Creates a MultiHandlerEnhancerDefinition instance with the given {@code delegates}, which are automatically
     * ordered based on the {@link org.axonframework.common.Priority @Priority} annotation on their respective classes.
     * Classes with the same Priority are kept in the order as provided in the {@code delegates}.
     * <p>
     * If one of the delegates is a MultiHandlerEnhancerDefinition itself, that factory's delegates are 'mixed' with
     * the given {@code delegates}, based on their respective order.
     *
     * @param delegates The delegates to include in the factory
     * @return an instance delegating to the given {@code delegates}
     */
    public static MultiHandlerEnhancerDefinition ordered(HandlerEnhancerDefinition... delegates) {
        return ordered(Arrays.asList(delegates));
    }

    /**
     * Creates a MultiHandlerEnhancerDefinition instance with the given {@code delegates}, which are automatically
     * ordered based on the {@link org.axonframework.common.Priority @Priority} annotation on their respective classes.
     * Classes with the same Priority are kept in the order as provided in the {@code delegates}.
     * <p>
     * If one of the delegates is a MultiHandlerEnhancerDefinition itself, that factory's delegates are 'mixed' with
     * the given {@code delegates}, based on their respective order.
     *
     * @param delegates The delegates to include in the factory
     * @return an instance delegating to the given {@code delegates}
     */
    public static MultiHandlerEnhancerDefinition ordered(Collection<HandlerEnhancerDefinition> delegates) {
        return new MultiHandlerEnhancerDefinition(flatten(delegates));
    }

    /**
     * Initializes an instance that delegates to the given {@code delegates}, in the order provided. Changes in
     * the given array are not reflected in the created instance.
     *
     * @param delegates The enhancers providing the parameter values to use
     */
    public MultiHandlerEnhancerDefinition(HandlerEnhancerDefinition... delegates) {
        this.enhancers = Arrays.copyOf(delegates, delegates.length);
    }

    /**
     * Initializes an instance that delegates to the given {@code delegates}, in the order provided. Changes in
     * the given List are not reflected in the created instance.
     *
     * @param delegates The list of enhancers providing the parameter values to use
     */
    public MultiHandlerEnhancerDefinition(Collection<HandlerEnhancerDefinition> delegates) {
        this.enhancers = delegates.toArray(new HandlerEnhancerDefinition[delegates.size()]);
    }

    private static HandlerEnhancerDefinition[] flatten(Collection<HandlerEnhancerDefinition> enhancers) {
        List<HandlerEnhancerDefinition> flattened = new ArrayList<>(enhancers.size());
        for (HandlerEnhancerDefinition handlerEnhancer : enhancers) {
            if (handlerEnhancer instanceof MultiHandlerEnhancerDefinition) {
                flattened.addAll(((MultiHandlerEnhancerDefinition) handlerEnhancer).getDelegates());
            } else {
                flattened.add(handlerEnhancer);
            }
        }
        flattened.sort(PriorityAnnotationComparator.getInstance());
        return flattened.toArray(new HandlerEnhancerDefinition[flattened.size()]);
    }

    /**
     * Returns the delegates of this instance, in the order they are evaluated to resolve parameters.
     *
     * @return the delegates of this instance, in the order they are evaluated to resolve parameters
     */
    public List<HandlerEnhancerDefinition> getDelegates() {
        return Arrays.asList(enhancers);
    }

    @Override
    public <T> MessageHandlingMember<T> wrapHandler(@Nonnull MessageHandlingMember<T> original) {
        MessageHandlingMember<T> resolver = original;
        for (HandlerEnhancerDefinition enhancer : enhancers) {
            resolver = enhancer.wrapHandler(resolver);
        }
        return resolver;
    }
}
