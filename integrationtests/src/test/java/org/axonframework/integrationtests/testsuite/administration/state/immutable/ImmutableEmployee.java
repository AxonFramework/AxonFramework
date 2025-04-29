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

package org.axonframework.integrationtests.testsuite.administration.state.immutable;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.integrationtests.testsuite.administration.AnnotationTestDefinitions;
import org.axonframework.integrationtests.testsuite.administration.commands.AssignTaskCommand;
import org.axonframework.integrationtests.testsuite.administration.commands.CreateEmployee;
import org.axonframework.integrationtests.testsuite.administration.common.PersonIdentifier;
import org.axonframework.integrationtests.testsuite.administration.events.EmailAddressChanged;
import org.axonframework.integrationtests.testsuite.administration.events.EmployeeCreated;
import org.axonframework.integrationtests.testsuite.administration.events.TaskAssigned;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public record ImmutableEmployee(
        PersonIdentifier identifier,
        String lastNames,
        String firstNames,
        String emailAddress,
        @AnnotationTestDefinitions.EntityMember
        ImmutableSalaryInformation salaryInformation,
        @AnnotationTestDefinitions.EntityMember
        List<ImmutableTask> taskList
) implements ImmutablePerson {

    @CommandHandler
    public void handle(CreateEmployee command, EventAppender eventAppender) {
        if (identifier != null) {
            throw new IllegalStateException("Employee already created");
        }
        eventAppender.append(new EmployeeCreated(
                command.identifier(),
                command.lastNames(),
                command.firstNames(),
                command.emailAddress(),
                command.role(),
                command.initialSalary()
        ));
    }

    @CommandHandler
    public void handle(AssignTaskCommand command, EventAppender eventAppender) {
        if (taskList.stream().filter(s -> !s.isCompleted()).collect(Collectors.toSet()).size() >= 3) {
            throw new IllegalStateException("Cannot assign more than 3 tasks to an employee");
        }
        eventAppender.append(new TaskAssigned(
                command.identifier(),
                command.id(),
                command.description()
        ));
    }

    @EventSourcingHandler
    public ImmutableEmployee on(EmployeeCreated event) {
        return new ImmutableEmployee(
                event.identifier(),
                event.lastNames(),
                event.firstNames(),
                event.emailAddress(),
                new ImmutableSalaryInformation(event.initialSalary(), event.role()),
                new ArrayList<>()
        );
    }

    @EventSourcingHandler
    public ImmutableEmployee on(TaskAssigned event) {
        List<ImmutableTask> newTaskList = new ArrayList<>(taskList);
        newTaskList.add(new ImmutableTask(event.taskId(), false));
        return new ImmutableEmployee(
                identifier,
                lastNames,
                firstNames,
                emailAddress,
                salaryInformation,
                newTaskList
        );
    }

    public List<ImmutableTask> getTaskList() {
        return taskList;
    }

    @EventSourcingHandler
    public ImmutableEmployee evolveTaskList(
            List<ImmutableTask> taskList) {
        return new ImmutableEmployee(
                identifier,
                lastNames,
                firstNames,
                emailAddress,
                salaryInformation,
                taskList
        );
    }

    @EventSourcingHandler
    public ImmutableEmployee on(EmailAddressChanged event) {
        return new ImmutableEmployee(
                identifier,
                lastNames,
                firstNames,
                event.emailAddress(),
                salaryInformation,
                taskList
        );
    }

    public ImmutableEmployee evolveSalaryInformation(ImmutableSalaryInformation immutableSalaryInformation) {
        return new ImmutableEmployee(
                identifier,
                lastNames,
                firstNames,
                emailAddress,
                immutableSalaryInformation,
                taskList
        );
    }
}


