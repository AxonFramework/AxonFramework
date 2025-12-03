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

import jakarta.validation.Valid;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.eventsourcing.annotation.EventTags;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.messaging.eventhandling.gateway.EventGateway;
import org.axonframework.modelling.StateManager;
import org.axonframework.modelling.annotation.InjectEntity;
import org.slf4j.Logger;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

@SpringBootApplication
public class UniversityTestApplication {

    static Logger logger = getLogger(UniversityTestApplication.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(UniversityTestApplication.class);
        //SpringApplication.run(UniversityTestApplication.class, args);
        app.setDefaultProperties(Map.of(
                "spring.config.location", "classpath:university/application.yml"
        ));
        app.setResourceLoader(new DefaultResourceLoader() {
            @Override
            public Resource getResource(String location) {
                if (!location.contains(":")) {
                    // no prefix -> resolve under university/
                    return super.getResource("classpath:/university/" + location);
                }
                return super.getResource(location);
            }
        });

        app.run(args);
    }

    // TODO: if command is declared like this then getting this error - only works if defined in a dedicated .java file
    //  NoHandlerForCommandException: No handler was subscribed for command [org.axonframework.extension.springboot.test.university.CreateCourse#0.0.1].
    // @Command(name = "CreateCourse") record CreateCourse(@NotEmpty String id, @NotEmpty String name) { }

    public static final String TAG_COURSE_ID = "courseId";

    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @EventTag(key = TAG_COURSE_ID)
    public @interface CourseIdTag { }

    @Service
    @Validated
    class MyHandler {

        @CommandHandler
        public void handle(@Valid CreateCourse cmd, EventAppender eventAppender) {
            logger.info("Received command: {}", cmd);
            eventAppender.append(new CourseCreated(cmd.id(), cmd.name()));
        }

        @EventHandler
        public void handle(CourseCreated event, @InjectEntity(idProperty = "id") Course course) {
            logger.info("Received event: {}", event);
        }

        @CommandHandler
        public void handle(
                @Valid UpdateCourse cmd,
                EventAppender eventAppender
                // uncomment this to remove the error or remove @EventTag from both events
                //, @InjectEntity(idProperty = "id") Course course
        ) {
            logger.info("Received command: {}", cmd);
            eventAppender.append(new CourseUpdated(cmd.id(), cmd.name()));
        }

        @EventHandler
        public void handle(CourseUpdated event) {
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
    ApplicationRunner runner(EventGateway gateway) {
        return args -> {
            gateway.publish(null, new CourseCreated("1", "Foo"));
            //gateway.sendAndWait(new UpdateCourse("1", "Bar"));
        };
    }
}
