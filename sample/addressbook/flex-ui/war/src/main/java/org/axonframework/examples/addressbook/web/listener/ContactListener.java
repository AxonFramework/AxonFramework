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

package org.axonframework.examples.addressbook.web.listener;

import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.examples.addressbook.web.dto.ContactDTO;
import org.axonframework.examples.addressbook.web.dto.RemovedDTO;
import org.axonframework.sample.app.api.ContactCreatedEvent;
import org.axonframework.sample.app.api.ContactDeletedEvent;
import org.axonframework.sample.app.api.ContactNameChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Jettro Coenradie
 */
@Component
public class ContactListener {

    private final static Logger logger = LoggerFactory.getLogger(ContactListener.class);

    private UpdateMessageProducerForFlex producer;

    @EventHandler
    public void handleContactCreatedEvent(ContactCreatedEvent event) {
        logger.debug("Received and event with name {} and identifier {}", event.getName(), event.getEventIdentifier());
        ContactDTO contactDTO = new ContactDTO();
        contactDTO.setName(event.getName());
        contactDTO.setUuid(event.getAggregateIdentifier().toString());
        producer.sendContactUpdate(contactDTO);
    }

    @EventHandler
    public void handleContactRemovedEvent(ContactDeletedEvent event) {
        RemovedDTO removedDTO = RemovedDTO.createRemovedFrom(event.getContactIdentifier().toString());
        producer.sendRemovedUpdate(removedDTO);
    }

    @EventHandler
    public void handleContactNameChangedEvent(ContactNameChangedEvent event) {
        logger.debug("Received and event with new name {} and identifier {}", event.getNewName(), event.getEventIdentifier());
        ContactDTO contactDTO = new ContactDTO();
        contactDTO.setName(event.getNewName());
        contactDTO.setUuid(event.getAggregateIdentifier().toString());
        producer.sendContactUpdate(contactDTO);
    }

    @Autowired
    public void setProducer(UpdateMessageProducerForFlex producer) {
        this.producer = producer;
    }
}
