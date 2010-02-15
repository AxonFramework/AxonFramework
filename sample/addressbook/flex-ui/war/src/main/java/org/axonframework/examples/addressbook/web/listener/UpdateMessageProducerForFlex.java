package org.axonframework.examples.addressbook.web.listener;

import org.axonframework.examples.addressbook.web.dto.AddressDTO;
import org.axonframework.examples.addressbook.web.dto.ContactDTO;
import org.axonframework.examples.addressbook.web.dto.RemovedDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.flex.messaging.MessageTemplate;
import org.springframework.stereotype.Component;

/**
 * @author Jettro Coenradie
 */
@Component
public class UpdateMessageProducerForFlex {

    private MessageTemplate template;

    @Autowired
    public UpdateMessageProducerForFlex(MessageTemplate template) {
        this.template = template;
    }

    public void sendContactUpdate(final ContactDTO contactDTO) {
        template.send(contactDTO);
    }

    public void sendAddressUpdate(final AddressDTO addressDTO) {
        template.send(addressDTO);
    }

    public void sendRemovedUpdate(final RemovedDTO removedDTO) {
        template.send(removedDTO);
    }
}
