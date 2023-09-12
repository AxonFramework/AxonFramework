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

package org.axonframework.modelling.command;

import org.axonframework.tracing.Span;

/**
 * Span factory that creates spans for the {@link Repository}. You can customize the spans of the bus by creating your
 * own implementation.
 *
 * @author Mitchell Herrijgers
 * @since 4.9.0
 */
public interface RepositorySpanFactory {

    /**
     * Creates a span that represents the loading of an aggregate with the provided identifier.
     *
     * @param aggregateId The identifier of the aggregate that is being loaded.
     * @return A span that represents the loading of the aggregate.
     */
    Span createLoadSpan(String aggregateId);

    /**
     * Creates a span that represents the time waiting to acquire a lock on an aggregate with the provided identifier.
     *
     * @param aggregateId The identifier of the aggregate that is trying to acquire a lock.
     * @return A span that represents the acquisition of the lock for the aggregate.
     */
    Span createObtainLockSpan(String aggregateId);

    /**
     * Creates a span that represents the time it took to hydrate the aggregate with data from, for example, the event
     * store.
     *
     * @param aggregateType The type of the aggregate that is being hydrated.
     * @param aggregateId   The identifier of the aggregate that is being hydrated.
     * @return A span that represents the hydration of the aggregate.
     */
    Span createInitializeStateSpan(String aggregateType, String aggregateId);
}
