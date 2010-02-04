package org.axonframework.examples.addressbook.commands {
import mx.collections.ArrayCollection;
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.SelectContactMessage;
import org.axonframework.examples.addressbook.model.Address;
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
        if (cachedContact.addresses == null || cachedContact.addresses.length == 0) {
            trace("Obtaining addresses from the server");
            return addressService.obtainContactAddresses(message.contact.uuid);
        } else {
            trace("Returning addresses from the cache");
            contactModel.selectedContact = cachedContact;
        }
        return null;
    }

    public function result(addresses:ArrayCollection):void {
        var uuid:String = (addresses.getItemAt(0) as Address).contactUUID;
        var cachedContact:Contact = contactModel.findContactByIdentifier(uuid);
        cachedContact.addresses = addresses;
        contactModel.selectedContact = cachedContact;
    }
}
}