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

package org.axonframework.configuration;

import jakarta.annotation.Nonnull;

/**
 * Interface describing an enhancement of a {@link ListableConfigurer listable configurer} of the Axon Framework
 * configuration API.
 * <p>
 * Through implementing the {@link #enhance(ListableConfigurer)} operation a {@code ConfigurerEnhancer} is able to
 * {@link ListableConfigurer#registerComponent(Class, ComponentBuilder) register} components and
 * {@link ListableConfigurer#registerDecorator(Class, int, ComponentDecorator) register} decorators. The registration of
 * components and/or decorators can be made conditional by using the {@link ListableConfigurer#hasComponent(Class)}
 * operation.
 * <p>
 * Note that enhancers have an {@link #order()} in which they enhance the {@code ListableConfigurer}. When not otherwise
 * specified, the order defaults to {@code 0}. Thus, without specifying the order, the insert order of enhancer dictates
 * the order.
 *
 * @author Steven van Beelen
 * @since 3.2.0
 */
@FunctionalInterface
public interface ConfigurerEnhancer {

    /**
     * Enhances the given {@code configurer} with, for example, additional {@link Component components} and
     * {@link ComponentDecorator decorators}.
     *
     * @param configurer The listable configurer instance to enhance.
     */
    void enhance(@Nonnull ListableConfigurer<?> configurer);

    /**
     * Returns the relative order this enhancer should be invoked in, compared to other instances.
     * <p>
     * Use lower (negative) values for modules providing sensible defaults, and higher values for modules overriding
     * values potentially previously set. Defaults to {@code 0} when not set.
     *
     * @return The order in which this enhancer should be invoked.
     */
    default int order() {
        return 0;
    }
}
