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

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.configuration.Configuration;

import java.util.Optional;

public class ConfigurationApplicationContext implements ApplicationContext {

    private final Configuration configuration;

    public ConfigurationApplicationContext(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public <C> Optional<C> getOptionalComponent(@Nonnull Class<C> type, @Nullable String name) {
        return configuration.getOptionalComponent(type, name);
    }
}
