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

import org.axonframework.axonserver.connector.AxonServerConfigurationEnhancer;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.integrationtests.testsuite.course.module.CourseCreated;
import org.axonframework.integrationtests.testsuite.course.module.CreateCourse;
import org.axonframework.integrationtests.testsuite.course.module.CreateCourseConfiguration;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class StudentAxonTestFixtureInMemoryEventStoreIntegrationTest {

    protected static final Logger logger = LoggerFactory.getLogger(
            StudentAxonTestFixtureInMemoryEventStoreIntegrationTest.class);

    private static EventSourcingConfigurer testConfigurer() {
        var configurer = EventSourcingConfigurer.create();
        configurer.componentRegistry(r -> r.disableEnhancer(AxonServerConfigurationEnhancer.class));
        return CreateCourseConfiguration.configure(configurer);
    }

    @RepeatedTest(10)
    void axonTestFixtureWorksWithInMemoryEventStore() {
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
