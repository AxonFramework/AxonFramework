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
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

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

    @Service
    @Validated
    class MyHandler {

        @CommandHandler
        public void handle(@Valid CreateCourse cmd) {
            logger.info("Received command: {}", cmd);
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
    ApplicationRunner runner(CommandGateway gateway) {
        return args -> {
            gateway.sendAndWait(new CreateCourse("1", "Foo"));
        };
    }
}
