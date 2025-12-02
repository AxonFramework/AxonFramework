package org.axonframework.examples.university.write.changecoursecapacity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

/**
 * REST Controller for renaming courses.
 * <p>
 * This controller handles course renaming requests and dispatches them via Axon CommandGateway.
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Slf4j
public class ChangeCourseCapacityController {

    private final CommandGateway commandGateway;

    /**
     * Endpoint for renaming an existing course.
     * <p>
     * Usage: POST /api/courses/{courseId}
     * <p>
     * Request body: { "name": "Course Name" }
     * <p>
     * Example with curl: curl -X PUT http://localhost:8080/api/courses/my-course-123/capacity \ -H "Content-Type:
     * application/json" \ -d '{"name":"Event Sourcing 101"}'
     */
    @PutMapping("/{courseId}/capacity")
    public ResponseEntity<Void> renameCourse(@PathVariable("courseId") String courseId,
                                             @RequestBody ChangeCourseCapacityRequest request) {
        log.info("Changing course capacity to: {}", request.capacity());

        try {
            ChangeCourseCapacity command = new ChangeCourseCapacity(CourseId.of(courseId), request.capacity);

            commandGateway.sendAndWait(command);

            log.info("Successfully changed capacity of course with ID: {}", courseId);

            return ResponseEntity
                    .status(HttpStatus.NO_CONTENT)
                    .build();
        } catch (Exception e) {
            log.error("Failed to change course capacity.", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    /**
     * Request DTO for changing capacity of a course.
     *
     * @param capacity The capacity of the course (required)
     */
    public record ChangeCourseCapacityRequest(
            Integer capacity
    ) {

    }
}