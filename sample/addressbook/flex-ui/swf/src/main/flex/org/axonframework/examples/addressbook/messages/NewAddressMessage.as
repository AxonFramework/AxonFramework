package org.axonframework.examples.addressbook.messages {
import org.axonframework.examples.addressbook.model.Address;

public class NewAddressMessage {

    public var address:Address;

    public function NewAddressMessage(address:Address) {
        this.address = address;
    }
}
}