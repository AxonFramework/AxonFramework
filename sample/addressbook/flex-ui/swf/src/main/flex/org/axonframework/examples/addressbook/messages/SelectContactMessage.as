package org.axonframework.examples.addressbook.messages {
import org.axonframework.examples.addressbook.model.Contact;

public class SelectContactMessage {
    public var contact:Contact;

    public function SelectContactMessage(contact:Contact) {
        this.contact = contact;
    }
}
}