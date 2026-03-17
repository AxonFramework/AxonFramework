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

package org.axonframework.examples.university.write.enrollstudent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller for enrolling students.
 * <p>
 * This controller handles student enrollment requests and dispatches them via Axon CommandGateway.
 */
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Slf4j
@Profile("!webmvc")
public class EnrollStudentInFacultyController {

    private final CommandGateway commandGateway;


    /**
     * Endpoint for enrolling a student.
     * <p>
     * Usage: POST /api/students
     * <p>
     * Request body: { "studentId": "student-id-string", "firstName": "Kermit", "lastName": "The Frog" }
     * <p>
     * Example with curl: curl -X POST http://localhost:8080/api/students \ -H "Content-Type: application/json" \ -d
     * '{"studentId":"13","firstName":"Kermit","lastName":"The Frog"}'
     */
    @PostMapping
    public ResponseEntity<EnrollStudentResponse> enrollStudentInFaculty(@RequestBody EnrollStudentRequest request) {
        log.info("Enrolling student: {} {}", request.firstName(), request.lastName());

        try {
            String studentId = request.studentId() != null && !request.studentId().isEmpty()
                    ? request.studentId()
                    : UUID.randomUUID().toString();

            var command = new EnrollStudentInFaculty(studentId, request.firstName(), request.lastName());

            commandGateway.sendAndWait(command);

            log.info("Successfully enrolled student with ID: {}", studentId);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new EnrollStudentResponse(studentId, request.firstName(), request.lastName()));
        } catch (Exception e) {
            log.error("Failed to enroll student.", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    /**
     * Request DTO for enrollment of a student in faculty.
     *
     * @param studentId Optional student ID. If not provided, a random ID will be generated.
     * @param firstName Student's first name (required)
     * @param lastName  Student's last name (required)
     */
    public record EnrollStudentRequest(
            String studentId,
            String firstName,
            String lastName
    ) {

    }

    /**
     * Response DTO after successfully enrollment of a student in faculty.
     *
     * @param studentId The ID of the created student
     * @param firstName Student's first name (required)
     * @param lastName  Student's last name (required)
     */
    public record EnrollStudentResponse(
            String studentId,
            String firstName,
            String lastName
    ) {

    }
}