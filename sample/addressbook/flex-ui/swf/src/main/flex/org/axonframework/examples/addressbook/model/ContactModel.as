package org.axonframework.examples.addressbook.model {
import mx.collections.ArrayCollection;

import org.axonframework.examples.addressbook.messages.AllContactsResultMessage;
import org.axonframework.examples.addressbook.messages.UpdatedContactAddressMessage;
import org.axonframework.examples.addressbook.messages.UpdatedContactMessage;

public class ContactModel {
    [Bindable]
    public var contacts:ArrayCollection = new ArrayCollection();

    public function ContactModel() {
    }

    [MessageHandler]
    public function searchResults(message:AllContactsResultMessage):void {
        this.contacts = message.results;
    }

    [MessageHandler]
    public function updateContact(message:UpdatedContactMessage):void {
        trace('Received an internal message from the consumer that a contact is updated');
        var uuid:String = message.contact.uuid;
        var contact:Contact = null;
        if (uuid != null && uuid != "") {
            contact = findContactByIdentifier(uuid);
        }
        if (contact != null) {
            trace('updating a contact after receving an event');
            contact.name = message.contact.name;
        } else {
            trace('creating a contact after receving an event');
            contacts.addItem(message.contact);
        }
    }

    [MessageHandler]
    public function updateAddressForContact(message:UpdatedContactAddressMessage):void {
        // TODO implement something
    }

    private function findContactByIdentifier(identifier:String):Contact {
        for each (var contact:Contact in contacts) {
            if (contact.uuid == identifier) {
                return contact;
            }
        }
        return null;
    }
}
}