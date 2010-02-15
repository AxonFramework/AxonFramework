package org.axonframework.examples.addressbook.commands {
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.NotificationMessage;
import org.axonframework.examples.addressbook.messages.RemoveContactMessage;
import org.axonframework.examples.addressbook.model.Contact;

public class RemoveContactCommand extends BaseCommand {
    private var contact:Contact;

    public function RemoveContactCommand() {
        super();
    }

    public function execute(message:RemoveContactMessage):AsyncToken {
        this.contact = message.contact;
        return addressService.removeContact(this.contact.uuid);
    }


    public function result():void {
        dispatcher(new NotificationMessage(this.contact.name + " removed"));
    }
}
}