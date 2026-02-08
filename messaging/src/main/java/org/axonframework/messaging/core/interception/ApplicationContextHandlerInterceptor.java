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
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.ApplicationContext;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.Objects;

/**
 * {@link MessageHandlerInterceptor} that registers the module-specific {@link ApplicationContext} as a resource
 * on the {@link ProcessingContext} before the rest of the interceptor chain proceeds.
 *
 * @author Mateusz Nowak
 * @since 5.1.0
 */
@Internal
public class ApplicationContextHandlerInterceptor implements MessageHandlerInterceptor<Message> {

    private final ApplicationContext applicationContext;

    public ApplicationContextHandlerInterceptor(@Nonnull ApplicationContext applicationContext) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext may not be null");
    }

    @Nonnull
    @Override
    public MessageStream<?> interceptOnHandle(@Nonnull Message message,
                                              @Nonnull ProcessingContext context,
                                              @Nonnull MessageHandlerInterceptorChain<Message> interceptorChain) {
        ProcessingContext updatedContext =
                context.withResource(ProcessingContext.APPLICATION_CONTEXT_RESOURCE, applicationContext);
        return interceptorChain.proceed(message, updatedContext);
    }
}
