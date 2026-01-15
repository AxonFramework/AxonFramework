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

package org.axonframework.messaging.monitoring.interception;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageDispatchInterceptorChain;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.queryhandling.interception.InterceptingQueryBus;
import org.axonframework.messaging.monitoring.MessageMonitor;
import org.axonframework.messaging.queryhandling.SubscriptionQueryUpdateMessage;

/**
 * A {@link MessageDispatchInterceptor} that intercepts a {@link MessageDispatchInterceptorChain} of
 * {@link SubscriptionQueryUpdateMessage} and registers the {@link MessageMonitor.MonitorCallback} hooks for
 * reporting. Invoked by the {@link InterceptingQueryBus}.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class MonitoringSubscriptionQueryUpdateDispatchInterceptor implements MessageDispatchInterceptor<SubscriptionQueryUpdateMessage> {

    private final MessageMonitor<? super SubscriptionQueryUpdateMessage> messageMonitor;

    /**
     * Constructs a new MonitoringSubscriptionQueryUpdateDispatchInterceptor using the given {@link MessageMonitor}.
     *
     * @param messageMonitor The {@link MessageMonitor} instance used for reporting.
     */
    public MonitoringSubscriptionQueryUpdateDispatchInterceptor(final @Nonnull MessageMonitor<? super SubscriptionQueryUpdateMessage> messageMonitor) {
        this.messageMonitor = messageMonitor;
    }

    @Nonnull
    @Override
    public MessageStream<?> interceptOnDispatch(@Nonnull SubscriptionQueryUpdateMessage message,
                                                @Nullable ProcessingContext context,
                                                @Nonnull MessageDispatchInterceptorChain<SubscriptionQueryUpdateMessage> interceptorChain) {
        if (context != null && context.isStarted()) {
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
