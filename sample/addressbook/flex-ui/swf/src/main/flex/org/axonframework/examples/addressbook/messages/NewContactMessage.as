package org.axonframework.examples.addressbook.messages {
import org.axonframework.examples.addressbook.model.Contact;

public class NewContactMessage {
    public var contact:Contact;

    public function NewContactMessage(contact:Contact) {
        trace("New contact message created");
        this.contact = contact;
    }
}
}