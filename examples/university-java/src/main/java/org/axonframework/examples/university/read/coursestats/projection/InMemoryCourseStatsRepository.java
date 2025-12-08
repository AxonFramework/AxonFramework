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

import org.axonframework.examples.university.shared.CourseId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class InMemoryCourseStatsRepository implements CourseStatsRepository {

    private final ConcurrentHashMap<CourseId, CoursesStats> stats = new ConcurrentHashMap<>();

    @Override
    public CoursesStats save(CoursesStats stats) {
        this.stats.put(stats.courseId(), stats);
        return stats;
    }

    @Override
    public Optional<CoursesStats> findById(CourseId courseId) {
        return Optional.ofNullable(stats.get(courseId));
    }

    @Override
    public List<CoursesStats> findAll() {
        return stats.values().stream().toList();
    }
}
