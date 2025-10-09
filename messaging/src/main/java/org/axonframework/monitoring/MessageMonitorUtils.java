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

package org.axonframework.monitoring;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.messaging.unitofwork.ProcessingLifecycle;
import org.axonframework.monitoring.MessageMonitor.MonitorCallback;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

/**
 * Provides commonly used methods for {@link MessageMonitor} and {@link MonitorCallback}
 */
public class MessageMonitorUtils {

    /**
     * Creates a {@link MonitorCallback} for each of the given {@code messages} using {@link MessageMonitor#onMessageIngested(Message)}
     * and registers:
     * <ul>
     *     <li>{@link MonitorCallback#reportSuccess()} on {@link ProcessingContext#onAfterCommit(Function)}</li>
     *     <li>{@link MonitorCallback#reportFailure(Throwable)} on {@link ProcessingContext#onError(ProcessingLifecycle.ErrorHandler)}</li>
     * </ul>
     *
     * Requires the {@link ProcessingContext} to be <code>non-null</code> and {@link ProcessingContext#isStarted() started},
     * if it is not, {@link MonitorCallback#reportIgnored()} is called instead.
     *
     * @param processingContext the current {@link ProcessingContext}, if <code>null</code>> or not started, the message is reported as ignored
     * @param messageMonitor the {@link MessageMonitor} used for callback creation
     * @param messages the {@link Message}s to report
     * @param <T> subtype of Message used
     */
    public static <T extends Message> void registerMonitorCallbacks(@Nullable ProcessingContext processingContext,
                                                                    @Nonnull MessageMonitor<T> messageMonitor,
                                                                    @Nonnull Iterable<? extends T> messages) {
        var monitorCallbacks = StreamSupport.stream(messages.spliterator(), false)
                                            .map(messageMonitor::onMessageIngested)
                                            .toList();

        if (processingContext != null && processingContext.isStarted()) {
            processingContext.onError((cts, phase, error) -> monitorCallbacks.forEach(it -> it.reportFailure(error)));
            processingContext.onAfterCommit(c -> monitorCallbacks.stream()
                                                                 .map(it -> CompletableFuture.runAsync(it::reportSuccess))
                                                                 // TODO: Collecting a Stream of CompletableFutures to allOf could be a FutureUtil imho
                                                                 .collect(collectingAndThen(
                                                                         toList(),
                                                                         list -> allOf(
                                                                                 list.toArray(CompletableFuture[]::new)))
                                                                 )
            );
        } else {
            monitorCallbacks.forEach(MonitorCallback::reportIgnored);
        }
    }

    /**
     * Creates a {@link MonitorCallback} for the given {@code message} using {@link MessageMonitor#onMessageIngested(Message)}
     * and registers:
     * <ul>
     *     <li>{@link MonitorCallback#reportSuccess()} on {@link ProcessingContext#onAfterCommit(Function)}</li>
     *     <li>{@link MonitorCallback#reportFailure(Throwable)} on {@link ProcessingContext#onError(ProcessingLifecycle.ErrorHandler)}</li>
     * </ul>
     *
     * Requires the {@link ProcessingContext} to be <code>non-null</code> and {@link ProcessingContext#isStarted() started},
     * if it is not, {@link MonitorCallback#reportIgnored()} is called instead.
     *
     * @param processingContext the current {@link ProcessingContext}, if <code>null</code>> or not started, the message is reported as ignored
     * @param messageMonitor the {@link MessageMonitor} used for callback creation
     * @param message the {@link Message} to report
     * @param <T> subtype of Message used
     */
    public static <T extends Message> void registerMonitorCallback(@Nullable ProcessingContext processingContext,
                                                                   @Nonnull MessageMonitor<T> messageMonitor,
                                                                   @Nonnull T message) {
        var monitorCallback = messageMonitor.onMessageIngested(message);

        if (processingContext != null && processingContext.isStarted()) {
            processingContext.onError((cts, phase, error) -> monitorCallback.reportFailure(error));
            processingContext.onAfterCommit(c -> CompletableFuture.runAsync(monitorCallback::reportSuccess));
        } else {
            monitorCallback.reportIgnored();
        }
    }

    private MessageMonitorUtils() {
        // utility class
    }
}
