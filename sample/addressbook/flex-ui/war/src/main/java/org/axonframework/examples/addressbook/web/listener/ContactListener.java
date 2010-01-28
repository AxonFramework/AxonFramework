package org.axonframework.examples.addressbook.web.listener;

import org.axonframework.core.eventhandler.annotation.EventHandler;
import org.axonframework.sample.core.ContactCreatedEvent;
import org.springframework.stereotype.Component;

/**
 * @author Jettro Coenradie
 */
@Component
public class ContactListener {

    @EventHandler
    public void handleContactCreatedEvent(ContactCreatedEvent event) {
        System.out.println("Received and event : " + event.getName());
    }
}
