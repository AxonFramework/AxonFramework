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

package org.axonframework.config.testsuite.student.models;

import org.axonframework.config.testsuite.student.common.StudentMentorModelIdentifier;
import org.axonframework.config.testsuite.student.events.MentorAssignedToStudentEvent;
import org.axonframework.eventsourcing.EventSourcingHandler;

public class StudentMentorAssignmentModel {

    private StudentMentorModelIdentifier identifier;
    private boolean mentorHasMentee;
    private boolean menteeHasMentor;

    public StudentMentorAssignmentModel(
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
}
