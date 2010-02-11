package org.axonframework.examples.addressbook.commands {
import org.axonframework.examples.addressbook.messages.NotificationMessage;
import org.axonframework.examples.addressbook.messages.UpdatedContactMessage;
import org.axonframework.examples.addressbook.model.Contact;
import org.axonframework.examples.addressbook.model.ContactModel;

/**
 * Handles incoming contact updates
 */
public class UpdatedContactCommand extends BaseCommand {

    [Inject]
    public var contactModel:ContactModel;

    public function UpdatedContactCommand() {
        super();
    }

    public function execute(message:UpdatedContactMessage):void {
        var uuid:String = message.contact.uuid;
        var contact:Contact = null;
        if (uuid != null && uuid != "") {
            contact = contactModel.findContactByIdentifier(uuid);
        }
        if (contact != null) {
            dispatcher(new NotificationMessage('Received contact update for ' + message.contact.name));
            contact.name = message.contact.name;
        } else {
            dispatcher(new NotificationMessage('Received new contact ' + message.contact.name));
            contactModel.contacts.addItem(message.contact);
        }
    }

}
}