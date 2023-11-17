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

package org.axonframework.modelling.command;

import org.axonframework.common.ReflectionUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Default implementation of {@link CreationPolicyAggregateFactory} that invokes the default, no-arguments constructor
 * of the aggregate class {@code A}.
 *
 * @param <A> The aggregate type this factory constructs.
 * @author Stefan Andjelkovic
 * @since 4.6.0
 */
public class NoArgumentConstructorCreationPolicyAggregateFactory<A> implements CreationPolicyAggregateFactory<A> {

    private final Class<? extends A> aggregateClass;

    /**
     * Construct an instance of the {@link NoArgumentConstructorCreationPolicyAggregateFactory} for the given type.
     *
     * @param aggregateClass The aggregate type.
     */
    public NoArgumentConstructorCreationPolicyAggregateFactory(@Nonnull Class<? extends A> aggregateClass) {
        this.aggregateClass = aggregateClass;
    }

    /**
     * Creates the aggregate instance based on the previously provided type. Invokes the default, no-argument
     * constructor of the class.
     *
     * @param identifier The identifier to create the aggregate with. Not used by this factory.
     * @return An aggregate instance.
     */
    @SuppressWarnings("deprecation") // Suppressed ReflectionUtils#ensureAccessible
    @Nonnull
    @Override
    public A create(@Nullable Object identifier) {
        try {
            return ReflectionUtils.ensureAccessible(aggregateClass.getDeclaredConstructor()).newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
