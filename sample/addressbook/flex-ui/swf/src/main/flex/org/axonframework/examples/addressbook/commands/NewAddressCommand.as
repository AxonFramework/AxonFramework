package org.axonframework.examples.addressbook.commands {
import org.axonframework.examples.addressbook.messages.NewAddressMessage;
import org.axonframework.examples.addressbook.model.Address;

public class NewAddressCommand extends BaseCommand {
    public function NewAddressCommand() {
        super();
    }

    public function execute(address:Address):void {
        // We could implement some validation here
        dispatcher(new NewAddressMessage(address));
    }
}
}