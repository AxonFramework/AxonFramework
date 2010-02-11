package org.axonframework.examples.addressbook.commands {
import mx.collections.ArrayCollection;
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.SelectContactMessage;
import org.axonframework.examples.addressbook.model.Contact;
import org.axonframework.examples.addressbook.model.ContactModel;

/**
 * Command class that is used to obtain addresses (or details) from a selected contact. Based on the provided contact
 * uuid or identifier we obtain the details. Whether the details need to be obtained from the server of from the cache
 * depends on the "details loaded" flag.
 *
 * If the details are already obtained from the server, the cached details are used. Updates to the details are pushed
 * by the server.
 */
public class SelectContactCommand extends BaseCommand {

    [Inject]
    public var contactModel:ContactModel;

    private var findAddressesFor:Contact;

    public function SelectContactCommand() {
        super();
    }

    public function execute(message:SelectContactMessage):AsyncToken {
        var cachedContact:Contact = contactModel.findContactByIdentifier(message.contact.uuid);
        contactModel.selectedContact = cachedContact;
        findAddressesFor = cachedContact;

        if (!cachedContact.detailsLoaded) {
            return addressService.obtainContactAddresses(message.contact.uuid);
        }
        return null;
    }

    public function result(addresses:ArrayCollection):void {
        var cachedContact:Contact = contactModel.findContactByIdentifier(findAddressesFor.uuid);
        cachedContact.addresses = addresses;
        cachedContact.detailsLoaded = true;
    }
}
}