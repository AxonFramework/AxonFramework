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

package org.axonframework.messaging.core;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.common.configuration.Configuration;

import java.util.Objects;

/**
 * An {@link ApplicationContext} implementation that retrieves components from a given {@link Configuration}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
@Internal
public class ConfigurationApplicationContext implements ApplicationContext {

    private final Configuration configuration;

    /**
     * Creates a new {@link ConfigurationApplicationContext} that retrieves components from the given {@code configuration}.
     *
     * @param configuration The configuration to retrieve components from.
     */
    public ConfigurationApplicationContext(@Nonnull Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration may not be null");
        this.configuration = configuration;
    }

    @Nonnull
    @Override
    public <C> C component(@Nonnull Class<C> type, @Nullable String name) {
        return configuration.getComponent(type, name);
    }

    @Nonnull
    @Override
    public <C> C component(@Nonnull Class<C> type) {
        return configuration.getComponent(type);
    }
}
