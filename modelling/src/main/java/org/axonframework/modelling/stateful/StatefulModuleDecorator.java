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

package org.axonframework.modelling.stateful;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.configuration.Module;
import org.axonframework.configuration.ModuleBuilder;
import org.axonframework.configuration.ModuleDecorator;
import org.axonframework.modelling.configuration.EntityModule;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;

import java.util.function.Consumer;

public class StatefulModuleDecorator implements ModuleDecorator {


    class EntitiesConfigurer {

        public <I, E> StatefulCommandHandlingModule.EntityPhase entity(@Nonnull EntityModule<I, E> entityModule) {

        }
    }

    @Override
    public Module decorate(@Nonnull Configuration config, @Nullable String name, @Nonnull Module delegate) {
        return null;
    }
}

