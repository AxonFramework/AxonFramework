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

import org.axonframework.eventhandling.SequentialPolicy;
import org.axonframework.eventhandling.annotation.AsynchronousEventListener;
import org.axonframework.eventhandling.annotation.EventHandler;
import org.axonframework.examples.addressbook.web.dto.AddressDTO;
import org.axonframework.examples.addressbook.web.dto.RemovedDTO;
import org.axonframework.sample.app.AddressRegisteredEvent;
import org.axonframework.sample.app.AddressRemovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Jettro Coenradie
 */
@Component
@AsynchronousEventListener(sequencingPolicyClass = SequentialPolicy.class)
public class AddressListener {

    private final static Logger logger = LoggerFactory.getLogger(AddressListener.class);

    private UpdateMessageProducerForFlex producer;

    @EventHandler
    public void handleAddressCreatedEvent(AddressRegisteredEvent event) {
        logger.debug("Received an event with name {} and identifier {}",
                     event.getAggregateIdentifier(), event.getEventIdentifier());
        AddressDTO addressDTO = AddressDTO.createFrom(
                event.getAddress(), event.getContactIdentifier(), event.getType());
        producer.sendAddressUpdate(addressDTO);
    }

    @EventHandler
    public void handleAddressRemovedEvent(AddressRemovedEvent event) {
        RemovedDTO removedDTO = RemovedDTO.createRemovedFrom(
                event.getContactIdentifier().toString(), event.getType());
        producer.sendRemovedUpdate(removedDTO);
    }

    @Autowired
    public void setProducer(UpdateMessageProducerForFlex producer) {
        this.producer = producer;
    }

}
