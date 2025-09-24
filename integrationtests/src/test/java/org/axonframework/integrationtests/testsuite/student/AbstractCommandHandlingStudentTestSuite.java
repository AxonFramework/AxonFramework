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

import jakarta.annotation.Nonnull;
import org.axonframework.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.integrationtests.testsuite.student.commands.ChangeStudentNameCommand;
import org.axonframework.integrationtests.testsuite.student.commands.EnrollStudentToCourseCommand;

import java.util.function.Consumer;

/**
 * Abstract test suite for command handling in a university context, extending the base student test suite. It provides
 * a common configuration for command handling modules and allows for additional command handlers to be registered by
 * subclasses.
 *
 * @author Mateusz Nowak
 * @author Mitchell Herrijgers
 * @author Steven van Beelen
 */
public abstract class AbstractCommandHandlingStudentTestSuite extends AbstractStudentTestSuite {

    private final CommandHandlingModule.SetupPhase commandHandlingModule =
            CommandHandlingModule.named("student-course-module");

    protected CommandGateway commandGateway;

    @Override
    protected EventSourcingConfigurer testSuiteConfigurer(EventSourcingConfigurer configurer) {
        return super.testSuiteConfigurer(configurer)
                    .registerCommandHandlingModule(commandHandlingModule.commandHandlers());
    }

    @Override
    protected void startApp() {
        super.startApp();
        commandGateway = startedConfiguration.getComponent(CommandGateway.class);
    }

    /**
     * Test suite implementations can invoke this method to register additional command handlers.
     *
     * @param handlerConfigurer The command handler phase of the {@link CommandHandlingModule}, allowing for command
     *                          handler registration.
     */
    protected void registerCommandHandlers(
            @Nonnull Consumer<CommandHandlingModule.CommandHandlerPhase> handlerConfigurer
    ) {
        commandHandlingModule.commandHandlers(handlerConfigurer);
    }

    protected void changeStudentName(String studentId, String name) {
        sendCommand(new ChangeStudentNameCommand(studentId, name));
    }

    protected void enrollStudentToCourse(String studentId, String courseId) {
        sendCommand(new EnrollStudentToCourseCommand(studentId, courseId));
    }
}
