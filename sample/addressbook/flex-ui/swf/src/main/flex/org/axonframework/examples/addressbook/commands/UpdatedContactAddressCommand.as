package org.axonframework.examples.addressbook.commands {
import org.axonframework.examples.addressbook.messages.NotificationMessage;
import org.axonframework.examples.addressbook.messages.UpdatedContactAddressMessage;
import org.axonframework.examples.addressbook.model.Contact;
import org.axonframework.examples.addressbook.model.ContactModel;

/**
 * Handles an incoming contact address updates by updating the model and sending a notification to the user.
 */
public class UpdatedContactAddressCommand extends BaseCommand {
    [Inject]
    public var contactModel:ContactModel;

    public function UpdatedContactAddressCommand() {
        super();
    }

    public function execute(message:UpdatedContactAddressMessage):void {
        var uuid:String = message.address.contactUUID;
        var contact:Contact = null;
        if (uuid != null && uuid != "") {
            contact = contactModel.findContactByIdentifier(uuid);
        }
        if (contact != null) {
            dispatcher(new NotificationMessage("Received an address update for " + contact.name));
            contact.addAddress(message.address);
        }
    }

}
}