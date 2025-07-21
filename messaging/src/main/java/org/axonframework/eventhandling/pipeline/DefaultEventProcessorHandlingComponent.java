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

package org.axonframework.eventhandling.pipeline;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.eventhandling.DelegatingEventHandlingComponent;
import org.axonframework.eventhandling.EventHandlingComponent;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessorSpanFactory;
import org.axonframework.eventhandling.MonitoringEventHandlingComponent;
import org.axonframework.eventhandling.TracingEventHandlingComponent;
import org.axonframework.eventhandling.interceptors.InterceptingEventHandlingComponent;
import org.axonframework.eventhandling.interceptors.MessageHandlerInterceptors;
import org.axonframework.monitoring.MessageMonitor;

@Internal
public class DefaultEventProcessorHandlingComponent extends DelegatingEventHandlingComponent {

    /**
     * Constructs the component with given {@code delegate} to receive calls.
     *
     * @param delegate The instance to delegate calls to.
     */
    public DefaultEventProcessorHandlingComponent(
            @Nonnull EventProcessorSpanFactory spanFactory,
            @Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor,
            @Nonnull MessageHandlerInterceptors messageHandlerInterceptors,
            @Nonnull EventHandlingComponent delegate,
            boolean streaming
    ) {
        super(
                new TracingEventHandlingComponent(
                        (event) -> spanFactory.createProcessEventSpan(streaming, event),
                        new MonitoringEventHandlingComponent(
                                messageMonitor,
                                new InterceptingEventHandlingComponent(
                                        messageHandlerInterceptors,
                                        delegate
                                )
                        )
                )
        );
    }
}
