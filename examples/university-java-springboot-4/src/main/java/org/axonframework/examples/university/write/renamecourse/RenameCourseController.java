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

package org.axonframework.examples.university.write.renamecourse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for renaming courses.
 * <p>
 * This controller handles course renaming requests and dispatches them via Axon CommandGateway.
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Slf4j
@Profile("!webmvc")
public class RenameCourseController {

    private final CommandGateway commandGateway;

    /**
     * Endpoint for renaming an existing course.
     * <p>
     * Usage: POST /api/courses/{courseId}
     * <p>
     * Request body: { "name": "Course Name" }
     * <p>
     * Example with curl: curl -X PUT http://localhost:8080/api/courses/my-course-123/rename \ -H "Content-Type:
     * application/json" \ -d '{"name":"Event Sourcing 101"}'
     */
    @PutMapping("/{courseId}/rename")
    public ResponseEntity<Void> renameCourse(@PathVariable("courseId") String courseId,
                                             @RequestBody RenameCourseRequest request) {
        log.info("Renaming course: {} to {}", request.name(), request.name);
        try {
            RenameCourse command = new RenameCourse(new CourseId(courseId), request.name());

            commandGateway.sendAndWait(command);

            log.info("Successfully renamed course with ID: {}", courseId);

            return ResponseEntity
                    .status(HttpStatus.NO_CONTENT)
                    .build();
        } catch (Exception e) {
            log.error("Failed to rename course", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    /**
     * Request DTO for renaming a course.
     *
     * @param name The name of the course (required)
     */
    public record RenameCourseRequest(
            String name
    ) {

    }
}