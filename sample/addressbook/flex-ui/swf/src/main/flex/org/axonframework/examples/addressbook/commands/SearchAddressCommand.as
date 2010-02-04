package org.axonframework.examples.addressbook.commands {
import mx.collections.ArrayCollection;
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.SearchForAddressesMessage;
import org.axonframework.examples.addressbook.model.AddressModel;

public class SearchAddressCommand extends BaseCommand {

    [Inject]
    public var addressModel:AddressModel;

    public function SearchAddressCommand() {
        super();
    }

    public function execute(message:SearchForAddressesMessage):AsyncToken {
        return addressService.searchAddresses(message.address);
    }

    public function result(addresses:ArrayCollection):void {
        addressModel.addresses = addresses;
    }

}
}