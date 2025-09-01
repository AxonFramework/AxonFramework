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
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.Context.ResourceKey;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link MessageHandlerInterceptor} that constructs a collection of correlation data through the given
 * {@link CorrelationDataProvider CorrelationDataProviders}.
 * <p/>
 * The correlation data is registered with the {@link ProcessingContext} upon
 * {@link #interceptOnHandle(Message, ProcessingContext, MessageHandlerInterceptorChain) intercepting} of a
 * {@code message} under {@link ResourceKey} {@link #CORRELATION_DATA}. Dispatching logic should use this resource when
 * constructing new message as part of the intercepted {@code ProcessingContext}.
 * <p>
 * Users can expect that this {@code CorrelationDataInterceptor} is <b>always</b> set for the user to ensure any
 * correlation data is present at all time.
 *
 * @param <M> The message type this interceptor can process.
 * @author Rene de Waele
 * @since 3.0.0
 */
@Internal
public class CorrelationDataInterceptor<M extends Message> implements MessageHandlerInterceptor<M> {

    /**
     * Resource key the correlation data is stored in the {@link ProcessingContext}.
     */
    public static final ResourceKey<Map<String, String>> CORRELATION_DATA = ResourceKey.withLabel("CorrelationData");

    private final List<CorrelationDataProvider> correlationDataProviders;

    /**
     * Construct a {@code CorrelationDataInterceptor} that generates correlation data from
     * {@link #interceptOnHandle(Message, ProcessingContext, MessageHandlerInterceptorChain) intercepted messages} with
     * the given {@code correlationDataProviders}.
     *
     * @param correlationDataProviders The correlation data providers to generate correlation data from
     *                                 {@link #interceptOnHandle(Message, ProcessingContext,
     *                                 MessageHandlerInterceptorChain) intercepted messages}.
     */
    public CorrelationDataInterceptor(CorrelationDataProvider... correlationDataProviders) {
        this(Arrays.asList(correlationDataProviders));
    }

    /**
     * Construct a {@code CorrelationDataInterceptor} that generates correlation data from
     * {@link #interceptOnHandle(Message, ProcessingContext, MessageHandlerInterceptorChain) intercepted messages} with
     * the given {@code correlationDataProviders}.
     *
     * @param correlationDataProviders The correlation data providers to generate correlation data from
     *                                 {@link #interceptOnHandle(Message, ProcessingContext,
     *                                 MessageHandlerInterceptorChain) intercepted messages}.
     */
    public CorrelationDataInterceptor(@Nonnull Collection<CorrelationDataProvider> correlationDataProviders) {
        this.correlationDataProviders = new ArrayList<>(correlationDataProviders);
    }

    @Override
    @Nonnull
    public MessageStream<?> interceptOnHandle(@Nonnull M message,
                                              @Nonnull ProcessingContext context,
                                              @Nonnull MessageHandlerInterceptorChain<M> interceptorChain) {
        Map<String, String> correlationData = new ConcurrentHashMap<>();
        correlationDataProviders.forEach(provider -> correlationData.putAll(provider.correlationDataFor(message)));
        return interceptorChain.proceed(message, context.withResource(CORRELATION_DATA, correlationData));
    }
}
