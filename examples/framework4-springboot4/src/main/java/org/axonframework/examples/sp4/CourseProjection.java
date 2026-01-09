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
package org.axonframework.examples.sp4;

import org.axonframework.eventhandling.EventHandler;
import org.axonframework.examples.sp4.event.CourseCreated;
import org.axonframework.examples.sp4.query.CourseSummary;
import org.axonframework.examples.sp4.query.FindAllCourses;
import org.axonframework.examples.sp4.query.FindCourseById;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory projection that keeps track of created courses.
 */
@Component
public class CourseProjection {

    private final Map<String, CourseSummary> courses = new ConcurrentHashMap<>();

    @EventHandler
    public void on(CourseCreated event) {
        courses.put(event.id(), new CourseSummary(event.id(), event.name()));
    }

    @QueryHandler
    public Collection<CourseSummary> handle(FindAllCourses query) {
        return courses.values();
    }

    @QueryHandler
    public Optional<CourseSummary> handle(FindCourseById query) {
        return Optional.ofNullable(courses.get(query.id()));
    }
}
