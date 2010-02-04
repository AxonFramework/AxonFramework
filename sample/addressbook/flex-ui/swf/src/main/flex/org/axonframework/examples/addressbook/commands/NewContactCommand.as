package org.axonframework.examples.addressbook.commands {
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.NewContactMessage;
import org.axonframework.examples.addressbook.messages.NotificationMessage;

public class NewContactCommand extends BaseCommand {

    public function NewContactCommand() {
        super();
    }

    public function execute(message:NewContactMessage):AsyncToken {
        trace('Executing the new contact command');
        return addressService.createContact(message.contact);
    }

    public function result():void {
        trace("The new contact has been created : ");
        dispatcher(new NotificationMessage("New contact has been received by the server"));
    }

}
}