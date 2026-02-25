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

package org.axonframework.examples.university.read.coursestats.projection;

import org.axonframework.examples.university.event.CourseCapacityChanged;
import org.axonframework.examples.university.event.CourseCreated;
import org.axonframework.examples.university.event.CourseRenamed;
import org.axonframework.examples.university.event.StudentSubscribedToCourse;
import org.axonframework.examples.university.event.StudentUnsubscribedFromCourse;
import org.axonframework.examples.university.read.coursestats.api.CoursesQueryResult;
import org.axonframework.examples.university.read.coursestats.api.FindAllCourses;
import org.axonframework.examples.university.read.coursestats.api.GetCourseStatsById;
import org.axonframework.messaging.core.annotation.SequencingPolicy;
import org.axonframework.messaging.core.sequencing.PropertySequencingPolicy;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.queryhandling.QueryUpdateEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@SequencingPolicy(type = PropertySequencingPolicy.class, parameters = {"courseId"})
class CoursesStatsProjector {

    private static final Logger logger = LoggerFactory.getLogger(CoursesStatsProjector.class);
    private final CourseStatsRepository repository;

    public CoursesStatsProjector(CourseStatsRepository repository) {
        this.repository = repository;
    }

    @EventHandler
    void handle(CourseCreated event, QueryUpdateEmitter emitter) {
        var stats = new CoursesStats(
                event.courseId(),
                event.name(),
                event.capacity(),
                0
        );
        repository.save(stats);
        emitUpdate(emitter, stats);
    }

    @EventHandler
    void handle(CourseRenamed event, QueryUpdateEmitter emitter) {
        var readModel = repository.findByIdOrThrow(event.courseId());
        var updatedReadModel = readModel.name(event.name());
        repository.save(updatedReadModel);
        emitUpdate(emitter, updatedReadModel);
    }

    @EventHandler
    void handle(CourseCapacityChanged event, QueryUpdateEmitter emitter) {
        var readModel = repository.findByIdOrThrow(event.courseId());
        var updatedReadModel = readModel.capacity(event.capacity());
        repository.save(updatedReadModel);
        emitUpdate(emitter, updatedReadModel);
    }

    @EventHandler
    void handle(StudentSubscribedToCourse event, QueryUpdateEmitter emitter) {
        var readModel = repository.findByIdOrThrow(event.courseId());
        var updatedReadModel = readModel.subscribedStudents(readModel.subscribedStudents() + 1);
        repository.save(updatedReadModel);
        emitUpdate(emitter, updatedReadModel);
    }

    @EventHandler
    void handle(StudentUnsubscribedFromCourse event, QueryUpdateEmitter emitter) {
        var readModel = repository.findByIdOrThrow(event.courseId());
        var updatedReadModel = readModel.subscribedStudents(readModel.subscribedStudents() - 1);
        repository.save(updatedReadModel);
        emitUpdate(emitter, updatedReadModel);
    }

    /**
     * Emits an update for subscription queries when course stats change. This should be called by the projection when
     * the read model is updated.
     */
    public void emitUpdate(QueryUpdateEmitter emitter, CoursesStats updatedStats) {
        logger.debug("[STATS PROJECTOR] Emitting updated courses stats for '{}'", updatedStats.courseId());
        emitter.emit(
                GetCourseStatsById.class,
                query -> updatedStats.courseId().equals(query.courseId()),
                new CoursesQueryResult(updatedStats)
        );
        emitter.emit(FindAllCourses.class,
                     q -> true,
                     new CoursesQueryResult(updatedStats)
        );
    }
}
