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

import org.axonframework.integrationtests.testsuite.AxonServerEventStorageEngineProvider;
import org.axonframework.integrationtests.testsuite.TestEventStorageEngine;
import org.axonframework.integrationtests.testsuite.student.commands.EnrollStudentToCourseCommand;
import org.axonframework.messaging.commandhandling.CommandMessage;
import org.axonframework.messaging.commandhandling.CommandResultMessage;
import org.axonframework.messaging.core.MessageStream;
import org.junit.jupiter.api.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests validating inline command and command result payload conversion behavior.
 * <p>
 * Pinned to Axon Server because payload conversion (serialization/deserialization) only occurs
 * when commands transit through {@code AxonServerCommandBus}.
 *
 * @author Jakob Hatzl
 */
@TestEventStorageEngine(AxonServerEventStorageEngineProvider.class)
class CommandPayloadConversionIT extends AbstractCommandHandlingStudentIT {

    @Test
    void commandAndResultSupportInlinePayloadConversion()
            throws InterruptedException, ExecutionException {
        String studentId = "96d720f0-5cc3-4f76-8d04-8a8d223aa484";
        String courseId = "6ee4defe-cbcb-4b6b-9b9a-f27621856932";
        EnrollStudentToCourseCommand exCommand = new EnrollStudentToCourseCommand(studentId, courseId);
        AtomicReference<CommandMessage> actualCommandMessage = new AtomicReference<>();

        // Register command handler
        registerCommandHandlers(handlerPhase -> handlerPhase.commandHandler(
                new org.axonframework.messaging.core.QualifiedName(EnrollStudentToCourseCommand.class),
                c -> (command, context) -> {
                    actualCommandMessage.set(command);
                    return MessageStream.just(SUCCESSFUL_COMMAND_RESULT).cast();
                }
        ));
        startApp();

        // send command
        CommandResultMessage message = (CommandResultMessage) commandGateway.send(exCommand).getResultMessage().get();

        // verify command message and result
        CommandMessage command = actualCommandMessage.get();
        assertThat(command.payloadType())
                .isNotEqualTo(EnrollStudentToCourseCommand.class);
        assertThatCode(() -> command.payloadAs(EnrollStudentToCourseCommand.class))
                .doesNotThrowAnyException();
        assertThat(command.payloadAs(EnrollStudentToCourseCommand.class))
                .isInstanceOf(EnrollStudentToCourseCommand.class)
                .isEqualTo(exCommand);

        assertThat(message.payloadType())
                .isNotEqualTo(String.class);
        assertThatCode(() -> message.payloadAs(String.class))
                .doesNotThrowAnyException();
        assertThat(message.payloadAs(String.class))
                .isEqualTo(SUCCESSFUL_COMMAND_RESULT.payload());
    }
}
