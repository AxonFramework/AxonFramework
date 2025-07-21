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

package org.axonframework.eventhandling.subscribing;

import jakarta.annotation.Nonnull;
import org.axonframework.eventhandling.ErrorHandler;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessorSpanFactory;
import org.axonframework.eventhandling.configuration.EventProcessorsCustomization;
import org.axonframework.monitoring.MessageMonitor;

/**
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class SubscribingEventProcessorsCustomization extends EventProcessorsCustomization {

    public SubscribingEventProcessorsCustomization(@Nonnull EventProcessorsCustomization eventProcessorsCustomization) {
        super(eventProcessorsCustomization);
    }

    public SubscribingEventProcessorsCustomization errorHandler(@Nonnull ErrorHandler errorHandler) {
        super.errorHandler(errorHandler);
        return this;
    }

    public EventProcessorsCustomization messageMonitor(
            @Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor) {
        super.messageMonitor(messageMonitor);
        return this;
    }

    public EventProcessorsCustomization spanFactory(@Nonnull EventProcessorSpanFactory spanFactory) {
        super.spanFactory(spanFactory);
        return this;
    }
}
