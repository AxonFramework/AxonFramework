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
import org.axonframework.common.Assert;

import java.util.Objects;

/**
 * Simple implementation of the {@link Module} to allow for configuration modularization.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class SimpleModule extends AbstractConfigurer<SimpleModule> implements Module<SimpleModule> {

    private final String name;

    /**
     * Initialize the {@code SimpleModule} based on the given {@code config}.
     *
     * @param name The name of {@code this SimpleModule}.
     */
    public SimpleModule(@Nonnull String name) {
        Assert.nonEmpty(name, "The Module name cannot be null or empty.");
        this.name = name;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public NewConfiguration build(@Nonnull LifecycleSupportingConfiguration parent) {
        super.setParent(Objects.requireNonNull(parent, "The parent Configuration cannot be null."));
        super.enhanceInvocationAndModuleConstruction();
        return super.config();
    }
}
