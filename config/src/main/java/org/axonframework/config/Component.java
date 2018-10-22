/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.axonframework.config;

import org.axonframework.common.Assert;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A Component used in the Axon Configurer. A Component describes an object that needs to be created, possibly based on
 * other components in the configuration, and initialized as part of a Configuration. Components are lazily initialized
 * when they are accessed. During the initialization, they may trigger initialization of components they depend on.
 *
 * @param <B> The type of Component contained
 */
public class Component<B> {

    private final String name;
    private final Supplier<Configuration> configuration;
    private Function<Configuration, ? extends B> builderFunction;
    private B instance;

    /**
     * Creates a component for the given {@code config} with given {@code name} created by the given
     * {@code builderFunction}. Then the Configuration is not initialized yet, consider using
     * {@link #Component(Supplier, String, Function)} instead.
     *
     * @param config          The Configuration the component is part of
     * @param name            The name of the component
     * @param builderFunction The builder function of the component
     */
    public Component(Configuration config, String name, Function<Configuration, ? extends B> builderFunction) {
        this(() -> config, name, builderFunction);
    }

    /**
     * Creates a component for the given {@code config} with given {@code name} created by the given
     * {@code builderFunction}.
     *
     * @param config          The supplier function of the configuration
     * @param name            The name of the component
     * @param builderFunction The builder function of the component
     */
    public Component(Supplier<Configuration> config, String name, Function<Configuration, ? extends B> builderFunction) {
        this.configuration = config;
        this.name = name;
        this.builderFunction = builderFunction;
    }

    /**
     * Retrieves the object contained in this component, triggering the builder function if the component hasn't been
     * built yet.
     *
     * @return the initialized component contained in this instance
     */
    public B get() {
        if (instance == null) {
            instance = builderFunction.apply(configuration.get());
        }
        return instance;
    }

    /**
     * Updates the builder function for this component.
     *
     * @param builderFunction The new builder function for the component
     * @throws IllegalStateException when the component has already been retrieved using {@link #get()}.
     */
    public void update(Function<Configuration, ? extends B> builderFunction) {
        Assert.state(instance == null, () -> "Cannot change " + name + ": it is already in use");
        this.builderFunction = builderFunction;
    }
}
