/*
 * Copyright (c) 2010-2023. Axon Framework
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

import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.tracing.Span;

import java.util.List;

/**
 * Span factory that creates spans for the {@link EventProcessor} implementations. You can customize the spans of the
 * event processors by creating your own implementation.
 *
 * @author Mitchell Herrijgers
 * @since 4.9.0
 */
public interface EventProcessorSpanFactory {

    /**
     * Creates a span for the batch of events that are handled by the event processor.
     *
     * @param streaming     Whether the event is handled by a {@link StreamingEventProcessor}.
     * @param eventMessages The event messages that are in the batch.
     * @return The created span.
     */
    Span createBatchSpan(boolean streaming, List<? extends EventMessage<?>> eventMessages);

    /**
     * Creates a span for the handling of an event. This entails the entire interceptor chain and the handler.
     * For just measuring the handler invocation, see {@link #createProcesEventSpan(EventMessage)}.
     *
     * @param streaming    Whether the event is handled by a {@link StreamingEventProcessor}.
     * @param eventMessage The event message that is handled.
     * @return The created span.
     */
    Span createHandleEventSpan(boolean streaming, EventMessage<?> eventMessage);

    /**
     * Creates a span for the processing of an event. This entails just the invocation handler.
     * @param eventMessage The event message that is handled.
     * @return The created span.
     */
    Span createProcesEventSpan(EventMessage<?> eventMessage);
}
