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
import org.axonframework.eventsourcing.annotations.EventSourcedEntity;
import org.axonframework.integrationtests.testsuite.administration.commands.ChangeEmailAddress;
import org.axonframework.integrationtests.testsuite.administration.common.PersonIdentifier;
import org.axonframework.integrationtests.testsuite.administration.events.EmailAddressChanged;
import org.axonframework.modelling.command.EntityId;

@EventSourcedEntity(
        concreteTypes = {
                ImmutableEmployee.class,
                ImmutableCustomer.class
        }, tagKey = "Person"
)
public interface ImmutablePerson {

    @EntityId
    PersonIdentifier identifier();

    String emailAddress();

    @CommandHandler
    default void handle(ChangeEmailAddress command, EventAppender appender) {
        if (command.emailAddress() == null || command.emailAddress().isBlank()) {
            throw new IllegalArgumentException("Email address cannot be null or blank");
        }
        if (command.emailAddress().equals(emailAddress())) {
            throw new IllegalArgumentException("Email address cannot be the same as the current one");
        }

        appender.append(new EmailAddressChanged(command.identifier(), command.emailAddress()));
    }

    @EventSourcingHandler
    ImmutablePerson on(EmailAddressChanged event);
}
