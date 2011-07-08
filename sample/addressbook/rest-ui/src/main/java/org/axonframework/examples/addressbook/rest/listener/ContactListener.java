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

import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.sample.app.api.ContactCreatedEvent;
import org.axonframework.sample.app.api.ContactDeletedEvent;
import org.axonframework.sample.app.api.ContactNameChangedEvent;
import org.axonframework.sample.app.query.ContactEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * <p>Listener for Contact related events. The data of the events is published using the provided publisher.</p>
 *
 * @author Jettro Coenradie
 */
@Component
public class ContactListener {

    private final static Logger logger = LoggerFactory.getLogger(ContactListener.class);
    private Publisher publisher;

    @EventHandler
    public void handleContactCreatedEvent(ContactCreatedEvent event) {
        logger.debug("Received a contact created event with name {} and identifier {}",
                event.getName(), event.getEventIdentifier());
        ContactEntry value = new ContactEntry();
        value.setName(event.getName());
        value.setIdentifier(event.getContactIdentifier());
        Message<ContactEntry> message = new Message<ContactEntry>("contact-created", value);
        publisher.publish(message);
    }

    @EventHandler
    public void handleContactRemovedEvent(ContactDeletedEvent event) {
        logger.debug("Contact removed event with identifier {}", event.getContactIdentifier());
        ContactEntry value = new ContactEntry();
        value.setIdentifier(event.getContactIdentifier());
        Message<ContactEntry> message = new Message<ContactEntry>("contact-removed", value);
        publisher.publish(message);
    }

    @EventHandler
    public void handleContactNameChangedEvent(ContactNameChangedEvent event) {
        logger.debug("Received a contact name changed event with new name {} and identifier {}",
                event.getNewName(), event.getEventIdentifier());
        ContactEntry value = new ContactEntry();
        value.setIdentifier(event.getContactIdentifier());
        value.setName(event.getNewName());
        Message<ContactEntry> message = new Message<ContactEntry>("contact-changed", value);
        publisher.publish(message);
    }

    @Autowired
    public void setPublisher(Publisher publisher) {
        this.publisher = publisher;
    }
}
