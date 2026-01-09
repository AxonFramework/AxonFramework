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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.axonframework.eventsourcing.eventstore.EmbeddedEventStore;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.examples.sp4.command.CreateCourse;
import org.axonframework.examples.sp4.query.CourseSummary;
import org.axonframework.examples.sp4.query.FindAllCourses;
import org.axonframework.examples.sp4.query.FindCourseById;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.UUID;
import java.util.function.BooleanSupplier;

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
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();

        mapper.activateDefaultTyping(
                ptv,
                ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE,
                JsonTypeInfo.As.PROPERTY
        );

        return mapper;
    }

    @Bean
    @Profile("inmem")
    InMemoryTokenStore tokenStore() {
        return new InMemoryTokenStore();
    }

    // Configure an in-memory Event Store for event-sourced aggregates
    @Bean
    @Profile("inmem")
    public EventStorageEngine eventStorageEngine() {
        return new InMemoryEventStorageEngine();
    }

    @Bean
    @Profile("inmem")
    public EventStore eventStore(EventStorageEngine storageEngine) {
        return EmbeddedEventStore.builder()
                .storageEngine(storageEngine)
                .build();
    }

    @Bean
    ApplicationRunner runner(CommandGateway commandGateway, QueryGateway queryGateway, AxonServerConnectionManager connectionManager) {
        return args -> {
            // Wait up to 10s for Axon Server connection
            await(10_000, 200, () ->
                    connectionManager.getConnection() != null && connectionManager.getConnection().isConnected()
            );
            String id = UUID.randomUUID().toString();
            commandGateway.sendAndWait(new CreateCourse(id, "Foo" + System.currentTimeMillis()));

            // Since projections are eventually consistent, wait until the projection knows about the course
            await(5_000, 100, () ->
                    queryGateway
                            .query(new FindCourseById(id), ResponseTypes.optionalInstanceOf(CourseSummary.class))
                            .join()
                            .isPresent()
            );
            queryGateway
                    .query(new FindCourseById(id), ResponseTypes.optionalInstanceOf(CourseSummary.class))
                    .join()
                    .ifPresent(course -> log.info("FindCourseById: {}", course));

            // Query all courses and log the result
            var all = queryGateway
                    .query(new FindAllCourses(), ResponseTypes.multipleInstancesOf(CourseSummary.class))
                    .join();
            log.info("FindAllCourses: {} entries -> {}", all.size(), all);
        };
    }


    static void await(long timeoutMs, long intervalMs, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(intervalMs);
        }
        throw new IllegalStateException("Condition not met within " + timeoutMs + " ms");
    }
}
