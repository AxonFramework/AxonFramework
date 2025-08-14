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

package org.axonframework.integrationtests.testsuite.student;

import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.modelling.configuration.StatefulCommandHandlingModule;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Sets up the basics for the testsuite of the University domain with Student/Mentor/Course entities.
 * <p>
 * Can be customized by overriding the relevant methods. By default, uses a mix of different available options to
 * validate the different ways of setting up the event sourcing repository.
 * <p>
 * When using this test suite, be sure to invoke {@link #startApp()} when the test is all set.
 *
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 *
 * @since 5.0.0
 */
@Testcontainers
public abstract class AbstractStatefulCommandHandlingUniversityTestSuite extends AbstractUniversityTestSuite {

    private StatefulCommandHandlingModule.CommandHandlerPhase statefulCommandHandlingModule;

    @BeforeEach
    void setUp() throws IOException {
        super.setUp();
        statefulCommandHandlingModule = StatefulCommandHandlingModule.named("student-course-module")
                                                                     .entities()
                                                                     .entity(studentEntity)
                                                                     .entity(courseEntity)
                                                                     .entities(this::registerAdditionalEntities)
                                                                     .commandHandlers();
    }

    @Override
    protected ConfigurationEnhancer testSuiteConfigurationEnhancer() {
        return config -> config.registerModule(statefulCommandHandlingModule.build());
    }

    /**
     * Test suite implementations can invoke this method to register additional command handlers.
     *
     * @param handlerConfigurer The command handler phase of the {@link StatefulCommandHandlingModule}, allowing for
     *                          command handler registration.
     */
    protected void registerCommandHandlers(
            Consumer<StatefulCommandHandlingModule.CommandHandlerPhase> handlerConfigurer
    ) {
        statefulCommandHandlingModule.commandHandlers(handlerConfigurer);
    }
}
