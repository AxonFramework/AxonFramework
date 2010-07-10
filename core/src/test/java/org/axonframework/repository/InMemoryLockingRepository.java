/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.repository;

import org.axonframework.domain.StubAggregate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author Allard Buijze
 */
public class InMemoryLockingRepository extends LockingRepository<StubAggregate> {

    private Map<UUID, StubAggregate> store = new HashMap<UUID, StubAggregate>();
    private int saveCount;

    public InMemoryLockingRepository(LockingStrategy strategy) {
        super(strategy);
    }

    public InMemoryLockingRepository(LockManager lockManager) {
        super(lockManager);
    }

    @Override
    protected void doSave(StubAggregate aggregate) {
        store.put(aggregate.getIdentifier(), aggregate);
        saveCount++;
    }

    @Override
    protected StubAggregate doLoad(UUID aggregateIdentifier) {
        return store.get(aggregateIdentifier);
    }

    public int getSaveCount() {
        return saveCount;
    }

    public void resetSaveCount() {
        saveCount = 0;
    }
}
