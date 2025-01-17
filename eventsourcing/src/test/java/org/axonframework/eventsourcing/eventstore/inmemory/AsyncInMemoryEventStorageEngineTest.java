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

package org.axonframework.eventsourcing.eventstore.inmemory;

import org.axonframework.eventsourcing.eventstore.SimpleEventStore;
import org.axonframework.eventsourcing.eventstore.StorageEngineTestSuite;

/**
 * Test class validating the {@link SimpleEventStore} together with the {@link AsyncInMemoryEventStorageEngine}.
 *
 * @author Steven van Beelen
 */
class AsyncInMemoryEventStorageEngineTest extends StorageEngineTestSuite<AsyncInMemoryEventStorageEngine> {

    @Override
    protected AsyncInMemoryEventStorageEngine buildStorageEngine() {
        return new AsyncInMemoryEventStorageEngine();
    }
}
