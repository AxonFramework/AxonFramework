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

package org.axonframework.integrationtests.testsuite.administration;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandlingComponent;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.configuration.Configuration;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.integrationtests.testsuite.administration.commands.AssignTaskCommand;
import org.axonframework.integrationtests.testsuite.administration.commands.ChangeEmailAddress;
import org.axonframework.integrationtests.testsuite.administration.commands.CompleteTaskCommand;
import org.axonframework.integrationtests.testsuite.administration.commands.CreateCustomer;
import org.axonframework.integrationtests.testsuite.administration.commands.CreateEmployee;
import org.axonframework.integrationtests.testsuite.administration.commands.GiveRaise;
import org.axonframework.integrationtests.testsuite.administration.common.PersonIdentifier;
import org.axonframework.integrationtests.testsuite.administration.common.PersonType;
import org.axonframework.messaging.unitofwork.ProcessingContext;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletionException;

/**
 * Test suite for verifying polymorphic behavior of entities. Can be implemented by different test classes that verify
 * different ways of building the {@link org.axonframework.modelling.entity.EntityCommandHandlingComponent}.
 */
public abstract class AbstractAdministrationTestSuite {

    private static final CreateEmployee CREATE_EMPLOYEE_1_COMMAND = new CreateEmployee(
            new PersonIdentifier(PersonType.EMPLOYEE, "1234"),
            "Herrijgers",
            "Mitchell",
            "mitchell.herrijgers@axoniq.io",
            "Bug Creator",
            3000.0);


    private static final CreateCustomer CREATE_CUSTOMER_1_COMMAND = new CreateCustomer(
            new PersonIdentifier(PersonType.CUSTOMER, "shomer"),
            "Simpson",
            "Homer",
            "homer@the-simpsons.io"
    );

    private CommandHandlingComponent component;
    private CommandGateway commandGateway;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer.create()
                               .lifecycleRegistry(lr -> {
                                   lr.onStart(0, c -> {
                                       component = getCommandHandlingComponent(c);
                                       c.getComponent(CommandBus.class).subscribe(component);
                                       commandGateway = c.getComponent(CommandGateway.class);
                                   });
                               })
                               .start();
    }

    abstract CommandHandlingComponent getCommandHandlingComponent(Configuration configuration);

    @Test
    void canNotCreateDuplicateEmployee() {
        commandGateway.send(CREATE_EMPLOYEE_1_COMMAND, ProcessingContext.NONE).getResultMessage().join();

        assertThrowsExceptionWithText("Employee already created", () -> {
            commandGateway.send(CREATE_EMPLOYEE_1_COMMAND, ProcessingContext.NONE).getResultMessage().join();
        });
    }


    @Test
    void canNotCreateDuplicateCustomer() {
        commandGateway.send(CREATE_CUSTOMER_1_COMMAND, ProcessingContext.NONE).getResultMessage().join();

        assertThrowsExceptionWithText("Customer already created", () -> {
            commandGateway.send(CREATE_CUSTOMER_1_COMMAND, ProcessingContext.NONE).getResultMessage().join();
        });
    }

    @Test
    void canOnlyUpdateEmailAddressToNewValueForEmployee() {
        commandGateway.send(CREATE_EMPLOYEE_1_COMMAND, ProcessingContext.NONE).getResultMessage().join();

        assertThrowsExceptionWithText("Email address cannot be the same as the current one", () -> {
            commandGateway.send(new ChangeEmailAddress(CREATE_EMPLOYEE_1_COMMAND.identifier(),
                                                       "mitchell.herrijgers@axoniq.io"), ProcessingContext.NONE)
                          .getResultMessage().join();
        });

        // But we can update to a new value
        commandGateway.send(new ChangeEmailAddress(CREATE_EMPLOYEE_1_COMMAND.identifier(), "mitchell@axoniq.io"),
                            ProcessingContext.NONE).getResultMessage().join();
    }

    @Test
    void canOnlyUpdateEmailAddressToNewValueForCustomer() {
        commandGateway.send(CREATE_CUSTOMER_1_COMMAND, ProcessingContext.NONE).getResultMessage().join();

        assertThrowsExceptionWithText("Email address cannot be the same as the current one", () -> {
            commandGateway.send(new ChangeEmailAddress(CREATE_CUSTOMER_1_COMMAND.identifier(),
                                                       CREATE_CUSTOMER_1_COMMAND.emailAddress()), ProcessingContext.NONE)
                          .getResultMessage().join();
        });

        // But we can update to a new value
        commandGateway.send(new ChangeEmailAddress(CREATE_CUSTOMER_1_COMMAND.identifier(), "dog@dog.y"),
                            ProcessingContext.NONE).getResultMessage().join();
    }

    @Test
    void canGiveRaiseToEmployee() {
        commandGateway.send(CREATE_EMPLOYEE_1_COMMAND, ProcessingContext.NONE).getResultMessage().join();

        commandGateway.send(new GiveRaise(CREATE_EMPLOYEE_1_COMMAND.identifier(), 5000000.0), ProcessingContext.NONE)
                      .getResultMessage().join();

        assertThrowsExceptionWithText("New salary must be greater than current salary", () -> {
            commandGateway.send(new GiveRaise(CREATE_EMPLOYEE_1_COMMAND.identifier(), 4000000.0),
                                ProcessingContext.NONE)
                          .getResultMessage().join();
        });
    }

    @Test
    void canAssignUpToThreeUncompletedTasksToEmployees() {
        commandGateway.send(CREATE_EMPLOYEE_1_COMMAND, ProcessingContext.NONE).getResultMessage().join();

        for (int i = 0; i < 3; i++) {
            commandGateway.send(new AssignTaskCommand(CREATE_EMPLOYEE_1_COMMAND.identifier(),
                                                      "task-" + i,
                                                      "Task " + i), ProcessingContext.NONE)
                          .getResultMessage().join();
        }

        assertThrowsExceptionWithText("Cannot assign more than 3 tasks to an employee", () -> {
            commandGateway.send(new AssignTaskCommand(CREATE_EMPLOYEE_1_COMMAND.identifier(), "task-4",
                                                      "Task " + 4),
                                ProcessingContext.NONE)
                          .getResultMessage().join();
        });

        // Now, let's complete one
        commandGateway.send(new CompleteTaskCommand(CREATE_EMPLOYEE_1_COMMAND.identifier(), "task-0"),
                            ProcessingContext.NONE)
                      .getResultMessage().join();

        // And assign a new one
        commandGateway.send(new AssignTaskCommand(CREATE_EMPLOYEE_1_COMMAND.identifier(), "task-4",
                                                  "Task " + 4), ProcessingContext.NONE)
                      .getResultMessage().join();
    }


    private void assertThrowsExceptionWithText(String expectedMessage, Runnable runnable) {
        try {
            runnable.run();
        } catch (CompletionException e) {
            Assertions.assertEquals(expectedMessage, e.getCause().getMessage());
            return;
        } catch (Exception e) {
            Assertions.fail("Expected CompletionException, but got: " + e.getClass().getSimpleName());
        }
        Assertions.fail("Expected CompletionException, but got no exception");
    }
}
