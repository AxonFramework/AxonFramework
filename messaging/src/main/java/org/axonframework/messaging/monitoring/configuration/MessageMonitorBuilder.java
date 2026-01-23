/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.monitoring.configuration;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.configuration.Configuration;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.monitoring.MessageMonitor;

/**
 * Functional interface for building a {@link MessageMonitor} for a specific component type and component name.
 * <p>
 * This interface allows {@code MessageMonitors} to be constructed with knowledge of the component they will monitor,
 * allowing for fine-grained control on how or when to construct an interceptor. This, for example, enables proper
 * tagging and categorization in metric systems.
 *
 * @param <M> the type of {@link Message} the resulting {@link MessageMonitor} will monitor
 * @author Steven van Beelen
 * @since 5.0.3
 */
@FunctionalInterface
public interface MessageMonitorBuilder<M extends Message> {

    /**
     * Builds a {@link MessageMonitor} for the specified component.
     *
     * @param config        the {@link Configuration} from which other components can be retrieved during construction
     * @param componentType the type of the component to build a monitor for
     * @param componentName the name of the component to build a monitor for
     * @return a {@link MessageMonitor} instance configured for the specified component or {@code null} when no monitor
     * is required for the given {@code componentType} and {@code componentName} combination
     */
    @Nullable
    MessageMonitor<? super M> build(
            @Nonnull Configuration config,
            @Nonnull Class<?> componentType,
            @Nullable String componentName
    );
}