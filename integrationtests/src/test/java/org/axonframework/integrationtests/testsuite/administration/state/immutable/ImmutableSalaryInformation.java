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
import org.axonframework.eventsourcing.annotations.EventSourcingHandler;
import org.axonframework.integrationtests.testsuite.administration.commands.GiveRaise;
import org.axonframework.integrationtests.testsuite.administration.events.RaiseGiven;

public record ImmutableSalaryInformation(
        Double salary,
        String role
) {

    @CommandHandler
    public void handle(GiveRaise command, EventAppender appender) {
        if (command.newSalary() <= salary) {
            throw new IllegalStateException("New salary must be greater than current salary");
        }
        appender.append(new RaiseGiven(command.identifier(), command.newSalary()));
    }

    @EventSourcingHandler
    public ImmutableSalaryInformation on(RaiseGiven event) {
        return new ImmutableSalaryInformation(
                event.newSalary(),
                role
        );
    }
}
