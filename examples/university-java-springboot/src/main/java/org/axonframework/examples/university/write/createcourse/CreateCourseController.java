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

package org.axonframework.examples.university.write.createcourse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for creating courses.
 * <p>
 * This controller handles course creation requests and dispatches them via Axon CommandGateway.
 */
@RestController
@RequestMapping("/api/courses")
@Slf4j
@RequiredArgsConstructor
public class CreateCourseController {

    private final CommandGateway commandGateway;

    /**
     * Endpoint for creating a new course.
     * <p>
     * Usage: POST /api/courses
     * <p>
     * Request body:
     * {
     *   "courseId": "course-id-string",
     *   "name": "Course Name",
     *   "capacity": 30
     * }
     * <p>
     * Example with curl:
     * curl -X POST http://localhost:8080/api/courses \
     *   -H "Content-Type: application/json" \
     *   -d '{"courseId":"my-course-123","name":"Event Sourcing 101","capacity":25}'
     */
    @PostMapping
    public ResponseEntity<CreateCourseResponse> createCourse(@RequestBody CreateCourseRequest request) {
        log.info("Creating course: {} with capacity: {}", request.name(), request.capacity());

        try {
            CourseId courseId = request.courseId() != null && !request.courseId().isEmpty()
                    ? new CourseId(request.courseId())
                    : CourseId.random();

            CreateCourse command = new CreateCourse(courseId, request.name(), request.capacity());

            commandGateway.sendAndWait(command);

            log.info("Successfully created course with ID: {}", courseId.raw());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new CreateCourseResponse(courseId.raw(), request.name(), request.capacity()));

        } catch (Exception e) {
            log.error("Failed to create course.", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    /**
     * Request DTO for creating a course.
     *
     * @param courseId Optional course ID. If not provided, a random ID will be generated.
     * @param name The name of the course (required)
     * @param capacity The maximum number of students (required)
     */
    public record CreateCourseRequest(
            String courseId,
            String name,
            int capacity
    ) {}

    /**
     * Response DTO after successfully creating a course.
     *
     * @param courseId The ID of the created course
     * @param name The name of the course
     * @param capacity The capacity of the course
     */
    public record CreateCourseResponse(
            String courseId,
            String name,
            int capacity
    ) {}
}