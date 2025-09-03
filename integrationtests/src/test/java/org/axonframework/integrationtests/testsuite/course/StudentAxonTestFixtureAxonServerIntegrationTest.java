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

package org.axonframework.integrationtests.testsuite.course;

import org.axonframework.axonserver.connector.AxonServerConfiguration;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.integrationtests.testsuite.course.module.CourseCreated;
import org.axonframework.integrationtests.testsuite.course.module.CreateCourse;
import org.axonframework.integrationtests.testsuite.course.module.CreateCourseConfiguration;
import org.axonframework.test.fixture.AxonTestFixture;
import org.axonframework.test.server.AxonServerContainer;
import org.axonframework.test.server.AxonServerContainerUtils;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

public class StudentAxonTestFixtureAxonServerIntegrationTest {

    protected static final Logger logger = LoggerFactory.getLogger(StudentAxonTestFixtureAxonServerIntegrationTest.class);

    private static final AxonServerContainer container = new AxonServerContainer(
            "docker.axoniq.io/axoniq/axonserver:2025.2.0-SNAPSHOT")
            .withAxonServerHostname("localhost")
            .withDevMode(true)
            .withReuse(true);


    @BeforeAll
    static void beforeAll() {
        container.start();
    }

    @AfterAll
    static void afterAll() {
        container.stop();
    }


    private static EventSourcingConfigurer testConfigurer() throws IOException {
        var configurer = EventSourcingConfigurer.create();
        AxonServerContainerUtils.purgeEventsFromAxonServer(container.getHost(),
                                                           container.getHttpPort(),
                                                           "default",
                                                           AxonServerContainerUtils.DCB_CONTEXT);
        logger.info("Using Axon Server for integration test. UI is available at http://localhost:{}",
                    container.getHttpPort());
        AxonServerConfiguration axonServerConfiguration = new AxonServerConfiguration();
        axonServerConfiguration.setServers(container.getHost() + ":" + container.getGrpcPort());
        configurer.componentRegistry(cr -> cr.registerComponent(
                AxonServerConfiguration.class,
                c -> axonServerConfiguration
        ));
        return CreateCourseConfiguration.configure(configurer);
    }

    @RepeatedTest(10)
    void axonTestFixtureWorksWithAxonServer() throws IOException {
        EventSourcingConfigurer configurer = testConfigurer();
        var fixture = AxonTestFixture.with(configurer);

        var courseId = UUID.randomUUID().toString();

        fixture.given()
               .when()
               .command(new CreateCourse(courseId))
               .then()
               .success()
               .events(new CourseCreated(courseId));
    }
}
