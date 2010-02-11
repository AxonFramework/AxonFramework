package org.axonframework.examples.addressbook.commands {
import mx.collections.ArrayCollection;
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.SearchForAddressesMessage;
import org.axonframework.examples.addressbook.model.AddressModel;

/**
 * Query command that executes a call to the backend to query all addresses and present them using the addressModel
 */
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