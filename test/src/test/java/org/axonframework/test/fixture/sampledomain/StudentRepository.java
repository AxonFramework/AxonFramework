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

/**
 * Repository interface for storing and retrieving student read models.
 */
public interface StudentRepository {

    /**
     * Saves a student read model to the repository.
     *
     * @param student The student read model to save
     */
    void save(StudentReadModel student);

    /**
     * Finds a student by their unique identifier.
     *
     * @param id The unique identifier of the student
     * @return The student read model, or null if not found
     */
    StudentReadModel findById(String id);

    /**
     * Finds a student by their unique identifier, throwing an exception if not found.
     *
     * @param id The unique identifier of the student
     * @return The student read model
     * @throws StudentNotFoundException if the student is not found
     */
    default StudentReadModel findByIdOrThrow(String id) {
        StudentReadModel student = findById(id);
        if (student == null) {
            throw new StudentNotFoundException("Student with id '" + id + "' not found");
        }
        return student;
    }
}
