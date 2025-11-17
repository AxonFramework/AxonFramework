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

package org.axonframework.extension.springboot.example;

import jakarta.validation.Valid;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.TokenStore;
import org.axonframework.messaging.eventhandling.processing.streaming.token.store.inmemory.InMemoryTokenStore;
import org.slf4j.Logger;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

import static org.slf4j.LoggerFactory.getLogger;

@SpringBootApplication
public class UniversityExampleApplication {

    static Logger logger = getLogger(UniversityExampleApplication.class);

    public static void main(String[] args) {
        var app = new SpringApplication(UniversityExampleApplication.class);
        app.run(args);
    }


    // TODO: if command is declared like this then getting this error - only works if defined in a dedicated .java file
    //  NoHandlerForCommandException: No handler was subscribed for command [org.axonframework.extension.springboot.test.university.CreateCourse#0.0.1].
    // @Command(name = "CreateCourse") record CreateCourse(@NotEmpty String id, @NotEmpty String name) { }

    public static final String TAG_COURSE_ID = "courseId";

    @Service
    @Validated
    class MyHandler {

        @CommandHandler
        public void handle(@Valid CreateCourse cmd, EventAppender eventAppender) {
            logger.info("Received command: {}", cmd);
            eventAppender.append(new CourseCreated(cmd.id(), cmd.name()));
        }

        @EventHandler
        public void handle(CourseCreated event) {
            logger.info("Received event: {}", event);
        }
    }

    @Service
    @Validated
    class StudentHandler {

        @CommandHandler
        public void handle(@Valid CreateStudent cmd) {
            logger.info("Received command: {}", cmd);
        }
    }

    @Bean
    //@ConditionalOnBean(CommandGateway.class)
    ApplicationRunner runner(CommandGateway gateway) {
        return args -> {
            gateway.sendAndWait(new CreateCourse("1", "Hello World ... " + UUID.randomUUID()));
        };
    }

    @Bean
    TokenStore tokenStore() {
        return new InMemoryTokenStore();
    }
}
