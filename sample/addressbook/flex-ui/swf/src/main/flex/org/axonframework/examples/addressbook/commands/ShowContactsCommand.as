package org.axonframework.examples.addressbook.commands {
import mx.collections.ArrayCollection;
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.ShowContactsMessage;
import org.axonframework.examples.addressbook.model.ContactModel;

/**
 * Command obtains all contacts from the server
 */
public class ShowContactsCommand  extends BaseCommand {

    [Inject]
    public var contactModel:ContactModel;

    public function ShowContactsCommand() {
        super();
    }

    public function execute(message:ShowContactsMessage):AsyncToken {
        return addressService.obtainAllContacts();
    }

    public function result(contacts:ArrayCollection):void {
        contactModel.contacts = contacts;
    }

}
}