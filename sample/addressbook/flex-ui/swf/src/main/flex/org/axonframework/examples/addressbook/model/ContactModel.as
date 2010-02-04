package org.axonframework.examples.addressbook.model {
import mx.collections.ArrayCollection;

import org.axonframework.examples.addressbook.messages.AllContactsResultMessage;
import org.axonframework.examples.addressbook.messages.ContactAddressesMessage;
import org.axonframework.examples.addressbook.messages.UpdatedContactMessage;

public class ContactModel {
    [Bindable]
    public var contacts:ArrayCollection = new ArrayCollection();

    [Bindable]
    public var selectedContact:Contact = new Contact();

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