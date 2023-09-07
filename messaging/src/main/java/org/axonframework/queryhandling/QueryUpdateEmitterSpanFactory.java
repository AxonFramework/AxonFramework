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

package org.axonframework.queryhandling;

import org.axonframework.tracing.Span;

/**
 * Span factory that creates spans for the {@link QueryBus}. You can customize the spans of the bus by creating your own
 * implementation.
 *
 * @author Mitchell Herrijgers
 * @since 4.9.0
 */
public interface QueryUpdateEmitterSpanFactory {

    /**
     * Creates a span for the scheduling of emitting a query update. Query updates are scheduled to be emitted in the
     * commit phase of the UnitOfWork, so these traces are separated to see the proper timings.
     *
     * @param update The update to create a span for.
     * @return The created span.
     */
    Span createUpdateScheduleEmitSpan(SubscriptionQueryUpdateMessage<?> update);

    /**
     * Creates a span for the actual emit of a query update when the UnitOfWork commits (or immediately if no UnitOfWork
     * is active).
     *
     * @param update The update to create a span for.
     * @return The created span.
     */
    Span createUpdateEmitSpan(SubscriptionQueryUpdateMessage<?> update);

    /**
     * Propagates the context of the current span to the given update message.
     *
     * @param update The update message to propagate the context to.
     * @param <T>    The type of the payload of the update message.
     * @param <M>    The type of the update message.
     * @return The update message with the propagated context.
     */
    <T, M extends SubscriptionQueryUpdateMessage<T>> M propagateContext(M update);
}
