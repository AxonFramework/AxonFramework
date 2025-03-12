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

import org.axonframework.integrationtests.testsuite.student.events.StudentEnrolledEvent;
import org.axonframework.integrationtests.testsuite.student.events.StudentNameChangedEvent;
import org.axonframework.eventsourcing.EventSourcingHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Event-sourced Student model
 */
public class Student {

    private String id;
    private String name;
    private String mentorId;
    private String menteeId;
    private List<String> coursesEnrolled = new ArrayList<>();

    public Student(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getCoursesEnrolled() {
        return coursesEnrolled;
    }

    public String getMentorId() {
        return mentorId;
    }

    public String getMenteeId() {
        return menteeId;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @EventSourcingHandler
    public void handle(StudentEnrolledEvent event) {
        coursesEnrolled.add(event.courseId());
    }

    @EventSourcingHandler
    public void handle(StudentNameChangedEvent event) {
        name = event.name();
    }
}
