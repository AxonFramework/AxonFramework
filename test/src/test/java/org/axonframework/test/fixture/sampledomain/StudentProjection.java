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

import org.axonframework.messaging.eventhandling.annotation.EventHandler;

/**
 * Event handler that projects student events into the student read model.
 */
public class StudentProjection {

    private final StudentRepository repository;

    /**
     * Constructs a StudentProjector with the given repository.
     *
     * @param repository The repository to store student read models
     */
    public StudentProjection(StudentRepository repository) {
        this.repository = repository;
    }

    /**
     * Handles the StudentNameChangedEvent by updating the student read model.
     *
     * @param event The student name changed event
     */
    @EventHandler
    public void on(StudentNameChangedEvent event) {
        repository.save(new StudentReadModel(event.id(), event.name()));
    }
}
