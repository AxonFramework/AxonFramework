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

package org.axonframework.integrationtests.testsuite.administration.state.mutable;

import org.axonframework.commandhandling.annotation.CommandHandler;
import org.axonframework.eventhandling.gateway.EventAppender;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.entity.annotation.EntityMember;
import org.axonframework.integrationtests.testsuite.administration.commands.AssignTaskCommand;
import org.axonframework.integrationtests.testsuite.administration.commands.CreateEmployee;
import org.axonframework.integrationtests.testsuite.administration.events.EmployeeCreated;
import org.axonframework.integrationtests.testsuite.administration.events.TaskAssigned;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MutableEmployee extends MutablePerson {

    @EntityMember
    private MutableSalaryInformation salary;
    @EntityMember
    private List<MutableTask> taskList = new ArrayList<>();

    @CommandHandler
    public void handle(CreateEmployee command, EventAppender eventAppender) {
        if (identifier != null) {
            throw new IllegalStateException("Employee is an existing entity");
        }
        eventAppender.append(new EmployeeCreated(
                command.identifier(),
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
    public void on(EmployeeCreated event) {
        this.identifier = event.identifier();
        this.emailAddress = event.emailAddress();
        this.salary = new MutableSalaryInformation(event.initialSalary(), event.role());
    }

    @EventSourcingHandler
    public void on(TaskAssigned event) {
        taskList.add(new MutableTask(event.taskId()));
    }

    public List<MutableTask> getTaskList() {
        return taskList;
    }

    public void setTaskList(
            List<MutableTask> taskList) {
        this.taskList = taskList;
    }
}


