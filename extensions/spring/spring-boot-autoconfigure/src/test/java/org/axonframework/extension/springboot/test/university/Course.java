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

package org.axonframework.extension.springboot.test.university;

import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.extension.spring.stereotype.EventSourced;
import org.slf4j.Logger;

import static org.axonframework.extension.springboot.test.university.UniversityTestApplication.TAG_COURSE_ID;
import static org.slf4j.LoggerFactory.getLogger;

@EventSourced(tagKey = TAG_COURSE_ID)
class Course {
    private static final Logger logger = getLogger(Course.class);

    private final String id;
    private String name;

    @EntityCreator
    Course(CourseCreated event) {
        logger.info("created course");
        this.id = event.id();
        this.name = event.name();
    }

    @EventSourcingHandler
    void handle(CourseUpdated event) {
        logger.info("updated course");
        name = event.name();
    }
}
