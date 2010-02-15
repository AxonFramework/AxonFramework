package org.axonframework.examples.addressbook.web.listener;

import org.axonframework.core.eventhandler.annotation.EventHandler;
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
public class AddressListener {
    private final static Logger logger = LoggerFactory.getLogger(AddressListener.class);

    private UpdateMessageProducerForFlex producer;

    @EventHandler
    public void handleAddressCreatedEvent(AddressRegisteredEvent event) {
        logger.debug("Received and event with name {} and identifier {}",
                event.getAggregateIdentifier(), event.getEventIdentifier());
        AddressDTO addressDTO = AddressDTO.createFrom(event.getAddress(), event.getContactIdentifier(), event.getType());
        producer.sendAddressUpdate(addressDTO);
    }

    @EventHandler
    public void handleAddressRemovedEvent(AddressRemovedEvent event) {
        RemovedDTO removedDTO = RemovedDTO.createRemovedFrom(event.getContactIdentifier().toString(), event.getType());
        producer.sendRemovedUpdate(removedDTO);
    }

    @Autowired
    public void setProducer(UpdateMessageProducerForFlex producer) {
        this.producer = producer;
    }

}
