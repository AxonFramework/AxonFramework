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

package org.axonframework.queryhandling.monitoring;

import jakarta.annotation.Nonnull;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.axonframework.queryhandling.SinkWrapper;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage;
import org.axonframework.queryhandling.UpdateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
// TODO 3595 - Introduce monitoring logic here.
public class MonitoringQueryUpdateEmitter implements QueryUpdateEmitter {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final QueryUpdateEmitter delegate;
    private final MessageMonitor<? super SubscriptionQueryUpdateMessage> monitor;

    /**
     *
     * @param delegate
     * @param monitor
     */
    public MonitoringQueryUpdateEmitter(@Nonnull QueryUpdateEmitter delegate,
                                        @Nonnull MessageMonitor<? super SubscriptionQueryUpdateMessage> monitor) {
        this.delegate = Objects.requireNonNull(delegate, "The delegate QueryUpdateEmitter must not be null.");
        this.monitor = Objects.requireNonNull(monitor, "The MessageMonitor must not be null.");
    }

    @Override
    public void emit(@Nonnull Predicate<SubscriptionQueryMessage> filter,
                     @Nonnull SubscriptionQueryUpdateMessage update) {

    }

    @SuppressWarnings("unchecked")
    private void doEmit(SubscriptionQueryMessage query, SinkWrapper<?> updateHandler,
                        SubscriptionQueryUpdateMessage update) {
        MessageMonitor.MonitorCallback monitorCallback = monitor.onMessageIngested(update);
        try {
            ((SinkWrapper<SubscriptionQueryUpdateMessage>) updateHandler).next(update);
            monitorCallback.reportSuccess();
        } catch (Exception e) {
            logger.info("An error occurred while trying to emit an update to a query '{}'. " +
                                "The subscription will be cancelled. Exception summary: {}",
                        query.type(), e.toString());
            monitorCallback.reportFailure(e);
//            updateHandlers.remove(query);
//            emitError(query, e, updateHandler);
        }
    }

    @Override
    public void complete(@Nonnull Predicate<SubscriptionQueryMessage> filter) {

    }

    @Override
    public void completeExceptionally(@Nonnull Predicate<SubscriptionQueryMessage> filter, @Nonnull Throwable cause) {

    }

    @Nonnull
    @Override
    public UpdateHandler subscribe(@Nonnull SubscriptionQueryMessage query,
                                   int updateBufferSize) {
        return null;
    }
}
