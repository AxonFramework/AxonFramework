package org.axonframework.examples.addressbook.messages {
import org.axonframework.examples.addressbook.model.Address;
import org.axonframework.examples.addressbook.model.Contact;

public class RemoveAddressMessage {
    public var address:Address;
    public var contact:Contact;

    public function RemoveAddressMessage(address:Address, contact:Contact) {
        this.address = address;
        this.contact = contact;
    }
}
}