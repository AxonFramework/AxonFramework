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

package org.axonframework.test.fixture.sampledomain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link StudentRepository} for testing purposes.
 */
public class InMemoryStudentRepository implements StudentRepository {

    private final Map<String, StudentReadModel> store = new ConcurrentHashMap<>();

    @Override
    public void save(StudentReadModel student) {
        store.put(student.id(), student);
    }

    @Override
    public StudentReadModel findById(String id) {
        return store.get(id);
    }
}
