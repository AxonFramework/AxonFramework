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

package org.axonframework.examples.university.write.enrollstudent;

import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.examples.university.event.StudentEnrolledInFaculty;
import org.axonframework.examples.university.shared.FacultyTags;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.modelling.annotation.InjectEntity;
import org.springframework.stereotype.Component;

@Component
public class EnrollStudentInFacultyCommandHandler {

    @EventSourced(tagKey = FacultyTags.STUDENT_ID)
    public static class Student {

        private boolean exists;

        @EntityCreator
        private Student() {
        }

        @EventSourcingHandler
        void evolve(StudentEnrolledInFaculty enrolledInFaculty) {
            exists = true;
        }
    }

    @CommandHandler
    void handle(EnrollStudentInFaculty command,
                @InjectEntity(idProperty = "studentId") Student student,
                EventAppender eventAppender
    ) {
        if (!student.exists) {
            eventAppender.append(new StudentEnrolledInFaculty(command.studentId(),
                                                              command.firstName(),
                                                              command.lastName()));
        }
    }
}
