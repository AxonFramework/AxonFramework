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
import org.axonframework.common.annotation.Internal;

import java.util.function.Consumer;

/**
 * Internal configurer for the MessagingConfigurer, which is used to configure the event processing.
 */
@Internal
public class EventProcessingConfigurer implements ApplicationConfigurer {

    private final MessagingConfigurer delegate;

    private EventProcessingConfigurer(MessagingConfigurer delegate) {
        this.delegate = delegate;
    }

    public static EventProcessingConfigurer enhance(@Nonnull MessagingConfigurer messagingConfigurer) {
        return new EventProcessingConfigurer(messagingConfigurer)
                .componentRegistry(cr -> cr
                        .registerEnhancer(new EventProcessingConfigurationDefaults())
                );
    }

    @Override
    public EventProcessingConfigurer componentRegistry(@Nonnull Consumer<ComponentRegistry> componentRegistrar) {
        delegate.componentRegistry(componentRegistrar);
        return this;
    }

    @Override
    public EventProcessingConfigurer lifecycleRegistry(@Nonnull Consumer<LifecycleRegistry> lifecycleRegistrar) {
        delegate.lifecycleRegistry(lifecycleRegistrar);
        return this;
    }

    @Override
    public AxonConfiguration build() {
        return delegate.build();
    }
}
