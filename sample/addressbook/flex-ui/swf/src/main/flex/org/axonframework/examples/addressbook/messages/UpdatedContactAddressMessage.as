package org.axonframework.examples.addressbook.messages {
import org.axonframework.examples.addressbook.model.Address;

public class UpdatedContactAddressMessage {
    public var address:Address;

    public function UpdatedContactAddressMessage(address:Address) {
        this.address = address;
    }
}
}