package org.axonframework.examples.addressbook.commands {
import org.axonframework.examples.addressbook.messages.NewContactMessage;
import org.axonframework.examples.addressbook.model.Contact;

public class NewContactCommand extends BaseCommand {

    public function NewContactCommand() {
        super();
    }

    public function execute(contact:Contact):void {
        trace('Executing the new contact command');
        // TODO add some validation
        dispatcher(new NewContactMessage(contact));
    }
}
}