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
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageDispatchInterceptorChain;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.MessageHandlerInterceptorChain;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.monitoring.MessageMonitor;

import java.util.concurrent.CompletableFuture;

/**
 * A {@link MessageDispatchInterceptor} that intercepts a {@link MessageDispatchInterceptorChain} of
 * {@link EventMessage} and registers the {@link MessageMonitor.MonitorCallback} hooks for reporting. Invoked by the
 * {@link org.axonframework.eventhandling.InterceptingEventSink}.
 *
 * @author Jan Galinski
 * @since 5.0.0
 */
public class MonitoringEventHandlerInterceptor implements MessageHandlerInterceptor<EventMessage> {

    private final MessageMonitor<? super EventMessage> messageMonitor;

    /**
     * Constructs a new MonitoringEventHandlerInterceptor using the given {@link MessageMonitor}.
     *
     * @param messageMonitor The {@link MessageMonitor} instance used for reporting.
     */
    public MonitoringEventHandlerInterceptor(final @Nonnull MessageMonitor<? super EventMessage> messageMonitor) {
        this.messageMonitor = messageMonitor;
    }

    @Nonnull
    @Override
    public MessageStream<?> interceptOnHandle(@Nonnull EventMessage message,
                                              @Nonnull ProcessingContext context,
                                              @Nonnull MessageHandlerInterceptorChain<EventMessage> interceptorChain) {
        if (context.isStarted()) {
            final var monitorCallback = messageMonitor.onMessageIngested(message);

            context.onError(
                    (ctx, phase, error) -> monitorCallback.reportFailure(error)
            );
            context.runOnAfterCommit(
                    ctx -> monitorCallback.reportSuccess()
            );
        }
        return interceptorChain.proceed(message, context);
    }
}
