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

package org.axonframework.integrationtests.testsuite.student;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.configuration.MessagingConfigurer;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.integrationtests.testsuite.student.commands.ChangeStudentNameCommand;
import org.axonframework.integrationtests.testsuite.student.commands.EnrollStudentToCourseCommand;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageDispatchInterceptor;
import org.axonframework.messaging.core.MessageHandlerInterceptor;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.List;
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
public abstract class AbstractCommandHandlingStudentIT extends AbstractStudentIT {

    private CommandHandlingModule.SetupPhase commandHandlingModule;
    private final List<MessageDispatchInterceptor<Message>> dispatchInterceptors = new ArrayList<>();
    private final List<MessageHandlerInterceptor<CommandMessage>> handlerInterceptors = new ArrayList<>();

    protected CommandGateway commandGateway;

    @BeforeEach
    void beforeEach() {
        commandHandlingModule = CommandHandlingModule.named("student-course-module");
        dispatchInterceptors.clear();
        handlerInterceptors.clear();
    }

    @Override
    protected EventSourcingConfigurer testSuiteConfigurer(EventSourcingConfigurer configurer) {
        EventSourcingConfigurer configured = super.testSuiteConfigurer(configurer)
                                                  .registerCommandHandlingModule(commandHandlingModule.commandHandlers());

        // Register interception if any were added
        if (!dispatchInterceptors.isEmpty() || !handlerInterceptors.isEmpty()) {
            configured = configured.messaging(this::registerInterceptors);
        }

        return configured;
    }

    /**
     * Registers dispatch and handler interception with the messaging configurer.
     *
     * @param messagingConfigurer The messaging configurer to register interception with.
     * @return The configured messaging configurer.
     */
    private MessagingConfigurer registerInterceptors(MessagingConfigurer messagingConfigurer) {
        MessagingConfigurer result = messagingConfigurer;
        for (MessageDispatchInterceptor<Message> interceptor : dispatchInterceptors) {
            result = result.registerCommandDispatchInterceptor(config -> interceptor);
        }
        for (MessageHandlerInterceptor<CommandMessage> interceptor : handlerInterceptors) {
            result = result.registerCommandHandlerInterceptor(config -> interceptor);
        }
        return result;
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

    /**
     * Registers a command dispatch interceptor that will be applied to all commands.
     *
     * @param interceptor The dispatch interceptor to register.
     */
    protected void registerCommandDispatchInterceptor(@Nonnull MessageDispatchInterceptor<Message> interceptor) {
        dispatchInterceptors.add(interceptor);
    }

    /**
     * Registers a command handler interceptor that will be applied during command handling.
     *
     * @param interceptor The handler interceptor to register.
     */
    protected void registerCommandHandlerInterceptor(@Nonnull MessageHandlerInterceptor<CommandMessage> interceptor) {
        handlerInterceptors.add(interceptor);
    }

    protected void changeStudentName(String studentId, String name) {
        sendCommand(new ChangeStudentNameCommand(studentId, name));
    }

    protected void enrollStudentToCourse(String studentId, String courseId) {
        sendCommand(new EnrollStudentToCourseCommand(studentId, courseId));
    }
}
