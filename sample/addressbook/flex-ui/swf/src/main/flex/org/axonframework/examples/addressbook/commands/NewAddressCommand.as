package org.axonframework.examples.addressbook.commands {
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.NewAddressMessage;
import org.axonframework.examples.addressbook.messages.NotificationMessage;
import org.axonframework.examples.addressbook.model.Address;

public class NewAddressCommand extends BaseCommand {
    private var address:Address;

    public function NewAddressCommand() {
        super();
        trace("New address command created");
    }

    public function execute(message:NewAddressMessage):AsyncToken {
        trace("Create a new address from the received message");
        // We could implement some validation here
        this.address = message.address;
        return addressService.createAddress(message.address);
    }

    public function result():void {
        trace("New address for : " + this.address.street);
        dispatcher(new NotificationMessage("New address for : " + this.address.contactName));
    }

}
}