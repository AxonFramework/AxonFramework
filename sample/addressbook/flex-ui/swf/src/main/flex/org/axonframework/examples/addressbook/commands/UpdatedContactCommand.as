package org.axonframework.examples.addressbook.commands {
import org.axonframework.examples.addressbook.messages.UpdatedContactMessage;
import org.axonframework.examples.addressbook.model.Contact;
import org.axonframework.examples.addressbook.model.ContactModel;

public class UpdatedContactCommand extends BaseCommand {

    [Inject]
    public var contactModel:ContactModel;


    public function UpdatedContactCommand() {
        super();
    }

    public function execute(message:UpdatedContactMessage):void {
        trace('Received an internal message from the consumer that a contact is updated');
        var uuid:String = message.contact.uuid;
        var contact:Contact = null;
        if (uuid != null && uuid != "") {
            contact = contactModel.findContactByIdentifier(uuid);
        }
        if (contact != null) {
            trace('updating a contact after receving an event');
            contact.name = message.contact.name;
        } else {
            trace('creating a contact after receving an event');
            trace('Addresses of new contact : ' + message.contact.addresses);
            contactModel.contacts.addItem(message.contact);
        }
    }

}
}