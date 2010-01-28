package org.axonframework.examples.addressbook.messages {
import org.axonframework.examples.addressbook.model.Address;

public class SearchForAddressesMessage {

    public var address:Address;

    public function SearchForAddressesMessage(address:Address = null) {
        this.address = address;
    }
}
}