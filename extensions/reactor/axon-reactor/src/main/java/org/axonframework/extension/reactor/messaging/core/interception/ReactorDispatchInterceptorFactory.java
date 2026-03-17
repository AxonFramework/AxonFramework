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

package org.axonframework.extension.reactor.messaging.core.interception;

import org.axonframework.common.configuration.Configuration;
import org.axonframework.extension.reactor.messaging.core.ReactorMessageDispatchInterceptor;
import org.axonframework.messaging.core.Message;
import org.jspecify.annotations.Nullable;

/**
 * Functional interface for building a {@link ReactorMessageDispatchInterceptor} for a specific component type and
 * component name.
 * <p>
 * This interface allows {@code ReactorMessageDispatchInterceptors} to be constructed with knowledge of the component
 * they will intercept, allowing for fine-grained control on how or when to construct an interceptor.
 *
 * @param <M> the type of {@link Message} the resulting {@link ReactorMessageDispatchInterceptor} will intercept
 * @author Theo Emanuelsson
 * @since 5.1.0
 * @see ReactorMessageDispatchInterceptor
 * @see ReactorDispatchInterceptorRegistry
 */
@FunctionalInterface
public interface ReactorDispatchInterceptorFactory<M extends Message> {

    /**
     * Builds a {@link ReactorMessageDispatchInterceptor} for the specified component.
     *
     * @param config        the {@link Configuration} from which other components can be retrieved during construction
     * @param componentType the type of the component to build a dispatch interceptor for
     * @param componentName the name of the component to build a dispatch interceptor for
     * @return a {@link ReactorMessageDispatchInterceptor} instance configured for the specified component or
     * {@code null} when no interceptor is required for the given {@code componentType} and {@code componentName}
     * combination
     */
    @Nullable
    ReactorMessageDispatchInterceptor<? super M> build(
            Configuration config,
            Class<?> componentType,
            @Nullable String componentName
    );
}
