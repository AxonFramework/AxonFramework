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
import org.axonframework.eventsourcing.annotations.EventCriteriaBuilder;
import org.axonframework.eventsourcing.annotations.EventSourcedEntity;
import org.axonframework.eventsourcing.annotations.reflection.EntityCreator;
import org.axonframework.eventsourcing.annotations.reflection.InjectEntityId;
import org.axonframework.eventstreaming.EventCriteria;
import org.axonframework.integrationtests.testsuite.administration.commands.ChangeEmailAddress;
import org.axonframework.integrationtests.testsuite.administration.common.PersonIdentifier;
import org.axonframework.integrationtests.testsuite.administration.common.PersonType;
import org.axonframework.integrationtests.testsuite.administration.events.EmailAddressChanged;
import org.axonframework.modelling.command.EntityId;

@EventSourcedEntity(
        concreteTypes = {
                MutableEmployee.class,
                MutableCustomer.class
        }
)
public abstract class MutablePerson {

    @EntityId
    protected PersonIdentifier identifier;
    protected String emailAddress;

    @CommandHandler
    public void handle(ChangeEmailAddress command, EventAppender appender) {
        if (command.emailAddress() == null || command.emailAddress().isBlank()) {
            throw new IllegalArgumentException("Email address cannot be null or blank");
        }
        if (command.emailAddress().equals(emailAddress)) {
            throw new IllegalArgumentException("Email address cannot be the same as the current one");
        }

        appender.append(new EmailAddressChanged(command.identifier(), command.emailAddress()));
    }

    @EventSourcingHandler
    public void on(EmailAddressChanged event) {
        this.emailAddress = event.emailAddress();
    }

    @EntityCreator
    public static MutablePerson create(@InjectEntityId PersonIdentifier id) {
        if (id.type() == PersonType.EMPLOYEE) {
            return new MutableEmployee();
        } else if (id.type() == PersonType.CUSTOMER) {
            return new MutableCustomer();
        }
        throw new IllegalArgumentException("Unknown type: " + id.type());
    }

    @EventCriteriaBuilder
    static EventCriteria eventCriteria(PersonIdentifier identifier) {
        return EventCriteria.havingTags("Person", identifier.key());
    }
}
