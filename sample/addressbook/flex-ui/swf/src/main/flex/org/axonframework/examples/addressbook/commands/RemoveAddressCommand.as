package org.axonframework.examples.addressbook.commands {
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.NotificationMessage;
import org.axonframework.examples.addressbook.messages.RemoveAddressMessage;
import org.axonframework.examples.addressbook.model.Address;
import org.axonframework.examples.addressbook.model.Contact;

public class RemoveAddressCommand extends BaseCommand {
    private var address:Address;
    private var contact:Contact;

    public function RemoveAddressCommand() {
        super();
    }

    public function execute(message:RemoveAddressMessage):AsyncToken {
        this.address = message.address;
        this.contact = message.contact;

        return addressService.removeAddressFor(this.contact.uuid, this.address.type);
    }

    public function result():void {
        dispatcher(new NotificationMessage(
                this.address.type + " address for : " + this.address.contactName + " removed"));
    }

}
}