/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.eventsourcing.eventstore;

/**
 * Extension of {@link StorageEngineBackedEventStoreTestSuite} for aggregate-based storage engines. These engines use
 * sequence-number-based conflict detection (unique constraint on aggregate identifier + sequence number) rather than
 * criteria-based conflict detection.
 * <p>
 * Aggregate-based engines (e.g., JPA aggregate event storage) should extend this suite. DCB-capable engines should
 * extend {@link DcbBasedStorageEngineBackedEventStoreTestSuite} instead.
 *
 * @author Mateusz Nowak
 * @see StorageEngineBackedEventStoreTestSuite
 * @see DcbBasedStorageEngineBackedEventStoreTestSuite
 */
public abstract class AggregateBasedStorageEngineBackedEventStoreTestSuite<E extends EventStorageEngine>
        extends StorageEngineBackedEventStoreTestSuite<E> {
}
