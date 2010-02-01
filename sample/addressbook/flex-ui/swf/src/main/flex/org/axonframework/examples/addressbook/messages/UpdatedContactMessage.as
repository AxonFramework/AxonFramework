package org.axonframework.examples.addressbook.messages {
import org.axonframework.examples.addressbook.model.Contact;

public class UpdatedContactMessage {
    public var contact:Contact;

    public function UpdatedContactMessage(contact:Contact) {
        this.contact = contact;
    }
}
}