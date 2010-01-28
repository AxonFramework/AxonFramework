package org.axonframework.examples.addressbook.commands {
import org.axonframework.examples.addressbook.messages.SearchForAddressesMessage;
import org.axonframework.examples.addressbook.model.Address;

public class SearchAddressCommand extends BaseCommand {

    public function SearchAddressCommand() {
        super();
    }

    public function execute(address:Address):void {
        dispatcher(new SearchForAddressesMessage(address));
    }

}
}