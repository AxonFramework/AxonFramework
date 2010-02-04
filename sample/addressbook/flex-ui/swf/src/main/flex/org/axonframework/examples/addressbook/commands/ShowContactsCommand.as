package org.axonframework.examples.addressbook.commands {
import mx.collections.ArrayCollection;
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.ShowContactsMessage;
import org.axonframework.examples.addressbook.model.ContactModel;

public class ShowContactsCommand  extends BaseCommand {

    [Inject]
    public var contactModel:ContactModel;

    public function ShowContactsCommand() {
        super();
    }

    public function execute(message:ShowContactsMessage):AsyncToken {
        trace("Obtaining all contacts");
        return addressService.obtainAllContacts();
    }

    public function result(contacts:ArrayCollection):void {
        trace("Obtained all contacts and putting them into the model");
        contactModel.contacts = contacts;
    }

}
}