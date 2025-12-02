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

import org.axonframework.messaging.queryhandling.annotation.QueryHandler;

/**
 * Query handler for student-related queries.
 */
public class StudentQueryHandler {

    private final StudentRepository repository;

    /**
     * Constructs a StudentQueryHandler with the given repository.
     *
     * @param repository The repository to query student read models
     */
    public StudentQueryHandler(StudentRepository repository) {
        this.repository = repository;
    }

    /**
     * Handles queries to find a student by ID, returning a wrapped result.
     *
     * @param query The get student by ID query
     * @return The wrapped result containing the student read model
     * @throws StudentNotFoundException if the student is not found
     */
    @QueryHandler
    public GetStudentById.Result handle(GetStudentById query) {
        return new GetStudentById.Result(
                repository.findByIdOrThrow(query.id())
        );
    }
}
