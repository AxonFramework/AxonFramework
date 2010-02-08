package org.axonframework.examples.addressbook.commands {
import mx.collections.ArrayCollection;
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.SelectContactMessage;
import org.axonframework.examples.addressbook.model.Contact;
import org.axonframework.examples.addressbook.model.ContactModel;

public class SelectContactCommand extends BaseCommand {

    [Inject]
    public var contactModel:ContactModel;

    private var findAddressesFor:Contact;

    public function SelectContactCommand() {
        super();
    }

    public function execute(message:SelectContactMessage):AsyncToken {
        trace('Obtaining the addresses for contact : ' + message.contact.name);
        var cachedContact:Contact = contactModel.findContactByIdentifier(message.contact.uuid);
        contactModel.selectedContact = cachedContact;
        findAddressesFor = cachedContact;

        if (!cachedContact.detailsLoaded) {
            trace("Obtaining addresses from the server");
            return addressService.obtainContactAddresses(message.contact.uuid);
        } else {
            trace("Returning addresses from the cache");
        }
        return null;
    }

    public function result(addresses:ArrayCollection):void {
        trace("Received new addresses : " + addresses.length + " for : " + findAddressesFor.name);
        var cachedContact:Contact = contactModel.findContactByIdentifier(findAddressesFor.uuid);
        cachedContact.addresses = addresses;
        cachedContact.detailsLoaded = true;
    }
}
}