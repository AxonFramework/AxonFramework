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

import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.MessageMonitor.MonitorCallback;

import java.util.List;

/**
 * Decorator for {@link EventProcessor} that adds message monitoring logic using a {@link MessageMonitor}.
 */
public class MonitoringEventProcessorDecorator extends EventProcessorDecorator {
    private final MessageMonitor<? super EventMessage<?>> messageMonitor;

    public MonitoringEventProcessorDecorator(EventProcessor delegate, MessageMonitor<? super EventMessage<?>> messageMonitor) {
        super(delegate);
        this.messageMonitor = messageMonitor;
    }

    /**
     * Wraps the given processing logic with message monitoring callbacks.
     *
     * @param eventMessages The batch of messages being processed
     * @param processingLogic The logic to execute for each message
     */
    protected void withMonitoring(List<? extends EventMessage<?>> eventMessages, MessageProcessingLogic processingLogic) throws Exception {
        for (EventMessage<?> message : eventMessages) {
            MonitorCallback callback = messageMonitor.onMessageIngested(message);
            try {
                processingLogic.process(message);
                callback.reportSuccess();
            } catch (Exception e) {
                callback.reportFailure(e);
                throw e;
            }
        }
    }

    /**
     * Functional interface for processing a single event message.
     */
    @FunctionalInterface
    public interface MessageProcessingLogic {
        void process(EventMessage<?> message) throws Exception;
    }
} 