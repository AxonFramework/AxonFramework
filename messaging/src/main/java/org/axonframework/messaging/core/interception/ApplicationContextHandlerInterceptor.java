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

package org.axonframework.messaging.core.interception;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.ApplicationContext;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Objects;

/**
 * A {@link MessageHandlerInterceptor} that overrides the {@link ApplicationContext} on the
 * {@link ProcessingContext} before the handler is invoked.
 * <p>
 * This interceptor uses {@link ProcessingContext#withResource(org.axonframework.messaging.core.Context.ResourceKey,
 * Object)} to set the {@link ProcessingContext#APPLICATION_CONTEXT} resource, ensuring that
 * {@link ProcessingContext#component(Class)} and {@link ProcessingContext#component(Class, String)} resolve components
 * from the provided {@link ApplicationContext} rather than the default one.
 * <p>
 * This is used to provide module-specific component resolution when handling messages within a
 * {@link org.axonframework.common.configuration.Module Module}. Each module registers this interceptor with its own
 * {@code ApplicationContext}, so handlers within that module resolve components from the module's
 * {@link org.axonframework.common.configuration.Configuration Configuration} rather than the root one.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 * @see ProcessingContext#APPLICATION_CONTEXT
 */
public class ApplicationContextHandlerInterceptor implements MessageHandlerInterceptor<Message> {

    private final ApplicationContext applicationContext;

    /**
     * Constructs an {@link ApplicationContextHandlerInterceptor} that overrides the {@link ApplicationContext}
     * on the {@link ProcessingContext} with the given {@code applicationContext}.
     *
     * @param applicationContext The {@link ApplicationContext} to use for component resolution during message handling.
     */
    public ApplicationContextHandlerInterceptor(@Nonnull ApplicationContext applicationContext) {
        Objects.requireNonNull(applicationContext, "applicationContext may not be null");
        this.applicationContext = applicationContext;
    }

    @Nonnull
    @Override
    public MessageStream<?> interceptOnHandle(
            @Nonnull Message message,
            @Nonnull ProcessingContext context,
            @Nonnull MessageHandlerInterceptorChain<Message> interceptorChain
    ) {
        ProcessingContext overriddenContext =
                context.withResource(ProcessingContext.APPLICATION_CONTEXT, applicationContext);
        return interceptorChain.proceed(message, overriddenContext);
    }
}
