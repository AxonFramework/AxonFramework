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
import jakarta.annotation.Nullable;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingLifecycle;
import org.axonframework.monitoring.MessageMonitor;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Abstract base class to provide monitoring functionality for message interceptors.
 * This class ensures that MessageMonitor callbacks are correctly registered within the lifecycle
 * of messages during their processing, such as during dispatch or handling.
 * <p>
 * Classes extending this interceptor are expected to use this functionality to monitor
 * message processing performance and behavior.
 *
 * @param <T> The type of message this interceptor monitors, extending {@link Message}.
 *
 * @author Jan Galinski
 * @since 5.0.0
 */
class AbstractMonitoringInterceptor<T extends Message> {

    protected final MessageMonitor<? super T> messageMonitor;

    protected AbstractMonitoringInterceptor(@Nonnull MessageMonitor<? super T> messageMonitor) {
        this.messageMonitor = requireNonNull(messageMonitor, "MessageMonitor may not be null.");
    }

    /**
     * Creates a {@link MessageMonitor.MonitorCallback} for the given {@code message} using
     * {@link MessageMonitor#onMessageIngested(Message)} and registers:
     * <ul>
     *     <li>{@link MessageMonitor.MonitorCallback#reportSuccess()} on {@link ProcessingContext#onAfterCommit(Function)}</li>
     *     <li>{@link MessageMonitor.MonitorCallback#reportFailure(Throwable)} on {@link ProcessingContext#onError(ProcessingLifecycle.ErrorHandler)}</li>
     * </ul>
     * <p>
     * Requires the {@link ProcessingContext} to be <code>non-null</code> and {@link ProcessingContext#isStarted() started},
     * if it is not, {@link MessageMonitor.MonitorCallback#reportIgnored()} is called instead.
     *
     * @param processingContext the current {@link ProcessingContext}, if <code>null</code> or not started, the message
     *                          is reported as ignored
     * @param message           the {@link Message} to report
     */
    protected void registerMonitorCallback(@Nullable ProcessingContext processingContext,
                                           @Nonnull T message) {
        var monitorCallback = messageMonitor.onMessageIngested(message);

        if (processingContext != null && processingContext.isStarted()) {
            processingContext.onError((cts, phase, error) -> monitorCallback.reportFailure(error));
            processingContext.onAfterCommit(c -> CompletableFuture.runAsync(monitorCallback::reportSuccess));
        } else {
            // TODO(JG): what to do in this case?
            monitorCallback.reportIgnored();
        }
    }
}
