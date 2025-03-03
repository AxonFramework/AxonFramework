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


import org.axonframework.commandhandling.annotation.AnnotatedCommandHandlingComponent;
import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.config.Configuration;
import org.axonframework.config.testsuite.student.commands.ChangeStudentNameCommand;
import org.axonframework.config.testsuite.student.events.StudentNameChangedEvent;
import org.axonframework.config.testsuite.student.models.Student;
import org.axonframework.eventhandling.EventSink;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.axonframework.modelling.ModelRegistry;
import org.axonframework.modelling.command.StatefulCommandHandlingComponent;
import org.axonframework.modelling.command.annotation.InjectModel;
import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests whether stateful command handling components can handle singular model commands.
 */
class SingleModelCommandHandlingComponentTest extends AbstractStudentTestsuite {

    @Test
    void canHandleCommandThatTargetsOneModelViaLambdaModelContainer() {
        var component = StatefulCommandHandlingComponent
                .create("MyStatefulCommandHandlingComponent", registry)
                .subscribe(
                        new QualifiedName(ChangeStudentNameCommand.class),
                        (command, model, context) -> {
                            ChangeStudentNameCommand payload = (ChangeStudentNameCommand) command.getPayload();
                            Student student = model.getModel(Student.class, payload.id()).join();
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

    @Test
    void canHandleCommandThatTargetsOneModelViaAnnotatedModelContainerParameter() {
        SingleModelAnnotatedCommandHandler handler = new SingleModelAnnotatedCommandHandler();

        var configuration = Mockito.mock(Configuration.class);
        Mockito.when(configuration.getComponent(ModelRegistry.class)).thenReturn(registry);
        Mockito.when(configuration.getComponent(EventSink.class)).thenReturn(eventStore);

        var component = StatefulCommandHandlingComponent
                .create("InjectedStateHandler", registry)
                .subscribe(new AnnotatedCommandHandlingComponent<>(
                        handler,
                        getParameterResolverFactory()));


        changeStudentName(component, "my-studentId-1", "name-1");
        verifyStudentName("my-studentId-1", "name-1");


        changeStudentName(component, "my-studentId-1", "name-2");
        verifyStudentName("my-studentId-1", "name-2");
    }

    class SingleModelAnnotatedCommandHandler {

        @CommandHandler
        public void handle(
                ChangeStudentNameCommand command,
                @InjectModel Student student,
                EventSink eventSink,
                ProcessingContext context
        ) {
            // Change name through event
            eventSink.publish(context, DEFAULT_CONTEXT, new GenericEventMessage<>(
                    new MessageType(StudentNameChangedEvent.class),
                    new StudentNameChangedEvent(student.getId(), command.name())
            ));
            // Model through magic of repository automatically updated
            assertEquals(student.getName(), command.name());
        }
    }
}