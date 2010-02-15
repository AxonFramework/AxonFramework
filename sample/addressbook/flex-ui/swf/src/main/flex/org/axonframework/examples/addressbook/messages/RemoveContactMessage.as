package org.axonframework.examples.addressbook.messages {
import org.axonframework.examples.addressbook.model.Contact;

public class RemoveContactMessage {
    public var contact:Contact;

    public function RemoveContactMessage(contact:Contact) {
        this.contact = contact;
    }
}
}