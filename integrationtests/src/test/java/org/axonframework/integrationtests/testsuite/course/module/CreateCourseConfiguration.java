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

package org.axonframework.integrationtests.testsuite.course.module;

import org.axonframework.messaging.commandhandling.GenericCommandResultMessage;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.QualifiedName;
import org.axonframework.messaging.core.conversion.MessageConverter;

public class CreateCourseConfiguration {

    public static EventSourcingConfigurer configure(EventSourcingConfigurer configurer) {
        var stateEntity = EventSourcedEntityModule
                .autodetected(String.class, CreateCourseCommandHandler.State.class);

        var commandHandlingModule = CommandHandlingModule
                .named("CreateCourse")
                .commandHandlers()
                .commandHandler(new QualifiedName(CreateCourse.class), config -> ((command, context) -> {
                    var converter = context.component(MessageConverter.class);
                    var eventAppender = EventAppender.forContext(context);
                    var payload = command.payloadAs(CreateCourse.class, converter);
                    eventAppender.append(new CourseCreated(payload.courseId()));
                    return MessageStream.just(SUCCESSFUL_COMMAND_RESULT);
                }));
        return configurer
                .registerEntity(stateEntity)
                .registerCommandHandlingModule(commandHandlingModule);
    }

    private CreateCourseConfiguration() {
        // Prevent instantiation
    }

    protected static final GenericCommandResultMessage SUCCESSFUL_COMMAND_RESULT =
            new GenericCommandResultMessage(new MessageType("empty"), "successful");
}
