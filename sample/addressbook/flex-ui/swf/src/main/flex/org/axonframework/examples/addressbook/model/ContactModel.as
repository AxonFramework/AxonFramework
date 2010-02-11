package org.axonframework.examples.addressbook.model {
import mx.collections.ArrayCollection;

import org.axonframework.examples.addressbook.messages.ContactAddressesMessage;
import org.axonframework.examples.addressbook.messages.ErrorNotificationMessage;

public class ContactModel {

    [MessageDispatcher]
    public var dispatcher:Function;

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
            dispatcher(new ErrorNotificationMessage("Got an address for non existing user "
                    + message.contactIdentifier));
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