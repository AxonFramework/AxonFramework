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

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.examples.sp4.command.CreateCourse;
import org.axonframework.examples.sp4.query.CourseSummary;
import org.axonframework.examples.sp4.query.FindAllCourses;
import org.axonframework.examples.sp4.query.FindCourseById;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Main application class.
 */
@SpringBootApplication
public class SpringBoot4ExampleApplication {

    private static final Logger log = LoggerFactory.getLogger(SpringBoot4ExampleApplication.class);

    /**
     * Starts the application
     *
     * @param args command line args.
     */
    public static void main(String[] args) {
        new SpringApplication(SpringBoot4ExampleApplication.class).run(args);
    }

    @Bean
    InMemoryTokenStore tokenStore() {
        return new InMemoryTokenStore();
    }

    // Configure an in-memory Event Store for event-sourced aggregates
    @Bean
    public EventStorageEngine eventStorageEngine() {
        return new InMemoryEventStorageEngine();
    }

    @Bean
    public EventStore eventStore(EventStorageEngine storageEngine) {
        return EmbeddedEventStore.builder()
                                 .storageEngine(storageEngine)
                                 .build();
    }

    @Bean
    ApplicationRunner runner(CommandGateway commandGateway, QueryGateway queryGateway) {
        return args -> {
            // Send a command to create a course
            String id = "1";
            commandGateway.sendAndWait(new CreateCourse(id, "Foo"));

            // Since projections are eventually consistent, wait until the projection knows about the course
            long deadline = System.currentTimeMillis() + 5_000; // up to 5 seconds
            while (System.currentTimeMillis() < deadline) {
                var maybeCourse = queryGateway
                        .query(new FindCourseById(id), ResponseTypes.optionalInstanceOf(CourseSummary.class))
                        .join();
                if (maybeCourse.isPresent()) {
                    log.info("FindCourseById: {}", maybeCourse.get());
                    break;
                }
                Thread.sleep(100);
            }

            // Query all courses and log the result
            var all = queryGateway
                    .query(new FindAllCourses(), ResponseTypes.multipleInstancesOf(CourseSummary.class))
                    .join();
            log.info("FindAllCourses: {} entries -> {}", all.size(), all);
        };
    }
}
