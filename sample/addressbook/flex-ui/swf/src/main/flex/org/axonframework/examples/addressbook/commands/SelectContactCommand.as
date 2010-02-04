package org.axonframework.examples.addressbook.commands {
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.SelectContactMessage;
import org.axonframework.examples.addressbook.model.Contact;
import org.axonframework.examples.addressbook.model.ContactModel;

public class SelectContactCommand extends BaseCommand {

    [Inject]
    public var contactModel:ContactModel;

    public function SelectContactCommand() {
        super();
    }

    public function execute(message:SelectContactMessage):AsyncToken {
        trace('Obtaining the addresses for contact : ' + message.contact.name);
        var cachedContact:Contact = contactModel.findContactByIdentifier(message.contact.uuid);
        if (cachedContact == null) {
            return addressService.obtainContactAddresses(message.contact.uuid);
        } else {
            contactModel.selectedContact = cachedContact;
        }
        return null;
    }

    public function result(contact:Contact):void {
        contactModel.contacts.addItem(contact);
        contactModel.selectedContact = contact;
    }
}
}