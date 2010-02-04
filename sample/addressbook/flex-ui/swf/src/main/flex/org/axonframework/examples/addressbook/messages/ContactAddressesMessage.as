package org.axonframework.examples.addressbook.messages {
import mx.collections.ArrayCollection;

public class ContactAddressesMessage {
    public var contactIdentifier:String;
    public var addresses:ArrayCollection;

    public function ContactAddressesMessage(contactIdentifier:String, addresses:ArrayCollection) {
        this.contactIdentifier = contactIdentifier;
        this.addresses = addresses;
    }
}
}