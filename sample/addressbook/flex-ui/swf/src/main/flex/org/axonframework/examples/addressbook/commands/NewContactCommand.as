package org.axonframework.examples.addressbook.commands {
import mx.rpc.AsyncToken;

import org.axonframework.examples.addressbook.messages.NewContactMessage;
import org.axonframework.examples.addressbook.messages.NotificationMessage;
import org.axonframework.examples.addressbook.messages.ValidationMessage;
import org.axonframework.examples.addressbook.model.Contact;

/**
 * Command that makes use of the received NewContactMessage mesage to create a new Contact
 */
public class NewContactCommand extends BaseCommand {
    private var contact:Contact;

    public function NewContactCommand() {
        super();
    }

    public function execute(message:NewContactMessage):AsyncToken {
        if (message.contact.name.length < 1) {
            dispatcher(new ValidationMessage("Name field is required for contact"));
            return null;
        }
        this.contact = message.contact;
        return addressService.createContact(this.contact);
    }

    public function result():void {
        dispatcher(new NotificationMessage("New contact : " + contact.name));
    }

}
}