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

import org.axonframework.core.eventhandler.annotation.EventHandler;
import org.axonframework.examples.addressbook.web.dto.ContactDTO;
import org.axonframework.sample.app.ContactCreatedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Jettro Coenradie
 */
@Component
public class ContactListener {

    private JmsContactMessageProducer producer;


    @EventHandler
    public void handleContactCreatedEvent(ContactCreatedEvent event) {
        System.out.println("Received and event : " + event.getName() + event.getEventIdentifier());
        ContactDTO contactDTO = new ContactDTO();
        contactDTO.setName(event.getName());
        contactDTO.setUuid(event.getAggregateIdentifier().toString());
        producer.sendContactUpdate(contactDTO);
    }

    @Autowired
    public void setProducer(JmsContactMessageProducer producer) {
        this.producer = producer;
    }
}
