package org.axonframework.examples.addressbook.commands {
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.NewAddressMessage;
import org.axonframework.examples.addressbook.messages.NotificationMessage;
import org.axonframework.examples.addressbook.model.Address;

/**
 * Command that receives the request to create a new address : NewAddressMessage
 */
public class NewAddressCommand extends BaseCommand {
    private var address:Address;

    public function NewAddressCommand() {
        super();
    }

    public function execute(message:NewAddressMessage):AsyncToken {
        // TODO We should implement some validation here
        this.address = message.address;
        return addressService.createAddress(message.address);
    }

    public function result():void {
        dispatcher(new NotificationMessage("New address for : " + this.address.contactName));
    }

}
}