/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.sample.app.command;

import org.axonframework.core.repository.eventsourcing.EventSourcingRepository;

import java.util.UUID;

/**
 * <p>Event sourcing repository class that handles all @{code Contact} actions like save and delete. Through it's
 * parent class generated domain events are dispatched.</p>
 *
 * @author Allard Buijze
 */
class ContactRepository extends EventSourcingRepository<Contact> {

    /**
     * {@inheritDoc}
     */
    @Override
    protected Contact instantiateAggregate(UUID aggregateIdentifier) {
        return new Contact(aggregateIdentifier);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getTypeIdentifier() {
        return Contact.class.getSimpleName();
    }
}
