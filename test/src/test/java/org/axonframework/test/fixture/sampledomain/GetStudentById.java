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
 * Query to retrieve a student by their ID.
 *
 * @param id The unique identifier of the student to retrieve
 */
public record GetStudentById(String id) {

    /**
     * Wrapped result containing the student read model.
     *
     * @param student The student read model, or null if not found
     */
    public record Result(StudentReadModel student) {
    }
}
