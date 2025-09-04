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

package org.axonframework.messaging.interceptors;

import jakarta.annotation.Nonnull;
import org.axonframework.common.TypeReference;
import org.axonframework.common.annotation.Internal;
import org.axonframework.configuration.ComponentBuilder;
import org.axonframework.configuration.ComponentDefinition;
import org.axonframework.configuration.Configuration;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageDispatchInterceptor;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of the {@link DispatchInterceptorRegistry}, maintaining a list of generic {@link Message}
 * {@link MessageDispatchInterceptor MessageDispatchInterceptors}.
 * <p>
 * This implementation ensures give interceptor factory methods are only invoked once.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class DefaultDispatchInterceptorRegistry implements DispatchInterceptorRegistry {

    private static final TypeReference<MessageDispatchInterceptor<? super Message>> INTERCEPTOR_TYPE_REF = new TypeReference<>() {
    };

    private final List<ComponentDefinition<MessageDispatchInterceptor<? super Message>>> dispatchInterceptorBuilders = new ArrayList<>();

    @Nonnull
    @Override
    public DispatchInterceptorRegistry registerInterceptor(
            @Nonnull ComponentBuilder<MessageDispatchInterceptor<? super Message>> interceptorBuilder
    ) {
        this.dispatchInterceptorBuilders.add(ComponentDefinition.ofType(INTERCEPTOR_TYPE_REF)
                                                                .withBuilder(interceptorBuilder));
        return this;
    }

    @Nonnull
    @Override
    public List<MessageDispatchInterceptor<? super Message>> interceptors(@Nonnull Configuration config) {
        List<MessageDispatchInterceptor<? super Message>> dispatchInterceptors = new ArrayList<>();
        for (ComponentDefinition<MessageDispatchInterceptor<? super Message>> interceptorBuilder : dispatchInterceptorBuilders) {
            if (!(interceptorBuilder instanceof ComponentDefinition.ComponentCreator<MessageDispatchInterceptor<? super Message>> creator)) {
                // The compiler should avoid this from happening.
                throw new IllegalArgumentException("Unsupported component definition type: " + interceptorBuilder);
            }
            MessageDispatchInterceptor<? super Message> dispatchInterceptor = creator.createComponent().resolve(config);
            dispatchInterceptors.add(dispatchInterceptor);
        }
        return dispatchInterceptors;
    }
}
