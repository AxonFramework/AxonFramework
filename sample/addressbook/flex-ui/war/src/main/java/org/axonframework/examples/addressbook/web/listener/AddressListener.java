package org.axonframework.examples.addressbook.web.listener;

import org.axonframework.core.eventhandler.annotation.EventHandler;
import org.axonframework.examples.addressbook.web.dto.AddressDTO;
import org.axonframework.sample.app.AddressAddedEvent;
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
    public void handleContactCreatedEvent(AddressAddedEvent event) {
        logger.debug("Received and event with name {} and identifier {}",
                event.getAggregateIdentifier(), event.getEventIdentifier());
        AddressDTO addressDTO = AddressDTO.createFrom(event.getAddress(), event.getContactIdentifier(), event.getType());
        producer.sendAddressUpdate(addressDTO);
    }


    @Autowired
    public void setProducer(UpdateMessageProducerForFlex producer) {
        this.producer = producer;
    }

}
