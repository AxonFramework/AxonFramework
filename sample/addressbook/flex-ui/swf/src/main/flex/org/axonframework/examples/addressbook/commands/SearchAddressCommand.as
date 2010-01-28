package org.axonframework.examples.addressbook.commands {
import org.axonframework.examples.addressbook.messages.SearchForAddressesMessage;
import org.axonframework.examples.addressbook.model.Address;

public class SearchAddressCommand {
    [MessageDispatcher]
    public var dispatcher:Function;

    public function SearchAddressCommand() {
        trace('SearchAddressCommand is created');
    }

    public function create(address:Address):void {
        dispatcher(new SearchForAddressesMessage(address));
    }

}
}