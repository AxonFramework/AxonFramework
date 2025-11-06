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

package org.axonframework.messaging.core.interception;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.Context.ResourceKey;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.axonframework.messaging.core.MessageHandlerInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.correlation.CorrelationDataProvider;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link MessageDispatchInterceptor} and {@link MessageHandlerInterceptor} implementation using
 * {@link CorrelationDataProvider CorrelationDataProviders} to collect and set a collection of correlation data.
 * <p/>
 * The correlation data is registered with the {@link ProcessingContext} upon
 * {@link #interceptOnHandle(Message, ProcessingContext, MessageHandlerInterceptorChain) interception} of a
 * {@code message} under {@link ResourceKey} {@link #CORRELATION_DATA}. On
 * {@link #interceptOnDispatch(Message, ProcessingContext, MessageDispatchInterceptorChain)}, the
 * {@code ProcessingContext} is checked for the existence of this resource. When present, the given {@link Message}
 * receive the correlation data as additional {@link org.axonframework.messaging.MetaData}.
 * <p>
 * Users can expect that this {@code CorrelationDataInterceptor} is <b>always</b> set for the user to ensure any
 * correlation data is present at all time.
 *
 * @param <M> The message type this interceptor can process.
 * @author Rene de Waele
 * @author Steven van Beelen
 * @since 3.0.0
 */
@Internal
public class CorrelationDataInterceptor<M extends Message>
        implements MessageDispatchInterceptor<M>, MessageHandlerInterceptor<M> {

    /**
     * Resource key the correlation data is stored in the {@link ProcessingContext}.
     */
    public static final ResourceKey<Map<String, String>> CORRELATION_DATA = ResourceKey.withLabel("CorrelationData");

    private final List<CorrelationDataProvider> correlationDataProviders;

    /**
     * Construct a {@code CorrelationDataInterceptor} that collects correlation data from
     * {@link #interceptOnHandle(Message, ProcessingContext, MessageHandlerInterceptorChain) intercepted messages} with
     * the given {@code correlationDataProviders}, and sets it during
     * {@link #interceptOnDispatch(Message, ProcessingContext, MessageDispatchInterceptorChain) dispatching}
     *
     * @param correlationDataProviders The correlation data providers to generate correlation data from
     *                                 {@link #interceptOnHandle(Message, ProcessingContext,
     *                                 MessageHandlerInterceptorChain) intercepted messages}.
     */
    public CorrelationDataInterceptor(CorrelationDataProvider... correlationDataProviders) {
        this(Arrays.asList(correlationDataProviders));
    }

    /**
     * Construct a {@code CorrelationDataInterceptor} that collects correlation data from
     * {@link #interceptOnHandle(Message, ProcessingContext, MessageHandlerInterceptorChain) intercepted messages} with
     * the given {@code correlationDataProviders}, and sets it during
     * {@link #interceptOnDispatch(Message, ProcessingContext, MessageDispatchInterceptorChain) dispatching}
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
    public MessageStream<?> interceptOnDispatch(@Nonnull M message,
                                                @Nullable ProcessingContext context,
                                                @Nonnull MessageDispatchInterceptorChain<M> chain) {
        //noinspection unchecked
        return context == null || !context.containsResource(CORRELATION_DATA)
                ? chain.proceed(message, context)
                : chain.proceed((M) message.andMetadata(context.getResource(CORRELATION_DATA)), context);
    }

    @Override
    @Nonnull
    public MessageStream<?> interceptOnHandle(@Nonnull M message,
                                              @Nonnull ProcessingContext context,
                                              @Nonnull MessageHandlerInterceptorChain<M> chain) {
        Map<String, String> correlationData = new ConcurrentHashMap<>();
        correlationDataProviders.forEach(provider -> correlationData.putAll(provider.correlationDataFor(message)));
        return chain.proceed(message, context.withResource(CORRELATION_DATA, correlationData));
    }
}
