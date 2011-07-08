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

package org.axonframework.examples.addressbook.rest.listener;

import org.axonframework.eventhandling.SequentialPolicy;
import org.axonframework.eventhandling.annotation.AsynchronousEventListener;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.sample.app.api.AddressRegisteredEvent;
import org.axonframework.sample.app.api.AddressRemovedEvent;
import org.axonframework.sample.app.query.AddressEntry;
import org.axonframework.sample.app.query.ContactEntry;
import org.axonframework.sample.app.query.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>Listener for address related events. All events are pushed to the provided Publisher.</p>
 *
 * @author Jettro Coenradie
 */
@Component
@AsynchronousEventListener(sequencingPolicyClass = SequentialPolicy.class)
public class AddressListener {
    private final static Logger logger = LoggerFactory.getLogger(AddressListener.class);
    private Publisher publisher;
    private ContactRepository contactRepository;

    @EventHandler
    public void handleAddressCreatedEvent(AddressRegisteredEvent event) {
        logger.debug("Received a address created event with type {} and identifier {}",
                event.getType().toString(), event.getEventIdentifier());
        ContactEntry contactEntry = obtainContactByIdentifier(event.getContactIdentifier());
        AddressEntry value = new AddressEntry();
        value.setIdentifier(event.getContactIdentifier());
        value.setName(contactEntry.getName());
        value.setAddressType(event.getType());
        value.setStreetAndNumber(event.getAddress().getStreetAndNumber());
        value.setZipCode(event.getAddress().getZipCode());
        value.setCity(event.getAddress().getCity());
        publisher.publish(new Message<AddressEntry>("address-created", value));
    }

    @EventHandler
    public void handleAddressRemovedEvent(AddressRemovedEvent event) {
        ContactEntry contactEntry = obtainContactByIdentifier(event.getContactIdentifier());
        logger.debug("Received an address removed event with type {} for contact {}", event.getType(), contactEntry.getIdentifier());
        Map<String, Object> message = new HashMap<String, Object>();
        message.put("contact", contactEntry);
        message.put("addressType", event.getType());
        publisher.publish(new Message<Map<String, Object>>("address-removed", message));
    }

    private ContactEntry obtainContactByIdentifier(String identifier) {
        return contactRepository.loadContactDetails(identifier);
    }

    @Autowired
    public void setPublisher(Publisher publisher) {
        this.publisher = publisher;
    }

    @Autowired
    public void setContactRepository(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }
}
