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

package org.axonframework.sample.core.command;

import org.axonframework.core.AggregateDeletedEvent;
import org.axonframework.core.eventhandler.annotation.AbstractAnnotatedAggregateRoot;
import org.axonframework.core.eventhandler.annotation.EventHandler;
import org.axonframework.sample.core.Address;
import org.axonframework.sample.core.AddressAddedEvent;
import org.axonframework.sample.core.AddressChangedEvent;
import org.axonframework.sample.core.AddressRegisteredEvent;
import org.axonframework.sample.core.AddressRemovedEvent;
import org.axonframework.sample.core.AddressType;
import org.axonframework.sample.core.ContactCreatedEvent;
import org.axonframework.sample.core.ContactDeletedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author Allard Buijze
 */
class Contact extends AbstractAnnotatedAggregateRoot {

    private Map<AddressType, Address> addresses = new HashMap<AddressType, Address>();

    public Contact(String name) {
        apply(new ContactCreatedEvent(name));
    }

    public Contact(UUID identifier) {
        super(identifier);
    }

    public void registerAddress(AddressType type, Address address) {
        if (addresses.containsKey(type)) {
            apply(new AddressChangedEvent(type, address));
        } else {
            apply(new AddressAddedEvent(type, address));
        }
    }

    public void removeAddress(AddressType type) {
        if (addresses.remove(type) != null) {
            apply(new AddressRemovedEvent(type));
        }
    }

    @EventHandler
    protected void handleContactCreatedEvent(ContactCreatedEvent event) {
    }

    @EventHandler
    protected void handleAddressAddedEvent(AddressRegisteredEvent event) {
        addresses.put(event.getType(), event.getAddress());
    }

    @EventHandler
    protected void handleAddressRemovedEvent(AddressRemovedEvent event) {
        addresses.remove(event.getType());
    }

    @Override
    protected AggregateDeletedEvent createDeletedEvent() {
        return new ContactDeletedEvent();
    }

}
