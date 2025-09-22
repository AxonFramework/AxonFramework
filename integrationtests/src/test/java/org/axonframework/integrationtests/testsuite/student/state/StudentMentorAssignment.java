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

package org.axonframework.integrationtests.testsuite.student.state;

import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.EventCriteriaBuilder;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.eventstreaming.Tag;
import org.axonframework.integrationtests.testsuite.student.common.StudentMentorModelIdentifier;
import org.axonframework.integrationtests.testsuite.student.events.MentorAssignedToStudentEvent;

import java.util.List;

@EventSourcedEntity
public class StudentMentorAssignment {

    private StudentMentorModelIdentifier identifier;
    private boolean mentorHasMentee;
    private boolean menteeHasMentor;

    public StudentMentorAssignment(
            StudentMentorModelIdentifier identifier) {
        this.identifier = identifier;
    }

    public boolean isMentorHasMentee() {
        return mentorHasMentee;
    }

    public boolean isMenteeHasMentor() {
        return menteeHasMentor;
    }

    @EventSourcingHandler
    public void handle(MentorAssignedToStudentEvent event) {
        if (event.mentorId().equals(this.identifier.mentorId())) {
            mentorHasMentee = true;
        } else if (event.menteeId().equals(this.identifier.menteeId())) {
            menteeHasMentor = true;
        }
    }

    @EventCriteriaBuilder
    public static EventCriteria resolveCriteria(StudentMentorModelIdentifier id) {
        return EventCriteria.either(
                List.of(
                        EventCriteria.havingTags(new Tag("Student", id.menteeId()))
                                     .andBeingOneOfTypes(MentorAssignedToStudentEvent.class.getName()),
                        EventCriteria.havingTags(new Tag("Student", id.mentorId()))
                                     .andBeingOneOfTypes(MentorAssignedToStudentEvent.class.getName())
                )
        );
    }
}
