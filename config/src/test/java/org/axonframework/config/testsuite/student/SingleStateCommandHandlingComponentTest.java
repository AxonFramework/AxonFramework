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

package org.axonframework.config.testsuite.student;


import org.axonframework.config.testsuite.student.commands.ChangeStudentNameCommand;
import org.axonframework.config.testsuite.student.events.StudentNameChangedEvent;
import org.axonframework.config.testsuite.student.state.Student;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.junit.jupiter.api.*;

/**
 * Tests whether stateful command handling components can handle singular model commands.
 */
class SingleStateCommandHandlingComponentTest extends AbstractStudentTestsuite {

    @Test
    void canHandleCommandThatTargetsOneStateViaLambdaStateManager() {
        var component = StatefulCommandHandlingComponent
                .create("MyStatefulCommandHandlingComponent", registry)
                .subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, state, context) -> {
                            ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                            Student student = state.loadEntity(Student.class, payload.id(), context).join();
                            appendEvent(context,
                                        new StudentNameChangedEvent(student.getId(), payload.name()));
                            return MessageStream.empty().cast();
                        });

        changeStudentName(component, "my-studentId-1", "name-1");
        verifyStudentName("my-studentId-1", "name-1");
        changeStudentName(component, "my-studentId-1", "name-2");
        verifyStudentName("my-studentId-1", "name-2");
        changeStudentName(component, "my-studentId-1", "name-3");
        verifyStudentName("my-studentId-1", "name-3");
        changeStudentName(component, "my-studentId-1", "name-4");
        verifyStudentName("my-studentId-1", "name-4");

        changeStudentName(component, "my-studentId-2", "name-5");
        verifyStudentName("my-studentId-1", "name-4");
        verifyStudentName("my-studentId-2", "name-5");
    }
}