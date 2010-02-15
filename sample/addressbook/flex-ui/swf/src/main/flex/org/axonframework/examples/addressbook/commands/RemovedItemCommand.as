package org.axonframework.examples.addressbook.commands {
import org.axonframework.examples.addressbook.messages.NotificationMessage;
import org.axonframework.examples.addressbook.messages.RemovedItemMessage;
import org.axonframework.examples.addressbook.model.Contact;
import org.axonframework.examples.addressbook.model.ContactModel;

public class RemovedItemCommand extends BaseCommand {
    [Inject]
    public var contactModel:ContactModel;

    public function RemovedItemCommand() {
        super();
    }

    public function execute(message:RemovedItemMessage):void {

        var uuid:String = message.removed.contactIdentifier;
        var contact:Contact = null;
        if (uuid != null && uuid != "") {
            contact = contactModel.findContactByIdentifier(uuid);
        }
        if (contact != null) {
            if (message.removed.addressType == null || message.removed.addressType.length == 0) {
                contactModel.removeContact(uuid);
                dispatcher(new NotificationMessage("removed the contact " + contact.name));
            } else {
                contact.removeAddress(message.removed.addressType);
                dispatcher(new NotificationMessage(
                        "removed the " + message.removed.addressType + " address for " + contact.name));
            }
        }
    }

}
}