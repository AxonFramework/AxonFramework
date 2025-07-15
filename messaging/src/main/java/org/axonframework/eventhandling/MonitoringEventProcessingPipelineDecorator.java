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

package org.axonframework.eventhandling;

import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.MessageMonitor.MonitorCallback;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Decorator for {@link EventProcessingPipeline} that adds message monitoring logic using a {@link MessageMonitor}.
 */
public class MonitoringEventProcessingPipelineDecorator extends EventProcessingPipelineDecorator {
    private final MessageMonitor<? super EventMessage<?>> messageMonitor;

    public MonitoringEventProcessingPipelineDecorator(EventProcessingPipeline delegate, MessageMonitor<? super EventMessage<?>> messageMonitor) {
        super(delegate);
        this.messageMonitor = messageMonitor;
    }

    @Override
    public CompletableFuture<Void> processInUnitOfWork(
            List<? extends EventMessage<?>> eventMessages,
            UnitOfWork unitOfWork,
            Collection<Segment> processingSegments
    ) throws Exception {
        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
        for (EventMessage<?> message : eventMessages) {
            MonitorCallback callback = messageMonitor.onMessageIngested(message);
            result = result.thenCompose(v -> {
                try {
                    return delegate.processInUnitOfWork(List.of(message), unitOfWork, processingSegments)
                        .handle((r, ex) -> {
                            if (ex == null) {
                                callback.reportSuccess();
                            } else {
                                callback.reportFailure(ex);
                                if (ex instanceof RuntimeException) throw (RuntimeException) ex;
                                if (ex instanceof Error) throw (Error) ex;
                                throw new java.util.concurrent.CompletionException(ex);
                            }
                            return null;
                        });
                } catch (Exception ex) {
                    callback.reportFailure(ex);
                    throw new java.util.concurrent.CompletionException(ex);
                }
            });
        }
        return result;
    }
} 