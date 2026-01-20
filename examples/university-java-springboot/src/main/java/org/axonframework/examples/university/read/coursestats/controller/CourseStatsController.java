/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.examples.university.read.coursestats.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.examples.university.read.coursestats.api.CoursesQueryResult;
import org.axonframework.examples.university.read.coursestats.api.FindAllCourses;
import org.axonframework.examples.university.read.coursestats.api.GetCourseStatsById;
import org.axonframework.examples.university.shared.CourseId;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST Controller demonstrating subscription queries with Server-Sent Events (SSE).
 * <p>
 * This controller streams real-time course statistics updates to clients.
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Slf4j
@Profile("!webmvc")
public class CourseStatsController {

    private final QueryGateway queryGateway;

    /**
     * Endpoint for retrieving all courses.
     * <p>
     * Usage: GET /api/courses/stats/
     */
    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<CoursesQueryResult> getCoursesStats() {
        return Mono
                .fromFuture(queryGateway.queryMany(new FindAllCourses(), CoursesQueryResult.class))
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Endpoint for retrieving all courses.
     * <p>
     * Usage: GET /api/courses/stats/stream
     */
    @GetMapping(value = "/stats/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<CoursesQueryResult>> streamCoursesStats() {
        return Flux
                .from(
                        queryGateway.subscriptionQuery(
                                new FindAllCourses(),
                                CoursesQueryResult.class
                        )
                )
                .doOnNext(it -> log.info("Received course stats update: {}", it))
                .doOnError(it -> log.error("Received course stats error.", it))
                .map(result -> ServerSentEvent.<CoursesQueryResult>builder()
                                              .data(result)
                                              .event("courses-stats-update")
                                              .build());
    }

    /**
     * Endpoint for getting course stats.
     * <p>
     * Usage: GET /api/courses/{courseId}/stats
     */
    @GetMapping(value = "/{courseId}/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<CoursesQueryResult> getCourseStats(
            @PathVariable(name = "courseId") String courseId) {

        var id = new CourseId(courseId);
        var query = new GetCourseStatsById(id);

        return Mono
                .fromFuture(
                        queryGateway.query(
                                query,
                                CoursesQueryResult.class
                        )

                )
                .doOnNext(it -> log.info("Received course stats: {}", it))
                .doOnError(it -> log.error("Received course stats error", it));
    }

    /**
     * Endpoint for streaming course stats using Server-Sent Events (SSE).
     * <p>
     * Usage: GET /api/courses/{courseId}/stats/stream
     * <p>
     * The client will receive: 1. An initial event with the current course stats 2. Real-time update events whenever
     * the course stats change
     * <p>
     * Example with curl: curl -N http://localhost:8080/api/courses/{courseId}/stats/stream
     * <p>
     * Example with JavaScript: const eventSource = new EventSource('/api/courses/{courseId}/stats/stream');
     * eventSource.onmessage = (event) => { const stats = JSON.parse(event.data); console.log('Course stats:', stats);
     * };
     */
    @GetMapping(value = "/{courseId}/stats/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<CoursesQueryResult>> streamCourseStats(
            @PathVariable(name = "courseId") String courseId) {

        log.info("Client subscribed to course stats stream for courseId: {}", courseId);

        var id = new CourseId(courseId);
        var query = new GetCourseStatsById(id);

        return Flux
                .from(
                        queryGateway.subscriptionQuery(
                                query,
                                CoursesQueryResult.class
                        )

                )
                .doOnNext(it -> log.info("Received course stats update: {}", it))
                .doOnError(it -> log.error("Received course stats error", it))
                .map(result -> ServerSentEvent.<CoursesQueryResult>builder()
                                              .data(result)
                                              .event("course-stats-update")
                                              .build());
    }

}
