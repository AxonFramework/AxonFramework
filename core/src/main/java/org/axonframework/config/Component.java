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

package org.axonframework.config;

import org.axonframework.common.Assert;

import java.util.function.Function;
import java.util.function.Supplier;

public class Component<B> {

    private final String name;
    private Supplier<Configuration> configuration;
    private Function<Configuration, ? extends B> builderFunction;
    private B instance;

    public Component(Configuration configurer, String name, Function<Configuration, ? extends B> builderFunction) {
        this(() -> configurer, name, builderFunction);
    }

    public Component(Supplier<Configuration> configurer, String name, Function<Configuration, ? extends B> builderFunction) {
        this.configuration = configurer;
        this.name = name;
        this.builderFunction = builderFunction;
    }

    public B get() {
        if (instance == null) {
            instance = builderFunction.apply(configuration.get());
        }
        return instance;
    }

    public void update(Function<Configuration, ? extends B> builderFunction) {
        Assert.state(instance == null, "Cannot change " + name + ": it is already in use");
        this.builderFunction = builderFunction;
    }
}
