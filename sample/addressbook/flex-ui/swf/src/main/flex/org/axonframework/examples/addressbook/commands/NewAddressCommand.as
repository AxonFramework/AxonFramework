package org.axonframework.examples.addressbook.commands {
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.NewAddressMessage;
import org.axonframework.examples.addressbook.messages.NotificationMessage;

public class NewAddressCommand extends BaseCommand {
    public function NewAddressCommand() {
        super();
    }


    public function execute(message:NewAddressMessage):AsyncToken {
        trace("Create a new address from the received message");
        // We could implement some validation here
        return addressService.createAddress(message.address);
    }

    public function result():void {
        trace("The new address command has been received");
        dispatcher(new NotificationMessage("The new address command has been received"));
    }

}
}