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

package org.axonframework.monitoring.interceptors;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.queryhandling.QueryMessage;

import java.util.concurrent.CompletableFuture;

/**
 * A {@link MessageHandlerInterceptor} for {@link QueryMessage} that intercepts the
 * {@link MessageHandlerInterceptorChain} to register the {@link MessageMonitor.MonitorCallback} functions on the
 * {@link ProcessingContext} hooks.
 * <p/>
 * Invoked by the {@link org.axonframework.queryhandling.interceptors.InterceptingQueryBus}.
 * <p/>
 * Events are only monitored when handled.
 *
 * @author Jan Galinski
 * @since 5.0.0
 */
public class MonitoringQueryHandlerInterceptor implements MessageHandlerInterceptor<QueryMessage> {

    private final MessageMonitor<? super QueryMessage> messageMonitor;

    /**
     * Constructs a MonitoringQueryHandlerInterceptor using the given {@link MessageMonitor}.
     *
     * @param messageMonitor used for reporting
     */
    public MonitoringQueryHandlerInterceptor(final @Nonnull MessageMonitor<? super QueryMessage> messageMonitor) {
        this.messageMonitor = messageMonitor;
    }

    @Nonnull
    @Override
    public MessageStream<?> interceptOnHandle(@Nonnull QueryMessage message,
                                              @Nonnull ProcessingContext context,
                                              @Nonnull MessageHandlerInterceptorChain<QueryMessage> interceptorChain) {
        if (context.isStarted()) {
            final var monitorCallback = messageMonitor.onMessageIngested(message);

            context.onError(
                    (cts, phase, error) -> monitorCallback.reportFailure(error)
            );
            context.onAfterCommit(
                    c -> CompletableFuture.runAsync(monitorCallback::reportSuccess)
            );
        }
        return interceptorChain.proceed(message, context);
    }
}
