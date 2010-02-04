package org.axonframework.examples.addressbook.model {
import mx.collections.ArrayCollection;

import org.axonframework.examples.addressbook.messages.ContactAddressesMessage;

public class ContactModel {
    [Bindable]
    public var contacts:ArrayCollection = new ArrayCollection();

    [Bindable]
    public var selectedContact:Contact = new Contact();

    public function ContactModel() {
    }

    [MessageHandler]
    public function obtainedAddressForContact(message:ContactAddressesMessage):void {
        trace('Retrieved an internal message containing the new addresses for a contact');
        var uuid:String = message.contactIdentifier;
        var contact:Contact = findContactByIdentifier(uuid);
        if (contact == null) {
            trace('Retrieving addresses for a contact we do not have');
            // TODO jettro : try to log an error and send it to the server ???
            return;
        }
        contact.addresses = message.addresses;
    }

    public function findContactByIdentifier(identifier:String):Contact {
        for each (var contact:Contact in contacts) {
            if (contact.uuid == identifier) {
                return contact;
            }
        }
        return null;
    }
}
}