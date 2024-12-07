/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.axonframework.messaging.Context.ResourceKey;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.correlation.CorrelationDataProvider;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.UnitOfWork;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Message interceptor that registers {@link CorrelationDataProvider CorrelationDataProviders} with the Unit of Work.
 * <p/>
 * The registered CorrelationDataProviders copy correlation MetaData over from the Message processed by the Unit of Work
 * to new Messages that are created during processing.
 *
 * @param <T> The type of Message that can be intercepted
 * @author Rene de Waele
 * @since 3.0
 */
public class CorrelationDataInterceptor<T extends Message<?>> implements MessageHandlerInterceptor<T> {

    public static final ResourceKey<Map<String, Object>> CORRELATION_DATA = ResourceKey.getFor("CorrelationData");
    private final List<CorrelationDataProvider> correlationDataProviders;

    /**
     * Initializes the interceptor that registers given {@code correlationDataProviders} with the current Unit of Work.
     *
     * @param correlationDataProviders The CorrelationDataProviders to register with the Interceptor
     */
    public CorrelationDataInterceptor(CorrelationDataProvider... correlationDataProviders) {
        this(Arrays.asList(correlationDataProviders));
    }

    /**
     * Initializes the interceptor that registers given {@code correlationDataProviders} with the current Unit of Work.
     *
     * @param correlationDataProviders The CorrelationDataProviders to register with the Interceptor
     */
    public CorrelationDataInterceptor(Collection<CorrelationDataProvider> correlationDataProviders) {
        this.correlationDataProviders = new ArrayList<>(correlationDataProviders);
    }

    @Override
    public Object handle(@Nonnull UnitOfWork<? extends T> unitOfWork, @Nonnull InterceptorChain interceptorChain)
            throws Exception {
        correlationDataProviders.forEach(unitOfWork::registerCorrelationDataProvider);
        return interceptorChain.proceedSync();
    }

    @Override
    public <M extends T, R extends Message<?>> MessageStream<? extends R> interceptOnHandle(
            @Nonnull M message,
            ProcessingContext context,
            InterceptorChain<M, R> interceptorChain
    ) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        correlationDataProviders.forEach(c -> map.putAll(c.correlationDataFor(message)));
        return interceptorChain.proceed(message, context.withResource(CORRELATION_DATA, map));
    }
}
