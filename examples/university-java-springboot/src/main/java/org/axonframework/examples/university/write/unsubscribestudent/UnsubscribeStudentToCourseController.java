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

package org.axonframework.examples.university.write.unsubscribestudent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.examples.university.write.subscribestudent.SubscribeStudentToCourse;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for unsubscribing students from courses.
 * <p>
 * This controller handles student unsubscription requests and dispatches them via Axon CommandGateway.
 */
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@Slf4j
@Profile("!webmvc")
public class UnsubscribeStudentToCourseController {

    private final CommandGateway commandGateway;


    @DeleteMapping
    public ResponseEntity<Void> unsubscribeStudentFromCourse(@RequestBody UnsubscribeStudentToCourseRequest request) {
        log.info("Unsubscribing student {} from course {}", request.studentId(), request.courseId());

        try {
            var command = new UnsubscribeStudentFromCourse(request.studentId(), CourseId.of(request.courseId()));

            commandGateway.sendAndWait(command);

            log.info("Successfully un-subscribed student with ID: {} from course {}", command.studentId(), request.courseId());

            return ResponseEntity
                    .status(HttpStatus.NO_CONTENT)
                    .build();
        } catch (Exception e) {
            log.error("Failed to unsubscribe student to course.", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }

    /**
     * Request DTO for student un-subscription.
     *
     * @param studentId
     * @param courseId
     */
    public record UnsubscribeStudentToCourseRequest(
            String studentId,
            String courseId
    ) {

    }
}